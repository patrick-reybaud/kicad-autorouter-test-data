package app.freerouting.board.facade;

import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.*;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;

/** Exercises actual entry-side constructors; no shove or routing result is emulated. */
public final class ShapeEntryOracle extends BoardConstructionOracle {
  // Lossless state interning. Equality uses the complete JSON text, not a hash.
  static final List<JsonElement> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){var json=(JsonElement)value;String key=json.toString();Integer existing=snapshotIds.get(key);if(existing!=null)return existing;int id=snapshots.size();snapshots.add(json);snapshotIds.put(key,id);return id;}

  static Object value(ShapeEntrySide side,FloatPoint input){return side==null?null:obj("no",side.no,"intersection",side.borderIntersection==null?null:list(bits(side.borderIntersection.x),bits(side.borderIntersection.y)),"inputAlias",side.borderIntersection==input,"sentinel",side==ShapeEntrySide.NOT_CALCULATED);}
  static String error(Throwable t){if(t instanceof ClassCastException)return "cast_error";if(t instanceof NullPointerException)return "null_error";if(t instanceof IndexOutOfBoundsException)return "index_error";if(t instanceof IllegalArgumentException)return "argument_error";if(t instanceof ArithmeticException)return "arithmetic_error";if(t instanceof NegativeArraySizeException)return "length_error";return t.getClass().getName()+":"+t.getMessage();}
  static Point point(JsonElement v){if(v.isJsonNull())return null;var a=v.getAsJsonArray();return new IntPoint(a.get(0).getAsInt(),a.get(1).getAsInt());}
  static TileShape tile(int i){return switch(i){case 0->new IntBox(-100,-100,100,100);case 1->new IntOctagon(-100,-100,100,100,-150,150,-150,150).normalize();case 2->new IntBox(-100,-100,100,100).toSimplex();case 3->TileShape.getInstance(new Point[]{new IntPoint(-100,-100),new IntPoint(100,-100),new IntPoint(-100,100)});case 4->Simplex.EMPTY;case 5->new IntBox(0,0,0,0);default->new IntBox(-100,0,100,0);};}
  static Object geometryCase(JsonObject input)throws Exception {
    var shape=tile(input.get("shape").getAsInt());var p=new ArrayList<Point>();for(var q:input.getAsJsonArray("points"))p.add(point(q));
    Polyline polyline=null;ShapeEntrySide result=null;String failure=null;
    try{
      polyline=new Polyline(p.toArray(Point[]::new));int mode=input.get("mode").getAsInt();int index=input.get("index").getAsInt();
      result=switch(mode){case 0->new ShapeEntrySide(polyline,index,shape);case 1->new ShapeEntrySide(point(input.get("from")),shape);default->new ShapeEntrySide(new LineSegment(polyline,index),shape,input.get("left").getAsBoolean());};
    }catch(Throwable t){failure=error(t);}
    return obj("input",input,"expected",obj("error",failure,"entry",value(result,null)));
  }
  static Object publicCase(Path boardPath,JsonObject input)throws Exception {
    var o=new Observers();o.constructionMetadata=true;
    RoutingBoard b;try(var in=Files.newInputStream(boardPath)){var result=DsnReader.readBoard(in,o,new ItemIdGenerator(),boardPath.getFileName().toString());if(!(result instanceof BoardReadResult.Success success))throw new IllegalStateException(result.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);
    var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(app.freerouting.board.model.items.Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();
    var changed=new TreeSet<Integer>();var initial=NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of());
    var points=new ArrayList<Point>();for(var p:input.getAsJsonArray("corners"))points.add(point(p));
    int net=input.get("net").getAsInt(),layer=input.get("layer").getAsInt();
    int width=b.rules.getTraceHalfWidth(net,layer);int clearance=b.rules.nets.get(net).getNetClass().getTraceClearanceClass();
    PolylineTrace trace=b.insertTraceWithoutCleaning(new Polyline(points.toArray(Point[]::new)),layer,width,new int[]{net},clearance,FixedState.UNFIXED);
    var before=NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of());
    ShapeAndEntrySide side=null;String failure=null;
    try{side=new ShapeAndEntrySide(trace,input.get("index").getAsInt(),input.get("orthogonal").getAsBoolean(),input.get("check").getAsBoolean());}catch(Throwable t){failure=error(t);}
    Object observed=side==null?null:obj("shape",shape(side.shape),"entry",value(side.fromSide,null),"shapeIsTreeShape",side.shape==trace.getTreeShape(b.searchTreeManager.getDefaultTree(),input.get("index").getAsInt()));
    var after=NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of());
    return obj("input",input,"width",width,"clearance",clearance,"initial",snapshot(initial),"before",snapshot(before),"result",obj("error",failure,"value",observed),"after",snapshot(after));
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var geometry=new ArrayList<Object>();
    for(var c:input.getAsJsonArray("geometry"))geometry.add(geometryCase(c.getAsJsonObject()));
    var real=new ArrayList<Object>();for(var c:input.getAsJsonArray("public"))real.add(publicCase(Path.of(args[1]),c.getAsJsonObject()));
    var direct=new ArrayList<Object>();for(int no:new int[]{Integer.MIN_VALUE,-1,0,1,8,Integer.MAX_VALUE})for(boolean nil:new boolean[]{false,true}){FloatPoint point=nil?null:new FloatPoint(-0.0,0.5);var side=new ShapeEntrySide(no,point);Object before=value(side,point);side.borderIntersection=null;direct.add(obj("no",no,"null",nil,"before",before,"after",value(side,point)));}
    Files.writeString(Path.of(args[2]),NeutralBoardModel.JSON.toJson(obj("geometry",geometry,"public",real,"snapshots",snapshots,"direct",direct,"sentinel",value(ShapeEntrySide.NOT_CALCULATED,null),"nativeRoutingExecuted",false))+"\n");
  }
}
