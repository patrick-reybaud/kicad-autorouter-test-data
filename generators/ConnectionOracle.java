package app.freerouting.board.model.items;
import app.freerouting.autoroute.*;
import app.freerouting.autoroute.path.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.actions.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.datastructures.*;
import java.io.*;import java.util.*;
public class ConnectionOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double v){return Long.toHexString(Double.doubleToLongBits(v));}
 static double dbl(Scanner s){return Double.longBitsToDouble(Long.parseUnsignedLong(s.next(),16));}
 static class Data {
 State s;int serial,id,first,last,filter;boolean route,nullContacts;double length;
 Data(State s,int i,Scanner in){this.s=s;serial=i;id=in.nextInt();route=in.nextInt()!=0;first=in.nextInt();last=in.nextInt();nullContacts=in.nextInt()!=0;filter=in.nextInt();length=dbl(in);}
 boolean route(){s.touch(100+serial,0);return route;}
 Set<Item> contacts(){s.touch(200+serial,0);if(nullContacts)return null;var result=new TreeSet<Item>();for(int i=1;i<=8;i++)if(s.edges[serial][i])result.add(s.items[i]);return result;}
 Point point(Item other){int i=Arrays.asList(s.items).indexOf(other);s.touch(300+serial,i);return i<0?null:s.points[s.locations[serial][i]];}
 int first(){s.touch(400+serial,0);return first;}int last(){s.touch(500+serial,0);return last;}
 Set<Item> filter(Point point){s.touch(600+serial,point==null?0:point.getId());if(filter==-2)return null;if(filter>=0){var forced=new TreeSet<Item>();for(int i=1;i<=filter;i++)forced.add(s.items[i]);return forced;}var out=new TreeSet<Item>();for(int i=1;i<=8;i++)if(s.edges[serial][i]&&s.points[s.locations[serial][i]]!=null&&s.points[s.locations[serial][i]].equals(point))out.add(s.items[i]);return out;}
 }
 static class Plain extends Item{Data d;Plain(Data d){super(new int[]{1},1,d.id,0,FixedState.UNFIXED,null);this.d=d;}
 public boolean isRoutable(){return d.route();}public Set<Item> getNormalContacts(){return d.contacts();}public Point normalContactPoint(Item other){return d.point(other);}public int firstLayer(){return d.first();}public int lastLayer(){return d.last();}
 public ItemAutorouteInfo getAutorouteInfo(){d.s.touch(700+d.serial,0);return super.getAutorouteInfo();}
 public Item copy(int i){throw new UnsupportedOperationException();}public boolean isObstacle(Item i){return true;}public int tileShapeCount(){return 0;}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return new TileShape[0];}public boolean isOnLayer(int l){return true;}public int shapeLayer(int i){return 0;}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public void translateBy(app.freerouting.geometry.planar.Vector v){}public void turn90Degree(int f,IntPoint p){}public void rotateApprox(double a,FloatPoint p){}public void changePlacementSide(IntPoint p){}public IntBox boundingBox(){return new IntBox(0,0,10,10);}public boolean isSelectedByFilter(ItemSelectionFilter f){return true;}public void printInfo(ItemInfoPrinter p,Locale l){}
 }
 static class TraceItem extends PolylineTrace{Data d;TraceItem(Data d){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(10,0)}),0,1,new int[]{1},1,d.id,0,FixedState.UNFIXED,null);this.d=d;}
 public boolean isRoutable(){return d.route();}public Set<Item> getNormalContacts(){return d.contacts();}public Point normalContactPoint(Item other){return d.point(other);}public int firstLayer(){return d.first();}public int lastLayer(){return d.last();}
 public ItemAutorouteInfo getAutorouteInfo(){d.s.touch(700+d.serial,0);return super.getAutorouteInfo();}
 public Set<Item> getNormalContacts(Point p,boolean ignore){return d.filter(p);}public double getLength(){d.s.touch(800+d.serial,0);return d.length;}
 }
 static class State {
 Point[] points={null,new IntPoint(0,0),new IntPoint(100,0),new IntPoint(200,100),new IntPoint(300,0),new IntPoint(500,200),new IntPoint(-100,-200),Point.getInstance(java.math.BigInteger.valueOf(-5),java.math.BigInteger.valueOf(11),java.math.BigInteger.valueOf(3))};
 Item[] items=new Item[9];Data[] data=new Data[9];boolean[][] edges=new boolean[9][9];int[][] locations=new int[9][9];Connection[] connections=new Connection[5];ItemAutorouteInfo[] infos=new ItemAutorouteInfo[5];Set<Item> old;
 String events="";boolean active=false;int failure=-1,failAt=1,seen=0,next=1;IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
 int id(Object x){return x==null?0:ids.computeIfAbsent(x,k->next++);}void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
 String invoke(Call f){events="";seen=0;active=true;String r=call(f);active=false;return r+"~"+(events.isEmpty()?"-":events);}
 String list(Set<Item> x){if(x==null)return "N";String r=id(x)+":"+x.size();for(var item:x)r+=","+Arrays.asList(items).indexOf(item);return r;}
 String info(ItemAutorouteInfo x){return x==null?"N":id(x)+":"+id(x.getPrecalculatedConnection());}
 String dump(){String r="";for(int i=1;i<=8;i++)r+="I"+i+":"+info(items[i].getAutorouteInfoPur());for(int i=1;i<=4;i++){r+="S"+i+":"+info(infos[i])+"C"+i+":";var c=connections[i];if(c==null)r+="N";else r+=id(c)+":"+(c.startPoint==null?"N":c.startPoint.getId())+":"+c.startLayer+":"+(c.endPoint==null?"N":c.endPoint.getId())+":"+c.endLayer+":"+list(c.itemList);}return r+"O"+list(old);}
 }
 static String run(String line){Scanner in=new Scanner(line);in.next();State s=new State();for(int i=1;i<=8;i++){int kind=in.nextInt();var d=new Data(s,i,in);s.data[i]=d;s.items[i]=kind==0?new Plain(d):new TraceItem(d);}for(int i=2;i<=8;i++){int parent=in.nextInt(),point=in.nextInt();s.edges[i][parent]=s.edges[parent][i]=true;s.locations[i][parent]=s.locations[parent][i]=point;}var out=new ArrayList<String>();out.add(s.dump());int n=in.nextInt();
 for(int j=0;j<n;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();double value=dbl(in);out.add(s.invoke(()->switch(op){
 case "G"->{s.connections[a]=Connection.get(s.items[b]);yield s.id(s.connections[a]);}case "T"->bits(s.connections[a].traceLength());case "D"->bits(s.connections[a].getDetour());
 case "C"->{s.items[a].clearAutorouteInfo();yield "ok";}case "H"->{s.infos[a]=s.items[b].getAutorouteInfo();yield s.id(s.infos[a]);}case "N"->{s.infos[a].setPrecalculatedConnection(s.connections[b]);yield "ok";}
 case "U"->{s.old=s.connections[a].itemList;yield "ok";}case "A"->s.old.add(s.items[a]);case "R"->s.old.remove(s.items[a]);case "X"->{s.old.clear();yield "ok";}
 case "S"->{s.data[a].route=b!=0;s.data[a].nullContacts=c!=0;s.data[a].filter=d;s.data[a].length=value;yield "ok";}
 case "L"->{s.data[a].first=b;s.data[a].last=c;yield "ok";}case "P"->{s.locations[a][b]=s.locations[b][a]=c;yield "ok";}
 case "F"->{s.failure=a;s.failAt=b;yield "ok";}default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[] args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null)System.out.println(line+" | "+run(line));}
}
