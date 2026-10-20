package app.freerouting.board.facade;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;

/** Additional actual-board corpus: observe each insertion, without altering earlier fixtures. */
public final class BoardConstructionPrefixesOracle extends BoardConstructionOracle {
  static final class Recorder {
    final NeutralBoardModel export;final RoutingBoard board;final Observers observers;
    final Set<Integer> changed=new TreeSet<>();final ArrayList<Object> operations=new ArrayList<>(),observations=new ArrayList<>();
    Recorder(NeutralBoardModel e,RoutingBoard b,Observers o)throws Exception {export=e;board=b;observers=o;observe("initial");}
    void observe(String result)throws Exception {observations.add(NeutralBoardModel.obj("result",result,"observation",NeutralBoardItemsOracle.snapshot(export,board,observers,changed,List.of())));}
    void insert(Callable<Item> construction)throws Exception {
      Item item=construction.call();if(item==null)throw new IllegalStateException("Rejected positive insertion");
      operations.add(NeutralBoardModel.obj("insert",NeutralBoardItemsOracle.describe(export,item)));
      observe(""+item.getId());
    }
    void edit(String op)throws Exception {
      operations.add(NeutralBoardModel.obj("edit",op));
      String result=call(()->{switch(op){
        case "tree"->{return board.searchTreeManager.getAutorouteTree(2).key;}
        case "snapshot"->board.generateSnapshot();
        case "moveVia"->board.getItem(7).moveBy(new IntVector(17,-29));
        case "moveArea"->board.getItem(10).moveBy(new IntVector(-21,33));
        case "deleteTrace"->board.removeItem(board.getItem(5));
        case "undo"->{return board.undo(changed);}
        case "redo"->{return board.redo(changed);}
        case "mark"->board.markAllChangedArea();
        default->throw new AssertionError(op);
      }return "ok";});observe(result);
    }
  }
  static String run(int config)throws Exception {
    int host=config%3;String hostName=host==0?null:host==1?"":"KiCad";
    var parser=host==0?null:new Communication.SpecctraParserInfo("\"",hostName,null,null,null,false);
    var com=new Communication(Unit.values()[config%4],1000+config,parser,null,new ItemIdGenerator(),null);
    PolylineShape[] outlines={new IntBox(-9000,-9000,9000,9000),new IntBox(7000,7000,7500,7500)};
    var seed=NeutralBoardModelOracle.seed(config,outlines,com);var b=seed.board();int last=b.getLayerCount()-1;
    var packagePins=(Package.Pin[])field(seed.front(),"pins");var pin=packagePins[0];
    packagePins[0]=new Package.Pin(pin.name,pin.padstackId,new IntVector(-500,0),pin.rotationInDegree);
    double angle=new double[]{0,37,90}[config%3];var fixed=FixedState.values()[config%4];
    Area plane=new PolylineArea(new IntBox(-300,-200,300,200),new PolylineShape[]{new IntBox(-30,-20,30,20)});
    var viaKeepout=new Circle(new IntPoint(0,0),80);
    var componentOutline=new PolygonShape(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(100,40),new IntPoint(40,40),new IntPoint(40,100),new IntPoint(0,100)});
    var export=new NeutralBoardModel();export.metadata(b);var outline=NeutralBoardItemsOracle.describe(export,b.getOutline());
    // Geometry definitions may precede the items that use them. Register the whole
    // input dictionary now; no future item, tree, undo state or callback is installed.
    export.area(plane);export.area(viaKeepout);export.area(componentOutline);
    Object initial=NeutralBoardItemsOracle.freeze(NeutralBoardModel.obj("version","NBF2","metadata",export.metadata(b),"communication",NeutralBoardItemsOracle.communication(b),"items",List.of(outline)));
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();
    var r=new Recorder(export,b,seed.observers());
    r.insert(()->b.insertPin(1,0,new int[]{1},1,FixedState.UNFIXED));
    r.edit("tree"); // The compensated tree exists before the remaining items enter.
    r.insert(()->b.insertPin(1,1,new int[]{2},2,FixedState.USER_FIXED));
    r.insert(()->b.insertPin(2,0,new int[]{1,2},1,FixedState.SHOVE_FIXED));
    r.insert(()->b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(1000,-4000),new IntPoint(3000,-4000),new IntPoint(3000,-3500)}),0,23,new int[]{1},1,FixedState.UNFIXED));
    r.insert(()->b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(1000,-3000),new IntPoint(4000,-3000)}),last,31,new int[]{2},2,FixedState.UNFIXED));
    r.insert(()->b.insertVia(seed.via(),new IntPoint(1000,-2000),new int[]{1},1,FixedState.UNFIXED,true));
    r.insert(()->b.insertEscapeVia(seed.via(),new IntPoint(5000,-2000),new int[]{2},2,FixedState.SHOVE_FIXED,last));
    r.insert(()->b.insertObstacle(seed.shared(),0,new IntVector(-2000,-2000),angle,config%2!=0,1,1,"keep \u00e9",fixed));
    r.insert(()->{var item=new ConductionArea(plane,last,new IntVector(3000,0),angle,false,new int[]{2},2,0,0,"plane",config%2==0,FixedState.UNFIXED,b);if(config%2!=0)item.setIsFilled(false);b.insertItem(item);return item;});
    r.insert(()->b.insertViaObstacle(viaKeepout,last,new IntVector(-3000,0),angle,false,1,2,"viaKeepout",fixed));
    r.insert(()->b.insertComponentObstacle(seed.shared(),0,new IntVector(-3000,1000),angle,config%2!=0,1,2,"componentKeepout",fixed));
    r.insert(()->b.insertComponentOutline(componentOutline,true,new IntVector(5000,3000),angle,1,true,false,true,FixedState.UNFIXED));
    if(b.getItems().size()!=13||b.getRevision()!=13)throw new IllegalStateException("Initial insertions performed unrecorded edits");
    for(String op:new String[]{"snapshot","moveVia","moveArea","deleteTrace","undo","redo","mark"})r.edit(op);
    Object input=NeutralBoardModel.obj("initial",initial,"operations",r.operations);
    return NeutralBoardModel.JSON.toJson(input)+" | "+NeutralBoardModel.JSON.toJson(NeutralBoardModel.obj("config",config,"observations",r.observations));
  }
  public static void main(String[] args)throws Exception{try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String line:Files.readAllLines(Path.of(args[0])))out.write(run(Integer.parseInt(line))+"\n");}}
}
