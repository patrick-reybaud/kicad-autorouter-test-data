package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
public class ItemContactsOracle {
 static String events;static ArrayList<Item> items;static ArrayList<int[]> links;static int fail;
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IllegalStateException e){return "callback_error";}}
 static Set<Item> contacts(int index){events+="c"+index+",";if(fail==index)throw new IllegalStateException();var a=links.get(index);if(a==null)return null;var s=new TreeSet<Item>();for(int i:a)s.add(items.get(i));return s;}
 static class Plain extends Item {
  int index;Plain(int index,int id,int[] nets,int component){super(nets,1,id,component,FixedState.UNFIXED,null);this.index=index;}
  public Set<Item> getNormalContacts(){return contacts(index);}
  public Item copy(int id){throw new UnsupportedOperationException();}public boolean isObstacle(Item other){throw new UnsupportedOperationException();}public int tileShapeCount(){throw new UnsupportedOperationException();}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){throw new UnsupportedOperationException();}
  public boolean isOnLayer(int l){throw new UnsupportedOperationException();}public int firstLayer(){return 0;}public int lastLayer(){return 0;}
  public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}public IntBox boundingBox(){throw new UnsupportedOperationException();}public int shapeLayer(int i){return 0;}public boolean isSelectedByFilter(ItemSelectionFilter f){throw new UnsupportedOperationException();}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static class Area extends ConductionArea {
  int index;Area(int index,int id,int[] nets,int component){super(null,0,app.freerouting.geometry.planar.Vector.ZERO,0,false,nets,1,id,component,null,false,FixedState.UNFIXED,null);this.index=index;}
  public Set<Item> getNormalContacts(){return contacts(index);}
 }
 static class TraceItem extends PolylineTrace {
  Point start,end;int index;TraceItem(int index,int layer,Point a,Point b){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(10,0)}),layer,1,new int[]{1},1,100+index,0,FixedState.UNFIXED,null);this.index=index;start=a;end=b;}
  public Point firstCorner(){events+="f"+index+",";return start;}public Point lastCorner(){events+="l"+index+",";return end;}
 }
 static class Drill extends DrillItem {
  int from,to,index;Drill(int index,int from,int to,Point p){super(p,new int[]{2},1,100+index,0,FixedState.UNFIXED,null);this.index=index;this.from=from;this.to=to;}
  public int firstLayer(){events+="f"+index+",";return from;}public int lastLayer(){events+="l"+index+",";return to;}
  public Shape getShape(int i){throw new UnsupportedOperationException();}public Padstack getPadstack(){throw new UnsupportedOperationException();}public Item copy(int id){throw new UnsupportedOperationException();}public boolean isObstacle(Item other){throw new UnsupportedOperationException();}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public boolean isSelectedByFilter(ItemSelectionFilter f){throw new UnsupportedOperationException();}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static Point point(int n){return switch(n){case 0->null;case 1->new IntPoint(0,0);case 2->new IntPoint(10,0);case 3->new IntPoint(4,6);case 4->Point.getInstance(BigInteger.valueOf(1),BigInteger.valueOf(2),BigInteger.valueOf(3));default->new IntPoint(0,0);};}
 static String point(Point p){if(p==null)return "null";return p.getId()+":"+Long.toHexString(Double.doubleToLongBits(p.toFloat().x))+","+Long.toHexString(Double.doubleToLongBits(p.toFloat().y));}
 static String list(Collection<Item> s){String v=""+s.size();for(var x:s)v+=","+items.indexOf(x);return v;}
 static String run(String input){Scanner in=new Scanner(input);String kind=in.next();events="";items=new ArrayList<>();items.add(null);ArrayList<String> out=new ArrayList<>();
 if(kind.equals("P")){
  for(int i=1;i<=2;i++){int type=in.nextInt(),a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();items.add(switch(type){case 0->null;case 1->new Plain(i,100+i,new int[]{1},0);case 2->new TraceItem(i,a,point(c),point(d));case 3->new Drill(i,a,b,point(c));default->throw new AssertionError();});}
  for(int i=1;i<=2;i++)for(int j=0;j<3;j++){final int a=i,b=j;events="";out.add(call(()->point(items.get(a).normalContactPoint(items.get(b)))));out.add(events.isEmpty()?"-":events);}
 }else{
  int n=in.nextInt();links=new ArrayList<>();links.add(null);
  for(int i=1;i<=n;i++){int type=in.nextInt(),id=in.nextInt(),net=in.nextInt(),comp=in.nextInt();int[] ns=net<0?null:net==0?new int[0]:new int[]{net,2};items.add(type==0?new Plain(i,id,ns,comp):new Area(i,id,ns,comp));int count=in.nextInt();int[] a=count<0?null:new int[count];if(a!=null)for(int j=0;j<count;j++)a[j]=in.nextInt();links.add(a);}
  fail=in.nextInt();int count=in.nextInt();
  for(int i=0;i<count;i++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();events="";
   if(op.equals("C")){out.add(call(()->list(items.get(a).getConnectedSet(b,c!=0))));out.add(events.isEmpty()?"-":events);}
   else{TreeSet<Item> visited=new TreeSet<>();for(int j=1;j<=n;j++)if((d&(1<<(j-1)))!=0)visited.add(items.get(j));out.add(call(()->items.get(a).isCycleRecu(visited,items.get(b),items.get(c),op.equals("I"))));out.add(list(visited));out.add(events.isEmpty()?"-":events);}
  }
 }
 if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[] args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
