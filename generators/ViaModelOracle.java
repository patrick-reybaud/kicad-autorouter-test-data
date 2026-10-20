package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import app.freerouting.board.trace.PolylineTrace;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
public class ViaModelOracle {
 static String events="";
 interface Call{Object run()throws Exception;}
 static String call(Call f){try{return String.valueOf(f.run());}catch(InvocationTargetException e){return call(()->{throw (Exception)e.getCause();});}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(IllegalStateException e){return "callback_error";}catch(ClassCastException e){return "cast_error";}catch(NegativeArraySizeException e){return "size_error";}catch(Exception e){throw new RuntimeException(e);}}
 static String number(double x){return "d"+Long.toHexString(Double.doubleToLongBits(x));}
 static String point(Point p){if(p==null)return "null";var f=p.toFloat();return p.getId()+":"+number(f.x)+","+number(f.y);}
 static String box(IntBox b){return b.ll.x+","+b.ll.y+","+b.ur.x+","+b.ur.y;}
 static String shape(Shape s){if(s==null)return "null";if(s instanceof Circle c)return "C:"+point(c.center)+":"+c.radius;TileShape t=(TileShape)s;String v=t.getClass().getSimpleName()+":"+t.dimension()+":"+box(t.boundingBox());for(int i=0;i<t.borderLineCount();i++)v+=";"+point(t.corner(i));return v;}
 static String shapes(TileShape[] s){String v=""+s.length;for(var t:s)v+="/"+shape(t);return v;}
 static ConvexShape[] pool(){return new ConvexShape[]{null,new Circle(new IntPoint(0,0),12),new IntBox(-15,-7,15,7),new IntOctagon(-12,-12,12,12,-18,18,-18,18),Simplex.getInstance(new Line[]{new Line(new IntPoint(-12,-8),new IntPoint(13,-8)),new Line(new IntPoint(13,-8),new IntPoint(1,17)),new Line(new IntPoint(1,17),new IntPoint(-12,-8))}),new Circle(new IntPoint(30,40),5),new IntBox(15,20,25,24),IntBox.EMPTY};}
 static Point center(int n){return switch(n){case 0->null;case 1->new IntPoint(0,0);case 2->new IntPoint(30,40);case 3->Point.getInstance(BigInteger.valueOf(7),BigInteger.valueOf(-5),BigInteger.valueOf(3));default->new IntPoint(-17,8);};}
 static IdentityHashMap<Shape,Integer> shapeIds;
 static int shapeId(Shape s){if(s==null)return 0;Integer id=shapeIds.get(s);if(id==null){id=shapeIds.size()+1;shapeIds.put(s,id);}return id;}
 static class TestVia extends Via {
  TestVia(Padstack pad,Point center,int[] nets,FixedState fixed,boolean attach,BasicBoard board){super(pad,center,nets,1,41,7,fixed,attach,board);}
  public Padstack getPadstack(){events+="p,";return super.getPadstack();}
  public boolean isPlacedOnFront(){events+="f,";return super.isPlacedOnFront();}
  public void clearDerivedData(){events+="d,";super.clearDerivedData();}
  void center(Point p)throws Exception{var f=DrillItem.class.getDeclaredField("center");f.setAccessible(true);f.set(this,p);}
 }
 static Object field(Object o,Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static String state(Via d)throws Exception{
  Padstack pad=(Padstack)field(d,Via.class,"padstack");Shape[] shapes=(Shape[])field(d,Via.class,"precalculatedShapes");String cache=shapes==null?"null":""+shapes.length;if(shapes!=null)for(Shape s:shapes)cache+=","+shapeId(s);
  return point(d.getCenter())+":"+field(d,DrillItem.class,"precalculatedFirstLayer")+":"+field(d,DrillItem.class,"precalculatedLastLayer")+":"+number((double)field(d,DrillItem.class,"precalculatedMinWidth"))+":"+(pad==null?0:pad.id)+":"+cache+":"+d.isEscapeVia+":"+d.escapeViaSmdLayer+":"+(events.isEmpty()?"-":events);
 }
 static record Node(int type,int id,int first,int last,boolean obstacle,int[] nets){}
 static ArrayList<Item> policyItems;static ArrayList<Node> policyNodes;static int failure;static int[] contactIds;static int contactKind;
 static int layer(int index,boolean first){events+=(first?"f":"l")+index+",";if(failure==index*3+(first?0:1))throw new IllegalStateException();return first?policyNodes.get(index).first:policyNodes.get(index).last;}
 static boolean obstacle(int index){events+="o"+index+",";if(failure==index*3+2)throw new IllegalStateException();return policyNodes.get(index).obstacle;}
 static class OtherVia extends Via {
  int index;OtherVia(int i,Node n){super(null,Point.ZERO,n.nets,0,n.id,0,FixedState.UNFIXED,false,null);index=i;}
  public int firstLayer(){return layer(index,true);}public int lastLayer(){return layer(index,false);}
 }
 static class OtherPin extends Pin {
  int index;OtherPin(int i,Node n){super(0,0,n.nets,0,n.id,FixedState.UNFIXED,null);index=i;}
  public int firstLayer(){return layer(index,true);}public int lastLayer(){return layer(index,false);}
 }
 static class OtherTrace extends PolylineTrace {
  int index;OtherTrace(int i,Node n){super(new Polyline(new Point[]{Point.ZERO,new IntPoint(1,0)}),0,1,n.nets,0,n.id,0,FixedState.UNFIXED,null);index=i;}
  public int firstLayer(){return layer(index,true);}public int lastLayer(){return layer(index,false);}
 }
 static class OtherArea extends ConductionArea {
  int index;OtherArea(int i,Node n){super(null,0,app.freerouting.geometry.planar.Vector.ZERO,0,false,n.nets,0,n.id,0,null,n.obstacle,FixedState.UNFIXED,null);index=i;}
  public int firstLayer(){return layer(index,true);}public int lastLayer(){return layer(index,false);}public boolean getIsObstacle(){return obstacle(index);}
 }
 static class OtherComponent extends ComponentObstacleArea {
  int index;OtherComponent(int i,Node n){super(null,0,app.freerouting.geometry.planar.Vector.ZERO,0,false,0,n.id,0,null,FixedState.UNFIXED,null);index=i;netNumbers=n.nets;}
  public int firstLayer(){return layer(index,true);}public int lastLayer(){return layer(index,false);}
 }
 static class PolicyVia extends Via {
  PolicyVia(int[] nets,boolean attach){super(null,Point.ZERO,nets,0,100,0,FixedState.UNFIXED,attach,null);}
  public Set<Item> getNormalContacts(){events+="n,";if(contactKind==2)return null;Set<Item> result=contactKind==0?new TreeSet<>():new LinkedHashSet<>();for(int i:contactIds)result.add(policyItems.get(i));return result;}
 }
 static int[] nets(Scanner in){int n=in.nextInt();int[] a=n<0?null:new int[n];for(int i=0;i<n;i++)a[i]=in.nextInt();return a;}
 static String policy(Scanner in){
  boolean attach=in.nextInt()!=0;failure=in.nextInt();int[] nets=nets(in);PolicyVia d=new PolicyVia(nets,attach);d.netNumbers=nets;
  policyItems=new ArrayList<>();policyItems.add(null);policyNodes=new ArrayList<>();policyNodes.add(null);int n=in.nextInt();
  for(int i=1;i<=n;i++){int type=in.nextInt(),id=in.nextInt(),first=in.nextInt(),last=in.nextInt();boolean obs=in.nextInt()!=0;Node node=new Node(type,id,first,last,obs,nets(in));policyNodes.add(node);Item item=switch(type){case 0->new OtherVia(i,node);case 1->new OtherPin(i,node);case 2->new OtherTrace(i,node);case 3->new OtherArea(i,node);default->new OtherComponent(i,node);};item.netNumbers=node.nets;policyItems.add(item);}
  ArrayList<String> out=new ArrayList<>();for(Item item:policyItems){events="";out.add(call(()->d.isObstacle(item)));out.add(events.isEmpty()?"-":events);}events="";out.add(call(()->d.isObstacle(d)));out.add(events.isEmpty()?"-":events);
  int count=in.nextInt();for(int i=0;i<count;i++){contactKind=in.nextInt();int size=in.nextInt();contactIds=new int[size];for(int j=0;j<size;j++)contactIds[j]=in.nextInt();events="";out.add(call(d::isTail));out.add(events.isEmpty()?"-":events);}
  return String.join(" ",out);
 }
 static String run(String input)throws Exception{
  Scanner in=new Scanner(input);String kind=in.next();if(kind.equals("P")){String result=policy(in);if(in.hasNext())throw new AssertionError();return result;}int flags=in.nextInt(),ctr=in.nextInt(),initial=in.nextInt(),fixed=in.nextInt(),attach=in.nextInt(),nc=in.nextInt();int[]nets=nc<0?null:new int[nc];for(int i=0;i<nc;i++)nets[i]=in.nextInt();
  LayerStructure layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("I1",true),new Layer("I2",true),new Layer("B",true)});ClearanceMatrix matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});BasicBoard board=new BasicBoard(new IntBox(-100,-100,100,100),layers,new PolylineShape[0],0,new BoardRules(layers,matrix),new Communication());
  ConvexShape[] pool=pool();shapeIds=new IdentityHashMap<>();for(var shape:pool)shapeId(shape);Padstacks registry=new Padstacks(layers);ArrayList<Padstack> pads=new ArrayList<>();pads.add(null);ArrayList<ConvexShape[]> arrays=new ArrayList<>();arrays.add(null);
  int count=in.nextInt();for(int i=1;i<=count;i++){int n=in.nextInt();ConvexShape[] a=n<0?null:new ConvexShape[n];for(int j=0;j<n;j++)a[j]=pool[in.nextInt()];arrays.add(a);pads.add(registry.add("pad"+i,a,false,false));}
  BoardLibrary library=new BoardLibrary((flags&4)!=0?null:registry,new Packages(registry));int ns=in.nextInt();Padstack[] subset=ns<0?null:new Padstack[ns];for(int i=0;i<ns;i++)subset[i]=pads.get(in.nextInt());if(subset!=null)library.setViaPadstacks(subset);var lf=BasicBoard.class.getDeclaredField("library");lf.setAccessible(true);lf.set(board,(flags&2)!=0?null:library);
  TestVia d=new TestVia(pads.get(initial),center(ctr),nets,fixed<0?null:FixedState.values()[fixed],attach!=0,(flags&1)!=0?null:board);if(nc==-2)d.netNumbers=null;
  ArrayList<String> out=new ArrayList<>();int ops=in.nextInt();
  for(int i=0;i<ops;i++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();events="";out.add(call(()->switch(op){
   case "G"->{Shape shape=d.getShape(a);yield shapeId(shape)+"/"+shape(shape);}case "F"->d.firstLayer();case "L"->d.lastLayer();case "W"->number(d.minWidth());case "R"->number(d.smallestRadius());case "B"->box(d.boundingBox());case "N"->d.tileShapeCount();case "r"->d.isRoutable();
   case "D"->{d.clearDerivedData();yield "ok";}case "T"->{d.translateBy(new IntVector(a,b));yield "ok";}case "Q"->{d.turn90Degree(a,new IntPoint(b,c));yield "ok";}case "M"->{d.changePlacementSide(new IntPoint(a,b));yield "ok";}
   case "P"->{d.setPadstack(pads.get(a));yield "ok";}case "C"->{d.center(center(a));yield "ok";}case "s"->{arrays.get(a)[b]=pool[c];yield "ok";}case "b"->{d.board=a==0?null:board;yield "ok";}case "e"->{d.isEscapeVia=a!=0;d.escapeViaSmdLayer=b;yield "ok";}
   case "f"->{ItemSelectionFilter filter=null;if(a>=0){filter=new ItemSelectionFilter();for(var choice:ItemSelectionFilter.SelectableChoices.values())filter.setSelected(choice,(a&(1<<choice.ordinal()))!=0);}yield d.isSelectedByFilter(filter);}
   case "x"->{Via copy=(Via)d.copy(a);yield copy.getId()+"/"+copy.getComponentId()+"/"+copy.clearanceClassIndex()+"/"+(copy.getPadstack()==d.getPadstack())+"/"+(copy.netNumbers==d.netNumbers)+"/"+Arrays.toString(copy.netNumbers).replace(" ","")+"/"+copy.attachAllowed+"/"+(copy.getFixedState()==d.getFixedState())+"/"+state(copy);}
   default->throw new AssertionError(op);
  }));out.add(state(d));}
  if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[]args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
