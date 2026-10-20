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
public final class MazeShoveOracle extends BoardConstructionOracle {
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
    var settings=new RouterSettings(b);settings.setFanoutEnabled(false);settings.setRunOptimizer(false);settings.setAutomaticNeckdown(true);var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());
    var costs=new ArrayList<Object>();for(var c:settings.getTraceCosts())costs.add(list(bits(c.horizontal()),bits(c.vertical())));var active=new ArrayList<Object>();var bend=new ArrayList<Object>();for(int i=0;i<b.getLayerCount();i++){active.add(settings.getLayerActive(i));bend.add(bits(settings.getBendCost(i)));}
    var resolved=obj("traceCosts",costs,"active",active,"bend",bend,"viaCosts",settings.getViaCosts(),"viasAllowed",settings.getViasAllowed(),"neckdown",settings.getAutomaticNeckdown());
    int width=in.get("width").getAsInt();Arrays.fill(ctrl.traceHalfWidth,width);Arrays.fill(ctrl.compensatedTraceHalfWidth,width+b.searchTreeManager.getDefaultTree().clearanceCompensationValue(1,0));ctrl.traceClearanceClassIndex=1;ctrl.maxShoveTraceRecursionDepth=3;ctrl.maxShoveViaRecursionDepth=3;
    var points=new ArrayList<Point>();for(var raw:in.getAsJsonArray("points")){var c=raw.getAsJsonArray();points.add(origin.translateBy(new IntVector(c.get(0).getAsInt(),c.get(1).getAsInt())));}
    var trace=b.insertTraceWithoutCleaning(new Polyline(points.toArray(Point[]::new)),0,width,new int[]{3},1,FixedState.UNFIXED);
    if(in.get("anchors").getAsBoolean()){b.insertVia(b.library.getViaPadstack(0),points.getFirst(),new int[]{3},1,FixedState.USER_FIXED,true);b.insertVia(b.library.getViaPadstack(0),points.getLast(),new int[]{3},1,FixedState.USER_FIXED,true);}
    if(in.get("barrier").getAsBoolean())b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin.translateBy(new IntVector(15000,-30000)),origin.translateBy(new IntVector(15000,60000))}),0,500,new int[]{4},1,FixedState.USER_FIXED);
    var tree=b.searchTreeManager.getDefaultTree();var room=new ObstacleExpansionRoom(trace,in.get("index").getAsInt(),tree);var box=room.getShape().boundingBox();int l=box.ll.x,rr=box.ur.x,lo=box.ll.y,hi=box.ur.y,mx=(l+rr)/2,my=(lo+hi)/2;
    var doors=new ArrayList<ExpansionDoor>();var rooms=new ArrayList<ExpansionRoom>();rooms.add(room);
    int[][] boxes={{l,hi,mx,hi+20000},{mx,hi,rr,hi+20000},{l,lo-20000,mx,lo},{mx,lo-20000,rr,lo},{l-20000,lo,l,hi},{rr,lo,rr+20000,hi},{l,lo,rr,hi},{rr+30000,hi+30000,rr+40000,hi+40000}};
    for(var a:boxes){var other=new CompleteFreeSpaceExpansionRoom(new IntBox(a[0],a[1],a[2],a[3]),0,rooms.size());rooms.add(other);var door=new ExpansionDoor(room,other);doors.add(door);room.addDoor(door);other.addDoor(door);}
    for(int i=0;i<trace.tileShapeCount();i++){var other=new ObstacleExpansionRoom(trace,i,tree);rooms.add(other);var door=new ExpansionDoor(room,other,2);doors.add(door);room.addDoor(door);other.addDoor(door);}
    var otherTrace=b.insertTraceWithoutCleaning(new Polyline(new Point[]{points.getLast(),points.getLast().translateBy(new IntVector(20000,0))}),0,width,new int[]{3},1,FixedState.UNFIXED);var otherRoom=new ObstacleExpansionRoom(otherTrace,0,tree);rooms.add(otherRoom);var link=new ExpansionDoor(room,otherRoom,2);doors.add(link);room.addDoor(link);otherRoom.addDoor(link);
    var target=new TargetItemExpansionDoor(trace,0,room,tree);
    var identities=new IdentityHashMap<Object,Integer>();var rows=new ArrayList<Object>();
    int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    var endpoint=MazeTraceShover.class.getDeclaredMethod("endPointsMatching",PolylineTrace.class,Item.class);endpoint.setAccessible(true);
    var candidates=new ArrayList<Item>();candidates.add(trace);candidates.add(otherTrace);candidates.addAll(b.getItems());
    for(var item:candidates){Object value=null;String error=null;try{value=endpoint.invoke(null,trace,item);}catch(Throwable e){error=ShapeEntryOracle.error(e instanceof java.lang.reflect.InvocationTargetException?e.getCause():e);}rows.add(obj("query",obj("op","endpoints","item",item.getId()),"value",value,"error",error,"sections",List.of(),"graph",graph(doors,identities),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));}
    for(int di=-1;di<doors.size();di++){
      var d=di<0?null:doors.get(di);FloatLine[] entries=d==null?new FloatLine[]{new FloatLine(origin.toFloat(),origin.toFloat())}:d.getSectionSegments(ctrl.compensatedTraceHalfWidth[0]);
      for(int si=0;si<entries.length;si++)for(boolean left:new boolean[]{false,true})for(int mismatch:new int[]{0,1,2}){
        if(mismatch==1)ctrl.traceHalfWidth[0]++;if(mismatch==2)ctrl.traceClearanceClassIndex++;
        var query=obj("op","shove","door",di,"section",si,"left",left,"mismatch",mismatch);var element=new MazeListElement(d==null?target:d,si,null,0,0,0,room,entries[si],false,MazeSearchElement.Adjustment.NONE,false);var out=new ArrayList<MazeTraceShover.DoorSection>();Object value=null;String error=null;
        try{value=MazeTraceShover.checkShoveTraceLine(element,room,b,ctrl,left,out);}catch(Throwable e){error=ShapeEntryOracle.error(e);}
        var sections=new ArrayList<Object>();for(var x:out){var door=(ExpansionDoor)field(x,"door");var line=(FloatLine)field(x,"sectionLine");sections.add(obj("door",doors.indexOf(door),"index",field(x,"sectionIndex"),"line",floatLine(line)));}
        rows.add(obj("query",query,"value",value,"error",error,"sections",sections,"graph",graph(doors,identities),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
        if(mismatch==1)ctrl.traceHalfWidth[0]--;if(mismatch==2)ctrl.traceClearanceClassIndex--;
      }
    }
    // Actual source trace modification leaves externally held room indices stale.
    trace.change(new Polyline(new Point[]{points.get(0),points.get(1)}));
    var staleDoor=doors.get(0);var staleEntries=staleDoor.getSectionSegments(ctrl.compensatedTraceHalfWidth[0]);
    for(boolean left:new boolean[]{false,true}){
      var query=obj("op","stale","door",0,"section",0,"left",left,"mismatch",0);var element=new MazeListElement(staleDoor,0,null,0,0,0,room,staleEntries[0],false,MazeSearchElement.Adjustment.NONE,false);var out=new ArrayList<MazeTraceShover.DoorSection>();Object value=null;String error=null;
      try{value=MazeTraceShover.checkShoveTraceLine(element,room,b,ctrl,left,out);}catch(Throwable e){error=ShapeEntryOracle.error(e);}
      var sections=new ArrayList<Object>();for(var x:out){var door=(ExpansionDoor)field(x,"door");var line=(FloatLine)field(x,"sectionLine");sections.add(obj("door",doors.indexOf(door),"index",field(x,"sectionIndex"),"line",floatLine(line)));}
      rows.add(obj("query",query,"value",value,"error",error,"sections",sections,"graph",graph(doors,identities),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"resolvedSettings",resolved,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic maze-shove unit graphs on admitted public ECC83; no complete maze or route");
  }
  static int identity(IdentityHashMap<Object,Integer> ids,Object x){if(x==null)return 0;return ids.computeIfAbsent(x,k->ids.size()+1);}
  static Object floatLine(FloatLine l){return list(bits(l.a.x),bits(l.a.y),bits(l.b.x),bits(l.b.y));}
  static Object graph(List<ExpansionDoor> doors,IdentityHashMap<Object,Integer> ids)throws Exception{var out=new ArrayList<Object>();for(var d:doors){var arr=new ArrayList<Object>();if(d.sectionArr!=null)for(var x:d.sectionArr)arr.add(list(identity(ids,x),x.isOccupied,x.sectionNoOfBacktrackDoor,x.roomRipped,x.adjustment.name()));out.add(obj("dimension",d.dimension,"shape",shape(d.getShape()),"array",identity(ids,d.sectionArr),"sections",arr));}return out;}
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
