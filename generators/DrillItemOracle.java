package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
public class DrillItemOracle {
 static String events="";
 interface Call{Object run()throws Exception;}
 static String call(Call f){try{return String.valueOf(f.run());}catch(InvocationTargetException e){return call(()->{throw (Exception)e.getCause();});}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(NegativeArraySizeException e){return "size_error";}catch(Exception e){throw new RuntimeException(e);}}
 static String number(double x){return "d"+Long.toHexString(Double.doubleToLongBits(x));}
 static String point(Point p){if(p==null)return "null";var f=p.toFloat();return p.getId()+":"+number(f.x)+","+number(f.y);}
 static String box(IntBox b){return b.ll.x+","+b.ll.y+","+b.ur.x+","+b.ur.y;}
 static String shape(Shape s){if(s==null)return "null";if(s instanceof Circle c)return "C:"+point(c.center)+":"+c.radius;TileShape t=(TileShape)s;String v=t.getClass().getSimpleName()+":"+t.dimension()+":"+box(t.boundingBox());for(int i=0;i<t.borderLineCount();i++)v+=";"+point(t.corner(i));return v;}
 static String shapes(TileShape[] s){String v=""+s.length;for(var t:s)v+="/"+shape(t);return v;}
 static ConvexShape[] pool(){return new ConvexShape[]{null,new Circle(new IntPoint(0,0),12),new IntBox(-15,-7,15,7),new IntOctagon(-12,-12,12,12,-18,18,-18,18),Simplex.getInstance(new Line[]{new Line(new IntPoint(-12,-8),new IntPoint(13,-8)),new Line(new IntPoint(13,-8),new IntPoint(1,17)),new Line(new IntPoint(1,17),new IntPoint(-12,-8))}),new Circle(new IntPoint(30,40),5),new IntBox(15,20,25,24),IntBox.EMPTY};}
 static Point center(int n){return switch(n){case 0->null;case 1->new IntPoint(0,0);case 2->new IntPoint(30,40);case 3->Point.getInstance(BigInteger.valueOf(7),BigInteger.valueOf(-5),BigInteger.valueOf(3));default->new IntPoint(-17,8);};}
 static class TestDrill extends DrillItem {
  Padstack pad;ConvexShape[] shapes;boolean front;
  TestDrill(Point p,int cl,BasicBoard b,Padstack pad,ConvexShape[] shapes,boolean front){super(p,new int[]{1},cl,41,0,FixedState.UNFIXED,b);this.pad=pad;this.shapes=shapes;this.front=front;}
  public Shape getShape(int i){events+="s"+i+",";return shapes[i];}
  public Padstack getPadstack(){events+="p,";return pad;}
  public boolean isPlacedOnFront(){events+="f,";return front;}
  public void clearDerivedData(){events+="d,";super.clearDerivedData();}
  protected TileShape[] calculateTreeShapes(ShapeSearchTree t){events+="c,";return super.calculateTreeShapes(t);}
  public Item copy(int id){throw new UnsupportedOperationException();}
  public boolean isObstacle(Item i){throw new UnsupportedOperationException();}
  public boolean isSelectedByFilter(ItemSelectionFilter f){throw new UnsupportedOperationException();}
  public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}
  public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
  void center(Point p){setCenter(p);}
 }
 static Object field(Object o,Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object invoke(ShapeSearchTree t,String method,Class<?>[] types,Object... args)throws Exception{var m=ShapeSearchTree.class.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(t,args);}
 static String state(TestDrill d)throws Exception{return point(d.getCenter())+":"+field(d,DrillItem.class,"precalculatedFirstLayer")+":"+field(d,DrillItem.class,"precalculatedLastLayer")+":"+number((double)field(d,DrillItem.class,"precalculatedMinWidth"))+":"+(field(d,Item.class,"searchTreesInfo")==null?"null":"cache")+":"+(events.isEmpty()?"-":events);}
 static ShapeSearchTree tree(BasicBoard b,int c)throws Exception{var ctor=ShapeSearchTree.class.getDeclaredConstructor(ShapeBoundingDirections.class,BasicBoard.class,int.class);ctor.setAccessible(true);return ctor.newInstance(FortyfiveDegreeBoundingDirections.INSTANCE,b,c);}
 static String run(String input)throws Exception{
  Scanner in=new Scanner(input);in.next();int angle=in.nextInt(),cl=in.nextInt(),comp=in.nextInt(),hole=in.nextInt(),signal=in.nextInt(),mask=in.nextInt(),kind=in.nextInt(),absolute=in.nextInt(),front=in.nextInt(),only=in.nextInt(),name=in.nextInt(),ctr=in.nextInt(),flags=in.nextInt();
  Layer[] ls=new Layer[4];for(int i=0;i<4;i++)ls[i]=new Layer("L"+i,(signal&(1<<i))!=0);var layers=new LayerStructure(ls);var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});matrix.setDefaultValue(12);matrix.setValue(1,2,0,42);matrix.setValue(1,2,2,64);var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(angle<0?null:AngleRestriction.values()[angle]);rules.setHoleClearance(hole);BasicBoard board=new BasicBoard(new IntBox(-100,-100,100,100),layers,new PolylineShape[0],0,rules,new Communication());
  if((flags&4)!=0){var f=BasicBoard.class.getDeclaredField("rules");f.setAccessible(true);f.set(board,null);}if((flags&16)!=0)ls[1]=null;
  ConvexShape[] pool=pool(),ps=new ConvexShape[4],ss=new ConvexShape[4];for(int i=0;i<4;i++){ps[i]=(mask&(1<<i))==0?null:pool[1+i%4];ss[i]=(mask&(1<<i))==0?null:pool[(kind+i)%pool.length];}
  Padstacks pads=new Padstacks(layers);String[] names={"via","via_10:5","via_10:30","via_10:0","via_7:3.3","via_10:bad",null};Padstack pad=pads.add(names[name],ps,true,absolute!=0);pad.holeOnly=only!=0;
  var trees=new ShapeSearchTree[]{tree(board,comp),new ShapeSearchTree45Degree(board,comp),new ShapeSearchTree90Degree(board,comp),tree(null,comp)};
  TestDrill d=new TestDrill(center(ctr),cl,(flags&1)!=0?null:board,(flags&2)!=0?null:pad,ss,front!=0);events="";ArrayList<String> out=new ArrayList<>();int count=in.nextInt();
  for(int j=0;j<count;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();events="";out.add(call(()->switch(op){
   case "F"->d.firstLayer();case "L"->d.lastLayer();case "S"->d.shapeLayer(a);case "A"->d.isOnLayer(a);case "N"->d.tileShapeCount();case "B"->box(d.boundingBox());case "R"->number(d.smallestRadius());case "W"->number(d.minWidth());case "G"->shape(d.getShapeOnLayer(a));
   case "K"->shapes(trees[a].calculateTreeShapes((flags&8)!=0?null:d));case "H"->shape((Shape)invoke(trees[a],"drillHoleObstacle",new Class<?>[]{DrillItem.class},(flags&8)!=0?null:d));case "U"->invoke(trees[a],"drillHoleClearanceDelta",new Class<?>[]{DrillItem.class,Shape.class,int.class},(flags&8)!=0?null:d,pool[b],c);
   case "g"->shape(d.getTreeShapeOnLayer(a<0?null:trees[a],b));case "t"->shape(d.getTileShapeOnLayer(a));case "C"->shape(d.getTraceConnectionShape(null,a));case "r"->{String v=""+d.getRatsnestCorners().length;for(var p:d.getRatsnestCorners())v+="/"+point(p);yield v;}
   case "D"->{d.clearDerivedData();yield "ok";}case "T"->{d.translateBy(new IntVector(a,b));yield "ok";}case "Q"->{d.turn90Degree(a,new IntPoint(b,c));yield "ok";}case "M"->{d.changePlacementSide(new IntPoint(a,b));yield "ok";}
   case "f"->{d.front=a!=0;yield "ok";}case "p"->{d.pad=a==0?null:pad;yield "ok";}case "c"->{d.center(center(a));yield "ok";}case "s"->{ss[a]=pool[b];yield "ok";}case "b"->{d.board=a==0?null:board;yield "ok";}default->throw new AssertionError(op);
  }));out.add(state(d));}
  if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[] args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
