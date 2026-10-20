package app.freerouting.board.facade;

import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.autoroute.path.FoundConnectionInserter;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;

/** Private insertion methods on real board models. No maze, prerecorded connection or route. */
public final class ConnectionInserterOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static java.lang.reflect.Method method(String name,Class<?>... types)throws Exception{var m=FoundConnectionInserter.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
  static Object history(Path path,JsonObject in)throws Exception {
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    int angle=in.get("angle").getAsInt(),pinId=in.get("pin").getAsInt();b.rules.setTraceAngleRestriction(angle==90?AngleRestriction.NINETY_DEGREE:AngleRestriction.FORTYFIVE_DEGREE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());b.startMarkingChangedArea();
    Pin pin=pinId==0?null:(Pin)b.getItem(pinId);Point origin=pin==null?new IntPoint(1500000,-1150000):pin.getCenter();int net=pin==null?2:pin.getNetNumber(0);
    var settings=new RouterSettings(b);settings.setFanoutEnabled(false);settings.setRunOptimizer(false);settings.setAutomaticNeckdown(in.get("neckdown").getAsBoolean());var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());
    var costs=new ArrayList<Object>();for(var c:settings.getTraceCosts())costs.add(list(bits(c.horizontal()),bits(c.vertical())));var active=new ArrayList<Object>();var bend=new ArrayList<Object>();for(int i=0;i<b.getLayerCount();i++){active.add(settings.getLayerActive(i));bend.add(bits(settings.getBendCost(i)));}
    var resolved=obj("traceCosts",costs,"active",active,"bend",bend,"viaCosts",settings.getViaCosts(),"viasAllowed",settings.getViasAllowed(),"neckdown",settings.getAutomaticNeckdown());
    Arrays.fill(ctrl.traceHalfWidth,in.get("width").getAsInt());ctrl.maxShoveTraceRecursionDepth=3;ctrl.maxShoveViaRecursionDepth=3;ctrl.maxSpringOverRecursionDepth=2;ctrl.pullTightAccuracy=100;ctrl.isFanout=in.get("fanout").getAsBoolean();ctrl.removeUnconnectedVias=false;
    if(in.get("obstacle").getAsBoolean())b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin.translateBy(new IntVector(0,-10000)),origin.translateBy(new IntVector(0,10000))}),0,1000,new int[]{net==3?2:3},1,FixedState.USER_FIXED);
    if(in.has("neckObstacle")&&in.get("neckObstacle").getAsBoolean())b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin.translateBy(new IntVector(-5000,in.has("neckOffset")?in.get("neckOffset").getAsInt():6000)),origin.translateBy(new IntVector(5000,in.has("neckOffset")?in.get("neckOffset").getAsInt():6000))}),0,500,new int[]{net==3?2:3},1,FixedState.USER_FIXED);
    PolylineTrace target=null;if(in.get("mode").getAsString().equals("connect"))target=b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin.translateBy(new IntVector(5000,-5000)),origin.translateBy(new IntVector(5000,5000))}),0,1000,new int[]{net},1,in.get("fixed").getAsBoolean()?FixedState.USER_FIXED:FixedState.UNFIXED);
    var ctor=FoundConnectionInserter.class.getDeclaredConstructor(RoutingBoard.class,AutorouteControl.class);ctor.setAccessible(true);var inserter=ctor.newInstance(b,ctrl);
    var points=new ArrayList<IntPoint>();for(var raw:in.getAsJsonArray("points")){var p=raw.getAsJsonArray();points.add((IntPoint)origin.translateBy(new IntVector(p.get(0).getAsInt(),p.get(1).getAsInt())));}
    Point from=points.isEmpty()?null:points.get(0),to=points.isEmpty()?null:points.get(points.size()-1);Pin startPin=in.get("startPin").getAsBoolean()?pin:null,endPin=in.get("endPin").getAsBoolean()?pin:null;
    var refs=new PolylineTighteningOracle.Refs();refs.id(from);refs.id(to);
    b.setShoveFailingObstacle(b.getOutline());b.setShoveFailingLayer(7);b.generateSnapshot();int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();
    for(String op:new String[]{"call","undo","redo"}){
      Object value=null;String error=null;
      try {
        if(op.equals("undo"))value=b.undo(changed);
        else if(op.equals("redo"))value=b.redo(changed);
        else switch(in.get("mode").getAsString()){
          case "trace"->{var type=Class.forName("app.freerouting.autoroute.path.FoundConnectionLocator$ResultItem");var c=type.getDeclaredConstructor(IntPoint[].class,int.class);c.setAccessible(true);var input=c.newInstance(points.toArray(IntPoint[]::new),0);value=method("insertTrace",type).invoke(inserter,input);}
          case "via"->value=method("insertVia",Point.class,int.class,int.class).invoke(inserter,from,in.get("fromLayer").getAsInt(),in.get("toLayer").getAsInt());
          case "neck"->value=method("insertNeckdown",Point.class,Point.class,int.class,Pin.class,Pin.class).invoke(inserter,from,to,0,startPin,endPin);
          case "try"->{Point result=(Point)method("tryNeckDown",Point.class,Point.class,int.class,Pin.class,boolean.class).invoke(inserter,from,to,0,pin,in.get("startPin").getAsBoolean());value=obj("id",refs.id(result),"point",NeutralBoardModel.xy(result),"isFrom",result==from,"isTo",result==to);}
          case "micro"->value=method("insertFanoutMicroNeckdown",Point.class,Point.class,int.class,int[].class,Pin.class,Pin.class).invoke(inserter,from,to,0,new int[]{net},startPin,endPin);
          case "connect"->value=b.connectToTrace((IntPoint)from,target,in.get("width").getAsInt(),1);
        }
      }catch(Throwable failure){error=ShapeEntryOracle.error(failure instanceof java.lang.reflect.InvocationTargetException?failure.getCause():failure);}
      Point first=(Point)field(inserter,"firstCorner"),last=(Point)field(inserter,"lastCorner");
      rows.add(obj("op",op,"value",value,"error",error,"first",NeutralBoardModel.xy(first),"last",NeutralBoardModel.xy(last),"firstLastAlias",first==last,"failing",b.getShoveFailingObstacle()==null?null:b.getShoveFailingObstacle().getId(),"failingLayer",b.getShoveFailingLayer(),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"resolvedSettings",resolved,"net",net,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic private-inserter unit histories on public ECC83; factory/maze/full autoroute not exercised");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
