package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.util.*;
public class ContactQueriesOracle {
 static String events;static ArrayList<Item> items;
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}}
 static Point point(int n){return switch(n){case 0->null;case 1->new IntPoint(0,0);case 2->new IntPoint(20,0);case 3->new IntPoint(20,20);case 4->new IntPoint(0,20);case 5->new IntPoint(10,0);case 6->new IntPoint(1,0);default->new IntPoint(50,50);};}
 static TileShape tile(int n){return switch(n){case 0->null;case 1->new IntBox(-2,-2,22,2);case 2->new IntBox(18,-2,22,22);case 3->new IntBox(-2,-2,2,2);case 4->new IntBox(-5,-5,5,5);case 5->new IntBox(-10,-10,30,30);case 6->new IntBox(48,48,52,52);default->new IntOctagon(-5,-5,5,5,-7,7,-7,7);};}
 static Area area(int n){if(n==0)return null;if(n==3)return new PolylineArea(new IntBox(-10,-10,30,30),new PolylineShape[]{new IntBox(-2,-2,2,2)});return tile(n);}
 static class Data {int index,first,last,a,b;TileShape[] shapes;Data(int i,int f,int l,int a,int b,int s,int t){index=i;first=f;last=l;this.a=a;this.b=b;shapes=t<0?new TileShape[]{tile(s)}:new TileShape[]{tile(s),tile(t)};}}
 static class Plain extends Item {
  Data d;Plain(Data d,int id,int[] nets,BasicBoard board){super(nets,1,id,0,FixedState.UNFIXED,board);this.d=d;}
  public int firstLayer(){return d.first;}public int lastLayer(){return d.last;}public int shapeLayer(int i){return i==0?d.first:d.last;}
  public int tileShapeCount(){return d.shapes.length;}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return d.shapes;}
  public Item copy(int id){throw new UnsupportedOperationException();}public boolean isObstacle(Item o){return false;}public boolean isOnLayer(int l){return l>=d.first&&l<=d.last;}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}public IntBox boundingBox(){return new IntBox(-10,-10,60,60);}public boolean isSelectedByFilter(ItemSelectionFilter f){throw new UnsupportedOperationException();}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static class Connect extends Plain implements Connectable {public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){throw new UnsupportedOperationException();}Connect(Data d,int id,int[] n,BasicBoard b){super(d,id,n,b);}}
 static class TraceItem extends PolylineTrace {
  Data d;TraceItem(Data d,int id,int[] nets,BasicBoard b){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(20,0)}),d.first,1,nets,1,id,0,FixedState.UNFIXED,b);this.d=d;}
  public Point firstCorner(){events+="f"+d.index+",";return point(d.a);}public Point lastCorner(){events+="l"+d.index+",";return point(d.b);}
  public int tileShapeCount(){return d.shapes.length;}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return d.shapes;}
 }
 static class Drill extends DrillItem {
  Data d;Drill(Data d,int id,int[] nets,BasicBoard b){super(point(d.a),nets,1,id,0,FixedState.UNFIXED,b);this.d=d;}
  public int firstLayer(){return d.first;}public int lastLayer(){return d.last;}public int shapeLayer(int i){return i==0?d.first:d.last;}public int tileShapeCount(){return d.shapes.length;}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return d.shapes;}
  public Shape getShape(int i){return d.shapes[i];}public Padstack getPadstack(){throw new UnsupportedOperationException();}public Item copy(int id){throw new UnsupportedOperationException();}public boolean isObstacle(Item o){return false;}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public boolean isSelectedByFilter(ItemSelectionFilter f){throw new UnsupportedOperationException();}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static class Plane extends ConductionArea {
  Data d;Plane(Data d,int id,int[] nets,BasicBoard b){super(area(d.a),d.first,app.freerouting.geometry.planar.Vector.ZERO,0,false,nets,1,id,d.b,null,true,FixedState.UNFIXED,b);this.d=d;}
  public Area getArea(){events+="a"+d.index+",";return area(d.a);}
  public TileShape getTileShape(int i){return d.shapes[i];}
  public int tileShapeCount(){return d.shapes.length;}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return d.shapes;}
 }
 static String list(Collection<Item> s){if(s==null)return "null";String v=""+s.size();for(var i:s)v+=","+items.indexOf(i);return v;}
 static String shape(TileShape s){return s==null?"null":s.getId()+":"+s.dimension();}
 static String sortedEvents;
 static class Key implements Comparable<Key>{final int id,key;Key(int i,int k){id=i;key=k;}public int compareTo(Key other){sortedEvents+=id+">"+other.id+",";return key-other.key;}}
 static String keys(Collection<Key> a){String v=""+a.size();for(var k:a)v+=","+k.id;return v;}
 static String sorted(Scanner in){int serial=0;TreeSet<Key> first=new TreeSet<>(),second=new TreeSet<>(),result=new TreeSet<>();ArrayList<Key> tail=new ArrayList<>();sortedEvents="";
  for(int j=0;j<4;j++){int n=in.nextInt();for(int i=0;i<n;i++){Key k=new Key(serial++,in.nextInt());if(j==0)result.add(k);else if(j==1)first.add(k);else if(j==2)second.add(k);else tail.add(k);}}
  sortedEvents="";result.addAll(first);String a=keys(result);result.addAll(second);String b=keys(result);for(var k:tail)result.add(k);return a+" "+b+" "+keys(result)+" "+(sortedEvents.isEmpty()?"-":sortedEvents);
 }
 static String run(String input){Scanner in=new Scanner(input);if(in.next().equals("S"))return sorted(in);int flags=in.nextInt(),n=in.nextInt();events="";
  var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("I",true),new Layer("B",true)});var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});var rules=new BoardRules(layers,matrix);BasicBoard board=new BasicBoard(new IntBox(-100,-100,100,100),layers,new PolylineShape[0],0,rules,new Communication());var outline=board.getOutline();if(outline!=null){board.searchTreeManager.remove(outline);board.itemList.delete(outline);}items=new ArrayList<>();items.add(null);
  for(int i=1;i<=n;i++){int type=in.nextInt(),id=in.nextInt(),net=in.nextInt(),first=in.nextInt(),last=in.nextInt(),a=in.nextInt(),b=in.nextInt(),s=in.nextInt(),t=in.nextInt();Data d=new Data(i,first,last,a,b,s,t);int[] nets=net<0?null:net==0?new int[0]:new int[]{net};items.add(switch(type){case 1->new Plain(d,id,nets,board);case 2->new TraceItem(d,id,nets,board);case 3->new Drill(d,id,nets,board);case 4->new Plane(d,id,nets,board);case 5->new Connect(d,id,nets,board);default->throw new AssertionError();});}
  for(int i=1;i<items.size();i++)board.searchTreeManager.insert(items.get(i));
  if((flags&1)!=0)for(int i=1;i<items.size();i++)items.get(i).board=null;
  ArrayList<String> out=new ArrayList<>();int count=in.nextInt();
  for(int j=0;j<count;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();events="";out.add(call(()->switch(op){
   case "A"->list(items.get(a).getAllContacts());case "L"->list(items.get(a).getAllContacts(b));case "N"->list(items.get(a).getNormalContacts());case "C"->items.get(a).isConnected();case "c"->items.get(a).isConnectedOnLayer(b);case "G"->list(items.get(a).getConnectedSet(b,c!=0));
   case "S"->list(((Trace)items.get(a)).getStartContacts());case "E"->list(((Trace)items.get(a)).getEndContacts());case "P"->list(((Trace)items.get(a)).getNormalContacts(point(b),c!=0));case "T"->items.get(a).isTail();
   case "Q"->shape(((ConductionArea)items.get(a)).getTraceConnectionShape(c==0?board.searchTreeManager.getDefaultTree():null,b));
   default->throw new AssertionError(op);
  }));out.add(events.isEmpty()?"-":events);}
  if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[] args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
