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

/** Actual public AutorouteEngine entry and a separately observed source stage sequence. */
public final class PublicRouteOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static Object locator(app.freerouting.autoroute.path.FoundConnectionLocator x)throws Exception{
    if(x==null)return null;var parts=new ArrayList<Object>();
    for(Object item:x.connectionItems){var corners=new ArrayList<Object>();for(var p:(IntPoint[])field(item,"corners"))corners.add(list(p.x,p.y));parts.add(obj("layer",field(item,"layer"),"corners",corners));}
    return obj("start",x.startItem==null?null:x.startItem.getId(),"target",x.targetItem==null?null:x.targetItem.getId(),"startLayer",x.startLayer,"targetLayer",x.targetLayer,"parts",parts);
  }
  static Object metrics(RoutingBoard b){
    double length=0;int remaining=0,violations=0;
    for(var t:b.getTraces())length+=t.getLength();
    for(int n=1;n<=b.rules.nets.maxNetNumber();n++)remaining+=Math.max(0,b.getConnectedSets(n).size()-1);
    for(var i:b.getItems())violations+=i.clearanceViolationCount();
    return obj("traceCount",b.getTraces().size(),"viaCount",b.getVias().size(),"lengthBits",bits(length),"remainingTopologicalConnections",remaining,"itemClearanceViolationCount",violations);
  }
  static Object helpers(JsonObject in,RoutingBoard b,BoardEntryOracle.Entry entry,int initial)throws Exception {
    int variant=in.get("variant").getAsInt();
    var a=b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(-30000,-20000),new IntPoint(-20000,-20000)}),0,500,new int[]{2},1,FixedState.UNFIXED);
    b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(-20000,-20000),new IntPoint(-10000,-20000)}),0,500,new int[]{2},1,variant==2?FixedState.USER_FIXED:FixedState.UNFIXED);
    var via=b.insertVia(b.library.getViaPadstack(0),new IntPoint(-20000,-20000),new int[]{2},1,FixedState.UNFIXED,true);
    if(variant!=1)b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(-20000,-20000),new IntPoint(-20000,-10000)}),1,500,new int[]{2},1,FixedState.UNFIXED);
    var area=new IntBox(-90000,-80000,-70000,-60000);
    b.insertViaObstacle(area,0,1,FixedState.UNFIXED);b.insertComponentObstacle(area,0,1,FixedState.UNFIXED);
    b.insertConductionArea(area,0,new int[]{3},1,false,FixedState.UNFIXED);
    int prepared=snapshot(entry.snapshot());var rows=new ArrayList<Object>();var others=new ArrayList<Item>(b.getItems());others.add(null);
    for(var subject:List.of(a,via))for(var other:others){Object value=null;String error=null;try{value=subject.isObstacle(other);}catch(Throwable e){error=ShapeEntryOracle.error(e);}rows.add(obj("op","obstacle","subject",subject.getId(),"other",other==null?null:other.getId(),"value",value,"error",error));}
    int stop=in.get("stop").getAsInt(),net=in.get("net").getAsInt();Object value=null;String error=null;try{value=b.removeTraceTails(net,stop<0?null:Item.StopConnectionOption.values()[stop]);}catch(Throwable e){error=ShapeEntryOracle.error(e);}
    rows.add(obj("op","tails","value",value,"error",error,"board",snapshot(entry.snapshot())));
    return obj("input",in,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic tail/predicate unit cases on a fresh public board; not a route benchmark");
  }
  static Object attemptValues(){
    var rows=new ArrayList<Object>();
    for(int state=-1;state<app.freerouting.autoroute.AutorouteAttemptState.values().length;state++)for(int mode=0;mode<4;mode++){
      var st=state<0?null:app.freerouting.autoroute.AutorouteAttemptState.values()[state];String detail=mode==1?null:mode==2?"":"é / 🚀\n";
      var value=mode==0?new app.freerouting.autoroute.AutorouteAttemptResult(st):new app.freerouting.autoroute.AutorouteAttemptResult(st,detail);
      Object rendered=null;String error=null;try{rendered=value.toString();}catch(Throwable e){error=ShapeEntryOracle.error(e);}
      rows.add(obj("state",state,"mode",mode,"details",detail,"storedDetails",value.details,"rendered",rendered,"error",error));
    }
    return rows;
  }
  static Object history(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var entry=new BoardEntryOracle.Entry(b,observer,exporter);int initial=snapshot(entry.snapshot());
    if(in.has("helpers"))return helpers(in,b,entry,initial);
    var settings=new RouterSettings(b);settings.setFanoutEnabled(false);settings.setRunOptimizer(false);
    var costs=new ArrayList<Object>();for(var c:settings.getTraceCosts())costs.add(list(bits(c.horizontal()),bits(c.vertical())));var active=new ArrayList<Object>();var bend=new ArrayList<Object>();for(int i=0;i<b.getLayerCount();i++){active.add(settings.getLayerActive(i));bend.add(bits(settings.getBendCost(i)));}
    var resolved=obj("traceCosts",costs,"active",active,"bend",bend,"viaCosts",settings.getViaCosts(),"viasAllowed",settings.getViasAllowed(),"neckdown",settings.getAutomaticNeckdown(),"startRipupCosts",settings.getStartRipupCosts());
    var rows=new ArrayList<Object>();boolean staged=in.get("staged").getAsBoolean(),failure=in.get("failure").getAsBoolean();
    for(int net=1;net<=(failure?1:b.rules.nets.maxNetNumber());net++){
      Item start=b.getConnectableItems(net).iterator().next();var from=start.getConnectedSet(net);var to=start.getUnconnectedSet(net);
      if(to.isEmpty()&&!failure){rows.add(obj("net",net,"op","skip","start",start.getId(),"from",ids(from),"to",ids(to)));continue;}
      var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());ctrl.removeUnconnectedVias=false;
      var engine=b.initAutoroute(net,ctrl.traceClearanceClassIndex,null,null,false);var ripped=new TreeSet<Item>();Object result;
      var stages=new ArrayList<Object>();
      if(staged){
        var identities=new IdentityHashMap<Object,Integer>();var search=MazeSearchEngine.getInstance(from,to,engine,ctrl);
        stages.add(obj("op","initialize","value",search!=null,"search",MazeSearchOracle.state(search,identities),"board",snapshot(entry.snapshot())));
        if(search==null)throw new IllegalStateException("Unexpected initialization failure in staged positive run");
        boolean more;int count=0;do{if(++count>10000)throw new IllegalStateException("Bench step cap");more=search.occupyNextElement();stages.add(obj("op","occupy","value",more,"search",MazeSearchOracle.state(search,identities),"board",snapshot(entry.snapshot())));}while(more);
        var found=search.findConnection();stages.add(obj("op","find","value",MazeSearchOracle.result(found,identities),"search",MazeSearchOracle.state(search,identities),"board",snapshot(entry.snapshot())));
        var located=app.freerouting.autoroute.path.FoundConnectionLocator.getInstance(found,ctrl,engine.autorouteSearchTree,b.rules.getTraceAngleRestriction(),ripped,null);
        stages.add(obj("op","locate","value",locator(located),"ripped",ids(ripped),"board",snapshot(entry.snapshot())));
        engine.clear();stages.add(obj("op","clear","board",snapshot(entry.snapshot())));
        var connections=new TreeSet<Item>();var changedNets=new TreeSet<Integer>();var stop=Item.StopConnectionOption.FANOUT_VIA;
        for(var item:ripped){connections.addAll(item.getConnectionItems(stop));for(int i=0;i<item.netCount();i++)changedNets.add(item.getNetNumber(i));}
        boolean activate=!b.observersActive();if(activate)b.startNotifyObservers();b.removeItems(connections);for(int changed:changedNets)b.removeTraceTails(changed,stop);
        stages.add(obj("op","remove","connections",ids(connections),"nets",changedNets,"board",snapshot(entry.snapshot())));
        var inserted=FoundConnectionInserter.getInstance(located,b,ctrl);if(activate)b.endNotifyObservers();result=obj("state",inserted==null?"FAILED":"ROUTED","details","");
      }else{
        var attempt=engine.autorouteConnection(from,to,ctrl,ripped,null);result=obj("state",attempt.state.name(),"details",attempt.details);
      }
      Object measured=metrics(b);
      rows.add(obj("net",net,"op","route","start",start.getId(),"from",ids(from),"to",ids(to),"control",obj("ripupCosts",ctrl.ripupCosts,"widths",ctrl.traceHalfWidth,"clearance",ctrl.traceClearanceClassIndex),"result",result,"ripped",ids(ripped),"stages",stages,"metrics",measured,"board",snapshot(entry.snapshot())));
      System.err.println("history="+in.get("case")+" net="+net+" result="+result+" metrics="+measured);
    }
    return obj("input",in,"resolvedSettings",resolved,"initial",initial,"rows",rows,"source","Public ECC83: actual AutorouteEngine API and independent source-stage observations, no prepared path");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"attemptValues\":"+NeutralBoardModel.JSON.toJson(attemptValues())+",\"nativeRoutingExecuted\":false}\n");}
  }
}
