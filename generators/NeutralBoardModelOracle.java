package app.freerouting.board.facade;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.core.library.*;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.nio.file.*;
import java.util.*;

/** Metadata round-trip and subsequent source mutations on a real, fresh RoutingBoard. */
public final class NeutralBoardModelOracle extends BoardConstructionOracle {
  record Seed(RoutingBoard board,Observers observers,IntBox shared,Padstack via,Package front) {}
  static Seed seed(int config,PolylineShape[] outlines,Communication communication)throws Exception {
    int count=2+config%5;
    Layer[] ls=new Layer[count];for(int l=0;l<count;l++)ls[l]=new Layer(l==0?"F.Cu":l==count-1?"B.Cu":"Inner "+l,l==0||l==count-1||(config+l)%3!=0);
    var layers=new LayerStructure(ls);var m=new ClearanceMatrix(4,layers,new String[]{"none","default","Power \u03a9","\ud83d\udca1\ud800"});
    for(int i=0;i<4;i++)for(int j=0;j<4;j++)for(int l=0;l<count;l++)m.setValue(i,j,l,(i+j+l+config)%19+3);
    // A lowered cell deliberately leaves the source monotonic maxima stale.
    m.setValue(2,3,0,999);m.setValue(2,3,0,4);
    var rules=new BoardRules(layers,m);var o=new Observers();o.constructionMetadata=true;var com=communication==null?new Communication():communication;com.observers=o;
    var b=new RoutingBoard(new IntBox(-10000,-10000,10000,10000),layers,outlines,1,rules,com);
    b.library.padstacks=new Padstacks(layers);b.library.packages=new Packages(b.library.padstacks);
    var shared=new IntBox(-80,-30,80,30);var circle=new Circle(new IntPoint(3,-4),23);
    var polygon=new PolygonShape(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(100,30),new IntPoint(30,30),new IntPoint(30,100),new IntPoint(0,100)});
    var hole=new PolylineArea(new IntBox(-200,-200,200,200),new PolylineShape[]{new IntBox(-10,-10,10,10),shared});
    var octagon=new Circle(new IntPoint(0,0),100).boundingOctagon();
    ConvexShape[] shapes=new ConvexShape[count];for(int l=0;l<count;l++)shapes[l]=(l==1&&count>2)?null:l%2==0?shared:circle;
    var p0=b.library.padstacks.add("pad \u00e9",shapes,true,config%2!=0);p0.holeOnly=config%4==0;
    var p1=b.library.padstacks.add("via",new ConvexShape[count],false,false);for(int l=0;l<count;l++)((ConvexShape[])field(p1,"shapes"))[l]=octagon;
    if(config%3!=0)b.library.setViaPadstacks(config%3==1?new Padstack[]{p1,p0,p1}:new Padstack[0]);
    Package.Pin[] pins={new Package.Pin("1",p0.id,new RationalVector(java.math.BigInteger.valueOf(-1001),java.math.BigInteger.valueOf(3),java.math.BigInteger.valueOf(2)),37.0),new Package.Pin("2",p0.id,new IntVector(500,0),-0.0)};
    var keep=new Package.Keepout[]{new Package.Keepout("keep out",hole,0),null,new Package.Keepout(null,polygon,count-1)};
    var front=b.library.packages.add("DIP \u03b1",pins,new Shape[]{shared,polygon,circle,octagon,Simplex.EMPTY,new IntBox(-71,-55,83,101).toSimplex()},new double[]{1.25,-0.0,0.0,2.0},new boolean[]{true,false,true,false},keep,keep,null,true);
    var back=b.library.packages.add("DIP \u03b1",pins,null,null,null,null,null,keep,false);
    var part=b.library.logicalParts.add("logic",new LogicalPart.PartPin[]{new LogicalPart.PartPin(1,"2","G",1,"b",2),new LogicalPart.PartPin(0,"1","G",1,"a",2)});
    for(int i=0;i<3;i++){var v=new ViaInfo("V"+i,i%2==0?p0:p1,1+i,i%2==0,rules);rules.viaInfos.add(v);}
    for(int i=0;i<3;i++){var v=new ViaRule("VR"+i);for(int j=0;j<=i;j++)v.appendVia(rules.viaInfos.get((i+j)%3));rules.viaRules.add(v);}
    for(int i=0;i<4;i++){var cl=rules.netClasses.append("Class "+i,layers,m,(config+i)%4==0);cl.setTraceClearanceClass(i);cl.setViaRule(rules.viaRules.get(i%3));cl.setShoveFixed(i==1);cl.setPullTight(i!=2);cl.setIgnoreCyclesWithAreas(i%2==0);cl.setMinimumTraceLength(10.125*i);cl.setMaximumTraceLength(10000.25+i);for(int l=0;l<count;l++){cl.setTraceHalfWidth(l,25+i*12+l);cl.setActiveRoutingLayer(l,(i+l)%3!=0);}cl.defaultItemClearanceClasses.set(DefaultItemClearanceClasses.ItemClass.PIN,i);}
    rules.netClasses.get(2).defaultItemClearanceClasses=rules.netClasses.get(1).defaultItemClearanceClasses;
    if(config%2==0)rules.netClasses.get(3).defaultItemClearanceClasses=null;
    rules.setDefaultTraceHalfWidth(0,4);rules.setDefaultTraceHalfWidth(0,900);rules.setDefaultTraceHalfWidth(0,33);
    rules.setTraceAngleRestriction(AngleRestriction.valueOf(config%3));rules.setIgnoreConduction(config%2==0);rules.setUseSlowAutorouteAlgorithm(config%3==0);rules.setHoleClearance(config-4);rules.setPinEdgeToTurnDist(config%2==0?-0.0:5.75);
    for(int i=0;i<6;i++){var n=rules.nets.add("Net "+i,1+i%2,i%2!=0);n.setClass(rules.netClasses.get(i%4));}
    for(int i=0;i<4;i++){var p=b.components.add("U"+i,i==3?null:new IntPoint(1000*i,2000),new double[]{0,37,90,275}[i],i%2==0,front,back,i==2,i%2==0?"part \u00e9":null);if(i<2)p.setLogicalPart(part);}
    b.components.setFlipStyleRotateFirst(config%2==0);
    return new Seed(b,o,shared,p1,front);
  }
  static String run(int config)throws Exception {
    var seed=seed(config,new PolylineShape[0],null);var b=seed.board();var o=seed.observers();var shared=seed.shared();var p1=seed.via();var front=seed.front();var rules=b.rules;var m=rules.clearanceMatrix;int count=b.getLayerCount();
    String neutral=NeutralBoardModel.exportMetadata(b);items=new Pool();trees=new Pool();shapesPool();componentHistory=new Pool();Set<Integer> changed=new TreeSet<>();
    var operations=List.of("snapshot","width","clearance","layers","via","component","undo","redo","matrix","net","class","aliasPin","aliasKeepouts");
    var output=new ArrayList<Object>();output.add(NeutralBoardModel.obj("metadata",new NeutralBoardModel().metadata(b),"state",state(b,o,changed)));
    for(String op:operations){String result=call(()->{switch(op){case "snapshot"->b.generateSnapshot();case "width"->rules.setDefaultTraceHalfWidth(count-1,14+config);case "clearance"->m.setValue(1,2,count-1,120+config);case "layers"->rules.netClasses.get(1).setAllInnerLayersActive(true);case "via"->rules.viaRules.get(0).appendVia(rules.viaInfos.get(2));case "component"->b.components.rotate(1,37,new IntPoint(50,100));case "undo"->{return b.undo(changed);}case "redo"->{return b.redo(changed);}case "matrix"->m.setValue(2,3,0,2);case "net"->rules.nets.get(1).setClass(rules.netClasses.get(3));case "class"->rules.netClasses.get(1).defaultItemClearanceClasses.set(DefaultItemClearanceClasses.ItemClass.PIN,3);case "aliasPin"->((Package.Pin[])field(front,"pins"))[1]=new Package.Pin("changed",p1.id,new IntVector(700,-400),23);case "aliasKeepouts"->front.keepouts[1]=new Package.Keepout("new",shared,count-1);default->throw new AssertionError(op);}return "ok";});output.add(NeutralBoardModel.obj("result",result,"metadata",new NeutralBoardModel().metadata(b),"state",state(b,o,changed)));}
    return neutral+" | "+NeutralBoardModel.JSON.toJson(NeutralBoardModel.obj("config",config,"operations",operations,"observations",output));
  }
  static void shapesPool(){shapes=new Pool();arrays=new Pool();}
  public static void main(String[] args)throws Exception{try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String s:Files.readAllLines(Path.of(args[0])))out.write(run(Integer.parseInt(s))+"\n");}}
}
