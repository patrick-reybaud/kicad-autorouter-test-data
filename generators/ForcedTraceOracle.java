package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.optimize.TraceShover;
import app.freerouting.core.library.Padstack;
import app.freerouting.rules.ViaInfo;
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

/** Actual source shove insertion and undo/redo; these are unit histories, not a route. */
public final class ForcedTraceOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static Object history(Path path,JsonObject in)throws Exception {
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    int angle=in.get("angle").getAsInt(),layer=0;b.rules.setTraceAngleRestriction(angle==90?AngleRestriction.NINETY_DEGREE:AngleRestriction.FORTYFIVE_DEGREE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());
    IntPoint origin=new IntPoint(1500000,-1150000);
    int variant=in.get("variant").getAsInt();
    if(variant==1||variant==2)b.insertVia(b.library.getViaPadstack(0),origin,new int[]{3},1,variant==2?FixedState.UNFIXED:FixedState.USER_FIXED,true);
    if(variant==5||variant==17||variant==18||variant==20){
      int dy=variant==5?5000:50000,dx=variant==20?10000:0;
      Point lo=origin.translateBy(new IntVector(dx,-dy)),hi=origin.translateBy(new IntVector(dx,dy));
      b.insertTraceWithoutCleaning(new Polyline(new Point[]{lo,hi}),layer,variant==20?500:1000,new int[]{3},1,variant==20?FixedState.USER_FIXED:FixedState.UNFIXED);
      if(variant==18){b.insertVia(b.library.getViaPadstack(0),lo,new int[]{3},1,FixedState.USER_FIXED,true);b.insertVia(b.library.getViaPadstack(0),hi,new int[]{3},1,FixedState.USER_FIXED,true);}
    }
    if(variant==16){var pad=b.library.padstacks.add(new IntBox(-2000,-2000,2000,2000),layer,layer);var pack=b.library.packages.add(new app.freerouting.core.library.Package.Pin[]{new app.freerouting.core.library.Package.Pin("1",pad.id,new IntVector(0,0),0)});var component=b.components.add(origin,0,true,pack);b.insertPin(component.id,0,new int[]{2},1,FixedState.UNFIXED);}
    if(variant==21){Point from=origin.translateBy(new IntVector(-50000,angle==90?0:-10000)),to=origin.translateBy(new IntVector(-40000,0));b.insertTraceWithoutCleaning(new Polyline(from,to),0,1000,new int[]{2},1,FixedState.UNFIXED);}
    if(variant==22){Point from=origin.translateBy(new IntVector(-10000,angle==90?0:-5000)),to=origin.translateBy(new IntVector(-5000,0));b.insertTraceWithoutCleaning(new Polyline(from,to),0,1000,new int[]{2},1,FixedState.UNFIXED);}
    b.setShoveFailingObstacle(b.getOutline());b.setShoveFailingLayer(7);b.generateSnapshot();int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();
    var points=new ArrayList<Point>();for(var value:in.getAsJsonArray("points")){var p=value.getAsJsonArray();points.add(origin.translateBy(new IntVector(p.get(0).getAsInt(),p.get(1).getAsInt())));}
    if(in.get("rational").getAsBoolean()){var p=(IntPoint)points.get(0);points.set(0,Point.getInstance(java.math.BigInteger.valueOf(p.x*2L+1),java.math.BigInteger.valueOf(p.y*2L),java.math.BigInteger.TWO));}
    Point from=points.get(0),to=points.get(points.size()-1);Polyline polyline=new Polyline(points.toArray(Point[]::new));var pointRefs=new PolylineTighteningOracle.Refs();pointRefs.id(from);pointRefs.id(to);
    var via=new ViaInfo("oracle",b.library.getViaPadstack(0),1,true,b.rules);int[] widths=new int[b.getLayerCount()];Arrays.fill(widths,1000);
    var limit=in.get("stopAfter").getAsInt()<0?null:new Limit(in.get("stopAfter").getAsInt());
    for(String op:new String[]{"check","insert","undo","redo"}){
      Object value=null;String error=null;
      try{
        int depth=in.get("depth").getAsInt(),viaDepth=in.get("viaDepth").getAsInt(),spring=in.get("springDepth").getAsInt(),tidy=in.get("tidy").getAsInt();
        if(op.equals("check"))value=b.checkForcedTracePolyline(polyline,1000,0,new int[]{2},1,depth,viaDepth,spring);
        else if(op.equals("undo"))value=b.undo(changed);
        else if(op.equals("redo"))value=b.redo(changed);
        else if(in.get("mode").getAsString().equals("via"))value=b.forcedVia(via,origin,new int[]{2},1,widths,depth,viaDepth,tidy,100,0);
        else {
          Point result=in.get("mode").getAsString().equals("segment")?b.insertForcedTraceSegment(from,to,1000,0,new int[]{2},1,depth,viaDepth,spring,tidy,100,in.get("withCheck").getAsBoolean(),limit):b.insertForcedTracePolyline(polyline,1000,0,new int[]{2},1,depth,viaDepth,spring,tidy,100,in.get("withCheck").getAsBoolean(),limit);
          value=obj("id",pointRefs.id(result),"point",NeutralBoardModel.xy(result),"isFrom",result==from,"isTo",result==to);
        }
      }catch(Throwable failure){error=ShapeEntryOracle.error(failure);}
      rows.add(obj("op",op,"value",value,"error",error,"cache",PolylineTighteningOracle.cache(polyline,pointRefs),"limitCalls",limit==null?null:limit.calls,"failing",b.getShoveFailingObstacle()==null?null:b.getShoveFailingObstacle().getId(),"failingLayer",b.getShoveFailingLayer(),"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic source forced trace/via unit histories on admitted public ECC83; no autoroute");
  }
  static class Limit extends TimeLimit { int calls,after; Limit(int n){super(Integer.MAX_VALUE);after=n;} @Override public boolean limitExceeded(){return calls++>=after;} }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
