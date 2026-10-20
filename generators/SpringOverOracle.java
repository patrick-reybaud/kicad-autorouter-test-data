package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.optimize.TraceShover;
import app.freerouting.board.trace.PolylineTrace;
import static app.freerouting.board.facade.NeutralBoardModel.list;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;

/** Actual source spring-over decisions; these are unit histories, not a route. */
public final class SpringOverOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static final class Refs {final List<Object> values=new ArrayList<>();int id(Object p){if(p==null)return 0;for(int i=0;i<values.size();i++)if(values.get(i)==p)return i+1;values.add(p);return values.size();}}
  static Object cache(Polyline p,Refs refs)throws Exception {
    var corners=(Point[])field(p,"precalculatedCorners");var floats=(FloatPoint[])field(p,"precalculatedFloatCorners");var box=(IntBox)field(p,"precalculatedBoundingBox");
    Object cs=null,fs=null;if(corners!=null){var out=new ArrayList<Object>();for(var q:corners)out.add(list(refs.id(q),NeutralBoardModel.xy(q)));cs=out;}if(floats!=null){var out=new ArrayList<Object>();for(var q:floats)out.add(q==null?null:list(bits(q.x),bits(q.y)));fs=out;}
    return obj("corners",cs,"floats",fs,"box",box==null?null:shape(box));
  }
  static Object geometry(Polyline p,Refs points,Refs lines,Refs polylines)throws Exception {
    if(p==null)return null;int id=polylines.id(p);Object before=cache(p,points);var ls=new ArrayList<Object>();for(var l:p.lines)ls.add(list(lines.id(l),NeutralBoardModel.xy(l.a),NeutralBoardModel.xy(l.b)));
    var corners=new ArrayList<Object>();for(int i=0;i<p.cornerCount();i++)corners.add(NeutralBoardModel.xy(p.corner(i)));
    String length=bits(p.lengthApprox());return obj("id",id,"lines",ls,"corners",corners,"length",length,"cacheBefore",before,"cacheAfter",cache(p,points));
  }
  static Object history(Path path,JsonObject in)throws Exception {
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    int angle=in.get("angle").getAsInt();b.rules.setTraceAngleRestriction(angle==90?AngleRestriction.NINETY_DEGREE:angle==45?AngleRestriction.FORTYFIVE_DEGREE:AngleRestriction.NONE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());b.startMarkingChangedArea();
    Point origin=in.get("pin").getAsBoolean()?((Pin)b.getItem(3)).getCenter():new IntPoint(1500000,-1150000);
    int variant=in.get("variant").getAsInt();
    if(variant==1||variant==2||variant==8)b.insertVia(b.library.getViaPadstack(0),origin,new int[]{variant==8?2:3},1,variant==2?FixedState.UNFIXED:FixedState.USER_FIXED,true);
    if(variant>=3&&variant<=7){
      Point[] ps=variant==4?new Point[]{origin.translateBy(new IntVector(0,-5000)),origin,origin.translateBy(new IntVector(5000,0))}:new Point[]{origin.translateBy(new IntVector(0,-5000)),origin.translateBy(new IntVector(0,5000))};
      b.insertTraceWithoutCleaning(new Polyline(ps),0,1000,new int[]{3},1,variant==5?FixedState.UNFIXED:FixedState.SHOVE_FIXED);
      if(variant==6||variant==7)b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin.translateBy(new IntVector(-5000,0)),origin.translateBy(new IntVector(5000,0))}),0,variant==7?20000:1000,new int[]{3},1,FixedState.SHOVE_FIXED);
    }
    if(variant>=9){
      var box=new IntBox(-5000,-3000,5000,3000);var translation=origin.differenceBy(Point.ZERO);
      if(variant==9)b.insertObstacle(box,0,translation,0,false,0,1,"spring obstacle",FixedState.USER_FIXED);
      if(variant==10)b.insertViaObstacle(box,0,translation,0,false,0,1,"spring via keepout",FixedState.USER_FIXED);
      if(variant==11)b.insertComponentObstacle(box,0,translation,0,false,0,1,"spring component keepout",FixedState.USER_FIXED);
      if(variant==12||variant==13){var area=new ConductionArea(box,0,translation,0,false,new int[]{3},1,0,0,"spring plane",variant==13,FixedState.USER_FIXED,b);b.insertItem(area);}
    }
    if(in.get("contact").getAsBoolean())b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin,origin.translateBy(new IntVector(10000,0))}),0,100,new int[]{2},1,FixedState.SHOVE_FIXED);
    b.setShoveFailingObstacle(b.getOutline());b.setShoveFailingLayer(7);
    b.generateSnapshot();int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();var pointRefs=new Refs();var lineRefs=new Refs();var polylineRefs=new Refs();var shover=new TraceShover(b);
    var spring=TraceShover.class.getDeclaredMethod("springOver",Polyline.class,int.class,int.class,int[].class,int.class,boolean.class,int.class,Set.class);spring.setAccessible(true);
    var ignore=TraceShover.class.getDeclaredMethod("getIgnoreItemsAtTiePins",TileShape.class,int.class,int[].class);ignore.setAccessible(true);
    for(var element:in.getAsJsonArray("queries")){
      var q=element.getAsJsonObject();String op=q.get("op").getAsString();Object value=null;String error=null;Polyline result=null;
      var ps=new ArrayList<Point>();for(var v:q.getAsJsonArray("points")){var xy=v.getAsJsonArray();ps.add(origin.translateBy(new IntVector(xy.get(0).getAsInt(),xy.get(1).getAsInt())));}var polyline=new Polyline(ps.toArray(Point[]::new));
      int[] nets=NeutralBoardModel.JSON.fromJson(q.get("nets"),int[].class);Set<Pin> pins=null;if(q.get("pins").getAsInt()!=0){pins=new TreeSet<>();if(q.get("pins").getAsInt()==2)for(var item:b.getItems())if(item instanceof Pin pin)pins.add(pin);}
      int width=q.get("width").getAsInt(),layer=q.get("layer").getAsInt(),clearance=q.get("clearance").getAsInt();
      try{
        if(op.equals("both"))result=shover.springOverObstacles(polyline,width,layer,nets,clearance,pins);
        else if(op.equals("ccw"))result=(Polyline)spring.invoke(shover,polyline,width,layer,nets,clearance,q.get("connected").getAsBoolean(),q.get("depth").getAsInt(),pins);
        else {var contacts=(Collection<Item>)ignore.invoke(shover,polyline.offsetShape(width,0),layer,nets);var ids=new ArrayList<Integer>();for(var item:contacts)ids.add(item.getId());value=ids;}
      }catch(Throwable failure){error=ShapeEntryOracle.error(failure instanceof java.lang.reflect.InvocationTargetException?failure.getCause():failure);}
      Object output=geometry(result,pointRefs,lineRefs,polylineRefs),inputState=geometry(polyline,pointRefs,lineRefs,polylineRefs);
      rows.add(obj("input",q,"value",value,"error",error,"same",result==polyline,"result",output,"polyline",inputState,"failing",b.getShoveFailingObstacle()==null?null:b.getShoveFailingObstacle().getId(),"failingLayer",b.getShoveFailingLayer(),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic spring-over unit histories on admitted public ECC83; no route");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
