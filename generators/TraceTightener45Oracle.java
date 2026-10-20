package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.optimize.TraceTightener;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;
import static app.freerouting.board.facade.CollisionChecksOracle.xy;

/** Bench only: calls actual source methods; never substitutes a route or optimizer. */
public final class TraceTightener45Oracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static final class Refs {final List<Object> values=new ArrayList<>();int id(Object p){if(p==null)return 0;for(int i=0;i<values.size();i++)if(values.get(i)==p)return i+1;values.add(p);return values.size();}}
  static final class Stop implements Stoppable {int count=0;final int after;Stop(int after){this.after=after;}public void requestStop(){}public boolean isStopRequested(){return count++>=after;}}
  static void set(Object o,String name,Object value)throws Exception {Class<?> c=o.getClass();for(;;){try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException e){c=c.getSuperclass();if(c==null)throw e;}}}
  static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception {Class<?> c=o.getClass();for(;;){try{var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(NoSuchMethodException e){c=c.getSuperclass();if(c==null)throw e;}}}
  static Object cache(Polyline p,Refs refs)throws Exception {
    var corners=(Point[])field(p,"precalculatedCorners");var floats=(FloatPoint[])field(p,"precalculatedFloatCorners");var box=(IntBox)field(p,"precalculatedBoundingBox");
    Object cs=null,fs=null;if(corners!=null){var out=new ArrayList<Object>();for(var q:corners)out.add(list(refs.id(q),NeutralBoardModel.xy(q)));cs=out;}if(floats!=null){var out=new ArrayList<Object>();for(var q:floats)out.add(q==null?null:list(bits(q.x),bits(q.y)));fs=out;}
    return obj("corners",cs,"floats",fs,"box",box==null?null:shape(box));
  }
  static Object value(Object result,Polyline input,Refs points,Refs lines)throws Exception {
    if(result instanceof Polyline p){var before=cache(p,points);var ls=new ArrayList<Object>();for(var line:p.lines)ls.add(list(lines.id(line),NeutralBoardModel.xy(line.a),NeutralBoardModel.xy(line.b)));var cs=new ArrayList<Object>();for(var point:p.corners())cs.add(list(points.id(point),NeutralBoardModel.xy(point)));var length=bits(p.lengthApprox());return obj("sameInput",p==input,"lines",ls,"cacheBefore",before,"corners",cs,"length",length,"cacheAfter",cache(p,points));}
    if(result instanceof Line line)return list(lines.id(line),NeutralBoardModel.xy(line.a),NeutralBoardModel.xy(line.b));
    return result;
  }
  static Object context(TraceTightener t,Set<Pin> pins,int[] nets)throws Exception {
    var actual=(Set<?>)field(t,"contactPins");var ids=actual==null?null:actual.stream().map(p->((Item)p).getId()).toList();
    return obj("layer",field(t,"currentLayer"),"width",field(t,"currentHalfWidth"),"nets",field(t,"currentNetNumbers"),"netAlias",field(t,"currentNetNumbers")==nets,"clearance",field(t,"currentClearanceClassIndex"),"pins",ids,"pinAlias",actual==pins,"minimum",field(t,"minTranslateDist"));
  }
  static Object history(Path path,JsonObject in)throws Exception {
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    b.rules.setTraceAngleRestriction(AngleRestriction.FORTYFIVE_DEGREE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());
    var added=new ArrayList<PolylineTrace>();
    for(var t:in.getAsJsonArray("obstacles")){var q=t.getAsJsonObject();var ps=new ArrayList<Point>();for(var p:q.getAsJsonArray("points"))ps.add(xy(p));added.add(b.insertTraceWithoutCleaning(new Polyline(ps.toArray(Point[]::new)),0,q.get("width").getAsInt(),new int[]{q.get("net").getAsInt()},1,FixedState.UNFIXED));}
    int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();
    for(var element:in.getAsJsonArray("queries")){
      var q=element.getAsJsonObject();b.startMarkingChangedArea();
      var ps=new ArrayList<Point>();for(var p:q.getAsJsonArray("points"))ps.add(xy(p));Polyline p;
      if(q.has("lines")){var ls=new ArrayList<Line>();for(var l:q.getAsJsonArray("lines")){var a=l.getAsJsonArray();ls.add(new Line(xy(a.get(0)),xy(a.get(1))));}p=new Polyline(ls.toArray(Line[]::new));}else p=new Polyline(ps.toArray(Point[]::new));
      var refs=new Refs();var lineRefs=new Refs();for(var line:p.lines)lineRefs.id(line);
      var stop=new Stop(q.get("stopAfter").getAsInt());Set<Pin> pins=q.get("pinsNull").getAsBoolean()?null:new TreeSet<>();
      int[] nets={q.get("net").getAsInt()};IntOctagon clip=null;if(!q.get("clip").isJsonNull()){var a=q.getAsJsonArray("clip");clip=new IntBox(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),a.get(3).getAsInt()).boundingOctagon();}
      Point keep=q.get("keep").isJsonNull()?null:xy(q.get("keep"));
      var t=TraceTightener.getInstance(b,new int[0],clip,q.get("minimum").getAsInt(),stop,0,keep,0);
      int width=q.get("width").getAsInt();set(t,"currentLayer",0);set(t,"currentHalfWidth",width+b.searchTreeManager.getDefaultTree().clearanceCompensationValue(1,0));set(t,"currentNetNumbers",nets);set(t,"currentClearanceClassIndex",1);set(t,"contactPins",pins);
      var before=cache(p,refs);Object result=null;String error=null;String op=q.get("op").getAsString();
      try{
        result=switch(op){
          case "pull"->t.pullTight(p,0,width,nets,1,pins);
          case "line"->call(t,"repositionLine",new Class<?>[]{Line[].class,int.class},p.lines,q.get("index").getAsInt());
          case "smoothenCorner","smoothenSharpCorner","smoothenNonIntegerCorner"->call(t,op,new Class<?>[]{Line[].class,int.class},p.lines,q.get("index").getAsInt());
          case "smoothStart"->call(t,"smoothenStartCornerAtTrace",new Class<?>[]{PolylineTrace.class},added.get(q.get("index").getAsInt()));
          case "smoothEnd"->call(t,"smoothenEndCornerAtTrace",new Class<?>[]{PolylineTrace.class},added.get(q.get("index").getAsInt()));
          case "split"->t.splitTracesAtKeepPoint();
          case "smooth"->t.smoothenEndCornersAtTrace(added.get(q.get("index").getAsInt()));
          default->call(t,op,new Class<?>[]{Polyline.class},p);
        };
        result=value(result,p,refs,lineRefs);
      }catch(Throwable failure){if(failure instanceof InvocationTargetException e)failure=e.getCause();error=ShapeEntryOracle.error(failure);}
      rows.add(obj("input",q,"value",result,"error",error,"cacheBefore",before,"inputCacheAfter",cache(p,refs),"context",context(t,pins,nets),"stopCalls",stop.count,"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic tightener unit queries on public ECC83; no native route");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
