package app.freerouting.board.facade;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.*;
import java.nio.file.*;
import java.util.*;
/** Uses the unchanged source ComponentOutline and real RoutingBoard. */
public class ComponentOutlineOracle extends BoardConstructionOracle {
  static Pool areas;
  static Object treeCache(Item item,Object tree)throws Exception {Object info=field(item,"searchTreesInfo");if(info==null)return null;for(Object record:(Collection<?>)field(info,"treeList"))if(field(record,"tree")==tree)return field(record,"precalculatedTreeShapes");return null;}
  static String area(Area a)throws Exception {
    if(a==null)return "null";
    if(a instanceof Circle c)return "C:"+point(c.center)+":"+c.radius;
    if(a instanceof TileShape t)return shape(t);
    if(a instanceof PolygonShape p){String s="P:"+p.borderLineCount();for(int i=0;i<p.borderLineCount();i++)s+="~"+point(p.corner(i));return s;}
    var p=(PolylineArea)a;String s="A:"+area(p.getBorder());var holes=p.getHoles();s+="/"+(holes==null?"null":""+holes.length);if(holes!=null)for(var h:holes)s+="~"+area(h);return s;
  }
  static Area makeArea(int kind) {
    Point[] corners={new IntPoint(-300,-170),new IntPoint(470,-170),new IntPoint(470,110),new IntPoint(80,110),new IntPoint(80,360),new IntPoint(-300,360)};
    return switch(kind){
      case 0->new IntBox(-300,-170,470,360);case 1->new Circle(new IntPoint(31,-27),190);
      case 2->new PolygonShape(corners);case 3->new PolylineArea(new PolygonShape(corners),new PolylineShape[]{new IntBox(-100,-50,0,70)});
      case 4->new IntOctagon(-300,-170,470,360,-450,530,-310,650);
      case 5->TileShape.getInstance(new Point[]{new IntPoint(-300,-170),new IntPoint(470,-110),new IntPoint(30,360)});
      case 6->Simplex.EMPTY;case 7->null;case 8->new IntBox(0,0,300,0);case 9->new IntBox(4,7,4,7);
      case 10->new PolylineArea(new IntBox(-300,-170,470,360),null);
      case 11->new PolylineArea(null,new PolylineShape[0]);
      case 12->new PolylineArea(new IntBox(-300,-170,470,360),new PolylineShape[]{null});default->throw new AssertionError();};
  }
  static String invoke(Action a)throws Exception {try{return call(a);}catch(NegativeArraySizeException e){return "negative_array_error";}catch(ClassCastException e){return "cast_error";}}
  static String description(ComponentOutline c)throws Exception {
    var v=(Vector)field(c,"translation");String s=c.getId()+":"+c.isFront()+":"+c.isCourtyard()+":"+c.isFabrication()+":"+c.isClosed()+":"+bits((double)field(c,"rotationInDegree"))+":"+(v==null?"null":bits(v.toFloat().x)+","+bits(v.toFloat().y));
    s+="/"+areas.id(field(c,"relativeArea"))+":"+areas.id(field(c,"precalculatedAbsoluteArea"));
    s+="/"+invoke(()->area(c.getArea()))+"/"+areas.id(field(c,"precalculatedAbsoluteArea"));
    s+="/"+invoke(()->box(c.boundingBox()))+"/"+invoke(()->c.getLayer()+":"+c.firstLayer()+":"+c.lastLayer()+":"+c.shapeLayer(-8)+":"+c.isOnLayer(0)+":"+c.isOnLayer(1)+":"+c.isOnLayer(2));
    s+="/"+c.tileShapeCount()+":"+c.isSelectedByFilter(null)+":"+c.isObstacle((Item)null);return s;
  }
  static String runOutline(String line)throws Exception {
    var in=new Scanner(line);int kind=in.nextInt(),config=in.nextInt(),mode=in.nextInt();double angle=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));
    var layers=new LayerStructure(new Layer[]{new Layer("F.Cu",true),new Layer("In1.Cu",false),new Layer("B.Cu",true)});var matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});matrix.setDefaultValue(12);var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(AngleRestriction.valueOf(config%3));var com=new Communication();var o=new Observers();o.constructionMetadata=true;com.observers=o;
    var b=new RoutingBoard(new IntBox(-100000,-100000,100000,100000),layers,new PolylineShape[0],1,rules,com);rules.nets.add("A",1,false);rules.nets.add("B",1,false);b.components.setFlipStyleRotateFirst(config>=2);
    var relative=makeArea(kind);Vector translation=mode==2?null:mode==3?Vector.getInstance(java.math.BigInteger.valueOf(3),java.math.BigInteger.valueOf(-5),java.math.BigInteger.valueOf(2)):mode==4?Vector.ZERO:new IntVector(300,400);
    boolean front=config%2==0;boolean valid=mode==0;
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();areas=new Pool();var out=new ArrayList<String>();
    ComponentOutline c=valid?b.insertComponentOutline(relative,front,translation,angle,7,true,config%2==1,true,FixedState.USER_FIXED):new ComponentOutline(relative,front,translation,angle,99,7,true,config%2==1,true,FixedState.USER_FIXED,mode==1?null:b);
    // A null/unbounded factory rejection must not allocate an item ID or emit callbacks.
    out.add("factory="+invoke(()->{var q=b.insertComponentOutline(kind==0?null:Simplex.EMPTY,true,Vector.ZERO,0,1,false,false,false,FixedState.UNFIXED);return q==null?"null":"item";}));
    var tree=b.searchTreeManager.getDefaultTree();Set<Integer> changed=new TreeSet<>();
    for(int step=0;step<14;step++){
      ComponentOutline current=c;int k=step;
      out.add("op="+invoke(()->{switch(k){case 0->{}case 1->current.rotateApprox(angle,new FloatPoint(.5,-.5));case 2->current.rotateApprox(0,null);case 3->current.translateBy(Vector.ZERO);case 4->current.rotateApprox(37,null);case 5->current.changePlacementSide(null);case 6->current.clearDerivedData();case 7->current.turn90Degree(-1,new IntPoint(10,20));case 8->current.changePlacementSide(new IntPoint(0,0));case 9->current.translateBy(null);case 10->current.turn90Degree(0,null);case 11->current.rotateApprox(-angle,FloatPoint.ZERO);case 12->{b.components.setFlipStyleRotateFirst(!b.components.getFlipStyleRotateFirst());current.clearDerivedData();}case 13->current.translateBy(new IntVector(11,-23));}return "ok";}));
      out.add("value="+description(c));out.add("treeCount="+c.treeShapeCount(tree));out.add("treeCache="+arrays.id(treeCache(c,tree)));
      var copy=(ComponentOutline)c.copy(1000+step);out.add("copy="+description(copy));
      if(valid)out.addAll(state(b,o,changed));
    }
    if(valid){b.generateSnapshot();c.moveBy(new IntVector(7,-9));out.addAll(state(b,o,changed));out.add("undo="+b.undo(changed));out.addAll(state(b,o,changed));out.add("redo="+b.redo(changed));out.addAll(state(b,o,changed));b.removeItem(b.getItem(c.getId()));out.addAll(state(b,o,changed));}
    return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception {try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String line:Files.readAllLines(Path.of(args[0])))out.write(line+" | "+runOutline(line)+"\n");}}
}
