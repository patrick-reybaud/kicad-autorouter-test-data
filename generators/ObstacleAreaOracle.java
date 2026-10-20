package app.freerouting.board.facade;
import app.freerouting.board.actions.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.*;
import java.nio.file.*;
import java.util.*;
/** Actual source ObstacleArea, Communication, Unit and specialized ShapeSearchTree. */
public class ObstacleAreaOracle extends ComponentOutlineOracle {
  static String pieces(TileShape[] p){String s=""+arrays.id(p);if(p!=null){s+=":"+p.length;for(var t:p)s+="~"+shapes.id(t)+":"+shape(t);}return s;}
  static String description(ObstacleArea c)throws Exception {
    Vector v=c.getTranslation();String s=c.getId()+":"+c.getLayer()+":"+c.getSideChanged()+":"+bits(c.getRotationInDegree())+":"+(v==null?"null":bits(v.toFloat().x)+","+bits(v.toFloat().y));
    s+="/"+areas.id(c.getRelativeArea())+":"+areas.id(field(c,"precalculatedAbsoluteArea"));
    s+="/"+invoke(()->area(c.getArea()))+"/"+areas.id(field(c,"precalculatedAbsoluteArea"));
    s+="/"+invoke(()->box(c.boundingBox()))+"/"+c.firstLayer()+":"+c.lastLayer()+":"+c.shapeLayer(-7)+":"+c.isOnLayer(0)+":"+c.isOnLayer(1)+":"+c.isOnLayer(2);
    s+="/"+invoke(()->pieces(c.splitToConvex()))+"/"+invoke(()->pieces(c.splitToConvex()));
    s+="/"+invoke(()->c.tileShapeCount());for(int no:new int[]{-1,0,1,1000})s+="/"+invoke(()->{var t=c.getTileShape(no);return shapes.id(t)+":"+shape(t);});
    s+="/"+invoke(()->c.getArea().contains(new IntPoint(300,400)))+"/"+invoke(()->c.getArea().contains(new IntPoint(0,0)));
    s+="/"+invoke(()->c.isObstacle((Item)null));
    for(var choices:new ItemSelectionFilter.SelectableChoices[]{ItemSelectionFilter.SelectableChoices.KEEPOUT,ItemSelectionFilter.SelectableChoices.FIXED,ItemSelectionFilter.SelectableChoices.UNFIXED}){var filter=new ItemSelectionFilter(choices);s+="/"+invoke(()->c.isSelectedByFilter(filter));}
    s+="/"+invoke(()->c.isSelectedByFilter(null));return s;
  }
  static String runArea(String line)throws Exception {
    var in=new Scanner(line);
    if(line.startsWith("U ")){in.next();int f=in.nextInt(),t=in.nextInt();double value=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));return invoke(()->bits(Unit.scale(value,f<0?null:Unit.values()[f],t<0?null:Unit.values()[t])));}
    int kind=in.nextInt(),config=in.nextInt(),mode=in.nextInt();double angle=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));int unit=in.nextInt(),res=in.nextInt(),host=in.nextInt();int extraTree=in.hasNextInt()?in.nextInt():0;
    var layers=new LayerStructure(new Layer[]{new Layer("F.Cu",true),new Layer("In1.Cu",false),new Layer("B.Cu",true)});var matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});matrix.setDefaultValue(12);var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(AngleRestriction.valueOf(config%3));var o=new Observers();o.constructionMetadata=true;
    var info=host==0?null:new Communication.SpecctraParserInfo("\"",host==1?null:host==2?"":"KiCad",null,null,null,false);
    var com=new Communication(unit<0?null:Unit.values()[unit],res,info,null,new ItemIdGenerator(),o);
    var b=new RoutingBoard(new IntBox(-100000,-100000,100000,100000),layers,new PolylineShape[0],1,rules,com);rules.nets.add("A",1,false);rules.nets.add("B",1,false);b.components.setFlipStyleRotateFirst(config/6!=0);
    var relative=makeArea(kind);Vector translation=mode==2?null:mode==3?Vector.getInstance(java.math.BigInteger.valueOf(3),java.math.BigInteger.valueOf(-5),java.math.BigInteger.valueOf(2)):mode==4?Vector.ZERO:new IntVector(300,400);
    boolean side=(config/3)%2!=0,valid=mode==0||mode==5||mode==6;
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();areas=new Pool();var out=new ArrayList<String>();
    ObstacleArea c=mode==0?b.insertObstacle(relative,config%3,translation,angle,side,1,7,"area",FixedState.USER_FIXED):mode==5?b.insertObstacle(relative,config%3,1,FixedState.USER_FIXED):new ObstacleArea(relative,config%3,translation,angle,side,new int[]{1,2},1,99,7,"area",FixedState.USER_FIXED,mode==1?null:b);
    if(mode==6)b.insertItem(c);
    var trace=b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(300,400),new IntPoint(300,1000)}),0,10,new int[]{1},1,FixedState.UNFIXED);
    var foreign=b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(600,400),new IntPoint(600,1000)}),0,10,new int[]{3},1,FixedState.UNFIXED);
    out.add("host="+com.hostCadExists());for(var u:Unit.values())out.add("resolution="+invoke(()->bits(com.getResolution(u))));
    out.add("factory="+(b.insertObstacle(null,0,1,FixedState.UNFIXED)==null)+":"+(b.insertObstacle(null,0,null,0,false,1,0,null,FixedState.UNFIXED)==null));
    if(extraTree!=0)b.searchTreeManager.getAutorouteTree(1);
    var tree=b.searchTreeManager.getDefaultTree();Set<Integer> changed=new TreeSet<>();
    for(int step=0;step<14;step++){
      int k=step;
      out.add("op="+invoke(()->{switch(k){case 0->{}case 1->c.rotateApprox(angle,new FloatPoint(.5,-.5));case 2->c.rotateApprox(0,null);case 3->c.translateBy(Vector.ZERO);case 4->c.rotateApprox(37,null);case 5->c.changePlacementSide(null);case 6->c.clearDerivedData();case 7->c.turn90Degree(-1,new IntPoint(10,20));case 8->c.changePlacementSide(new IntPoint(0,0));case 9->c.translateBy(null);case 10->c.turn90Degree(0,null);case 11->c.rotateApprox(-angle,FloatPoint.ZERO);case 12->{b.components.setFlipStyleRotateFirst(!b.components.getFlipStyleRotateFirst());c.clearDerivedData();}case 13->c.translateBy(new IntVector(11,-23));}return "ok";}));
      out.add("value="+description(c));out.add("treeCount="+invoke(()->c.treeShapeCount(tree)));out.add("treeCache="+arrays.id(treeCache(c,tree)));out.add("obstacles="+c.isObstacle(trace)+":"+c.isObstacle(foreign)+":"+c.isObstacle(c));
      var copy=(ObstacleArea)c.copy(1000+step);out.add("copy="+description(copy));out.add("netCopy="+(c.netNumbers==copy.netNumbers));
      if(valid)out.addAll(state(b,o,changed,true));
    }
    if(valid){b.generateSnapshot();c.moveBy(new IntVector(7,-9));out.addAll(state(b,o,changed,true));out.add("undo="+b.undo(changed));out.addAll(state(b,o,changed,true));out.add("redo="+b.redo(changed));out.addAll(state(b,o,changed,true));b.removeItem(b.getItem(c.getId()));out.addAll(state(b,o,changed,true));}
    return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception {try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String line:Files.readAllLines(Path.of(args[0])))out.write(line+" | "+runArea(line)+"\n");}}
}
