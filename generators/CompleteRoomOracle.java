package app.freerouting.board.searchtree;
import app.freerouting.autoroute.*;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.maze.*;
import app.freerouting.board.facade.*;
import app.freerouting.board.actions.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.Communication;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.rules.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.datastructures.*;
import java.io.*;
import java.util.*;
public class CompleteRoomOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double v){return Long.toHexString(Double.doubleToLongBits(v));}
 static double dbl(Scanner s){return Double.longBitsToDouble(Long.parseUnsignedLong(s.next(),16));}
 static class Data{State s;int serial,shape,connection,layer;boolean obstacle;Data(State s,int n){this.s=s;serial=n;}TileShape shape(int i){s.touch(300+serial,i);return s.shapes[shape];}int layer(int i){s.touch(400+serial,i);return layer;}boolean obstacle(int n){s.touch(500+serial,n);return obstacle;}}
 static class Plain extends Item{
  Data d;Plain(Data d,int net){super(new int[]{net},1,100+d.serial,0,FixedState.UNFIXED,null);this.d=d;}
  public int treeShapeCount(ShapeTree t){return 1;}public TileShape getTreeShape(ShapeTree t,int i){return d.shape(i);}public int shapeLayer(int i){return d.layer(i);}public boolean isTraceObstacle(int n){return d.obstacle(n);}
  public Item copy(int i){throw new UnsupportedOperationException();}public boolean isObstacle(Item i){return true;}public int tileShapeCount(){return 1;}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return new TileShape[]{d.s.shapes[d.shape]};}public boolean isOnLayer(int l){return true;}public int firstLayer(){return d.layer;}public int lastLayer(){return d.layer;}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public void translateBy(app.freerouting.geometry.planar.Vector v){}public void turn90Degree(int f,IntPoint p){}public void rotateApprox(double a,FloatPoint p){}public void changePlacementSide(IntPoint p){}public IntBox boundingBox(){return new IntBox(0,0,10,10);}public boolean isSelectedByFilter(ItemSelectionFilter f){return true;}public void printInfo(ItemInfoPrinter p,Locale l){}
 }
 static class TraceItem extends PolylineTrace{
  Data d;TraceItem(Data d,int net){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(10,0)}),0,1,new int[]{net},1,100+d.serial,0,FixedState.UNFIXED,null);this.d=d;}
  public int treeShapeCount(ShapeTree t){return 1;}public TileShape getTreeShape(ShapeTree t,int i){return d.shape(i);}public int shapeLayer(int i){return d.layer(i);}public boolean isTraceObstacle(int n){return d.obstacle(n);}
  public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){d.s.touch(600+d.serial,i);return d.connection<0?super.getTraceConnectionShape(t,i):d.s.shapes[d.connection];}
 }
 static class ViaItem extends Via{
  Data d;ViaItem(Data d,int net){super(null,new IntPoint(0,0),new int[]{net},1,100+d.serial,0,FixedState.UNFIXED,false,null);this.d=d;}
  public int treeShapeCount(ShapeTree t){return 1;}public TileShape getTreeShape(ShapeTree t,int i){return d.shape(i);}public int shapeLayer(int i){return d.layer(i);}public boolean isTraceObstacle(int n){return d.obstacle(n);}
  public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){d.s.touch(600+d.serial,i);return d.connection<0?super.getTraceConnectionShape(t,i):d.s.shapes[d.connection];}
 }
 static class Area extends ConductionArea{
  Data d;Area(Data d,int net){super(null,0,app.freerouting.geometry.planar.Vector.ZERO,0,false,new int[]{net},1,100+d.serial,0,null,false,FixedState.UNFIXED,null);this.d=d;}
  public int treeShapeCount(ShapeTree t){return 1;}public TileShape getTreeShape(ShapeTree t,int i){return d.shape(i);}public int shapeLayer(int i){return d.layer(i);}public boolean isTraceObstacle(int n){return d.obstacle(n);}
  public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){d.s.touch(600+d.serial,i);return d.connection<0?super.getTraceConnectionShape(t,i):d.s.shapes[d.connection];}
 }
 static class Room extends CompleteFreeSpaceExpansionRoom{
  State s;int serial;ShapeTree.Leaf[] savedEntries;
  Room(State s,int serial,TileShape shape,int layer,int id){super(shape,layer,id);this.s=s;this.serial=serial;}
  public TileShape getShape(){s.touch(100+serial,0);return super.getShape();}public int getLayer(){s.touch(200+serial,0);return super.getLayer();}
  public void setSearchTreeEntries(ShapeTree.Leaf[] e,ShapeTree t){savedEntries=e;super.setSearchTreeEntries(e,t);}
 }
 static class Bad implements ShapeTree.Storable{
  public int treeShapeCount(ShapeTree t){return 0;}public TileShape getTreeShape(ShapeTree t,int i){return null;}public void setSearchTreeEntries(ShapeTree.Leaf[] l,ShapeTree t){}public int compareTo(Object o){return 0;}
 }
 static class Tree extends ShapeSearchTree{
  State s;Tree(State s){super(OrthogonalBoundingDirections.INSTANCE,null,0);this.s=s;}
  public void overlappingTreeEntries(ConvexShape shape,int layer,int[] nets,Collection<ShapeTree.TreeEntry> out){s.touch(700,layer);s.events+="query:"+(shape==null?"N":((TileShape)shape).getId())+":"+nets[0]+",";if(s.mask<0){super.overlappingTreeEntries(shape,layer,nets,out);return;}Object[] objects={s.current,s.rooms[3-s.current.serial],s.items[1],s.items[2],s.items[3],s.items[4],null,s.bad};for(int i=0;i<objects.length;i++)if((s.mask&(1<<i))!=0)out.add(new ShapeTree.TreeEntry((ShapeTree.Storable)objects[i],s.index));}
 }
 static class Engine extends AutorouteEngine{
  State s;int net;Engine(State s,Tree t)throws Exception{super(board(),0,false);this.s=s;var field=AutorouteEngine.class.getField("autorouteSearchTree");field.setAccessible(true);field.set(this,t);}
  public int getNetNumber(){s.touch(800,net);return net;}
 }
 static RoutingBoard board(){LayerStructure l=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});BoardRules r=new BoardRules(l,new ClearanceMatrix(2,l,new String[]{"none","default"}));return new RoutingBoard(new IntBox(-200,-200,300,300),l,new PolylineShape[0],0,r,new Communication());}
 static class State{
  TileShape[] shapes={null,new IntBox(0,0,100,100),new IntBox(100,0,200,100),new IntBox(50,20,150,150),new IntBox(10,10,20,20),Simplex.EMPTY,new IntBox(-20,-20,20,20).toIntOctagon()};
  Room[] rooms=new Room[3];Item[] items=new Item[5];Data[] data=new Data[5];TargetItemExpansionDoor[] targets=new TargetItemExpansionDoor[9];Collection<TargetItemExpansionDoor> oldTargets;ExpansionDoor edge;IncompleteFreeSpaceExpansionRoom incomplete=new IncompleteFreeSpaceExpansionRoom(new IntBox(0,0,1,1),0,null);Bad bad=new Bad();Tree tree;Engine engine,nullTreeEngine;Room current;int mask,index;boolean active;int failure=-1,failAt=1,seen;String events="";IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();int next=1;
  int id(Object x){return x==null?0:ids.computeIfAbsent(x,k->next++);}
  void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(failure==code&&++seen==failAt)throw new IllegalStateException();}
  String invoke(Call f){events="";seen=0;active=true;String result=call(f);active=false;return result+"~"+(events.isEmpty()?"-":events);}
  String section(MazeSearchElement e){return id(e)+":"+e.isOccupied+":"+id(e.backtrackDoor)+":"+e.ripupCost;}
  String collection(Collection<TargetItemExpansionDoor> c){if(c==null)return "N";String r=id(c)+":"+c.size();for(var d:c)r+=d==null?",N":","+id(d)+":"+section(d.getMazeSearchElement(0));return r;}
  String dump(){String r="";for(int i=1;i<=2;i++){var room=rooms[i];r+="R"+i+":"+room.isNetDependent()+":"+collection(room.getTargetDoors())+":"+room.getDoors().size();}r+="O"+collection(oldTargets);for(int i=1;i<targets.length;i++)r+="T"+i+":"+(targets[i]==null?"N":id(targets[i])+":"+targets[i].getShape().getId()+":"+section(targets[i].getMazeSearchElement(0)));r+="E";for(var leaf:tree.toArray())r+=id(leaf.object)+":"+leaf.shapeIndexInObject+",";return r;}
 }
 static String run(String line)throws Exception{
  Scanner in=new Scanner(line);in.next();State s=new State();for(int i=1;i<=2;i++)s.rooms[i]=new Room(s,i,s.shapes[in.nextInt()],in.nextInt(),in.nextInt());for(int i=1;i<=4;i++){var d=new Data(s,i);d.shape=in.nextInt();d.connection=in.nextInt();d.layer=in.nextInt();d.obstacle=in.nextInt()!=0;s.data[i]=d;int net=in.nextInt();s.items[i]=switch(i){case 1->new Plain(d,net);case 2->new TraceItem(d,net);case 3->new ViaItem(d,net);default->new Area(d,net);};}
  s.tree=new Tree(s);for(int i=1;i<=4;i++)s.tree.insert(s.items[i]);s.engine=new Engine(s,s.tree);s.nullTreeEngine=new Engine(s,null);s.edge=new ExpansionDoor(s.rooms[1],s.rooms[2],1);s.rooms[1].addDoor(s.edge);s.rooms[2].addDoor(s.edge);ArrayList<String> out=new ArrayList<>();out.add(s.dump());int n=in.nextInt();
  for(int step=0;step<n;step++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();double value=dbl(in);
   out.add(s.invoke(()->switch(op){
    case "T"->{ShapeTree.TreeEntry e=b<0?null:new ShapeTree.TreeEntry(b<5?s.items[b]:b==5?s.rooms[1]:b==6?s.rooms[2]:s.bad,c);s.rooms[a].calculateTargetDoors(e,d,s.tree);yield "ok";}
    case "D"->{s.targets[a]=new TargetItemExpansionDoor(s.items[b],c,d==0?null:s.rooms[d],s.tree);yield s.id(s.targets[a]);}
    case "L"->{s.targets[a]=new ArrayList<>(s.rooms[b].getTargetDoors()).get(c);yield s.id(s.targets[a]);}
    case "A"->{s.rooms[a].addTargetDoor(s.targets[b]);yield "ok";}case "R"->s.rooms[a].removeDoor(b<0?s.edge:s.targets[b]);
    case "C"->{s.rooms[a].clearDoors();yield "ok";}case "B"->{s.rooms[a].resetDoors();yield "ok";}case "N"->{s.rooms[a].setNetDependent();yield "ok";}
    case "U"->{s.oldTargets=s.rooms[a].getTargetDoors();yield "ok";}case "V"->{if(b<0)s.oldTargets.clear();else s.oldTargets.add(s.targets[b]);yield "ok";}
    case "P"->{var e=s.targets[a].getMazeSearchElement(b);e.isOccupied=true;e.backtrackDoor=s.targets[c];e.ripupCost=d;yield "ok";}
    case "S"->{s.rooms[a].setShape(s.shapes[b]);yield "ok";}case "s"->{s.data[a].shape=b;s.data[a].connection=c;yield "ok";}
    case "I"->{s.tree.insert(s.rooms[a]);yield "ok";}case "X"->{s.rooms[a].removeFromTree(b==0?null:s.tree);yield "ok";}case "J"->{s.rooms[a].setSearchTreeEntries(null,null);yield "ok";}case "W"->{s.rooms[a].savedEntries[b]=null;yield "ok";}
    case "Q"->{s.current=s.rooms[a];s.mask=d;s.index=c;s.engine.net=b;s.nullTreeEngine.net=b;yield s.current.validate(s.engine);}case "q"->{s.nullTreeEngine.net=b;yield s.rooms[a].validate(c==0?null:s.nullTreeEngine);}
    case "O"->{Object other=b==0?null:b<=2?s.rooms[b]:b==3?s.incomplete:b<=7?s.items[b-3]:s.bad;yield s.rooms[a].compareTo(other);}
    case "H"->{var r=s.rooms[a];yield r.treeShapeCount(null)+":"+(r.getTreeShape(null,c)==r.getShape())+":"+r.shapeLayer(c)+":"+r.isObstacle(d)+":"+r.isTraceObstacle(d)+":"+(r.getObject()==r);}
    case "F"->{s.failure=a;s.failAt=b;yield "ok";}
    case "E"->{AutorouteDiagnostic.Sink sink=b==0?null:x->{s.touch(900,x.layer());s.events+="diag:"+x.kind().ordinal()+":"+x.shape().getId()+":"+bits(x.intensity())+",";};s.rooms[a].emitDiagnostic(sink,value);yield "ok";}
    default->throw new AssertionError(op);
   }));out.add(s.dump());
  }return String.join(" ",out);
 }
 public static void main(String[] args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String s;while((s=in.readLine())!=null)System.out.println(s+" | "+run(s));}
}
