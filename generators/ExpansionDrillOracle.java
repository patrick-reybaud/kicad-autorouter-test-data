package app.freerouting.board.searchtree;
import app.freerouting.autoroute.*;
import app.freerouting.autoroute.drill.*;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.maze.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import java.io.*;import java.util.*;
public class ExpansionDrillOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(NegativeArraySizeException e){return "size_error";}catch(IllegalArgumentException e){return "argument_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double v){return Long.toHexString(Double.doubleToLongBits(v));}
 static double dbl(Scanner s){return Double.longBitsToDouble(Long.parseUnsignedLong(s.next(),16));}
 static class Events {
  String events="";boolean active=false;int failure=-1,failAt=1,seen=0,next=1;IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
  int id(Object x){return x==null?0:ids.computeIfAbsent(x,k->next++);}
  void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
  String invoke(Call f){events="";seen=0;active=true;String r=call(f);active=false;return r+"~"+(events.isEmpty()?"-":events);}
  String section(MazeSearchElement e){return id(e)+":"+e.isOccupied+":"+id(e.backtrackDoor)+":"+e.sectionNoOfBacktrackDoor+":"+e.roomRipped+":"+(e.adjustment==null?-1:e.adjustment.ordinal())+":"+e.ripupCost;}
  String drill(ExpansionDrill d){if(d==null)return "N";String r=id(d)+":"+call(()->d.getId())+":"+(d.getShape()==null?"N":d.getShape().getId())+":"+d.firstLayer+":"+d.lastLayer+":"+id(d.roomArr)+":"+d.roomArr.length;for(var room:d.roomArr)r+=","+id(room);r+="/"+d.mazeSearchElementCount();for(int i=0;i<d.mazeSearchElementCount();i++)r+=","+section(d.getMazeSearchElement(i));return r;}
 }
 static TileShape[] shapes={null,new IntBox(0,0,100,100),Simplex.EMPTY,new IntBox(-10,-20,20,40).toIntOctagon()};
 static Point[] points={null,new IntPoint(10,20),new IntPoint(-13,27),Point.getInstance(java.math.BigInteger.valueOf(-5),java.math.BigInteger.valueOf(11),java.math.BigInteger.valueOf(3))};
 static class Room extends CompleteFreeSpaceExpansionRoom {
  Events e;int serial;Room(Events e,int n,int l,int id){super(shapes[1],l,id);this.e=e;serial=n;}
  public int getLayer(){e.touch(100+serial,0);return super.getLayer();}
 }
 static class Tree extends ShapeSearchTree {
  State s;Set<SearchTreeObject> last;
  Tree(State s){super(OrthogonalBoundingDirections.INSTANCE,null,0);this.s=s;}
  public Set<SearchTreeObject> overlappingObjects(ConvexShape shape,int layer){s.touch(700,layer);s.events+="query:"+((TileShape)shape).getId()+",";if(s.mask<0){last=super.overlappingObjects(shape,layer);return last;}last=new TreeSet<>();for(int i:s.order)if((s.mask&(1<<i))!=0)last.add(s.rooms[i]);if((s.mask&256)!=0)last.add(s.item);return last;}
 }
 static class Engine extends AutorouteEngine {
  State s;int mode;Engine(State s,Tree t)throws Exception{super(CompleteRoomOracle.board(),0,false);this.s=s;var field=AutorouteEngine.class.getField("autorouteSearchTree");field.setAccessible(true);field.set(this,t);}
  public Collection<CompleteFreeSpaceExpansionRoom> completeExpansionRoom(IncompleteFreeSpaceExpansionRoom r){int layer=r.getLayer();s.touch(800,layer);s.events+="new:"+(r.getShape()==null)+":"+r.getContainedShape().getId()+":"+r.getDoors().size()+",";if(mode<0)return null;if(mode==0)return new ArrayList<>();if(mode==1)return Arrays.asList(s.rooms[Math.floorMod(layer,8)]);if(mode==2)return Arrays.asList((CompleteFreeSpaceExpansionRoom)null);if(mode==3)return Arrays.asList(s.rooms[0],s.rooms[1]);if(mode==4){var x=new Room(s,20+layer,layer,50+layer);s.created.add(x);return Arrays.asList(x);}throw new IllegalStateException();}
 }
 static class State extends Events {
  Room[] rooms=new Room[8];ArrayList<Room> created=new ArrayList<>();ExpansionDrill[] drills=new ExpansionDrill[4];CompleteExpansionRoom[] old;Tree tree;Engine engine,nullTreeEngine;Item item;int mask;int[] order=new int[8];
  String dump(){String r="";for(int i=1;i<4;i++)r+="D"+i+":"+drill(drills[i]);r+="O";if(old==null)r+="N";else{r+=id(old)+":"+old.length;for(var x:old)r+=","+id(x);}r+="Q";if(tree.last==null)r+="N";else for(var x:tree.last)r+=id(x)+",";return r;}
 }
 static String runDrill(Scanner in)throws Exception{
  State s=new State();for(int i=0;i<8;i++)s.rooms[i]=new Room(s,i,in.nextInt(),in.nextInt());for(int i=0;i<8;i++)s.order[i]=in.nextInt();
  CompleteRoomOracle.State helper=new CompleteRoomOracle.State();var data=new CompleteRoomOracle.Data(helper,1);data.shape=1;data.layer=0;s.item=new CompleteRoomOracle.Plain(data,1);
  s.tree=new Tree(s);for(var r:s.rooms)s.tree.insert(r);s.tree.insert(s.item);s.engine=new Engine(s,s.tree);s.nullTreeEngine=new Engine(s,null);var out=new ArrayList<String>();int n=in.nextInt();
  for(int j=0;j<n;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();double val=dbl(in);out.add(s.invoke(()->switch(op){
   case "D"->{s.drills[a]=new ExpansionDrill(shapes[b],points[c],d,(int)val);yield s.id(s.drills[a]);}
   case "Q"->{s.mask=b;s.engine.mode=c;yield s.drills[a].calculateExpansionRooms(d==0?s.engine:d==1?s.nullTreeEngine:null);}
   case "M"->{var m=s.drills[a].getMazeSearchElement(b);m.isOccupied=true;m.backtrackDoor=s.drills[c];m.sectionNoOfBacktrackDoor=d;m.roomRipped=true;m.adjustment=null;m.ripupCost=37;yield "ok";}
   case "R"->{s.drills[a].reset();yield "ok";}
   case "A"->{s.drills[a].roomArr[b]=c<0?null:s.rooms[c];yield "ok";}
   case "U"->{s.old=s.drills[a].roomArr;yield "ok";}case "V"->{s.old[a]=b<0?null:s.rooms[b];yield "ok";}
   case "H"->{var x=s.drills[a];yield x.getId()+":"+x.getDimension()+":"+(x.otherRoom(s.rooms[0])==null)+":"+x.mazeSearchElementCount()+":"+s.id(x.getMazeSearchElement(b));}
   case "F"->{s.failure=a;s.failAt=b;yield "ok";}
   case "E"->{AutorouteDiagnostic.Sink sink=b==0?null:x->{s.touch(900,x.layer());s.events+="diag:"+x.kind().ordinal()+":"+x.shape().getId()+":"+bits(x.intensity())+",";};s.drills[a].emitDiagnostic(sink,val);yield "ok";}
   default->throw new AssertionError(op);
  }));out.add(s.dump());}return String.join(" ",out);
 }
 static class ViaItem extends Via {
  Events e;Point center;int first,last,count,shape;
  ViaItem(Events e,Scanner in){super(null,null,new int[]{1},1,101,0,FixedState.UNFIXED,false,null);this.e=e;center=points[in.nextInt()];first=in.nextInt();last=in.nextInt();count=in.nextInt();shape=in.nextInt();}
  public Point getCenter(){e.touch(100,0);return center;}public int firstLayer(){e.touch(500,0);return first;}public int lastLayer(){e.touch(501,0);return last;}
  public int treeShapeCount(ShapeTree t){e.touch(200,0);return count;}public TileShape getTreeShape(ShapeTree t,int i){e.touch(300,i);return shapes[shape];}public int shapeLayer(int i){e.touch(400,i);return first+i;}
  public ItemAutorouteInfo getAutorouteInfo(){e.touch(600,0);return super.getAutorouteInfo();}
 }
 static String runVia(Scanner in){Events e=new Events();ViaItem v=new ViaItem(e,in);ExpansionDrill[] saved=new ExpansionDrill[5];CompleteExpansionRoom[][] old=new CompleteExpansionRoom[1][];var out=new ArrayList<String>();int n=in.nextInt();
  for(int j=0;j<n;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();out.add(e.invoke(()->switch(op){
   case "G"->{saved[a]=v.getAutorouteDrillInfo(null);yield e.id(saved[a]);}case "C"->{v.clearAutorouteInfo();yield "ok";}case "D"->{v.clearDerivedData();yield "ok";}
   case "S"->{v.center=points[a];v.first=b;v.last=c;v.count=d;yield "ok";}case "s"->{v.shape=a;yield "ok";}
   case "P"->{var x=saved[a].getMazeSearchElement(b);x.isOccupied=true;x.backtrackDoor=saved[c];x.ripupCost=d;yield "ok";}
   case "R"->{saved[a].reset();yield "ok";}case "U"->{old[0]=saved[a].roomArr;yield "ok";}
   case "F"->{e.failure=a;e.failAt=b;yield "ok";}
   default->throw new AssertionError(op);
  }));String r="I"+e.id(v.getAutorouteInfoPur());for(int i=1;i<5;i++)r+="D"+i+":"+e.drill(saved[i]);r+="O";if(old[0]==null)r+="N";else{r+=e.id(old[0])+":"+old[0].length;for(var x:old[0])r+=","+e.id(x);}out.add(r);}return String.join(" ",out);
 }
 static class Key implements Comparable<Key>{int serial,key;Events e;Key(Events e,int i,int k){this.e=e;serial=i;key=k;}public int compareTo(Key x){e.touch(1000+serial,x.serial);return Integer.compare(key,x.key);}}
 static String runIterator(Scanner in){Events e=new Events();Key[] keys=new Key[16];for(int i=0;i<16;i++)keys[i]=new Key(e,i,in.nextInt());TreeSet<Key> set=new TreeSet<>();@SuppressWarnings("unchecked") Iterator<Key>[] it=new Iterator[1];var out=new ArrayList<String>();int n=in.nextInt();for(int j=0;j<n;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt();out.add(e.invoke(()->switch(op){case "A"->set.add(keys[a]);case "I"->{it[0]=set.iterator();yield "ok";}case "H"->it[0].hasNext();case "N"->it[0].next().serial;case "R"->{it[0].remove();yield "ok";}case "M"->{keys[a].key=b;yield "ok";}default->throw new AssertionError(op);}));String r="S";for(var k:set)r+=k.serial+":"+k.key+",";out.add(r);}return String.join(" ",out);}
 public static void main(String[] args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null){Scanner s=new Scanner(line);String kind=s.next();String out=switch(kind){case "D"->runDrill(s);case "V"->runVia(s);case "I"->runIterator(s);default->throw new AssertionError(kind);};System.out.println(line+" | "+out);}}
}
