package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;

/** Actual via candidate generation and connected moves; these are unit histories, not a route. */
public final class ViaMovementOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static TileShape tile(JsonObject q){
    int x=q.get("x").getAsInt(),y=q.get("y").getAsInt(),w=q.get("w").getAsInt(),h=q.get("h").getAsInt();
    var box=new IntBox(x-w,y-h,x+w,y+h);
    return switch(q.get("kind").getAsInt()){
      case 0->box;
      case 1->box.boundingOctagon();
      case 2->box.toSimplex();
      case 3->TileShape.getInstance(new Point[]{new IntPoint(x-w,y-h),new IntPoint(x+w,y-h),new IntPoint(x,y+h)});
      default->null;
    };
  }
  static Object history(Path path,JsonObject in)throws Exception {
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));
    int angle=in.get("angle").getAsInt();b.rules.setTraceAngleRestriction(angle==90?AngleRestriction.NINETY_DEGREE:angle==45?AngleRestriction.FORTYFIVE_DEGREE:AngleRestriction.NONE);b.searchTreeManager.setClearanceCompensationUsed(in.get("compensation").getAsBoolean());b.startMarkingChangedArea();
    Via via=b.insertVia(b.library.getViaPadstack(0),new IntPoint(1500000,-1150000),new int[]{2},1,FixedState.UNFIXED,true);
    for(var t:in.getAsJsonArray("traces")){var q=t.getAsJsonObject();var points=new ArrayList<Point>();for(var p:q.getAsJsonArray("points")){var a=p.getAsJsonArray();points.add(new IntPoint(a.get(0).getAsInt(),a.get(1).getAsInt()));}b.insertTraceWithoutCleaning(new Polyline(points.toArray(Point[]::new)),q.get("layer").getAsInt(),q.get("width").getAsInt(),new int[]{2},q.get("clearance").getAsInt(),FixedState.UNFIXED);}
    b.generateSnapshot();int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()));var rows=new ArrayList<Object>();
    for(var element:in.getAsJsonArray("queries")){
      var q=element.getAsJsonObject();String op=q.get("op").getAsString();Object value=null;String error=null;
      try{
        switch(op){
          case "candidates"->{var result=DrillItemMover.tryShoveViaPoints(tile(q),q.get("layer").getAsInt(),via,q.get("clearance").getAsInt(),q.get("extended").getAsBoolean(),b);var out=new ArrayList<Object>();for(var p:result)out.add(NeutralBoardModel.xy(p));value=out;}
          case "move"->via.moveBy(new IntVector(q.get("dx").getAsInt(),q.get("dy").getAsInt()));
          case "undo"->value=b.undo(changed);
          case "redo"->value=b.redo(changed);
        }
      }catch(Throwable failure){error=ShapeEntryOracle.error(failure);}
      Object retained=obj("descriptor",NeutralBoardItemsOracle.describe(exporter,via),"onBoard",via.isOnTheBoard(),"isCurrent",b.getItem(via.getId())==via);
      rows.add(obj("input",q,"value",value,"error",error,"retained",retained,"board",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,observer,changed,List.of()))));
    }
    return obj("input",in,"initial",initial,"prepared",prepared,"rows",rows,"source","Synthetic via candidate/movement unit histories on admitted public ECC83; no route");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
  }
}
