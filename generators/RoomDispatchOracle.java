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
public class RoomDispatchOracle {
 interface Call{Object run();}interface Reflection{Object run()throws ReflectiveOperationException;}
 static Object reflect(Reflection f){try{return f.run();}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static Object field(Object o,String name){return reflect(()->{var f=SortedRoomNeighbours.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);});}
 static Object method(String name,Object receiver,Class<?>[] types,Object...args){return reflect(()->{var m=SortedRoomNeighbours.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(receiver,args);});}
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
 static class Board extends RoutingBoard {
 State s;Board(State s){this(s,new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)}));}Board(State s,LayerStructure l){super(new IntBox(-100,-100,200,200),l,new PolylineShape[0],0,new BoardRules(l,new ClearanceMatrix(2,l,new String[]{"none","default"})),new Communication());this.s=s;}
 public IntBox getBoundingBox(){if(s!=null)s.touch(900,0);return super.getBoundingBox();}
 }
 static class TreeGeneral extends ShapeSearchTree {State s;TreeGeneral(State s,Board b,boolean orthogonal){super(orthogonal?OrthogonalBoundingDirections.INSTANCE:FortyfiveDegreeBoundingDirections.INSTANCE,b,0);this.s=s;}
 public void overlappingTreeEntries(ConvexShape shape,int layer,Collection<ShapeTree.TreeEntry> entries){s.touch(600,layer);s.events+="query:"+s.shape((TileShape)shape)+",";super.overlappingTreeEntries(shape,layer,entries);}
 public Collection<IncompleteFreeSpaceExpansionRoom> completeShape(IncompleteFreeSpaceExpansionRoom room,int net,SearchTreeObject ignore,TileShape ignored){s.touch(610,net);String roomShape=s.shape(room.getShape());String contained=s.shape(room.getContainedShape());s.events+="complete:"+roomShape+":"+contained+",";return super.completeShape(room,net,ignore,ignored);}
 }
 static class TreeOrthogonal extends ShapeSearchTree90Degree {State s;TreeOrthogonal(State s,Board b){super(b,0);this.s=s;}
 public void overlappingTreeEntries(ConvexShape shape,int layer,Collection<ShapeTree.TreeEntry> entries){s.touch(600,layer);s.events+="query:"+s.shape((TileShape)shape)+",";super.overlappingTreeEntries(shape,layer,entries);}
 public Collection<IncompleteFreeSpaceExpansionRoom> completeShape(IncompleteFreeSpaceExpansionRoom room,int net,SearchTreeObject ignore,TileShape ignored){s.touch(610,net);String roomShape=s.shape(room.getShape());String contained=s.shape(room.getContainedShape());s.events+="complete:"+roomShape+":"+contained+",";return super.completeShape(room,net,ignore,ignored);}
 }
 static class TreeOctagonal extends ShapeSearchTree45Degree {State s;TreeOctagonal(State s,Board b){super(b,0);this.s=s;}
 public void overlappingTreeEntries(ConvexShape shape,int layer,Collection<ShapeTree.TreeEntry> entries){s.touch(600,layer);s.events+="query:"+s.shape((TileShape)shape)+",";super.overlappingTreeEntries(shape,layer,entries);}
 public Collection<IncompleteFreeSpaceExpansionRoom> completeShape(IncompleteFreeSpaceExpansionRoom room,int net,SearchTreeObject ignore,TileShape ignored){s.touch(610,net);String roomShape=s.shape(room.getShape());String contained=s.shape(room.getContainedShape());s.events+="complete:"+roomShape+":"+contained+",";return super.completeShape(room,net,ignore,ignored);}
 }
 static class Engine extends AutorouteEngine{
 State s;Engine(State s,ShapeSearchTree tree){super(s.board,0,false);this.s=s;reflect(()->{var f=AutorouteEngine.class.getField("autorouteSearchTree");f.setAccessible(true);f.set(this,tree);return null;});}
 public int getNetNumber(){s.touch(800,s.net);return s.net;}public int generateRoomIdNo(){s.touch(810,s.nextRoom);return ++s.nextRoom;}
 public void removeAllDoors(ExpansionRoom r){s.touch(820,s.id(r));r.clearDoors();}
 public IncompleteFreeSpaceExpansionRoom addIncompleteExpansionRoom(TileShape shape,int layer,TileShape contained){s.touch(830,layer);s.events+="add:"+s.shape(shape)+":"+s.shape(contained)+",";if(s.nullInsertion)return null;var r=new IncompleteFreeSpaceExpansionRoom(shape,layer,contained);s.created.add(r);return r;}
 }
 static class State{
 TileShape[] shapes={null,new IntBox(0,0,100,100),new IntBox(-50,0,0,100),new IntBox(0,-50,100,0),new IntBox(100,0,150,100),new IntBox(0,100,100,150),new IntBox(10,50,90,50),new IntBox(100,100,100,100),new IntBox(30,30,70,70),new IntBox(-20,-20,120,120),new IntBox(50,50,50,50),new IntBox(0,0,100,0),new IntBox(0,0,100,100).toSimplex(),TileShape.getInstance(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(30,100)})};
 Data[] data=new Data[5];Item[] items=new Item[5];ExpansionRoom[] rooms=new ExpansionRoom[6];ArrayList<IncompleteFreeSpaceExpansionRoom> created=new ArrayList<>();int[] order=new int[5];Board board;ShapeSearchTree tree;Engine engine;Collection<IncompleteFreeSpaceExpansionRoom> pending;Object neighbours;CompleteExpansionRoom result;int count,index,mode,net=1,nextRoom=1000;boolean nullInsertion=false;
 String events="";boolean active=false;int failure=-1,failAt=1,seen=0,next=1;IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
 void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
 String invoke(Call f){events="";seen=0;active=true;String r=call(f);active=false;return r+"~"+(events.isEmpty()?"-":events);}int id(Object o){return o==null?0:ids.computeIfAbsent(o,k->next++);}
 String shape(TileShape x){if(x==null)return "N";String r=x.getClass().getSimpleName()+":"+x.getId()+":"+x.dimension()+":"+x.borderLineCount();for(int i=0;i<x.borderLineCount();i++)r+=","+x.borderLine(i).getId();return r;}
 String room(ExpansionRoom r){if(r==null)return "N";String t=id(r)+":"+shape(r.getShape())+":"+r.getLayer();if(r instanceof IncompleteFreeSpaceExpansionRoom x)t+=":"+shape(x.getContainedShape());if(r instanceof CompleteFreeSpaceExpansionRoom x){t+=":"+x.isNetDependent()+":";for(var d:x.getTargetDoors())t+=id(d)+","+id(d.item)+","+d.treeEntryNo+","+shape(d.getShape())+";";}t+="D";for(var d:r.getDoors())t+=id(d)+","+id(d.firstRoom)+","+id(d.secondRoom)+","+d.getDimension()+";";return t;}
 String dump(){String t="R"+room(result);for(int i=1;i<rooms.length;i++)t+="I"+i+":"+room(rooms[i]);t+="C";for(var r:created)t+=room(r)+"/";t+="N";if(neighbours==null)t+="N";else{t+=id(neighbours)+":"+room((ExpansionRoom)field(neighbours,"completedRoom"))+":";@SuppressWarnings("unchecked")var sorted=(Collection<Object>)field(neighbours,"sortedNeighbours");for(Object n:sorted)t+=id(n)+",";t+="O";@SuppressWarnings("unchecked")var own=(Collection<ShapeTree.TreeEntry>)field(neighbours,"ownNetObjects");for(var e:own)t+=id(e.object)+","+e.shapeIndexInObject+";";}t+="P";if(pending==null)t+="N";else{t+=id(pending)+":"+pending.size()+":";for(var x:pending)t+=room(x)+"/";}return t;}
 }
 static String run(String line){Scanner in=new Scanner(line);String style=in.next();int treeKind=in.nextInt();State s=new State();if(style.equals("O"))for(int i=0;i<s.shapes.length;i++)if(s.shapes[i]!=null&&i!=12&&i!=13)s.shapes[i]=s.shapes[i].boundingOctagon();for(int i=1;i<=4;i++){var d=new Data(s,i,in);s.data[i]=d;s.items[i]=i%2==0?new Trace(d,i==4?2:1):new Plain(d,i==3?2:1);}for(int i=0;i<5;i++)s.order[i]=in.nextInt();s.board=new Board(s);s.tree=switch(treeKind){case 0->new TreeGeneral(s,s.board,false);case 1->new TreeGeneral(s,s.board,true);case 2->new TreeOrthogonal(s,s.board);case 3->new TreeOctagonal(s,s.board);default->null;};if(s.tree!=null)for(int n:s.order)if(n<5)s.tree.insert(s.items[n]);s.engine=new Engine(s,s.tree);s.rooms[1]=new Room(s,1,s.shapes[1],0,s.shapes[10]);s.rooms[2]=new Room(s,2,s.shapes[12],1,s.shapes[10]);s.rooms[3]=s.items[2].getAutorouteInfo().getExpansionRoom(0,s.tree==null?new TreeGeneral(s,s.board,false):s.tree);s.rooms[4]=new CompleteFreeSpaceExpansionRoom(s.shapes[9],0,45);
 var out=new ArrayList<String>();int steps=in.nextInt();for(int j=0;j<steps;j++){String op=in.next();if(Boolean.getBoolean("oracle.trace"))System.err.println("step="+j+" op="+op);int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();out.add(s.invoke(()->switch(op){
 case "C"->{s.result=SortedRoomNeighbours.complete(s.rooms[a],b==0?null:s.engine);yield s.id(s.result);}
 case "P"->{s.pending=s.tree.completeShape((IncompleteFreeSpaceExpansionRoom)s.rooms[a],s.net,null,null);s.rooms[5]=s.pending.isEmpty()?null:s.pending.iterator().next();yield s.id(s.pending)+":"+s.pending.size();}
 case "U"->{s.tree.remove(s.items[a].getSearchTreeEntries(s.tree));s.data[a].shape=b;s.data[a].obstacle=(d&1)!=0;s.data[a].routable=(d&2)!=0;s.tree.insert(s.items[a]);yield "ok";}
 case "S"->{s.count=a;s.index=b;s.mode=c;s.net=d;yield "ok";}
 case "s"->{s.data[a].shape=b;s.data[a].connection=c;s.data[a].obstacle=(d&1)!=0;s.data[a].routable=(d&2)!=0;yield "ok";}
 case "M"->{((Room)s.rooms[a]).setShape(s.shapes[b]);((Room)s.rooms[a]).setContainedShape(s.shapes[c]);yield "ok";}
 case "F"->{s.failure=a;s.failAt=b;yield "ok";}
 case "Z"->{s.nullInsertion=a!=0;yield "ok";}
 default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;int row=0;while((line=in.readLine())!=null){if(Boolean.getBoolean("oracle.trace"))System.err.println("row="+row++);System.out.println(line+" | "+run(line));}}
}
