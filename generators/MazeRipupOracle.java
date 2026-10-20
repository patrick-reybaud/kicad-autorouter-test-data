package app.freerouting.board.facade;

import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.autoroute.path.FoundConnectionInserter;
import app.freerouting.autoroute.maze.*;
import app.freerouting.autoroute.expansion.*;
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
public final class MazeRipupOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static Object history(Path path,JsonObject in)throws Exception {
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    int angle=in.get("angle").getAsInt();b.rules.setTraceAngleRestriction(angle==90?AngleRestriction.NINETY_DEGREE:AngleRestriction.FORTYFIVE_DEGREE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());b.startMarkingChangedArea();
    IntPoint origin=new IntPoint(1500000,-1150000);int net=2;
    var settings=new RouterSettings(b);settings.setStartRipupCosts(in.get("startCost").getAsInt());settings.setFanoutEnabled(false);settings.setRunOptimizer(false);settings.setAutomaticNeckdown(true);var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());
    var costs=new ArrayList<Object>();for(var c:settings.getTraceCosts())costs.add(list(bits(c.horizontal()),bits(c.vertical())));var active=new ArrayList<Object>();var bend=new ArrayList<Object>();for(int i=0;i<b.getLayerCount();i++){active.add(settings.getLayerActive(i));bend.add(bits(settings.getBendCost(i)));}
    var resolved=obj("traceCosts",costs,"active",active,"bend",bend,"viaCosts",settings.getViaCosts(),"viasAllowed",settings.getViasAllowed(),"neckdown",settings.getAutomaticNeckdown(),"startCost",settings.getStartRipupCosts());
    int width=1000;Arrays.fill(ctrl.traceHalfWidth,width);ctrl.traceClearanceClassIndex=1;ctrl.ripupCosts=in.get("cost").getAsInt();
    Point from=origin,to=origin.translateBy(new IntVector(30000,0));int variant=in.get("variant").getAsInt();
    if(variant==2){var pad=b.library.padstacks.add(new IntBox(-2000,-2000,2000,2000),0,0);var pack=b.library.packages.add(new app.freerouting.core.library.Package.Pin[]{new app.freerouting.core.library.Package.Pin("1",pad.id,new IntVector(0,0),0)});var comp=b.components.add(origin,0,true,pack);b.insertPin(comp.id,0,new int[]{3},1,FixedState.UNFIXED);}
    var trace=b.insertTraceWithoutCleaning(new Polyline(new Point[]{from,to}),0,width,new int[]{3},1,FixedState.UNFIXED);
    var contact=b.insertTraceWithoutCleaning(new Polyline(new Point[]{from.translateBy(new IntVector(-10000,0)),from}),0,500,new int[]{3},1,variant==3?FixedState.SHOVE_FIXED:variant==4?FixedState.USER_FIXED:FixedState.UNFIXED);
    if(variant==2)b.removeItem(contact);
    var via=b.insertVia(b.library.getViaPadstack(0),to,new int[]{3},1,FixedState.UNFIXED,true);
    if(variant==1)b.insertVia(b.library.getViaPadstack(0),from,new int[]{3},1,FixedState.USER_FIXED,true);
    if(variant==4)b.insertTraceWithoutCleaning(new Polyline(new Point[]{to,to.translateBy(new IntVector(0,30000))}),0,500,new int[]{3},1,FixedState.USER_FIXED);
    if(variant==5)b.insertTraceWithoutCleaning(new Polyline(new Point[]{to,to.translateBy(new IntVector(0,30000))}),0,2000,new int[]{3},1,FixedState.UNFIXED);
    if(variant>=6)b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin.translateBy(new IntVector(15000,variant==7?0:1500)),origin.translateBy(new IntVector(15000,15000))}),0,500,new int[]{variant==6?4:3},1,FixedState.UNFIXED);
    var engine=b.initAutoroute(2,1,null,null,false);var ctor=MazeSearchEngine.class.getDeclaredConstructor(AutorouteEngine.class,AutorouteControl.class);ctor.setAccessible(true);var search=ctor.newInstance(engine,ctrl);var type=Class.forName("app.freerouting.autoroute.maze.MazeRipupResolver");var rc=type.getDeclaredConstructor(MazeSearchEngine.class);rc.setAccessible(true);var resolver=rc.newInstance(search);
    var rip=type.getDeclaredMethod("checkRipup",MazeListElement.class,Item.class,boolean.class);rip.setAccessible(true);var leave=type.getDeclaredMethod("checkLeavingRippedItem",MazeListElement.class);leave.setAccessible(true);var enter=type.getDeclaredMethod("enterThroughSmallDoor",MazeListElement.class,Item.class);enter.setAccessible(true);var factor=type.getDeclaredMethod("calcFanoutViaRipupCostFactor",Trace.class);factor.setAccessible(true);
    var tree=engine.autorouteSearchTree;Arrays.fill(ctrl.compensatedTraceHalfWidth,width+tree.clearanceCompensationValue(1,0));
    var room=new ObstacleExpansionRoom(trace,0,tree);var box=room.getShape().boundingBox();var free=new CompleteFreeSpaceExpansionRoom(new IntBox(box.ll.x,box.ur.y,box.ur.x,box.ur.y+20000),0,1);var same=new ObstacleExpansionRoom(trace,0,tree);var other=new ObstacleExpansionRoom(contact,0,tree);var viaRoom=new ObstacleExpansionRoom(via,0,tree);
    var doors=new ArrayList<ExpansionDoor>();for(var prev:new CompleteExpansionRoom[]{free,same,other,viaRoom}){var d=new ExpansionDoor(room,prev);doors.add(d);room.addDoor(d);prev.addDoor(d);}
    int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();
    for(var raw:in.getAsJsonArray("queries")){
      var q=raw.getAsJsonObject();String op=q.get("op").getAsString();ctrl.ripupPassNo=q.get("pass").getAsInt();ctrl.removeUnconnectedVias=q.get("removeVias").getAsBoolean();ctrl.isFanout=q.get("fanout").getAsBoolean();var door=doors.get(q.get("door").getAsInt());var element=new MazeListElement(door,0,null,0,0,0,q.get("reverse").getAsBoolean()?free:room,new FloatLine(from.toFloat(),to.toFloat()),false,MazeSearchElement.Adjustment.values()[q.get("adjustment").getAsInt()],false);Item obstacle=q.get("item").getAsInt()==0?trace:q.get("item").getAsInt()==1?via:q.get("item").getAsInt()==2?contact:b.getItem(3);Object value=null;String error=null;
      try{switch(op){case "rip"->value=rip.invoke(resolver,element,obstacle,q.get("small").getAsBoolean());case "leave"->value=leave.invoke(resolver,element);case "enter"->value=enter.invoke(resolver,element,obstacle);case "factor"->value=bits((Double)factor.invoke(null,trace));}}
      catch(Throwable e){error=ShapeEntryOracle.error(e instanceof java.lang.reflect.InvocationTargetException?e.getCause():e);}
      var random=(java.util.Random)field(search,"randomGenerator");var seedField=java.util.Random.class.getDeclaredField("seed");seedField.setAccessible(true);long seed=((java.util.concurrent.atomic.AtomicLong)seedField.get(random)).get();
      rows.add(obj("query",q,"value",value,"error",error,"randomState",seed,"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    var random=(java.util.Random)field(search,"randomGenerator");var randomDoubles=new ArrayList<Object>();for(int i=0;i<32;i++)randomDoubles.add(bits(random.nextDouble()));
    var seedField=java.util.Random.class.getDeclaredField("seed");seedField.setAccessible(true);long finalSeed=((java.util.concurrent.atomic.AtomicLong)seedField.get(random)).get();
    return obj("input",in,"resolvedSettings",resolved,"initial",initial,"prepared",prepared,"rows",rows,"randomDoubles",randomDoubles,"finalRandomState",finalSeed,"source","Synthetic ripup cost and door unit histories on public ECC83; no actual ripping or route");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
