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

/** Actual public-board maze search. No supplied path or reconstructed search graph. */
public final class MazeSearchOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static Object state(MazeSearchEngine search,IdentityHashMap<Object,Integer> ids)throws Exception{
    if(search==null)return null;
    var random=(java.util.Random)field(search,"randomGenerator");var seed=(java.util.concurrent.atomic.AtomicLong)field(random,"seed");
    return obj("queue",queue(search,ids,null),"destination",describe((ExpandableObject)field(search,"destinationDoor"),ids),"destinationSection",field(search,"sectionNoOfDestinationDoor"),"randomState",seed.get());
  }
  static Object result(MazeSearchEngine.Result r,IdentityHashMap<Object,Integer> ids){return r==null?null:obj("destination",describe(r.destinationDoor,ids),"section",r.sectionNoOfDoor);}
  static Object history(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var entryState=new BoardEntryOracle.Entry(b,observer,exporter);
    int initial=snapshot(entryState.snapshot());
    int angle=in.get("angle").getAsInt();b.rules.setTraceAngleRestriction(angle==90?AngleRestriction.NINETY_DEGREE:AngleRestriction.FORTYFIVE_DEGREE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());
    int net=in.get("net").getAsInt();var settings=new RouterSettings(b);settings.setFanoutEnabled(false);settings.setRunOptimizer(false);
    var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());ctrl.removeUnconnectedVias=false;
    var costs=new ArrayList<Object>();for(var c:settings.getTraceCosts())costs.add(list(bits(c.horizontal()),bits(c.vertical())));var active=new ArrayList<Object>();var bend=new ArrayList<Object>();for(int i=0;i<b.getLayerCount();i++){active.add(settings.getLayerActive(i));bend.add(bits(settings.getBendCost(i)));}
    var resolved=obj("traceCosts",costs,"active",active,"bend",bend,"viaCosts",settings.getViaCosts(),"viasAllowed",settings.getViasAllowed(),"neckdown",settings.getAutomaticNeckdown(),"startRipupCosts",settings.getStartRipupCosts());
    Item start=b.getConnectableItems(net).iterator().next();var from=start.getConnectedSet(net);var to=start.getUnconnectedSet(net);
    var engine=b.initAutoroute(net,ctrl.traceClearanceClassIndex,null,null,false);var ids=new IdentityHashMap<Object,Integer>();var rows=new ArrayList<Object>();MazeSearchEngine search=null;String error=null;
    try{search=MazeSearchEngine.getInstance(from,to,engine,ctrl);}catch(Throwable e){error=ShapeEntryOracle.error(e);}
    rows.add(obj("op","initialize","value",search!=null,"error",error,"search",state(search,ids),"board",snapshot(entryState.snapshot())));
    if(search!=null){
      if(in.get("stepped").getAsBoolean()){
        boolean more=true;int n=0;
        while(more){
          if(++n>10000)throw new IllegalStateException("Bench safety cap reached, not a qualified search");
          Object value=null;error=null;try{more=search.occupyNextElement();value=more;}catch(Throwable e){error=ShapeEntryOracle.error(e);more=false;}
          rows.add(obj("op","occupy","value",value,"error",error,"search",state(search,ids),"board",snapshot(entryState.snapshot())));
          if(error!=null)break;
        }
      }
      Object value=null;error=null;try{value=result(search.findConnection(),ids);}catch(Throwable e){error=ShapeEntryOracle.error(e);}
      rows.add(obj("op","find","value",value,"error",error,"search",state(search,ids),"board",snapshot(entryState.snapshot())));
    }
    System.err.println("history="+in+" observations="+rows.size());
    return obj("input",in,"resolvedSettings",resolved,"initial",initial,"start",start.getId(),"from",ids(from),"to",ids(to),"rows",rows,"source","Actual source search on public board, no inserted route or supplied path");
  }
  static int identity(IdentityHashMap<Object,Integer> ids,Object x){if(x==null)return 0;return ids.computeIfAbsent(x,k->ids.size()+1);}
  static Object xy(FloatLine l){return list(bits(l.a.x),bits(l.a.y),bits(l.b.x),bits(l.b.y));}
  static Object describe(ExpandableObject d,IdentityHashMap<Object,Integer> ids){if(d==null)return null;var roomIds=new ArrayList<Object>();if(d instanceof ExpansionDrill drill)for(var room:drill.roomArr)roomIds.add(identity(ids,room));return obj("identity",identity(ids,d),"type",d.getClass().getSimpleName(),"id",d.getId(),"shape",shape(d.getShape()),"rooms",roomIds);}
  static Object queue(Object search,IdentityHashMap<Object,Integer> ids,FloatLine input)throws Exception {var out=new ArrayList<Object>();for(var e:(Collection<?>)field(search,"mazeExpansionList")){var d=(ExpandableObject)field(e,"door");var back=(ExpandableObject)field(e,"backtrackDoor");var room=(CompleteExpansionRoom)field(e,"nextRoom");var entry=(FloatLine)field(e,"shapeEntry");out.add(obj("identity",identity(ids,e),"door",describe(d,ids),"section",field(e,"sectionNoOfDoor"),"back",describe(back,ids),"backSection",field(e,"sectionNoOfBacktrackDoor"),"expansion",bits((Double)field(e,"expansionValue")),"sorting",bits((Double)field(e,"sortingValue")),"room",identity(ids,room),"entryIdentity",identity(ids,entry),"entry",xy(entry),"entryIsInput",entry==input,"ripped",field(e,"roomRipped"),"adjustment",field(e,"adjustment").toString(),"checked",field(e,"alreadyChecked"),"ripupCost",field(e,"ripupCost")));}return out;}
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
