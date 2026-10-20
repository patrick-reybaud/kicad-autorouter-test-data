package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.*;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import app.freerouting.util.TextManager;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class NetsOracle {
 static String events;static NetClass[] classes;static IdentityHashMap<Net,Integer> ids;
 static String[] names={"GND","gnd","VCC","",null,"I","\u0131","\u0130","\u03a3","\u03c2","\ud801\udc00","\ud801\udc28","\ud800","\u00df","ss","net#1"};
 static Locale[] locales={Locale.ENGLISH,Locale.FRENCH,Locale.GERMAN,Locale.JAPANESE};
 interface Call{Object run() throws Exception;}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}catch(Exception e){throw new RuntimeException(e);}}
 static String hex(String s){if(s==null)return "null";if(s.isEmpty())return "-";StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++)b.append(String.format(Locale.ROOT,"%04x",(int)s.charAt(i)));return b.toString();}
 static int cls(NetClass c){return c==null?-1:c==classes[0]?0:c==classes[1]?1:2;}
 static String net(Net n){if(n==null)return "null";return ids.computeIfAbsent(n,k->ids.size()+1)+":"+n.netNumber+":"+n.subnetNumber+":"+hex(n.name)+":"+n.containsPlane()+":"+cls(n.getNetClass())+":"+hex(n.toString());}
 static String nets(Collection<Net> n){String s=""+n.size();for(Net v:n)s+="/"+net(v);return s;}
 static String items(Collection<? extends Item> n){if(n==null)return "null";String s=""+n.size();for(Item v:n)s+="/"+(v==null?"null":v.getId());return s;}
 static class Rules extends BoardRules {
  boolean active;int choice;Rules(LayerStructure l,ClearanceMatrix m){super(l,m);}
  public NetClass getDefaultNetClass(){if(!active)return super.getDefaultNetClass();events+="D,";if(choice==-2)throw new IllegalStateException();return choice==-1?null:classes[choice];}
 }
 static class Board extends BasicBoard {
  int provider;List<Item> supplied=new ArrayList<>();
  Board(LayerStructure l,Rules r){super(new IntBox(-1000,-1000,1000,1000),l,new PolylineShape[0],0,r,new Communication());Item outline=getOutline();if(outline!=null)itemList.delete(outline);}
  public Collection<Item> getConnectableItems(int n){events+="G"+n+",";if(provider==1)return null;if(provider==2)return supplied;if(provider==3)throw new IllegalStateException();return super.getConnectableItems(n);}
 }
 static Board[] boards(){var l=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var m=new ClearanceMatrix(2,l,new String[]{"none","default"});classes=new NetClass[]{new NetClass("a",l,m,false),new NetClass("b",l,m,false)};Board[] result=new Board[2];for(int i=0;i<2;i++){Rules r=new Rules(l,m);result[i]=new Board(l,r);r.active=true;r.choice=i;}return result;}
 static class Plain extends Item {
  boolean routable;Plain(int[] n,int id,boolean r,BasicBoard b){super(n,1,id,0,FixedState.UNFIXED,b);routable=r;}
  public boolean isRoutable(){events+="R"+getId()+",";return routable;}
  public Item copy(int id){return new Plain(netNumbers,id,routable,board);}public boolean isOnLayer(int i){return i==0;}public IntBox boundingBox(){return IntBox.EMPTY;}public int tileShapeCount(){return 0;}public int firstLayer(){return 0;}public int lastLayer(){return 0;}public int shapeLayer(int i){return 0;}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return new TileShape[0];}public boolean isObstacle(Item i){return false;}public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double f,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public boolean isSelectedByFilter(ItemSelectionFilter f){return false;}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static class Connected extends Plain implements Connectable {
  Connected(int[]n,int id,boolean r,BasicBoard b){super(n,id,r,b);}public Point[] getRatsnestCorners(){return new Point[0];}public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){return null;}
 }
 static class TestTrace extends PolylineTrace {
  double length;TestTrace(int[]n,int id,FixedState fixed,double len,BasicBoard b){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(10,0)}),0,1,n,1,id,0,fixed,b);length=len;}
  public double getLength(){events+="L"+getId()+",";if(Double.doubleToRawLongBits(length)==0x7ff0000000000001L)throw new IllegalStateException();return length;}
 }
 static class NotItem implements UndoableObjects.Storable {public Object clone(){return this;}public int compareTo(Object o){return o==this?0:1;}}
 @SuppressWarnings("unchecked") static java.util.Vector<Net> raw(Nets n)throws Exception{Field f=Nets.class.getDeclaredField("nets");f.setAccessible(true);return (java.util.Vector<Net>)f.get(n);}
 static String registry(Scanner in)throws Exception{
  Board[] b=boards();Nets ns=new Nets();ns.setBoard(b[0]);ArrayList<String> out=new ArrayList<>();int steps=in.nextInt();
  for(int i=0;i<steps;i++){int op=in.nextInt(),a=in.nextInt(),c=in.nextInt(),d=in.nextInt();events="";out.add(call(()->switch(op){
   case 0->net(ns.add(names[a],c,d!=0));
   case 1->{String prefix=new TextManager(NetClasses.class,locales[a]).getText("net#");yield hex(prefix)+"/"+net(ns.newNet(locales[a]));}
   case 2->{ns.setBoard(a==2?null:b[a]);yield "ok";}
   case 3->{((Rules)b[a].rules).choice=c;yield "ok";}
   case 4->{ns.get(a).setClass(c<0?null:classes[c]);yield net(ns.get(a));}
   case 5->{ns.get(a).setContainsPlane(c!=0);yield net(ns.get(a));}
   case 6->net(ns.get(names[a],c))+"/"+nets(ns.get(names[a]));
   case 7->net(ns.get(a));
   case 8->ns.get(a).compareTo(ns.get(c));
   case 9->net(new Net(names[a],c,d,d==99?null:ns,true));
   case 10->{raw(ns).set(a,c==0?null:new Net(names[d],1,c,ns,false));yield "ok";}
   default->throw new AssertionError();}));out.add(ns.maxNetNumber()+":"+(ns.getBoard()==null?2:ns.getBoard()==b[0]?0:1)+":"+(events.isEmpty()?"-":events));
  }
  return String.join(" ",out);
 }
 static String number(double d){return Long.toHexString(Double.doubleToLongBits(d));}
 static String query(Scanner in)throws Exception{
  Board[] bs=boards();Board b=bs[0];Nets ns=new Nets();ns.setBoard(b);int flags=in.nextInt(),provider=in.nextInt(),number=in.nextInt(),count=in.nextInt();Net n=new Net("q",1,number,ns,false);ArrayList<Item> saved=new ArrayList<>();
  for(int i=0;i<count;i++){int type=in.nextInt(),id=in.nextInt(),fixed=in.nextInt(),route=in.nextInt(),nc=in.nextInt();int[] nn=new int[nc];for(int j=0;j<nc;j++)nn[j]=in.nextInt();double length=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));FixedState f=fixed<0?null:FixedState.values()[fixed];Item item=switch(type){case 0->new Plain(nn,id,route!=0,b);case 1->new Connected(nn,id,route!=0,b);case 2->new Pin(0,0,nn,1,id,f,b);case 3->new Via(null,new IntPoint(0,0),nn,1,id,0,f,false,b);default->new TestTrace(nn,id,f,length,b);};if((flags&4)!=0&&i%3==0)item.netNumbers=null;b.itemList.insert(item);saved.add(item);}
  b.supplied.addAll(saved);Collections.reverse(b.supplied);b.supplied.add(null);b.provider=provider;if((flags&1)!=0)ns.setBoard(null);if((flags&8)!=0){Field field=BasicBoard.class.getDeclaredField("itemList");field.setAccessible(true);field.set(b,new UndoableObjects());b.itemList.insert(new NotItem());}
  ArrayList<String> out=new ArrayList<>();
  for(int phase=0;phase<3;phase++){
   if(phase==1){b.itemList.generateSnapshot();if((flags&12)==0)for(int i=0;i<saved.size();i+=2)b.itemList.delete(saved.get(i));}
   if(phase==2)b.itemList.undo(null,null);
   Call[] calls={()->items(n.getTerminalItems()),()->items(n.getPins()),()->items(n.getItems()),()->number(n.getTraceLength()),()->n.getViaCount(),()->items(b.getConnectableItems(number))};
   for(Call c:calls){events="";out.add(call(c)+":"+(events.isEmpty()?"-":events));}
  }
  return String.join(" ",out);
 }
 public static void main(String[] args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;){events="";ids=new IdentityHashMap<>();Scanner in=new Scanner(s);String mode=in.next();String result=mode.equals("R")?registry(in):query(in);if(in.hasNext())throw new AssertionError();System.out.println(s+" | "+result);}}
}
