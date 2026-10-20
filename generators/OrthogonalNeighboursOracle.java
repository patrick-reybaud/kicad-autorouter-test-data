package app.freerouting.board.searchtree;
import app.freerouting.autoroute.*;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.maze.*;
import app.freerouting.board.facade.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.state.Communication;
import app.freerouting.rules.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.datastructures.*;
import java.io.*;import java.util.*;import java.lang.reflect.*;
public class OrthogonalNeighboursOracle {
 interface Call{Object run();}interface Reflection{Object run()throws ReflectiveOperationException;}
 static Object reflect(Reflection f){try{return f.run();}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static Object field(Object o,String name){return reflect(()->{var f=SortedOrthogonalRoomNeighbours.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);});}
 static Object nested(Object o,String name){return reflect(()->{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);});}
 static Object method(String name,Object receiver,Class<?>[] types,Object...args){return reflect(()->{var m=SortedOrthogonalRoomNeighbours.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(receiver,args);});}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(IllegalArgumentException e){return "argument_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}}
 static class Data{State s;int serial,shape,connection,layer,id;boolean obstacle,routable;Data(State s,int n,Scanner in){this.s=s;serial=n;shape=in.nextInt();connection=in.nextInt();layer=in.nextInt();id=in.nextInt();obstacle=in.nextInt()!=0;routable=in.nextInt()!=0;}TileShape shape(int i){s.touch(200+serial,i);return s.shapes[shape];}boolean obstacle(int n){s.touch(300+serial,n);return obstacle;}int id(){s.touch(100+serial,0);return id;}boolean routable(){s.touch(500+serial,0);return routable;}TileShape connection(int i){s.touch(400+serial,i);return s.shapes[connection];}}
 static class Plain extends CompleteRoomOracle.Plain{
 Data x;Plain(Data x,int net){super(new CompleteRoomOracle.Data(new CompleteRoomOracle.State(),x.serial),net);this.x=x;}
 public int treeShapeCount(ShapeTree t){return 3;}public TileShape getTreeShape(ShapeTree t,int i){return x.shape(i);}public int getId(){return x.id();}public boolean isTraceObstacle(int n){return x.obstacle(n);}public boolean isRoutable(){return x.routable();}public int shapeLayer(int i){return x.layer;}
 }
 static class Trace extends PolylineTrace{
 Data x;Trace(Data x,int net){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(100,100),new IntPoint(0,100)}),0,1,new int[]{net},1,100+x.serial,0,FixedState.UNFIXED,null);this.x=x;}
 public int treeShapeCount(ShapeTree t){return 3;}public TileShape getTreeShape(ShapeTree t,int i){return x.shape(i);}public int getId(){return x.id();}public boolean isTraceObstacle(int n){return x.obstacle(n);}public boolean isRoutable(){return x.routable();}public int shapeLayer(int i){return x.layer;}public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){return x.connection(i);}
 }
 static class Room extends IncompleteFreeSpaceExpansionRoom{
 State s;int serial;Room(State s,int serial,TileShape shape,int layer,TileShape contained){super(shape,layer,contained);this.s=s;this.serial=serial;}
 public TileShape getShape(){s.touch(700+serial,0);return super.getShape();}public TileShape getContainedShape(){s.touch(710+serial,0);return super.getContainedShape();}public int getLayer(){s.touch(720+serial,0);return super.getLayer();}
 }
 static class Tree extends ShapeSearchTree{
 State s;Tree(State s){super(FortyfiveDegreeBoundingDirections.INSTANCE,null,0);this.s=s;}
 public void overlappingTreeEntries(ConvexShape shape,int layer,Collection<ShapeTree.TreeEntry> entries){s.touch(600,layer);s.events+="query:"+s.shape((TileShape)shape)+",";for(int i=0;i<s.count;i++){int object=s.order[i%s.order.length];entries.add(new ShapeTree.TreeEntry(object==5?(CompleteFreeSpaceExpansionRoom)s.rooms[4]:s.items[object],s.index));}}
 public Collection<IncompleteFreeSpaceExpansionRoom> completeShape(IncompleteFreeSpaceExpansionRoom room,int net,SearchTreeObject ignore,TileShape ignored){s.touch(610,net);s.events+="complete:"+s.shape(room.getShape())+":"+s.shape(room.getContainedShape())+":"+s.id(ignore)+":"+s.shape(ignored)+",";if(s.mode<0)return null;var r=new LinkedList<IncompleteFreeSpaceExpansionRoom>();if(s.mode==1||s.mode==2||s.mode==3){var a=new IncompleteFreeSpaceExpansionRoom(s.shapes[s.mode==3?1:9],room.getLayer(),room.getContainedShape());r.add(a);if(s.mode==2)r.add(a);}return r;}
 }
 static RoutingBoard board(){var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});return new RoutingBoard(new IntBox(-100,-100,200,200),layers,new PolylineShape[0],0,new BoardRules(layers,new ClearanceMatrix(2,layers,new String[]{"none","default"})),new Communication());}
 static class Engine extends AutorouteEngine{
 State s;Engine(State s,Tree tree){super(board(),0,false);this.s=s;reflect(()->{var f=AutorouteEngine.class.getField("autorouteSearchTree");f.setAccessible(true);f.set(this,tree);return null;});}
 public int getNetNumber(){s.touch(800,s.net);return s.net;}public int generateRoomIdNo(){s.touch(810,s.nextRoom);return ++s.nextRoom;}
 public void removeAllDoors(ExpansionRoom r){s.touch(820,s.id(r));r.clearDoors();}
 public IncompleteFreeSpaceExpansionRoom addIncompleteExpansionRoom(TileShape shape,int layer,TileShape contained){s.touch(830,layer);s.events+="add:"+s.shape(shape)+":"+s.shape(contained)+",";if(s.nullInsertion)return null;var r=new IncompleteFreeSpaceExpansionRoom(shape,layer,contained);s.created.add(r);return r;}
 }
 static class State{
 TileShape[] shapes={null,new IntBox(0,0,100,100),new IntBox(-50,0,0,100),new IntBox(0,-50,100,0),new IntBox(100,0,150,100),new IntBox(0,100,100,150),new IntBox(10,50,90,50),new IntBox(100,100,100,100),new IntBox(30,30,70,70),new IntBox(-20,-20,120,120),new IntBox(50,50,50,50),new IntBox(0,0,100,0),new IntBox(0,0,100,100).toSimplex(),TileShape.getInstance(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(30,100)}),new IntBox(20,-50,70,0),new IntBox(100,20,150,70),new IntBox(20,100,70,150),new IntBox(-50,20,0,70),new IntBox(20,0,70,0),new IntBox(100,20,100,70),new IntBox(20,100,70,100),new IntBox(0,20,0,70),new IntBox(-2147483648,0,2147483647,0),new IntBox(0,-2147483648,0,2147483647)};
 Data[] data=new Data[5];Item[] items=new Item[5];ExpansionRoom[] rooms=new ExpansionRoom[5];ArrayList<IncompleteFreeSpaceExpansionRoom> created=new ArrayList<>();int[] order=new int[5];Tree tree;Engine engine;Object neighbours;CompleteExpansionRoom result;int count,index,mode,net=1,nextRoom=1000;boolean nullInsertion=false;
 String events="";boolean active=false;int failure=-1,failAt=1,seen=0,next=1;IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
 void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
 String invoke(Call f){events="";seen=0;active=true;String r=call(f);active=false;return r+"~"+(events.isEmpty()?"-":events);}int id(Object o){return o==null?0:ids.computeIfAbsent(o,k->next++);}
 String shape(TileShape x){if(x==null)return "N";String r=x.getClass().getSimpleName()+":"+x.getId()+":"+x.dimension()+":"+x.borderLineCount();for(int i=0;i<x.borderLineCount();i++)r+=","+x.borderLine(i).getId();return r;}
 String room(ExpansionRoom r){if(r==null)return "N";String t=id(r)+":"+shape(r.getShape())+":"+r.getLayer();if(r instanceof IncompleteFreeSpaceExpansionRoom x)t+=":"+shape(x.getContainedShape());if(r instanceof CompleteFreeSpaceExpansionRoom x){t+=":"+x.isNetDependent()+":";for(var d:x.getTargetDoors())t+=id(d)+","+id(d.item)+","+d.treeEntryNo+","+shape(d.getShape())+";";}t+="D";for(var d:r.getDoors())t+=id(d)+","+id(d.firstRoom)+","+id(d.secondRoom)+","+d.getDimension()+";";return t;}
 String dump(){String t="R"+room(result);for(int i=1;i<rooms.length;i++)t+="I"+i+":"+room(rooms[i]);t+="C";for(var r:created)t+=room(r)+"/";t+="N";if(neighbours==null)t+="N";else{t+=id(neighbours)+":"+room((ExpansionRoom)field(neighbours,"completedRoom"))+":";@SuppressWarnings("unchecked")var sorted=(Collection<Object>)field(neighbours,"sortedNeighbours");for(Object n:sorted){t+=id(n)+":"+nested(n,"firstTouchingSide")+":"+nested(n,"lastTouchingSide")+":"+shape((TileShape)nested(n,"intersection"))+",";}t+="E";for(boolean x:(boolean[])field(neighbours,"edgeInteriorTouchesObstacle"))t+=x+",";}return t;}
 }
 static String run(String line){Scanner in=new Scanner(line);in.next();State s=new State();for(int i=1;i<=4;i++){var d=new Data(s,i,in);s.data[i]=d;s.items[i]=i%2==0?new Trace(d,i==4?2:1):new Plain(d,i==3?2:1);}for(int i=0;i<5;i++)s.order[i]=in.nextInt();s.tree=new Tree(s);s.engine=new Engine(s,s.tree);s.rooms[1]=new Room(s,1,s.shapes[1],0,s.shapes[10]);s.rooms[2]=new Room(s,2,s.shapes[12],1,s.shapes[10]);s.rooms[3]=s.items[2].getAutorouteInfo().getExpansionRoom(0,s.tree);s.rooms[4]=new CompleteFreeSpaceExpansionRoom(s.shapes[9],0,45);
 var out=new ArrayList<String>();int steps=in.nextInt();for(int j=0;j<steps;j++){String op=in.next();if(Boolean.getBoolean("oracle.trace"))System.err.println("step="+j+" op="+op);int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();out.add(s.invoke(()->switch(op){
 case "Q"->{s.neighbours=method("calculateNeighbours",null,new Class<?>[]{ExpansionRoom.class,int.class,ShapeSearchTree.class,int.class},s.rooms[a],s.net,b==0?null:s.tree,c);yield s.id(s.neighbours);}
 case "C"->{s.result=SortedOrthogonalRoomNeighbours.calculate(s.rooms[a],b==0?null:s.engine);yield s.id(s.result);}
 case "R"->method("tryRemoveEdge",s.neighbours,new Class<?>[]{int.class,ShapeSearchTree.class},s.net,a==0?null:s.tree);
 case "N"->{method("calculateNewIncompleteRooms",s.neighbours,new Class<?>[]{AutorouteEngine.class},a==0?null:s.engine);yield "ok";}
 case "B"->s.shape((TileShape)method("removeBorderLine",null,new Class<?>[]{IntBox.class,int.class},(IntBox)s.shapes[a],b));
 case "A"->{s.neighbours=reflect(()->{var constructor=SortedOrthogonalRoomNeighbours.class.getDeclaredConstructor(ExpansionRoom.class,CompleteExpansionRoom.class);constructor.setAccessible(true);return constructor.newInstance(s.rooms[a],s.rooms[b]);});yield s.id(s.neighbours);}
 case "I"->{method("addSortedNeighbour",s.neighbours,new Class<?>[]{SearchTreeObject.class,IntBox.class,IntBox.class},s.items[a],(IntBox)s.shapes[b],(IntBox)s.shapes[c]);yield "ok";}
 case "J"->{var completed=(ExpansionRoom)field(s.neighbours,"completedRoom");var door=new ExpansionDoor(completed,s.rooms[4],a);completed.addDoor(door);s.rooms[4].addDoor(door);yield "ok";}
 case "V"->{((CompleteFreeSpaceExpansionRoom)s.rooms[4]).setShape(s.shapes[a]);yield "ok";}
 case "K"->{var completed=(ExpansionRoom)field(s.neighbours,"completedRoom");var other=new CompleteFreeSpaceExpansionRoom(s.shapes[a],0,100+b);var door=new ExpansionDoor(completed,other,b);completed.addDoor(door);other.addDoor(door);yield "ok";}
 case "Y"->{reflect(()->{var f=AutorouteEngine.class.getField("board");f.setAccessible(true);f.set(s.engine,a==0?null:board());return null;});yield "ok";}
 case "E"->{method("calculateIncompleteRoomsWithEmptyNeighbours",null,new Class<?>[]{ObstacleExpansionRoom.class,AutorouteEngine.class},s.rooms[3],a==0?null:s.engine);yield "ok";}
 case "S"->{s.count=a;s.index=b;s.mode=c;s.net=d;yield "ok";}
 case "s"->{s.data[a].shape=b;s.data[a].connection=c;s.data[a].obstacle=(d&1)!=0;s.data[a].routable=(d&2)!=0;yield "ok";}
 case "M"->{((Room)s.rooms[a]).setShape(s.shapes[b]);((Room)s.rooms[a]).setContainedShape(s.shapes[c]);yield "ok";}
 case "F"->{s.failure=a;s.failAt=b;yield "ok";}
 case "Z"->{s.nullInsertion=a!=0;yield "ok";}
 default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;int row=0;while((line=in.readLine())!=null){if(Boolean.getBoolean("oracle.trace"))System.err.println("row="+row++);System.out.println(line+" | "+run(line));}}
}
