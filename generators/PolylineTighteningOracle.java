package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.optimize.TraceTightener;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;
import static app.freerouting.board.facade.CollisionChecksOracle.xy;

/** Real source trace editing/tightening and pin-exit operations, not a routing replay. */
public final class PolylineTighteningOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static final class Refs {final List<Object> values=new ArrayList<>();int id(Object p){if(p==null)return 0;for(int i=0;i<values.size();i++)if(values.get(i)==p)return i+1;values.add(p);return values.size();}}
  static Object cache(Polyline p,Refs refs)throws Exception {
    var corners=(Point[])field(p,"precalculatedCorners");var floats=(FloatPoint[])field(p,"precalculatedFloatCorners");var box=(IntBox)field(p,"precalculatedBoundingBox");
    Object cs=null,fs=null;if(corners!=null){var out=new ArrayList<Object>();for(var q:corners)out.add(list(refs.id(q),NeutralBoardModel.xy(q)));cs=out;}if(floats!=null){var out=new ArrayList<Object>();for(var q:floats)out.add(q==null?null:list(bits(q.x),bits(q.y)));fs=out;}
    return obj("corners",cs,"floats",fs,"box",box==null?null:shape(box));
  }
  static Object retained(PolylineTrace trace,RoutingBoard b,NeutralBoardModel exporter,Refs points,Refs lines,Refs polylines)throws Exception {
    var p=trace.polyline();int id=polylines.id(p);var before=cache(p,points);var ls=new ArrayList<Object>();for(var line:p.lines)ls.add(list(lines.id(line),NeutralBoardModel.xy(line.a),NeutralBoardModel.xy(line.b)));
    var value=ShapeTraceEntriesOracle.trace(trace,exporter);
    return obj("trace",value,"isCurrent",b.getItem(trace.getId())==trace,"polylineId",id,"lines",ls,"cacheBefore",before,"cacheAfter",cache(p,points));
  }
  static Object history(Path path,JsonObject in)throws Exception {
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    var angle=in.get("angle").getAsInt()==90?AngleRestriction.NINETY_DEGREE:AngleRestriction.FORTYFIVE_DEGREE;
    b.rules.setTraceAngleRestriction(angle);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());b.startMarkingChangedArea();
    int pinId=in.get("pin").getAsInt();Pin pin=pinId==0?null:(Pin)b.getItem(pinId);Point origin=pin==null?new IntPoint(1500000,-1150000):pin.getCenter();
    int[] nets=pin==null?NeutralBoardModel.JSON.fromJson(in.get("nets"),int[].class):pin.netNumbers;
    var ps=new ArrayList<Point>();for(var p:in.getAsJsonArray("points")){var q=p.getAsJsonArray();ps.add(origin.translateBy(new IntVector(q.get(0).getAsInt(),q.get(1).getAsInt())));}
    if(in.get("reverse").getAsBoolean())Collections.reverse(ps);
    PolylineTrace trace=b.insertTraceWithoutCleaning(new Polyline(ps.toArray(Point[]::new)),0,in.get("width").getAsInt(),nets,1,FixedState.values()[in.get("fixed").getAsInt()]);
    if(in.get("obstacle").getAsBoolean())b.insertTraceWithoutCleaning(new Polyline(new Point[]{origin.translateBy(new IntVector(10000,-20000)),origin.translateBy(new IntVector(10000,20000))}),0,100,new int[]{3},1,FixedState.UNFIXED);
    if(!in.get("classPullTight").getAsBoolean())b.rules.nets.get(2).getNetClass().setPullTight(false);
    if(in.has("contact")){var contact=in.getAsJsonArray("contact");var cs=new ArrayList<Point>();for(var p:contact){var q=p.getAsJsonArray();cs.add(origin.translateBy(new IntVector(q.get(0).getAsInt(),q.get(1).getAsInt())));}b.insertTraceWithoutCleaning(new Polyline(cs.toArray(Point[]::new)),0,in.get("width").getAsInt(),nets,1,in.has("contactFixed")?FixedState.values()[in.get("contactFixed").getAsInt()]:FixedState.SHOVE_FIXED);}
    b.generateSnapshot();int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();var pointRefs=new Refs();var lineRefs=new Refs();var polylineRefs=new Refs();
    for(String op:NeutralBoardModel.JSON.fromJson(in.get("operations"),String[].class)){
      Object value=null;String error=null;
      try{
        switch(op){
          case "checkStart"->value=trace.checkConnectionToPin(true);
          case "checkEnd"->value=trace.checkConnectionToPin(false);
          case "changeAlias"->trace.change(trace.polyline());
          case "changeReconstruct"->trace.change(new Polyline(trace.polyline().lines.clone()));
          case "changeMiddle"->{var lines=trace.polyline().lines.clone();lines[2]=lines[2].translate(1000);trace.change(new Polyline(lines));}
          case "correctStart"->value=trace.correctConnectionToPin(true,angle);
          case "correctEnd"->value=trace.correctConnectionToPin(false,angle);
          case "swapStart"->value=trace.swapConnectionToPin(true);
          case "swapEnd"->value=trace.swapConnectionToPin(false);
          case "pull"->{var t=TraceTightener.getInstance(b,NeutralBoardModel.JSON.fromJson(in.get("onlyNets"),int[].class),null,100,null,0,null,-1);value=trace.pullTight(t);}
          case "pullOwn"->value=trace.pullTight(in.get("ownOnly").getAsBoolean(),100,null);
          case "smoothFork"->value=trace.smoothenEndCornersFork(in.get("ownOnly").getAsBoolean(),100,null);
          case "undo"->value=b.undo(changed);
          case "redo"->value=b.redo(changed);
        }
      }catch(Throwable failure){error=ShapeEntryOracle.error(failure);}
      Object saved=retained(trace,b,exporter,pointRefs,lineRefs,polylineRefs);
      rows.add(obj("op",op,"value",value,"error",error,"retained",saved,"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic trace editing and pin-exit unit histories on admitted public ECC83; no route");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
