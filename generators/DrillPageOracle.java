package app.freerouting.board.searchtree;
import app.freerouting.autoroute.*;
import app.freerouting.autoroute.drill.*;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.maze.*;
import app.freerouting.board.facade.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.Communication;
import app.freerouting.rules.*;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import java.io.*;import java.util.*;import java.lang.reflect.*;
public class DrillPageOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(NegativeArraySizeException e){return "size_error";}catch(IllegalArgumentException e){return "argument_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double v){return Long.toHexString(Double.doubleToLongBits(v));}
 static double dbl(Scanner s){return Double.longBitsToDouble(Long.parseUnsignedLong(s.next(),16));}
 static Object field(Object x,Class<?> c,String name){try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(x);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static class Data {State s;int serial,shape,center;boolean drillable,allowed;Data(State s,int i,Scanner in){this.s=s;serial=i;shape=in.nextInt();center=in.nextInt();drillable=in.nextInt()!=0;allowed=in.nextInt()!=0;}TileShape shape(int i){s.touch(300+serial,i);return s.shapes[shape];}boolean drillable(int n){s.touch(400+serial,n);return drillable;}boolean allowed(){s.touch(500+serial,0);return allowed;}Point center(){s.touch(600+serial,0);return s.points[center];}}
 static class Plain extends CompleteRoomOracle.Plain {
 Data data;Plain(Data d){super(new CompleteRoomOracle.Data(new CompleteRoomOracle.State(),d.serial),1);data=d;}
 public TileShape getTreeShape(ShapeTree t,int i){return data.shape(i);}public boolean isDrillable(int n){return data.drillable(n);}
 }
 static class PinItem extends Pin {
 Data d;PinItem(Data d){super(0,0,new int[]{1},1,100+d.serial,FixedState.UNFIXED,null);this.d=d;}
 public TileShape getTreeShape(ShapeTree t,int i){return d.shape(i);}public boolean isDrillable(int n){return d.drillable(n);}public boolean drillAllowed(){return d.allowed();}public Point getCenter(){return d.center();}
 }
 static LayerStructure layers(int count){Layer[] a=new Layer[count];for(int i=0;i<count;i++)a[i]=new Layer("L"+i,true);return new LayerStructure(a);}
 static class Board extends RoutingBoard {
 State s;int count;Board(State s,IntBox b,int n){this(s,b,layers(n));}Board(State s,IntBox b,LayerStructure l){super(b,l,new PolylineShape[0],0,new BoardRules(l,new ClearanceMatrix(2,l,new String[]{"none","default"})),new Communication());this.s=s;count=l.layers.length;}
 public int getLayerCount(){if(s==null)return super.getLayerCount();s.touch(100,count);return count;}
 public Set<Item> overlappingItems(Area shape,int layer){s.touch(900,layer);s.events+="pins:"+((TileShape)shape).getId()+",";if(s.pinMask<0)return null;var result=new TreeSet<Item>();for(int i=1;i<=4;i++)if((s.pinMask&(1<<i))!=0)result.add(s.items[i]);return result;}
 }
 static class Tree extends ShapeSearchTree {
 State s;Tree(State s){super(OrthogonalBoundingDirections.INSTANCE,null,0);this.s=s;}
 public void overlappingTreeEntries(ConvexShape shape,int layer,Collection<ShapeTree.TreeEntry> entries){s.touch(700,layer);s.events+="obstacles:"+(shape==null?"N":((TileShape)shape).getId())+",";for(int i:s.order)if((s.mask&(1<<i))!=0)entries.add(new ShapeTree.TreeEntry(s.items[i],s.index));if((s.mask&32)!=0)entries.add(new ShapeTree.TreeEntry(null,0));}
 public Set<SearchTreeObject> overlappingObjects(ConvexShape shape,int layer){s.touch(710,layer);s.events+="rooms:"+((TileShape)shape).getId()+",";return new TreeSet<>();}
 }
 static class Engine extends AutorouteEngine {
 State s;int net=1,mode=0;Engine(State s,Board b,Tree t)throws Exception{super(b,0,false);this.s=s;var f=AutorouteEngine.class.getField("autorouteSearchTree");f.setAccessible(true);f.set(this,t);}
 public int getNetNumber(){s.touch(800,net);return net;}
 public Collection<CompleteFreeSpaceExpansionRoom> completeExpansionRoom(IncompleteFreeSpaceExpansionRoom r){int layer=r.getLayer();s.touch(810,layer);s.events+="complete:"+r.getContainedShape().getId()+",";if(mode==-1)return null;if(mode==1)return new ArrayList<>();if(mode==2&&layer==1)return new ArrayList<>();if(mode==3)return Arrays.asList((CompleteFreeSpaceExpansionRoom)null);var room=new CompleteFreeSpaceExpansionRoom(r.getContainedShape(),layer,1000+s.rooms.size());s.rooms.add(room);return mode==4?Arrays.asList(room,room):Arrays.asList(room);}
 }
 static class Stop implements Stoppable {State s;int count;Stop(State s){this.s=s;}public void requestStop(){}public boolean isStopRequested(){s.touch(720,++count);return s.stopAt>=0&&count>=s.stopAt;}}
 static class State {
 TileShape[] shapes={null,new IntBox(0,0,100,100),new IntBox(20,20,30,30),new IntBox(60,60,70,70),new IntBox(100,0,100,100),new IntBox(-20,-20,120,120),Simplex.EMPTY,new IntBox(-20,30,25,80).toIntOctagon()};
 Point[] points={null,new IntPoint(10,10),new IntPoint(50,50),new IntPoint(100,50),new IntPoint(90,90),new IntPoint(25,25)};
 Item[] items=new Item[5];Data[] data=new Data[5];int[] order=new int[4];int mask,index,pinMask,stopAt=-1;Board board;Tree tree;Engine engine;Stop stop;DrillPage page;DrillPageArray array;Collection<ExpansionDrill> old;Collection<DrillPage> selected;ArrayList<CompleteFreeSpaceExpansionRoom> rooms=new ArrayList<>();
 String events="";boolean active=false;int failure=-1,failAt=1,seen=0,next=1;IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
 int id(Object x){return x==null?0:ids.computeIfAbsent(x,k->next++);}void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
 String invoke(Call f){events="";seen=0;active=true;String r=call(f);active=false;return r+"~"+(events.isEmpty()?"-":events);}
 String section(MazeSearchElement m){return id(m)+":"+m.isOccupied+":"+id(m.backtrackDoor)+":"+m.ripupCost;}
 String drills(Collection<ExpansionDrill> a){if(a==null)return "N";String r=id(a)+":"+a.size();for(var d:a){if(d==null){r+=",N";continue;}r+=","+id(d)+":"+d.getShape().getId()+":"+d.location.getId()+":"+d.firstLayer+":"+d.lastLayer;for(var x:d.roomArr)r+="/"+id(x);for(int i=0;i<d.mazeSearchElementCount();i++)r+=";"+section(d.getMazeSearchElement(i));}return r;}
 String page(DrillPage p){if(p==null)return "N";String r=id(p)+":"+call(()->p.getId())+":"+(p.getShape()==null?"N":p.getShape().getId())+":"+p.getDimension()+":"+(p.otherRoom(null)==null)+":"+p.mazeSearchElementCount();for(int i=0;i<p.mazeSearchElementCount();i++)r+=";"+section(p.getMazeSearchElement(i));@SuppressWarnings("unchecked")Collection<ExpansionDrill> a=(Collection<ExpansionDrill>)field(p,DrillPage.class,"drills");return r+"/"+drills(a);}
 String dump(){String r="P"+page(page)+"O"+drills(old)+"A";if(array==null)r+="N";else{DrillPage[][] pages=(DrillPage[][])field(array,DrillPageArray.class,"pages");r+=id(array)+":"+pages.length;for(var row:pages){r+="["+row.length;for(var p:row)r+="|"+page(p);r+="]";}}r+="S";if(selected==null)r+="N";else{r+=id(selected)+":"+selected.size();for(var p:selected)r+=","+id(p);}return r;}
 }
 static String run(String line)throws Exception{Scanner in=new Scanner(line);in.next();State s=new State();int layers=in.nextInt(),bound=in.nextInt();s.board=new Board(s,(IntBox)s.shapes[bound],layers);for(int i=1;i<=4;i++){int kind=in.nextInt();var d=new Data(s,i,in);s.data[i]=d;s.items[i]=kind==0?new Plain(d):new PinItem(d);}for(int i=0;i<4;i++)s.order[i]=in.nextInt();s.tree=new Tree(s);s.engine=new Engine(s,s.board,s.tree);s.stop=new Stop(s);var out=new ArrayList<String>();int n=in.nextInt();
 for(int j=0;j<n;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();double value=dbl(in);out.add(s.invoke(()->switch(op){
 case "P"->{s.page=new DrillPage(a==0?null:(IntBox)s.shapes[a],b==0?null:s.board);yield s.id(s.page);}
 case "G"->{s.engine.net=a;s.engine.mode=b;s.old=s.page.getDrills(c==0?null:s.engine,d!=0);yield s.id(s.old);}
 case "I"->{s.page.invalidate();yield "ok";}case "R"->{s.page.reset();yield "ok";}
 case "M"->{var m=s.page.getMazeSearchElement(a);m.isOccupied=true;m.backtrackDoor=s.page;m.ripupCost=b;yield "ok";}
 case "m"->{var drill=new ArrayList<>(s.old).get(a);var m=drill.getMazeSearchElement(b);m.isOccupied=true;m.backtrackDoor=s.page;m.ripupCost=c;yield "ok";}
 case "L"->{if(a==0)s.old.clear();else s.old.add(null);yield "ok";}
 case "S"->{s.mask=a;s.pinMask=b;s.index=c;s.stopAt=d;s.stop.count=0;s.engine.stoppableThread=d==-2?null:s.stop;yield "ok";}
 case "s"->{s.data[a].shape=b;s.data[a].center=c;s.data[a].drillable=(d&1)!=0;s.data[a].allowed=(d&2)!=0;yield "ok";}
 case "B"->{s.board.count=a;yield "ok";}
 case "A"->{s.array=new DrillPageArray(a==0?null:s.board,b);yield s.id(s.array);}
 case "Q"->{s.selected=s.array.overlappingPages(s.shapes[a]);yield s.id(s.selected);}
 case "q"->{s.page=new ArrayList<>(s.selected).get(a);yield s.id(s.page);}
 case "V"->{s.array.invalidate(s.shapes[a]);yield "ok";}case "Z"->{s.array.reset();yield "ok";}
 case "E"->{AutorouteDiagnostic.Sink sink=a==0?null:x->{s.touch(950,x.layer());s.events+="diag:"+x.kind().ordinal()+":"+x.shape().getId()+":"+bits(x.intensity())+",";};if(b==0)s.page.emitDiagnostics(sink,value);else s.array.emitDiagnostics(sink,value);yield "ok";}
 case "F"->{s.failure=a;s.failAt=b;yield "ok";}default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[] args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null)System.out.println(line+" | "+run(line));}
}
