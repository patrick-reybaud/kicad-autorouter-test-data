package app.freerouting.board.facade;
import app.freerouting.board.actions.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.core.library.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.*;
import java.nio.file.*;
import java.util.*;
/** Source conduction/via/component area state and policy. AWT rendering is not invoked. */
public class SpecialAreasOracle extends ObstacleAreaOracle {
  static String flags(ObstacleArea c)throws Exception {
    String s=c.getClass().getSimpleName()+":"+c.netCount();
    if(c instanceof ConductionArea a){s+=":"+a.getIsFilled()+":"+a.getIsObstacle();s+="/"+invoke(()->{String value="";for(Point p:a.getRatsnestCorners())value+="~"+point(p);return value.isEmpty()?"empty":value;});}
    if(c instanceof ComponentObstacleArea a)s+="/"+invoke(()->a.isFront());return s;
  }
  static String policies(ObstacleArea c,List<Item> peers)throws Exception {
    String s="";for(Item peer:peers)s+="~"+invoke(()->c.isObstacle(peer));
    for(int net:new int[]{-1,0,1,2,3})s+="~"+c.isTraceObstacle(net)+":"+c.isDrillable(net);
    for(var choice:ItemSelectionFilter.SelectableChoices.values())s+="~"+invoke(()->c.isSelectedByFilter(new ItemSelectionFilter(choice)));
    return s;
  }
  static ArrayList<String> snapshotSpecial(RoutingBoard b,Observers o,Set<Integer> changed,ObstacleArea c,List<Item> peers,boolean attached)throws Exception {
    var out=new ArrayList<String>();out.add("value="+description(c));out.add("flags="+flags(c));out.add("policies="+policies(c,peers));
    out.add("contacts="+invoke(()->ids(c.getNormalContacts())));
    if(c instanceof ConductionArea a){var tree=b.searchTreeManager.getDefaultTree();for(int index:new int[]{-1,0,1,1000})out.add("connectionShape="+invoke(()->{var t=a.getTraceConnectionShape(tree,index);return shapes.id(t)+":"+shape(t);}));}
    var copy=(ObstacleArea)c.copy(2000);out.add("copy="+(copy==null?"null":flags(copy)+"/"+description(copy)+"/netsAlias="+(c.netNumbers==copy.netNumbers)));
    if(attached)out.addAll(state(b,o,changed,true));return out;
  }
  static String runSpecial(String line)throws Exception {
    var in=new Scanner(line);int type=in.nextInt(),kind=in.nextInt(),config=in.nextInt(),mode=in.nextInt();boolean attached=mode!=3;
    var layers=new LayerStructure(new Layer[]{new Layer("F.Cu",true),new Layer("In1.Cu",false),new Layer("B.Cu",true)});var matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});matrix.setDefaultValue(12);var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(AngleRestriction.valueOf(config%3));var o=new Observers();o.constructionMetadata=true;var com=new Communication();com.observers=o;
    var b=new RoutingBoard(new IntBox(-10000,-10000,10000,10000),layers,new PolylineShape[0],1,rules,com);rules.nets.add("A",1,false);rules.nets.add("B",1,true);b.components.add("A",Point.ZERO,0,true,null,null,false,null);b.components.add("B",Point.ZERO,0,false,null,null,false,null);b.components.setFlipStyleRotateFirst(config>=6);
    b.library.padstacks=new Padstacks(layers);var pad=b.library.padstacks.add("via",new ConvexShape[]{new Circle(new IntPoint(0,0),20),new Circle(new IntPoint(0,0),20),new Circle(new IntPoint(0,0),20)},true,false);
    var trace=b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(300,400),new IntPoint(300,1000)}),0,10,new int[]{1},1,FixedState.UNFIXED);var foreign=b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(600,400),new IntPoint(600,1000)}),0,10,new int[]{2},1,FixedState.UNFIXED);var via=b.insertVia(pad,new IntPoint(300,400),new int[]{1},1,FixedState.UNFIXED,true);
    int[] nets=switch(config%4){case 0->new int[0];case 1->new int[]{1};case 2->new int[]{1,2};default->null;};var fixed=FixedState.values()[config/3%4];Area area=makeArea(kind);int layer=config%3,component=mode==1?0:1+config%2;double angle=new double[]{0,37,90}[config%3];boolean side=config%2!=0;BasicBoard context=attached?b:null;Vector translation=new IntVector(300,400);
    ObstacleArea c;
    if(type==0)c=mode==1?b.insertConductionArea(area,layer,nets,1,config%2==0,fixed):new ConductionArea(area,layer,translation,angle,side,nets,1,99,component,"plane",config%2==0,fixed,context);
    else if(type==1)c=mode==1?b.insertViaObstacle(area,layer,1,fixed):mode==2?b.insertViaObstacle(area,layer,translation,angle,side,1,component,"viaKeepout",fixed):new ViaObstacleArea(area,layer,translation,angle,side,nets,1,99,component,"viaKeepout",fixed,context);
    else c=mode==1?b.insertComponentObstacle(area,layer,1,fixed):mode==2?b.insertComponentObstacle(area,layer,translation,angle,side,1,component,"componentKeepout",fixed):new ComponentObstacleArea(area,layer,translation,angle,side,1,99,component,"componentKeepout",fixed,context);
    if(attached && mode!=1 && !(mode==2&&type!=0))b.insertItem(c);
    var peers=new ArrayList<Item>();peers.add(null);peers.add(c);peers.add(trace);peers.add(foreign);peers.add(via);peers.add(new Via(null,null,new int[]{2},1,901,0,FixedState.UNFIXED,true,null));peers.add(new Pin(1,0,new int[]{1},1,902,FixedState.UNFIXED,null));peers.add(new ComponentObstacleArea(null,0,Vector.ZERO,0,false,1,903,component,null,FixedState.UNFIXED,null));peers.add(new ComponentObstacleArea(null,0,Vector.ZERO,0,false,1,904,component+1,null,FixedState.UNFIXED,null));
    b.searchTreeManager.getAutorouteTree(1);items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();areas=new Pool();Set<Integer> changed=new TreeSet<>();var out=snapshotSpecial(b,o,changed,c,peers,attached);
    out.add("nullFactories="+(b.insertConductionArea(null,0,null,1,true,fixed)==null)+":"+(b.insertViaObstacle(null,0,1,fixed)==null)+":"+(b.insertViaObstacle(null,0,null,0,false,1,0,null,fixed)==null)+":"+(b.insertComponentObstacle(null,0,1,fixed)==null)+":"+(b.insertComponentObstacle(null,0,null,0,false,1,0,null,fixed)==null));
    int id=c.getId();
    for(int step=0;step<9;step++){
      ObstacleArea current=c;int k=step;out.add("op="+invoke(()->{switch(k){case 0->{if(current instanceof ConductionArea a)a.setIsFilled(false);else current.assignNetNo(1);}case 1->{if(current instanceof ConductionArea a)a.setIsObstacle(false);else current.clearDerivedData();}case 2->b.generateSnapshot();case 3->current.moveBy(new IntVector(11,-23));case 4->{return b.undo(changed);}case 5->{return b.redo(changed);}case 6->{if(current instanceof ConductionArea a){a.setIsFilled(true);a.setIsObstacle(true);}}case 7->current.changePlacementSide(new IntPoint(0,0));case 8->b.removeItem(current);}return "ok";}));
      if(attached && b.getItem(id) instanceof ObstacleArea restored)c=restored;
      out.addAll(snapshotSpecial(b,o,changed,c,peers,attached));
    }
    return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception{try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String line:Files.readAllLines(Path.of(args[0])))out.write(line+" | "+runSpecial(line)+"\n");}}
}
