package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.ShapeTraceEntries;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;
import static app.freerouting.board.facade.CollisionChecksOracle.json;
import static app.freerouting.board.facade.CollisionChecksOracle.xy;

/** Actual source trace-entry ordering, stack/piece decisions and board cutouts. */
public final class ShapeTraceEntriesOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String text=value.toString();Integer id=snapshotIds.get(text);if(id!=null)return id;int next=snapshots.size();snapshots.add(text);snapshotIds.put(text,next);return next;}
  static final class Refs { final List<Object> objects=new ArrayList<>();int id(Object o){if(o==null)return 0;for(int i=0;i<objects.size();i++)if(objects.get(i)==o)return i+1;objects.add(o);return objects.size();} }
  static Object point(FloatPoint p,Refs refs){return p==null?null:list(refs.id(p),bits(p.x),bits(p.y));}
  static Object state(ShapeTraceEntries entry,ShapeEntrySide input,RoutingBoard board,Refs nodes,Refs floats,Refs sides)throws Exception {
    Object anchor=field(entry,"listAnchor"),current=anchor;var seen=new IdentityHashMap<Object,Boolean>();
    while(current!=null&&!seen.containsKey(current)){seen.put(current,true);nodes.id(current);current=field(current,"next");}
    for(int i=0;i<nodes.objects.size();i++)nodes.id(field(nodes.objects.get(i),"next"));
    var side=(ShapeEntrySide)field(entry,"fromSide");Object from=side==null?null:obj("id",sides.id(side),"no",side.no,"point",point(side.borderIntersection,floats),"inputAlias",side==input,"sentinel",side==ShapeEntrySide.NOT_CALCULATED);
    var entries=new ArrayList<Object>();
    for(var node:nodes.objects){var trace=(PolylineTrace)field(node,"trace");entries.add(obj("id",nodes.id(node),"trace",trace.getId(),"traceOnBoard",trace.isOnTheBoard(),"traceIsCurrent",board.getItem(trace.getId())==trace,"line",field(node,"traceLineNo"),"edge",field(node,"edgeIndex"),"level",field(node,"stackLevel"),"point",point((FloatPoint)field(node,"entryApprox"),floats),"next",nodes.id(field(node,"next"))));}
    var obstacle=entry.getFoundObstacle();
    return obj("anchor",nodes.id(anchor),"from",from,"entries",entries,"pieces",entry.substituteTraceCount(),"depth",entry.stackDepth(),"tails",entry.traceTailsInShape(),"obstacle",obstacle==null?null:obstacle.getId(),"vias",entry.shoveViaList.stream().map(Item::getId).toList());
  }
  static Object trace(PolylineTrace t,NeutralBoardModel exporter)throws Exception {
    if(t==null)return null;var descriptor=NeutralBoardItemsOracle.describe(exporter,t);var corners=new ArrayList<Object>();for(var p:t.polyline().corners())corners.add(NeutralBoardModel.xy(p));var tiles=new ArrayList<Object>();for(int i=0;i<t.tileShapeCount();i++)tiles.add(shape(t.getTileShape(i)));
    return obj("descriptor",descriptor,"corners",corners,"length",bits(t.getLength()),"shapes",tiles,"onBoard",t.isOnTheBoard());
  }
  static Object history(Path path,JsonObject in)throws Exception {
    var o=new Observers();o.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,o,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()));
    b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());b.startMarkingChangedArea();
    var added=new ArrayList<Item>();
    for(var t:in.getAsJsonArray("traces")){var q=t.getAsJsonObject();var ps=new ArrayList<Point>();for(var p:q.getAsJsonArray("points"))ps.add(xy(p));added.add(b.insertTraceWithoutCleaning(new Polyline(ps.toArray(Point[]::new)),0,q.get("width").getAsInt(),NeutralBoardModel.JSON.fromJson(q.get("nets"),int[].class),1,FixedState.values()[q.get("fixed").getAsInt()]));}
    for(var v:in.getAsJsonArray("vias")){var q=v.getAsJsonObject();added.add(b.insertVia(b.library.getViaPadstack(0),xy(q.get("point")),NeutralBoardModel.JSON.fromJson(q.get("nets"),int[].class),1,FixedState.UNFIXED,true));}
    for(var id:in.getAsJsonArray("existing"))added.add(b.getItem(id.getAsInt()));
    Collection<Item> selected=in.get("sorted").getAsBoolean()?new TreeSet<>(added):new LinkedList<>(added);
    b.generateSnapshot();int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()));
    var coords=in.getAsJsonArray("box");TileShape shape=new IntBox(coords.get(0).getAsInt(),coords.get(1).getAsInt(),coords.get(2).getAsInt(),coords.get(3).getAsInt());
    int kind=in.get("shapeKind").getAsInt();if(kind==1)shape=shape.boundingOctagon();else if(kind==2)shape=shape.toSimplex();
    ShapeEntrySide from=null;var side=in.get("side");
    if(!side.isJsonNull()){int number=side.getAsInt();from=number==-99?ShapeEntrySide.NOT_CALCULATED:new ShapeEntrySide(number,in.get("sidePoint").isJsonNull()?null:new FloatPoint(in.getAsJsonArray("sidePoint").get(0).getAsDouble(),in.getAsJsonArray("sidePoint").get(1).getAsDouble()));}
    var entry=new ShapeTraceEntries(shape,0,NeutralBoardModel.JSON.fromJson(in.get("ownNets"),int[].class),1,from,b);
    var nodes=new Refs();var floats=new Refs();var sides=new Refs();var rows=new ArrayList<Object>();
    rows.add(obj("op","construct","value",null,"error",null,"entry",state(entry,from,b,nodes,floats,sides),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()))));
    Boolean stored=null;String failure=null;try{stored=entry.storeItems(selected,in.get("padCheck").getAsBoolean(),in.get("copperSharing").getAsBoolean());}catch(Throwable t){failure=ShapeEntryOracle.error(t);}
    rows.add(obj("op","store","value",stored,"error",failure,"entry",state(entry,from,b,nodes,floats,sides),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()))));
    if(Boolean.TRUE.equals(stored)){
      for(int i=0;;i++){
        if(i==32)throw new IllegalStateException("Bench piece bound exceeded; no route fallback");
        PolylineTrace piece=null;Object value=null;failure=null;try{piece=entry.nextSubstituteTracePiece();value=trace(piece,exporter);}catch(Throwable t){failure=ShapeEntryOracle.error(t);}
        rows.add(obj("op","next","value",value,"error",failure,"entry",state(entry,from,b,nodes,floats,sides),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()))));
        if(piece==null||failure!=null)break;
      }
      for(String operation:List.of("cutout","undo","redo")){
        Object value=null;failure=null;try{switch(operation){case "cutout"->entry.cutoutTraces(selected);case "undo"->value=b.undo(changed);case "redo"->value=b.redo(changed);}}catch(Throwable t){failure=ShapeEntryOracle.error(t);}
        rows.add(obj("op",operation,"value",value,"error",failure,"entry",state(entry,from,b,nodes,floats,sides),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()))));
      }
    }
    return obj("input",in,"initial",initial,"prepared",prepared,"selectedIds",selected.stream().map(Item::getId).toList(),"rows",rows,"source","Explicit synthetic shove-unit traces on public ECC83; no path search or native routing");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    var netCases=new ArrayList<Object>();var m=ShapeTraceEntries.class.getDeclaredMethod("netNosEqual",int[].class,int[].class);m.setAccessible(true);
    for(var c:input.getAsJsonArray("netCases")){var q=c.getAsJsonObject();Object result=null;String error=null;try{result=m.invoke(null,NeutralBoardModel.JSON.fromJson(q.get("a"),int[].class),NeutralBoardModel.JSON.fromJson(q.get("b"),int[].class));}catch(InvocationTargetException e){error=ShapeEntryOracle.error(e.getCause());}netCases.add(obj("input",q,"value",result,"error",error));}
    // Stream exact full snapshots rather than retaining duplicate Gson trees and a giant final JSON string.
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"netCases\":"+NeutralBoardModel.JSON.toJson(netCases)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
