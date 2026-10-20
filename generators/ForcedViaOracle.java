package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.optimize.TraceShover;
import app.freerouting.core.library.Padstack;
import app.freerouting.rules.ViaInfo;
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

/** Actual source shove insertion and undo/redo; these are unit histories, not a route. */
public final class ForcedViaOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static Object history(Path root,JsonObject in)throws Exception {
    Path path=root.resolve(in.get("board").getAsString()).resolve("prepared").resolve(in.get("board").getAsString()+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    int angle=in.get("angle").getAsInt(),layer=in.get("layer").getAsInt();b.rules.setTraceAngleRestriction(angle==90?AngleRestriction.NINETY_DEGREE:AngleRestriction.FORTYFIVE_DEGREE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());b.startMarkingChangedArea();b.rules.setHoleClearance(in.get("hole").getAsInt());
    IntPoint origin=new IntPoint(in.get("x").getAsInt(),in.get("y").getAsInt());
    int variant=in.get("variant").getAsInt();
    if(variant==1||variant==2)b.insertVia(b.library.getViaPadstack(0),origin,new int[]{3},1,variant==2?FixedState.UNFIXED:FixedState.USER_FIXED,true);
    if(variant==5||variant==17||variant==18||variant==20){
      int dy=variant==5?5000:50000,dx=variant==20?10000:0;
      Point lo=origin.translateBy(new IntVector(dx,-dy)),hi=origin.translateBy(new IntVector(dx,dy));
      b.insertTraceWithoutCleaning(new Polyline(new Point[]{lo,hi}),layer,variant==20?500:1000,new int[]{3},1,variant==20?FixedState.USER_FIXED:FixedState.UNFIXED);
      if(variant==18){b.insertVia(b.library.getViaPadstack(0),lo,new int[]{3},1,FixedState.USER_FIXED,true);b.insertVia(b.library.getViaPadstack(0),hi,new int[]{3},1,FixedState.USER_FIXED,true);}
    }
    if(variant==16){var pad=b.library.padstacks.add(new IntBox(-2000,-2000,2000,2000),layer,layer);var pack=b.library.packages.add(new app.freerouting.core.library.Package.Pin[]{new app.freerouting.core.library.Package.Pin("1",pad.id,new IntVector(0,0),0)});var component=b.components.add(origin,0,true,pack);b.insertPin(component.id,0,new int[]{2},1,FixedState.UNFIXED);}
    Padstack pad=null;ViaInfo info=null;
    if(in.get("mode").getAsString().equals("via")){
      ConvexShape[] pads=new ConvexShape[b.layerStructure.layers.length];var circle=new Circle(IntPoint.ZERO,1000);pads[0]=circle;pads[pads.length-1]=circle;
      pad=b.library.padstacks.add("oracle_2:1",pads,true,true);info=new ViaInfo("oracle",pad,1,in.get("attach").getAsBoolean(),b.rules);
    }
    b.setShoveFailingObstacle(b.getOutline());b.setShoveFailingLayer(7);b.generateSnapshot();int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();
    var holeMethod=ForcedViaInserter.class.getDeclaredMethod("holeCheckShape",Padstack.class,Point.class,RoutingBoard.class);holeMethod.setAccessible(true);
    var sideMethod=ForcedViaInserter.class.getDeclaredMethod("calculateFromSide",FloatPoint.class,TileShape.class,Simplex.class,double.class,boolean.class);sideMethod.setAccessible(true);
    for(var element:in.getAsJsonArray("queries")){
      var q=element.getAsJsonObject();String op=q.get("op").getAsString();Object value=null;String error=null;Object detail=null;
      try{
        Point location=q.has("nullPoint")&&q.get("nullPoint").getAsBoolean()?null:q.has("rational")&&q.get("rational").getAsBoolean()?Point.getInstance(java.math.BigInteger.valueOf(origin.x*2L+1),java.math.BigInteger.valueOf(origin.y*2L),java.math.BigInteger.TWO):origin;
        int depth=q.has("depth")?q.get("depth").getAsInt():3,viaDepth=q.has("viaDepth")?q.get("viaDepth").getAsInt():3;
        int[] widths=q.has("widths")&&!q.get("widths").isJsonNull()?new com.google.gson.Gson().fromJson(q.get("widths"),int[].class):null;
        switch(op){
          case "length"->{var a=q.getAsJsonArray("segment");Point start=origin.translateBy(new IntVector(a.get(0).getAsInt(),a.get(1).getAsInt())),end=origin.translateBy(new IntVector(a.get(2).getAsInt(),a.get(3).getAsInt()));var segment=new LineSegment(new Polyline(new Point[]{start,end}),1);value=bits(TraceShover.check(b,segment,q.get("left").getAsBoolean(),layer,new int[]{2},q.get("width").getAsInt(),1,depth,viaDepth));detail=obj("start",point(segment.startPoint()),"end",point(segment.endPoint()),"line",list(point(segment.getLine().a),point(segment.getLine().b)));}
          case "hole"->{Shape result=(Shape)holeMethod.invoke(null,pad,location,b);value=result==null?null:obj("center",point(((Circle)result).center),"radius",((Circle)result).radius);detail=bits(pad.getDrillRadius());}
          case "side","layer"->{var r=q.getAsJsonArray("room");TileShape room=new IntBox(origin.x+r.get(0).getAsInt(),origin.y+r.get(1).getAsInt(),origin.x+r.get(2).getAsInt(),origin.y+r.get(3).getAsInt());double radius=q.get("radius").getAsDouble();
            if(op.equals("layer"))value=ForcedViaInserter.checkLayer(radius,1,in.get("attach").getAsBoolean(),room,location,layer,new int[]{2},depth,viaDepth,b,q.get("width").getAsInt(),1).name();
            else {var viaShape=new Circle(origin,1000);TileShape tile=angle==90?viaShape.boundingBox():viaShape.boundingOctagon();ShapeEntrySide side=(ShapeEntrySide)sideMethod.invoke(null,origin.toFloat(),tile,room.toSimplex(),radius,angle==90);value=side==null?null:obj("side",side.no,"border",side.borderIntersection==null?null:list(bits(side.borderIntersection.x),bits(side.borderIntersection.y)));}
          }
          case "check"->value=ForcedViaInserter.check(info,location,new int[]{2},depth,viaDepth,b,widths,1);
          case "insert"->value=ForcedViaInserter.insert(info,location,new int[]{2},1,widths,depth,viaDepth,b);
          case "undo"->value=b.undo(changed);
          case "redo"->value=b.redo(changed);
        }
      }catch(Throwable failure){error=ShapeEntryOracle.error(failure instanceof java.lang.reflect.InvocationTargetException?failure.getCause():failure);}
      rows.add(obj("input",q,"value",value,"error",error,"detail",detail,"failing",b.getShoveFailingObstacle()==null?null:b.getShoveFailingObstacle().getId(),"failingLayer",b.getShoveFailingLayer(),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic source unit histories on unchanged admitted public board; no route");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
