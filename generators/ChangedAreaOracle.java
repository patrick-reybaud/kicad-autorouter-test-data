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
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;

/** Source changed-area unit histories on the admitted public board; no native routing replay. */
public final class ChangedAreaOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static final class Stop implements Stoppable {int count=0;final int after;Stop(int after){this.after=after;}public void requestStop(){}public boolean isStopRequested(){return count++>=after;}}
  static Object history(Path path,JsonObject in)throws Exception {
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));boolean initialFacade=field(b,"operations")!=null;
    b.rules.setTraceAngleRestriction(in.get("angle").getAsInt()==90?AngleRestriction.NINETY_DEGREE:AngleRestriction.FORTYFIVE_DEGREE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());
    if(in.get("mark").getAsBoolean())b.startMarkingChangedArea();
    var added=new ArrayList<Item>();
    for(var t:in.getAsJsonArray("traces")){var q=t.getAsJsonObject();var points=new ArrayList<Point>();for(var p:q.getAsJsonArray("points")){var xy=p.getAsJsonArray();points.add(new IntPoint(xy.get(0).getAsInt(),xy.get(1).getAsInt()));}if(in.get("mark").getAsBoolean())for(var point:points)b.joinChangedArea(point.toFloat(),q.get("layer").getAsInt());added.add(b.insertTraceWithoutCleaning(new Polyline(points.toArray(Point[]::new)),q.get("layer").getAsInt(),q.get("width").getAsInt(),new int[]{q.get("net").getAsInt()},1,FixedState.values()[q.get("fixed").getAsInt()]));}
    if(in.get("mode").getAsInt()==2)b.insertVia(b.library.getViaPadstack(0),new IntPoint(1506000,-1146000),new int[]{2},1,FixedState.UNFIXED,true);
    b.generateSnapshot();int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();
    IntOctagon clip=null;String clipKind=in.get("clip").getAsString();if(clipKind.equals("empty"))clip=IntOctagon.EMPTY;else if(clipKind.equals("duplicate-empty")){var e=IntOctagon.EMPTY;clip=new IntOctagon(e.leftX,e.bottomY,e.rightX,e.topY,e.upperLeftDiagonalX,e.lowerRightDiagonalX,e.lowerLeftDiagonalX,e.upperRightDiagonalX);}else if(clipKind.equals("box"))clip=new IntBox(1499000,-1151000,1507000,-1145000).boundingOctagon();
    Point keep=null;if(!in.get("keep").isJsonNull()){var a=in.getAsJsonArray("keep");keep=new IntPoint(a.get(0).getAsInt(),a.get(1).getAsInt());}
    int[] nets=NeutralBoardModel.JSON.fromJson(in.get("nets"),int[].class);
    Object facade=null;
    for(String op:List.of("facade","direct","facadeOpt","markAll","remove","undo","redo")){
      Object value=null;String error=null;var stop=new Stop(in.get("stopAfter").getAsInt());
      try{
        switch(op){
          case "facade"->{var method=RoutingBoard.class.getDeclaredMethod("getOperations");method.setAccessible(true);facade=method.invoke(b);var again=method.invoke(b);value=obj("same",facade==again,"owner",field(facade,"board")==b);}
          case "direct"->{var algorithm=TraceTightener.getInstance(b,nets,clip,100,stop,0,keep,0);algorithm.optChangedArea(null);}
          case "facadeOpt"->b.optChangedArea(nets,clip,100,null,stop,0,keep,0);
          case "markAll"->b.markAllChangedArea();
          case "remove"->{Collection<Item> selected=in.get("sorted").getAsBoolean()?new TreeSet<>():new LinkedList<>();for(var index:in.getAsJsonArray("remove"))selected.add(added.get(index.getAsInt()));value=b.removeItemsAndPullTight(selected,in.get("tidyWidth").getAsInt(),100);}
          case "undo"->value=b.undo(changed);
          case "redo"->value=b.redo(changed);
        }
      }catch(Throwable failure){error=ShapeEntryOracle.error(failure);}
      rows.add(obj("op",op,"value",value,"error",error,"stopCalls",stop.count,"facadeSame",field(b,"operations")==facade,"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"initial",initial,"initialFacade",initialFacade,"prepared",prepared,"rows",rows,"source","Synthetic changed-area unit histories on admitted public ECC83; traceCosts=null; no route");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
