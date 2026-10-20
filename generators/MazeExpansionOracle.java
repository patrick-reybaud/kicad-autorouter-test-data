package app.freerouting.board.facade;

import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.autoroute.path.FoundConnectionInserter;
import app.freerouting.autoroute.maze.*;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.drill.*;
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
public final class MazeExpansionOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static Object history(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    var entryState=new BoardEntryOracle.Entry(b,observer,exporter);
    int initial=snapshot(entryState.snapshot());
    int angle=in.get("angle").getAsInt();b.rules.setTraceAngleRestriction(angle==90?AngleRestriction.NINETY_DEGREE:AngleRestriction.FORTYFIVE_DEGREE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());b.startMarkingChangedArea();
    IntPoint origin=new IntPoint(in.get("x").getAsInt(),in.get("y").getAsInt());int net=2;
    var settings=new RouterSettings(b);settings.setFanoutEnabled(false);settings.setRunOptimizer(false);settings.setAutomaticNeckdown(true);var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());
    var costs=new ArrayList<Object>();for(var c:settings.getTraceCosts())costs.add(list(bits(c.horizontal()),bits(c.vertical())));var active=new ArrayList<Object>();var bend=new ArrayList<Object>();for(int i=0;i<b.getLayerCount();i++){active.add(settings.getLayerActive(i));bend.add(bits(settings.getBendCost(i)));}
    var resolved=obj("traceCosts",costs,"active",active,"bend",bend,"viaCosts",settings.getViaCosts(),"viasAllowed",settings.getViasAllowed(),"neckdown",settings.getAutomaticNeckdown());
    int variant=in.get("variant").getAsInt(),layers=b.getLayerCount();Pin pin=(Pin)b.getItem(in.get("pin").getAsInt());if(variant==4)origin=(IntPoint)pin.getCenter();
    var engine=b.initAutoroute(net,ctrl.traceClearanceClassIndex,null,null,false);var sc=MazeSearchEngine.class.getDeclaredConstructor(AutorouteEngine.class,AutorouteControl.class);sc.setAccessible(true);var search=sc.newInstance(engine,ctrl);var type=Class.forName("app.freerouting.autoroute.maze.MazeExpansionEngine");var ctor=type.getDeclaredConstructor(MazeSearchEngine.class);ctor.setAccessible(true);var helper=ctor.newInstance(search);
    ctrl.isFanout=variant==8;ctrl.fanoutStartPinName="U27-oracle";ctrl.fanoutStartPinCenter=pin.getCenter();ctrl.fanoutStartPinLayer=0;ctrl.maxShoveTraceRecursionDepth=3;ctrl.maxShoveViaRecursionDepth=3;
    var destination=field(search,"destinationDistance");destination.getClass().getMethod("join",IntBox.class,int.class).invoke(destination,new IntBox(origin.x+30000,origin.y+30000,origin.x+40000,origin.y+40000),layers-1);
    TileShape shape=new IntBox(origin.x-12000,origin.y-12000,origin.x+12000,origin.y+12000);var drill=new ExpansionDrill(shape,origin,0,layers-1);var rooms=new ArrayList<CompleteExpansionRoom>();
    Via obstacle=null;if(variant==2||variant==3)obstacle=b.insertVia(b.library.getViaPadstack(0),origin,new int[]{3},variant==3?0:ctrl.viaClearanceClass,FixedState.UNFIXED,true);
    if(variant==5)b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin.translateBy(new IntVector(-20000,0)),origin.translateBy(new IntVector(20000,0))}),0,500,new int[]{3},1,FixedState.USER_FIXED);
    for(int l=0;l<layers;l++){CompleteExpansionRoom room=obstacle==null?new CompleteFreeSpaceExpansionRoom(new IntBox(origin.x-30000,origin.y-(variant==1?100:30000),origin.x+30000,origin.y+(variant==1?100:30000)),l,100+l):new ObstacleExpansionRoom(obstacle,l-obstacle.firstLayer(),engine.autorouteSearchTree);rooms.add(room);drill.roomArr[l]=room;}
    var free=new CompleteFreeSpaceExpansionRoom(new IntBox(origin.x-50000,origin.y-50000,origin.x+50000,origin.y+50000),0,200);var door=new ExpansionDoor(rooms.get(0),free);var target=new TargetItemExpansionDoor(pin,0,rooms.get(0),engine.autorouteSearchTree);var page=new DrillPage(new IntBox(origin.x-10000,origin.y-10000,origin.x+10000,origin.y+10000),b);
    var ids=new IdentityHashMap<Object,Integer>();identity(ids,door);identity(ids,target);identity(ids,page);identity(ids,drill);for(var room:rooms)identity(ids,room);identity(ids,free);var entry=new FloatLine(new FloatPoint(origin.x-4000,origin.y-3000),new FloatPoint(origin.x+2000,origin.y+1000));identity(ids,entry);
    if(in.get("initializeRooms").getAsBoolean())engine.addIncompleteExpansionRoom(null,0,TileShape.getInstance(origin));
    int prepared=snapshot(entryState.snapshot());var rows=new ArrayList<Object>();
    for(var raw:in.getAsJsonArray("queries")){
      var q=raw.getAsJsonObject();String op=q.get("op").getAsString();int layer=q.get("layer").getAsInt();ctrl.ripupAllowed=q.get("ripup").getAsBoolean();ctrl.viaLowerBound=q.get("lower").getAsInt();ctrl.viaUpperBound=q.get("upper").getAsInt();for(int l=0;l<layers;l++)drill.getMazeSearchElement(l).isOccupied=(q.get("occupied").getAsInt()&(1<<l))!=0;
      boolean asPage=q.get("page").getAsBoolean();ExpandableObject backtrack=q.get("backtrack").getAsInt()==0?null:q.get("backtrack").getAsInt()==1?target:door;CompleteExpansionRoom next=rooms.get(layer);ExpandableObject fromDoor=asPage?page:door;int section=asPage?layer:0;
      Object value=null;String error=null;MazeListElement from=null;
      try{
        if(op.equals("scan")){var candidates=page.getDrills(engine,ctrl.attachSmdAllowed);if(!candidates.isEmpty()){var first=candidates.iterator().next();if(q.get("matching").getAsBoolean())next=first.roomArr[layer-first.firstLayer];if(q.get("occupied").getAsInt()!=0)first.getMazeSearchElement(layer-first.firstLayer).isOccupied=true;}fromDoor=page;section=layer;}
        if(op.equals("other")){fromDoor=drill;section=layer-drill.firstLayer;next=null;}
        from=new MazeListElement(fromDoor,section,backtrack,0,125.25,200,next,entry,q.get("ripped").getAsBoolean(),MazeSearchElement.Adjustment.NONE,false);
        switch(op){
          case "drill"->{var m=type.getDeclaredMethod("expandToDrill",ExpansionDrill.class,MazeListElement.class,int.class);m.setAccessible(true);m.invoke(helper,drill,from,q.get("cost").getAsInt());value="ok";}
          case "page"->{var m=type.getDeclaredMethod("expandToDrillPage",DrillPage.class,MazeListElement.class);m.setAccessible(true);m.invoke(helper,page,from);value="ok";}
          case "scan"->{var m=type.getDeclaredMethod("expandToDrillsOfPage",MazeListElement.class);m.setAccessible(true);m.invoke(helper,from);value="ok";}
          case "other"->{var m=type.getDeclaredMethod("expandToOtherLayers",MazeListElement.class);m.setAccessible(true);m.invoke(helper,from);value="ok";}
          case "check"->{var m=type.getDeclaredMethod("checkLayerWithAnyMatchingVia",ExpansionDrill.class,int.class,TileShape.class,int[].class);m.setAccessible(true);value=m.invoke(helper,drill,layer,rooms.get(layer).getShape(),new int[]{net}).toString();}
        }
      }catch(Throwable e){error=ShapeEntryOracle.error(e instanceof java.lang.reflect.InvocationTargetException?e.getCause():e);}
      rows.add(obj("query",q,"value",value,"error",error,"queue",queue(search,ids,entry),"drill",describe(drill,ids),"board",snapshot(entryState.snapshot())));
    }
    return obj("input",in,"resolvedSettings",resolved,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic expansion-helper unit histories on public boards; page candidates generated independently by each actual engine, no route replay");
  }
  static int identity(IdentityHashMap<Object,Integer> ids,Object x){if(x==null)return 0;return ids.computeIfAbsent(x,k->ids.size()+1);}
  static Object xy(FloatLine l){return list(bits(l.a.x),bits(l.a.y),bits(l.b.x),bits(l.b.y));}
  static Object describe(ExpandableObject d,IdentityHashMap<Object,Integer> ids){if(d==null)return null;var roomIds=new ArrayList<Object>();if(d instanceof ExpansionDrill drill)for(var room:drill.roomArr)roomIds.add(identity(ids,room));return obj("identity",identity(ids,d),"type",d.getClass().getSimpleName(),"id",d.getId(),"shape",shape(d.getShape()),"rooms",roomIds);}
  static Object queue(Object search,IdentityHashMap<Object,Integer> ids,FloatLine input)throws Exception {var out=new ArrayList<Object>();for(var e:(Collection<?>)field(search,"mazeExpansionList")){var d=(ExpandableObject)field(e,"door");var back=(ExpandableObject)field(e,"backtrackDoor");var room=(CompleteExpansionRoom)field(e,"nextRoom");var entry=(FloatLine)field(e,"shapeEntry");out.add(obj("identity",identity(ids,e),"door",describe(d,ids),"section",field(e,"sectionNoOfDoor"),"back",describe(back,ids),"backSection",field(e,"sectionNoOfBacktrackDoor"),"expansion",bits((Double)field(e,"expansionValue")),"sorting",bits((Double)field(e,"sortingValue")),"room",identity(ids,room),"entryIdentity",identity(ids,entry),"entry",xy(entry),"entryIsInput",entry==input,"ripped",field(e,"roomRipped"),"adjustment",field(e,"adjustment").toString(),"checked",field(e,"alreadyChecked")));}return out;}
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
