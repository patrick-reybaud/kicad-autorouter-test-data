package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.optimize.TraceShover;
import app.freerouting.datastructures.TimeLimit;
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

/** Actual recursive source shove checks; these are unit histories, not a route. */
public final class ShoveChecksOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static final class Limit extends TimeLimit {int calls=0;final int after;Limit(int after){super(Integer.MAX_VALUE);this.after=after;}@Override public boolean limitExceeded(){return calls++>=after;}}
  static Object history(Path path,JsonObject in)throws Exception {
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    int angle=in.get("angle").getAsInt();b.rules.setTraceAngleRestriction(angle==90?AngleRestriction.NINETY_DEGREE:angle==45?AngleRestriction.FORTYFIVE_DEGREE:AngleRestriction.NONE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());b.startMarkingChangedArea();
    Point origin=in.get("pin").getAsBoolean()?((Pin)b.getItem(3)).getCenter():new IntPoint(1500000,-1150000);
    int variant=in.get("variant").getAsInt();
    if(variant==1||variant==2||variant==8)b.insertVia(b.library.getViaPadstack(0),origin,new int[]{variant==8?2:3},1,variant==2?FixedState.UNFIXED:FixedState.USER_FIXED,true);
    if(variant>=3&&variant<=7){
      Point[] ps=variant==4?new Point[]{origin.translateBy(new IntVector(0,-5000)),origin,origin.translateBy(new IntVector(5000,0))}:new Point[]{origin.translateBy(new IntVector(0,-5000)),origin.translateBy(new IntVector(0,5000))};
      b.insertTraceWithoutCleaning(new Polyline(ps),0,1000,new int[]{3},1,variant==5?FixedState.UNFIXED:FixedState.SHOVE_FIXED);
      if(variant==6||variant==7)b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin.translateBy(new IntVector(-5000,0)),origin.translateBy(new IntVector(5000,0))}),0,variant==7?20000:1000,new int[]{3},1,FixedState.SHOVE_FIXED);
    }
    if(variant>=9){
      var box=new IntBox(-5000,-3000,5000,3000);var translation=origin.differenceBy(Point.ZERO);
      if(variant==9)b.insertObstacle(box,0,translation,0,false,0,1,"spring obstacle",FixedState.USER_FIXED);
      if(variant==10)b.insertViaObstacle(box,0,translation,0,false,0,1,"spring via keepout",FixedState.USER_FIXED);
      if(variant==11)b.insertComponentObstacle(box,0,translation,0,false,0,1,"spring component keepout",FixedState.USER_FIXED);
      if(variant==12||variant==13){var area=new ConductionArea(box,0,translation,0,false,new int[]{3},1,0,0,"spring plane",variant==13,FixedState.USER_FIXED,b);b.insertItem(area);}
    }
    if(in.get("contact").getAsBoolean())b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin,origin.translateBy(new IntVector(10000,0))}),0,100,new int[]{2},1,FixedState.SHOVE_FIXED);
    if(variant==16){var pad=b.library.padstacks.add(new IntBox(-2000,-2000,2000,2000),0,0);var pack=b.library.packages.add(new app.freerouting.core.library.Package.Pin[]{new app.freerouting.core.library.Package.Pin("1",pad.id,new IntVector(0,0),0)});var component=b.components.add(origin,0,true,pack);b.insertPin(component.id,0,new int[]{2},1,FixedState.UNFIXED);}
    Via drill=b.insertVia(b.library.getViaPadstack(0),origin.translateBy(new IntVector(-7000,0)),new int[]{2},1,FixedState.UNFIXED,true);
    b.setShoveFailingObstacle(b.getOutline());b.setShoveFailingLayer(7);
    b.generateSnapshot();int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();var shover=new TraceShover(b);var forced=new ForcedPadRouter(b);
    for(var element:in.getAsJsonArray("queries")){
      var q=element.getAsJsonObject();String op=q.get("op").getAsString();Object value=null;String error=null;
      int x=q.get("x").getAsInt(),y=q.get("y").getAsInt(),w=q.get("w").getAsInt(),h=q.get("h").getAsInt();IntPoint c=(IntPoint)origin.translateBy(new IntVector(x,y));
      TileShape shape=new IntBox(c.x-w,c.y-h,c.x+w,c.y+h);if(q.get("kind").getAsInt()==1)shape=shape.boundingOctagon();else if(q.get("kind").getAsInt()==2)shape=shape.toSimplex();
      Point from=origin.translateBy(new IntVector(-40000,-40000));ShapeEntrySide side=q.get("sideNull").getAsBoolean()?null:new ShapeEntrySide(from,shape);
      int[] nets=NeutralBoardModel.JSON.fromJson(q.get("nets"),int[].class);Collection<Item> ignoreItems=q.get("ignore").getAsInt()==0?null:q.get("ignore").getAsInt()==1?new LinkedList<>():new TreeSet<>();if(ignoreItems!=null&&q.get("ignore").getAsInt()==3)ignoreItems.add(drill);
      Limit limit=q.get("stopAfter").getAsInt()<0?null:new Limit(q.get("stopAfter").getAsInt());
      int depth=q.get("depth").getAsInt(),viaDepth=q.get("viaDepth").getAsInt(),layer=q.get("layer").getAsInt();
      try{
        if(op.equals("pad"))value=forced.checkForcedPad(shape,side,layer,nets,1,q.get("sharing").getAsBoolean(),ignoreItems,depth,viaDepth,q.get("front").getAsBoolean(),limit).toString();
        else if(op.equals("trace")){Direction dir=q.get("direction").getAsInt()<0?null:q.get("direction").getAsInt()==0?Direction.RIGHT:Direction.UP;value=shover.check(shape,side,dir,layer,nets,1,depth,viaDepth,q.get("springDepth").getAsInt(),limit);}
        else value=DrillItemMover.check(op.equals("pin")?(Pin)b.getItem(3):drill,new IntVector(x,y),depth,viaDepth,ignoreItems,b,limit);
      }catch(Throwable failure){error=ShapeEntryOracle.error(failure);}
      Object ignored=null;if(ignoreItems!=null){var ids=new ArrayList<Integer>();for(var item:ignoreItems)ids.add(item.getId());ignored=ids;}
      rows.add(obj("input",q,"value",value,"error",error,"ignore",ignored,"limitCalls",limit==null?null:limit.calls,"failing",b.getShoveFailingObstacle()==null?null:b.getShoveFailingObstacle().getId(),"failingLayer",b.getShoveFailingLayer(),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic mutually recursive shove-check unit histories on admitted public ECC83; no route");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
