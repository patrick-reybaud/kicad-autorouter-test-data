package app.freerouting.board.facade;

import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.AutorouteControl.ExpansionCostFactor;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.optimize.ViaOptimizer;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;

/** Actual source ViaOptimizer and non-null-cost changed-area calls on routed public boards. */
public final class ViaOptimizerOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static Object costsValue(ExpansionCostFactor[] costs){if(costs==null)return null;var out=new ArrayList<Object>();for(var c:costs)out.add(c==null?null:list(bits(c.horizontal()),bits(c.vertical())));return out;}
  static ExpansionCostFactor[] costs(int kind,RouterSettings settings,int layers){
    var base=settings.getTraceCosts();
    switch(kind){
      case 0:return null;
      case 1:return base;
      case 2:{var out=new ExpansionCostFactor[layers];for(int i=0;i<layers;i++)out[i]=new ExpansionCostFactor(1,1);return out;}
      case 3:{var out=new ExpansionCostFactor[layers];for(int i=0;i<layers;i++)out[i]=new ExpansionCostFactor(2,1);return out;}
      case 4:{var out=new ExpansionCostFactor[layers];for(int i=0;i<layers;i++)out[i]=new ExpansionCostFactor(1,2);return out;}
      case 5:{var out=new ExpansionCostFactor[layers];for(int i=0;i<layers;i++)out[i]=new ExpansionCostFactor(base[i].vertical(),base[i].horizontal());return out;}
      default:throw new IllegalArgumentException("costs kind");
    }
  }
  static Object metrics(RoutingBoard b){
    double length=0;int remaining=0,violations=0;
    for(var t:b.getTraces())length+=t.getLength();
    for(int n=1;n<=b.rules.nets.maxNetNumber();n++)remaining+=Math.max(0,b.getConnectedSets(n).size()-1);
    for(var i:b.getItems())violations+=i.clearanceViolationCount();
    return obj("traceCount",b.getTraces().size(),"viaCount",b.getVias().size(),"lengthBits",bits(length),"remainingTopologicalConnections",remaining,"itemClearanceViolationCount",violations);
  }
  static Object history(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var entry=new BoardEntryOracle.Entry(b,observer,exporter);int initial=snapshot(entry.snapshot());
    var settings=new RouterSettings(b);settings.setFanoutEnabled(false);settings.setRunOptimizer(false);
    var costsOut=new ArrayList<Object>();for(var c:settings.getTraceCosts())costsOut.add(list(bits(c.horizontal()),bits(c.vertical())));var active=new ArrayList<Object>();var bend=new ArrayList<Object>();for(int i=0;i<b.getLayerCount();i++){active.add(settings.getLayerActive(i));bend.add(bits(settings.getBendCost(i)));}
    var resolved=obj("traceCosts",costsOut,"active",active,"bend",bend,"viaCosts",settings.getViaCosts(),"viasAllowed",settings.getViasAllowed(),"neckdown",settings.getAutomaticNeckdown(),"startRipupCosts",settings.getStartRipupCosts());
    var rows=new ArrayList<Object>();String mode=in.get("mode").getAsString();
    if(mode.equals("synthetic")){
      int variant=in.get("variant").getAsInt();var nets=new int[]{2};
      // Route geometry away from board copper: a via with one or two traces and optional plane.
      var p=new IntPoint[]{new IntPoint(-30000,-20000),new IntPoint(-20000,-20000),new IntPoint(-20000,-10000),new IntPoint(-10000,-10000),new IntPoint(-14000,-20000),new IntPoint(-20000,-26000)};
      Via via=null;
      switch(variant){
        case 0->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);}
        case 1->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[1],p[0]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);}
        case 2->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],new IntPoint(-24000,-20000),new IntPoint(-24000,-16000),p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);}
        case 3->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[1]}),0,500,nets,1,FixedState.SHOVE_FIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);}
        case 4->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[1],p[2]}),1,500,nets,1,FixedState.UNFIXED);}
        case 5->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[1],p[3]}),1,500,nets,1,FixedState.UNFIXED);}
        case 6->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[1],p[4]}),1,500,nets,1,FixedState.UNFIXED);}
        case 7->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);b.insertConductionArea(new IntBox(-40000,-40000,-5000,-5000),1,nets,1,false,FixedState.UNFIXED);}
        case 8->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],new IntPoint(-24000,-20000),new IntPoint(-24000,-16000),p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);b.insertConductionArea(new IntBox(-40000,-40000,-5000,-5000),1,nets,1,false,FixedState.UNFIXED);}
        case 9->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[1],p[2]}),1,500,nets,1,FixedState.UNFIXED);b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[1],p[5]}),1,500,nets,1,FixedState.UNFIXED);}
        case 10->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.SHOVE_FIXED,true);b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[1],p[2]}),1,500,nets,1,FixedState.UNFIXED);}
        case 11->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[3]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),new IntPoint(-20000,-15000),nets,1,FixedState.UNFIXED,true);b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(-20000,-15000),p[2]}),1,500,nets,1,FixedState.UNFIXED);}
        case 12->{b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[0],p[1]}),0,500,nets,1,FixedState.UNFIXED);via=b.insertVia(b.library.getViaPadstack(0),p[1],nets,1,FixedState.UNFIXED,true);b.insertTraceWithoutCleaning(new Polyline(new Point[]{p[1],p[2]}),1,500,nets,1,FixedState.SHOVE_FIXED);}
        default->throw new IllegalArgumentException("variant");
      }
      int prepared=snapshot(entry.snapshot());
      for(int kind=0;kind<6;kind++)for(int depth:new int[]{10,1,0}){
        var costs=costs(kind,settings,b.getLayerCount());Object value=null;String error=null;
        try{value=ViaOptimizer.optViaLocation(b,via,costs,500,depth);}catch(Throwable e){error=ShapeEntryOracle.error(e);}
        rows.add(obj("op","optVia","via",via.getId(),"costsKind",kind,"costs",costsValue(costs),"accuracy",500,"depth",depth,"value",value,"error",error,"board",snapshot(entry.snapshot())));
        System.err.println("history="+in.get("case")+" variant="+variant+" kind="+kind+" depth="+depth+" value="+value+" error="+error);
      }
      return obj("input",in,"resolvedSettings",resolved,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic fanout/plane via unit cases on the admitted public board; not a route benchmark");
    }
    int limit=in.get("attempts").getAsInt();int attempts=0;var routed=new ArrayList<Object>();
    for(int net=1;net<=b.rules.nets.maxNetNumber()&&attempts<limit;net++){
      var available=b.getConnectableItems(net);if(available.isEmpty())continue;var first=available.iterator().next();var from=first.getConnectedSet(net);var to=first.getUnconnectedSet(net);if(to.isEmpty())continue;
      var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());ctrl.removeUnconnectedVias=false;
      var engine=b.initAutoroute(net,ctrl.traceClearanceClassIndex,null,null,false);var ripped=new TreeSet<Item>();
      var result=engine.autorouteConnection(from,to,ctrl,ripped,null);attempts++;
      routed.add(obj("net",net,"state",result.state.name(),"details",result.details,"ripped",ids(ripped)));
    }
    int prepared=snapshot(entry.snapshot());Object preparedMetrics=metrics(b);
    int kind=in.get("costsKind").getAsInt(),accuracy=in.get("accuracy").getAsInt(),depth=in.get("depth").getAsInt();var costs=costs(kind,settings,b.getLayerCount());
    if(mode.equals("direct")){
      var vias=new ArrayList<Via>(b.getVias());vias.sort(Comparator.comparingInt(Item::getId));
      for(var via:vias){
        Object value=null;String error=null;
        try{value=ViaOptimizer.optViaLocation(b,via,costs,accuracy,depth);}catch(Throwable e){error=ShapeEntryOracle.error(e);}
        rows.add(obj("op","optVia","via",via.getId(),"costsKind",kind,"costs",costsValue(costs),"accuracy",accuracy,"depth",depth,"value",value,"error",error,"metrics",metrics(b),"board",snapshot(entry.snapshot())));
        System.err.println("history="+in.get("case")+" board="+boardName+" via="+via.getId()+" kind="+kind+" value="+value+" error="+error);
      }
    }else if(mode.equals("changedArea")){
      Object value=null;String error=null;long start=System.nanoTime();
      try{b.markAllChangedArea();b.optChangedArea(new int[0],null,accuracy,costs,null,0);}catch(Throwable e){error=ShapeEntryOracle.error(e);}
      long elapsed=System.nanoTime()-start;
      rows.add(obj("op","optChangedArea","costsKind",kind,"costs",costsValue(costs),"accuracy",accuracy,"value",value,"error",error,"metrics",metrics(b),"board",snapshot(entry.snapshot())));
      System.err.println("history="+in.get("case")+" board="+boardName+" changedArea kind="+kind+" accuracy="+accuracy+" error="+error+" ms="+(elapsed/1e6));
    }else throw new IllegalArgumentException("mode");
    return obj("input",in,"resolvedSettings",resolved,"initial",initial,"routed",routed,"prepared",prepared,"preparedMetrics",preparedMetrics,"rows",rows,"source","Public board routed by actual low-level source attempts, then actual ViaOptimizer/optChangedArea calls with non-null costs");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
