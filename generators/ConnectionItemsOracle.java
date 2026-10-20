package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.util.*;
public class ConnectionItemsOracle {
 record Spec(int kind,int[] ids){}
 record Node(int type,int id,int first,int last,int fixed,int half,boolean route,int poly,int start,int end,int netmode,double length,Spec normal,Spec begin,Spec finish){}
 static ArrayList<Item> items;static ArrayList<Node> nodes;static int[][] points;static String events;static int reads,budget;static Spec ignore;
 interface Call{Object run();}
 static String call(Call c){try{return String.valueOf(c.run());}catch(NullPointerException e){return "null_error";}catch(IllegalStateException e){return "callback_error";}}
 static Spec read(Scanner in){int kind=in.nextInt(),n=in.nextInt();int[] ids=new int[n];for(int i=0;i<n;i++)ids[i]=in.nextInt();return new Spec(kind,ids);}
 static int index(Item item){return items.indexOf(item);}
 static Point point(int p){return p<0?null:new IntPoint(p,0);}
 static Set<Item> set(Spec s){if(s.kind==2)return null;Set<Item> r=s.kind==0?new TreeSet<>():new LinkedHashSet<>();for(int i:s.ids)r.add(items.get(i));return r;}
 static Set<Item> contacts(int i,int kind){events+=(kind==0?"n":kind==1?"s":"e")+i+",";if(++reads>budget)throw new IllegalStateException();Node n=nodes.get(i);return set(kind==0?n.normal:kind==1?n.begin:n.finish);}
 static Set<Item> atPoint(int i,Point p){int x=((IntPoint)p).x;events+="a"+i+":"+x+",";if(++reads>budget)throw new IllegalStateException();Spec s=nodes.get(i).normal;if(s.kind==2)return null;Set<Item> r=s.kind==0?new TreeSet<>():new LinkedHashSet<>();for(int j:s.ids)if(points[i][j]==x)r.add(items.get(j));return r;}
 static Point contact(int i,Item other){int j=index(other);events+="p"+i+":"+j+",";return point(points[i][j]);}
 static int first(int i){events+="f"+i+",";return nodes.get(i).first;}static int last(int i){events+="l"+i+",";return nodes.get(i).last;}
 static boolean route(int i){events+="r"+i+",";Node n=nodes.get(i);if(n.type==0)return n.route;if(n.type==1)return false;if(n.fixed<0)throw new NullPointerException();return n.fixed<2&&items.get(i).netCount()>0;}
 static Point start(int i){events+="b"+i+",";return point(nodes.get(i).start);}static Point end(int i){events+="z"+i+",";return point(nodes.get(i).end);}
 static double length(int i){events+="d"+i+",";return nodes.get(i).length;}
 static FixedState fixed(Node n){return n.fixed<0?null:FixedState.values()[n.fixed];}static int[] nets(Node n){return n.netmode==0?new int[0]:new int[]{1};}
 static Polyline poly(Node n){return new Polyline(n.poly==0?new Point[]{new IntPoint(0,0),new IntPoint(10,0)}:new Point[]{new IntPoint(0,0),new IntPoint(10,0),new IntPoint(10,10)});}
 static class Plain extends Item {
  int i;Plain(int i,Node n){super(nets(n),1,n.id,0,fixed(n),null);this.i=i;}
  public Set<Item> getNormalContacts(){return contacts(i,0);}public Point normalContactPoint(Item x){return contact(i,x);}public int firstLayer(){return first(i);}public int lastLayer(){return last(i);}public boolean isRoutable(){return route(i);}
  public Item copy(int id){throw new UnsupportedOperationException();}public boolean isObstacle(Item o){return false;}public int tileShapeCount(){throw new UnsupportedOperationException();}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){throw new UnsupportedOperationException();}public boolean isOnLayer(int l){return false;}public int shapeLayer(int n){return 0;}public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}public IntBox boundingBox(){throw new UnsupportedOperationException();}public boolean write(ObjectOutputStream s){return false;}public boolean isSelectedByFilter(ItemSelectionFilter f){return false;}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static class TestPin extends Pin {
  int i;TestPin(int i,Node n){super(0,0,nets(n),1,n.id,fixed(n),null);this.i=i;}
  public Set<Item> getNormalContacts(){return contacts(i,0);}public Point normalContactPoint(Item x){return contact(i,x);}public int firstLayer(){return first(i);}public int lastLayer(){return last(i);}public boolean isRoutable(){return route(i);}
 }
 static class TestVia extends Via {
  int i;TestVia(int i,Node n){super(null,Point.ZERO,nets(n),1,n.id,0,fixed(n),false,null);this.i=i;}
  public Set<Item> getNormalContacts(){return contacts(i,0);}public Point normalContactPoint(Item x){return contact(i,x);}public int firstLayer(){return first(i);}public int lastLayer(){return last(i);}public boolean isRoutable(){return route(i);}
 }
 static class TestTrace extends Trace {
  public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){throw new UnsupportedOperationException();}
  public boolean checkConnectionToPin(boolean b){throw new UnsupportedOperationException();}public boolean combine(){throw new UnsupportedOperationException();}public Collection<PolylineTrace> split(IntOctagon s){throw new UnsupportedOperationException();}public Trace[] split(Point p){throw new UnsupportedOperationException();}public boolean pullTight(app.freerouting.board.optimize.TraceTightener a){throw new UnsupportedOperationException();}
  int i;TestTrace(int i,Node n){super(0,n.half,nets(n),1,n.id,0,fixed(n),null);this.i=i;}
  public Set<Item> getNormalContacts(){return contacts(i,0);}public Set<Item> getNormalContacts(Point p,boolean ignore){return atPoint(i,p);}public Set<Item> getStartContacts(){return contacts(i,1);}public Set<Item> getEndContacts(){return contacts(i,2);}public Point normalContactPoint(Item x){return contact(i,x);}public int firstLayer(){return first(i);}public int lastLayer(){return last(i);}public boolean isRoutable(){return route(i);}public Point firstCorner(){return start(i);}public Point lastCorner(){return end(i);}public double getLength(){return length(i);}
  public Item copy(int id){throw new UnsupportedOperationException();}public int tileShapeCount(){throw new UnsupportedOperationException();}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){throw new UnsupportedOperationException();}public boolean isOnLayer(int l){return false;}public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}public IntBox boundingBox(){throw new UnsupportedOperationException();}public boolean write(ObjectOutputStream s){return false;}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static class TestPolyline extends PolylineTrace {
  int i;TestPolyline(int i,Node n){super(poly(n),0,n.half,nets(n),1,n.id,0,fixed(n),null);this.i=i;}
  public Set<Item> getNormalContacts(){return contacts(i,0);}public Set<Item> getNormalContacts(Point p,boolean ignore){return atPoint(i,p);}public Set<Item> getStartContacts(){return contacts(i,1);}public Set<Item> getEndContacts(){return contacts(i,2);}public Point normalContactPoint(Item x){return contact(i,x);}public int firstLayer(){return first(i);}public int lastLayer(){return last(i);}public boolean isRoutable(){return route(i);}public Point firstCorner(){return start(i);}public Point lastCorner(){return end(i);}public double getLength(){return length(i);}
 }
 static String list(Collection<Item> s){String r=""+s.size();for(Item i:s)r+=","+index(i);return r;}
 static String corners(Point[] a){String r=""+a.length;for(Point p:a)r+="/"+((IntPoint)p).x;return r;}
 static String run(String input){Scanner in=new Scanner(input);in.next();budget=in.nextInt();int n=in.nextInt();items=new ArrayList<>();items.add(null);nodes=new ArrayList<>();nodes.add(null);events="";
  for(int i=1;i<=n;i++){Node d=new Node(in.nextInt(),in.nextInt(),in.nextInt(),in.nextInt(),in.nextInt(),in.nextInt(),in.nextInt()!=0,in.nextInt(),in.nextInt(),in.nextInt(),in.nextInt(),Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16)),read(in),read(in),read(in));nodes.add(d);Item item=switch(d.type){case 0->new Plain(i,d);case 1->new TestPin(i,d);case 2->new TestVia(i,d);case 3->new TestTrace(i,d);default->new TestPolyline(i,d);};if(d.netmode<0)item.netNumbers=null;items.add(item);}
  points=new int[n+1][n+1];for(int i=1;i<=n;i++)for(int j=0;j<=n;j++)points[i][j]=in.nextInt();ignore=read(in);ArrayList<String> out=new ArrayList<>();
  for(Item item:items){if(item==null)continue;ArrayList<Call> calls=new ArrayList<>();calls.add(()->item.isFanoutVia(null));calls.add(()->item.isFanoutVia(new TreeSet<>()));calls.add(()->item.isFanoutVia(set(ignore)));for(Item.StopConnectionOption stop:new Item.StopConnectionOption[]{Item.StopConnectionOption.NONE,Item.StopConnectionOption.FANOUT_VIA,Item.StopConnectionOption.VIA,null})calls.add(()->list(item.getConnectionItems(stop)));if(item instanceof Trace||item instanceof Plain)calls.add(()->corners(item.getRatsnestCorners()));for(Call c:calls){reads=0;events="";out.add(call(c));out.add(events.isEmpty()?"-":events);}}
  if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[]args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
