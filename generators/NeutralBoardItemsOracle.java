package app.freerouting.board.facade;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.*;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.core.library.Package;
import java.nio.file.*;
import java.util.*;

/** NBF2 actual constructed items plus subsequent real board operations. */
public final class NeutralBoardItemsOracle extends BoardConstructionOracle {
  static Object freeze(Object o){return NeutralBoardModel.JSON.toJsonTree(o).deepCopy();}
  static Object lines(Polyline p)throws Exception {var a=new ArrayList<Object>();for(Line l:p.lines)a.add(NeutralBoardModel.list(NeutralBoardModel.xy(l.a),NeutralBoardModel.xy(l.b)));return a;}
  static Map<String,Object> describe(NeutralBoardModel export,Item item)throws Exception {
    var d=NeutralBoardModel.obj("kind",item.getClass().getSimpleName(),"id",item.getId(),"nets",item.netNumbers.clone(),"clearance",item.clearanceClassIndex(),"component",item.getComponentId(),"fixed",item.getFixedState().ordinal());
    if(item instanceof BoardOutline o){var shapes=new ArrayList<Object>();for(int n=0;n<o.shapeCount();n++)shapes.add(export.area(o.getShape(n)));d.put("shapes",shapes);d.put("outside",o.keepoutOutsideOutlineGenerated());}
    else if(item instanceof PolylineTrace t){d.put("lines",lines(t.polyline()));d.put("layer",t.getLayer());d.put("width",t.getHalfWidth());}
    else if(item instanceof Pin p){d.put("pin",p.getPinIndex());d.put("changedTo",p.getChangedTo().getId());}
    else if(item instanceof Via v){d.put("pad",v.getPadstack().id);d.put("center",NeutralBoardModel.xy(v.getCenter()));d.put("attach",v.attachAllowed);d.put("escape",v.isEscapeVia);d.put("smdLayer",v.escapeViaSmdLayer);}
    else if(item instanceof ObstacleArea a){d.put("area",export.area(a.getRelativeArea()));d.put("layer",a.getLayer());d.put("translation",NeutralBoardModel.xy(a.getTranslation()));d.put("rotation",bits(a.getRotationInDegree()));d.put("side",a.getSideChanged());d.put("name",NeutralBoardModel.str(a.name));if(a instanceof ConductionArea c){d.put("obstacle",c.getIsObstacle());d.put("filled",c.getIsFilled());}}
    else if(item instanceof ComponentOutline c){d.put("area",export.area((Area)field(c,"relativeArea")));d.put("translation",NeutralBoardModel.xy(field(c,"translation")));d.put("rotation",bits((Double)field(c,"rotationInDegree")));d.put("front",c.isFront());d.put("courtyard",c.isCourtyard());d.put("fabrication",c.isFabrication());d.put("closed",c.isClosed());}
    else throw new IllegalArgumentException("Unsupported neutral item "+item.getClass());
    return d;
  }
  static Object communication(RoutingBoard b)throws Exception {
    var c=b.communication;
    return NeutralBoardModel.obj("unit",c.unit==null?-1:c.unit.ordinal(),"resolution",c.resolution,"parserPresent",c.specctraParserInfo!=null,"host",NeutralBoardModel.str(c.specctraParserInfo==null?null:c.specctraParserInfo.hostCad),"lastId",c.idGenerator.maxGeneratedId(),"wraps",field(c.idGenerator,"wrapAroundCount"));
  }
  static Object snapshot(NeutralBoardModel export,RoutingBoard b,Observers o,Set<Integer> changed,List<Integer> retained)throws Exception {
    // Freeze each observation now: future source mutations must never rewrite an earlier expected state.
    var descriptors=new ArrayList<Object>();for(Item i:b.getItems())descriptors.add(describe(export,i));
    var connectivity=new ArrayList<Object>();for(int n=1;n<=b.rules.nets.maxNetNumber();n++){int net=n;String v=ids(b.getConnectableItems(n));v+="/"+call(()->{String s="";for(var set:b.getConnectedSets(net))s+="~"+ids(set);return s;});connectivity.add(v);}
    return freeze(NeutralBoardModel.obj("metadata",export.metadata(b),"communication",communication(b),"items",descriptors,"state",state(b,o,changed,true),"connectivity",connectivity,"changedArea",changedState(b)));
  }
  static String run(int config)throws Exception {
    boolean negative=config<0;if(negative)config=-config-1;
    int unit=config%4,host=config%3;String hostName=host==0?null:host==1?"":"KiCad";
    var parser=host==0?null:new Communication.SpecctraParserInfo("\"",hostName,null,null,null,false);
    var com=new Communication(Unit.values()[unit],1000+config,parser,null,new ItemIdGenerator(),null);
    PolylineShape[] outlines={new IntBox(-9000,-9000,9000,9000),new IntBox(7000,7000,7500,7500)};
    var seed=NeutralBoardModelOracle.seed(config,outlines,com);var b=seed.board();var o=seed.observers();int last=b.getLayerCount()-1;
    if(negative) {
      var export=new NeutralBoardModel();var metadata=export.metadata(b);var outline=describe(export,b.getOutline());
      Object model=freeze(NeutralBoardModel.obj("version","NBF2","metadata",metadata,"communication",communication(b),"items",List.of(outline)));
      items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();Set<Integer> changed=new TreeSet<>();
      var observations=new ArrayList<Object>();observations.add(snapshot(export,b,o,changed,List.of()));
      String result;try{b.insertPin(1,0,new int[]{1},1,FixedState.UNFIXED);result="ok";}catch(ClassCastException e){result="cast_error";}
      observations.add(NeutralBoardModel.obj("result",result,"observation",snapshot(export,b,o,changed,List.of())));
      return NeutralBoardModel.JSON.toJson(model)+" | "+NeutralBoardModel.JSON.toJson(NeutralBoardModel.obj("case","rational-pin-rejection","config",config,"operations",List.of("badPin"),"observations",observations));
    }
    // A separate, explicitly integer-grid input domain for constructible boards.
    var packagePins=(Package.Pin[])field(seed.front(),"pins");var oldPin=packagePins[0];
    packagePins[0]=new Package.Pin(oldPin.name,oldPin.padstackId,new IntVector(-500,0),oldPin.rotationInDegree);
    var created=new ArrayList<Item>();created.add(b.getOutline());
    created.add(b.insertPin(1,0,new int[]{1},1,FixedState.UNFIXED));created.add(b.insertPin(1,1,new int[]{2},2,FixedState.USER_FIXED));
    created.add(b.insertPin(2,0,new int[]{1,2},1,FixedState.SHOVE_FIXED));
    created.add(b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(1000,-4000),new IntPoint(3000,-4000),new IntPoint(3000,-3500)}),0,23,new int[]{1},1,FixedState.UNFIXED));
    created.add(b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(1000,-3000),new IntPoint(4000,-3000)}),last,31,new int[]{2},2,FixedState.UNFIXED));
    created.add(b.insertVia(seed.via(),new IntPoint(1000,-2000),new int[]{1},1,FixedState.UNFIXED,true));
    created.add(b.insertEscapeVia(seed.via(),new IntPoint(5000,-2000),new int[]{2},2,FixedState.SHOVE_FIXED,last));
    double angle=new double[]{0,37,90}[config%3];
    var fixed=FixedState.values()[config%4];
    created.add(b.insertObstacle(seed.shared(),0,new IntVector(-2000,-2000),angle,config%2!=0,1,1,"keep \u00e9",fixed));
    Area plane=new PolylineArea(new IntBox(-300,-200,300,200),new PolylineShape[]{new IntBox(-30,-20,30,20)});
    var conduction=new ConductionArea(plane,last,new IntVector(3000,0),angle,false,new int[]{2},2,0,0,"plane",config%2==0,FixedState.UNFIXED,b);
    if(config%2!=0)conduction.setIsFilled(false);b.insertItem(conduction);created.add(conduction);
    created.add(b.insertViaObstacle(new Circle(new IntPoint(0,0),80),last,new IntVector(-3000,0),angle,false,1,2,"viaKeepout",fixed));
    created.add(b.insertComponentObstacle(seed.shared(),0,new IntVector(-3000,1000),angle,config%2!=0,1,2,"componentKeepout",fixed));
    created.add(b.insertComponentOutline(new PolygonShape(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(100,40),new IntPoint(40,40),new IntPoint(40,100),new IntPoint(0,100)}),true,new IntVector(5000,3000),angle,1,true,false,true,FixedState.UNFIXED));
    // All initial insertions are non-cleaning/disjoint. Any implicit removal would invalidate this entry domain.
    if(b.getItems().size()!=created.size()||b.getRevision()!=created.size())throw new IllegalStateException("Initial neutral board contains edits");
    var export=new NeutralBoardModel();var metadata=export.metadata(b);var records=new ArrayList<Object>();for(Item i:created)records.add(describe(export,i));
    Object model=freeze(NeutralBoardModel.obj("version","NBF2","metadata",metadata,"communication",communication(b),"items",records));
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();Set<Integer> changed=new TreeSet<>();
    var observations=new ArrayList<Object>();observations.add(snapshot(export,b,o,changed,List.of()));var operations=new ArrayList<Object>();
    String[] ops={"tree","snapshot","moveVia","moveArea","deleteTrace","undo","redo","clearArea","filled","snapshot","swapPins","undo","redo","mark","normalize"};
    for(String op:ops){operations.add(op);String result=call(()->{switch(op){case "tree"->{return b.searchTreeManager.getAutorouteTree(2).key;}case "snapshot"->b.generateSnapshot();case "moveVia"->b.getItem(7).moveBy(new IntVector(17,-29));case "moveArea"->b.getItem(10).moveBy(new IntVector(-21,33));case "deleteTrace"->b.removeItem(b.getItem(5));case "undo"->{return b.undo(changed);}case "redo"->{return b.redo(changed);}case "clearArea"->b.getItem(9).clearDerivedData();case "filled"->((ConductionArea)b.getItem(10)).setIsFilled(false);case "swapPins"->{return ((Pin)b.getItem(2)).swap((Pin)b.getItem(3));}case "mark"->b.markAllChangedArea();case "normalize"->{return b.normalizeTraces(2);}default->throw new AssertionError(op);}return "ok";});observations.add(NeutralBoardModel.obj("result",result,"observation",snapshot(export,b,o,changed,List.of())));}
    return NeutralBoardModel.JSON.toJson(model)+" | "+NeutralBoardModel.JSON.toJson(NeutralBoardModel.obj("case","items","config",config,"operations",operations,"observations",observations));
  }
  public static void main(String[] args)throws Exception{try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String line:Files.readAllLines(Path.of(args[0])))out.write(run(Integer.parseInt(line))+"\n");}}
}
