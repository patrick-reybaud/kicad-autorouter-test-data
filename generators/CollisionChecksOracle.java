package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
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

/** Actual source collision/entry decisions. Public-board mutations below are synthetic unit tests. */
public final class CollisionChecksOracle extends BoardConstructionOracle {
  static final Method FRONT, CHECK, FROM;
  static {
    try {
      FRONT=ForcedPadRouter.class.getDeclaredMethod("inFrontOfPad",Line.class,TileShape.class,int.class,int.class,boolean.class);
      CHECK=ForcedPadRouter.class.getDeclaredMethod("calcCheckShapeForFromSide",TileShape.class,Point.class,Line.class);
      FROM=ForcedPadRouter.class.getDeclaredMethod("calcFromSide",TileShape.class,Point.class,int.class,int.class,int.class);
      for(var m:List.of(FRONT,CHECK,FROM))m.setAccessible(true);
    }catch(Exception e){throw new ExceptionInInitializerError(e);}
  }
  static Object invoke(Method m,Object receiver,Object... args)throws Throwable {
    try{return m.invoke(receiver,args);}catch(InvocationTargetException e){throw e.getCause();}
  }
  static final List<JsonElement> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){var j=(JsonElement)value;String key=j.toString();Integer id=snapshotIds.get(key);if(id!=null)return id;int n=snapshots.size();snapshots.add(j);snapshotIds.put(key,n);return n;}
  static Point xy(JsonElement v){return ShapeEntryOracle.point(v);}
  static Object xy(Point p){var q=(IntPoint)p;return list(q.x,q.y);}
  static TileShape tile(int i){
    if(i==7)return TileShape.getInstance(new Point[]{new IntPoint(0,0),new IntPoint(101,0),new IntPoint(0,99)});
    if(i==8)return null;
    return ShapeEntryOracle.tile(i);
  }
  static Object geometry(JsonObject input)throws Exception {
    Object result=null;String failure=null;
    try {
      TileShape pad=tile(input.get("shape").getAsInt());
      Point a=xy(input.get("a")),b=xy(input.get("b"));
      if(input.get("rational").getAsBoolean())a=Point.getInstance(java.math.BigInteger.ONE,java.math.BigInteger.valueOf(3),java.math.BigInteger.TWO);
      Line line=input.get("nullLine").getAsBoolean()?null:new Line(a,b);
      if(input.get("mode").getAsInt()==0)
        result=invoke(FRONT,null,line,pad,input.get("side").getAsInt(),input.get("width").getAsInt(),input.get("withSides").getAsBoolean());
      else result=shape((TileShape)invoke(CHECK,null,pad,xy(input.get("center")),line));
    }catch(Throwable t){failure=ShapeEntryOracle.error(t);}
    return obj("input",input,"expected",obj("error",failure,"value",result));
  }
  static JsonObject json(Object value){return NeutralBoardModel.JSON.toJsonTree(value).getAsJsonObject();}
  static JsonObject query(String op,TileShape s,int layer,int[] nets,int clearance,Collection<Pin> pins){
    var box=s.boundingBox();var ids=pins==null?null:pins.stream().map(Item::getId).toList();
    return json(obj("op",op,"box",list(box.ll.x,box.ll.y,box.ur.x,box.ur.y),"layer",layer,"nets",nets,"clearance",clearance,"pins",ids));
  }
  static Object history(Path boardPath,int variant,JsonObject inputs)throws Exception {
    var o=new Observers();o.constructionMetadata=true;
    RoutingBoard b;try(var in=Files.newInputStream(boardPath)){var result=DsnReader.readBoard(in,o,new ItemIdGenerator(),boardPath.getFileName().toString());if(!(result instanceof BoardReadResult.Success success))throw new IllegalStateException(result.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);
    var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()));
    b.searchTreeManager.setClearanceCompensationUsed(variant%2!=0);
    var pin=(Pin)b.getItem(3);int own=pin.getNetNumber(0),foreign=own==1?2:1;var center=(IntPoint)pin.getCenter();
    int tieMode=variant/2;
    if(tieMode>0){
      pin.netNumbers=new int[]{own,foreign};
      int end=tieMode==1?1:50000;
      b.insertTraceWithoutCleaning(new Polyline(center,new IntPoint(center.x+end,center.y)),0,1,new int[]{foreign},pin.clearanceClassIndex(),FixedState.UNFIXED);
    }
    int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()));
    var pins=new ArrayList<Pin>();for(var item:created)if(item instanceof Pin p)pins.add(p);
    var queries=new ArrayList<JsonObject>();
    for(var p:pins){
      var c=(IntPoint)p.getCenter();int net=p.netCount()>0?p.getNetNumber(0):1;int cl=p.clearanceClassIndex();
      TileShape small=new IntBox(c.x-2,c.y-2,c.x+2,c.y+2);
      queries.add(query("trace",small,p.firstLayer(),new int[]{net},cl,null));
      queries.add(query("trace",small,p.firstLayer(),new int[]{net},cl,List.of()));
      queries.add(query("trace",small,p.firstLayer(),new int[]{net},cl,List.of(p)));
      queries.add(query("shape",small,p.firstLayer(),new int[]{net},cl,null));
      queries.add(query("shape",small,p.firstLayer(),new int[0],cl,null));
      queries.add(query("trace",small,p.lastLayer(),new int[0],cl,null));
    }
    // The actual pin-3 tie-pad source decision at its centre, with and without the trace extending outside.
    queries.add(query("trace",new IntBox(center.x-100,center.y-100,center.x+100,center.y+100),0,new int[]{own},1,List.of(pin)));
    queries.add(query("trace",new IntBox(center.x-100,center.y-100,center.x+60000,center.y+100),0,new int[]{own},1,List.of(pin)));
    var bounds=b.getBoundingBox();
    queries.add(query("shape",new IntBox(bounds.ll.x-1,bounds.ll.y,bounds.ll.x,bounds.ll.y+1),0,new int[0],0,null));
    queries.add(query("trace",new IntBox(bounds.ll.x-1,bounds.ll.y,bounds.ll.x,bounds.ll.y+1),0,new int[0],0,null));
    for(var q:inputs.getAsJsonArray("polylines")){
      var c=q.getAsJsonObject();for(int[] nets:new int[][]{{c.get("net").getAsInt()},new int[0]})queries.add(json(obj("op","polyline","points",c.get("corners"),"layer",c.get("layer"),"nets",nets,"clearance",1,"width",10)));
    }
    for(var p:pins.subList(0,Math.min(8,pins.size()))){
      var box=p.getTileShapeOnLayer(0).boundingBox();var c=(IntPoint)p.getCenter();
      for(int delta:new int[]{1,500,2000,5000,10000}){
        int x=box.ur.x+delta;var q=query("from",new IntBox(x-2,c.y-2,x+2,c.y+2),0,new int[0],1,null);
        q.add("center",NeutralBoardModel.JSON.toJsonTree(list(x,c.y)));q.addProperty("offset",1);queries.add(q);
      }
    }
    var rows=new ArrayList<Object>();var router=new ForcedPadRouter(b);
    for(var q:queries){
      Object result=null;String failure=null;
      try {
        String op=q.get("op").getAsString();int layer=q.get("layer").getAsInt(),cl=q.get("clearance").getAsInt();
        int[] nets=NeutralBoardModel.JSON.fromJson(q.get("nets"),int[].class);
        if(op.equals("polyline")){
          var ps=new ArrayList<Point>();for(var p:q.getAsJsonArray("points"))ps.add(xy(p));var poly=new Polyline(ps.toArray(Point[]::new));int width=q.get("width").getAsInt();
          var temp=new PolylineTrace(poly,layer,width,nets,cl,0,0,FixedState.UNFIXED,b);
          result=obj("pins",temp.touchingPinsAtEndCorners().stream().map(Item::getId).toList(),"allowed",b.checkPolylineTrace(poly,layer,width,nets,cl));
        }else {
          var coords=q.getAsJsonArray("box");TileShape shape=new IntBox(coords.get(0).getAsInt(),coords.get(1).getAsInt(),coords.get(2).getAsInt(),coords.get(3).getAsInt());
          Set<Pin> contacts=null;if(!q.get("pins").isJsonNull()){contacts=new TreeSet<>();for(var id:q.getAsJsonArray("pins"))contacts.add((Pin)b.getItem(id.getAsInt()));}
          if(op.equals("trace"))result=b.checkTraceShape(shape,layer,nets,cl,contacts);
          else if(op.equals("shape"))result=b.checkShape(shape,layer,nets,cl);
          else {
            Point c=xy(q.get("center"));int offset=q.get("offset").getAsInt();
            ShapeEntrySide side=(ShapeEntrySide)invoke(FROM,router,shape,c,layer,offset,cl);
            var probes=new ArrayList<Object>();var os=(TileShape)shape.offset(offset);
            for(int clearance:new int[]{cl,0}){var outcomes=new ArrayList<Object>();for(int i=0;i<os.borderLineCount();i++){TileShape cs=(TileShape)invoke(CHECK,null,shape,c,os.borderLine(i));outcomes.add(list(shape(cs),b.checkTraceShape(cs,layer,new int[0],clearance,null)));}probes.add(outcomes);}
            result=obj("side",ShapeEntryOracle.value(side,null),"probes",probes);
          }
        }
      }catch(Throwable t){failure=ShapeEntryOracle.error(t);}
      rows.add(obj("input",q,"expected",obj("error",failure,"value",result),"after",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()))));
    }
    return obj("variant",variant,"initial",initial,"prepared",prepared,"rows",rows,"source","fr-ecc83: public board plus explicit synthetic collision queries/mutations; no routing");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var cases=new ArrayList<Object>();
    for(var c:input.getAsJsonArray("geometry"))cases.add(geometry(c.getAsJsonObject()));
    var histories=new ArrayList<Object>();for(int i=0;i<6;i++)histories.add(history(Path.of(args[1]),i,input));
    Files.writeString(Path.of(args[2]),NeutralBoardModel.JSON.toJson(obj("geometry",cases,"histories",histories,"snapshots",snapshots,"nativeRoutingExecuted",false))+"\n");
  }
}
