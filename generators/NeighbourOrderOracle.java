package app.freerouting.board.searchtree;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.*;
import app.freerouting.datastructures.*;
import java.io.*;import java.util.*;import java.lang.reflect.*;
public class NeighbourOrderOracle {
 static Class<?> outer=SortedRoomNeighbours.class,inner;
 static {try{inner=Class.forName(outer.getName()+"$SortedRoomNeighbour");}catch(Exception e){throw new AssertionError(e);}}
 interface Call{Object run();}
 static Object reflect(CallReflection f){try{return f.run();}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 interface CallReflection{Object run()throws ReflectiveOperationException;}
 static Object field(Object o,String name){return reflect(()->{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);});}
 static Object method(Class<?> type,String name,Object receiver,Class<?>[] params,Object...args){return reflect(()->{var m=type.getDeclaredMethod(name,params);m.setAccessible(true);return m.invoke(receiver,args);});}
 static Object construct(Class<?> type,Class<?>[] params,Object...args){return reflect(()->{var c=type.getDeclaredConstructor(params);c.setAccessible(true);return c.newInstance(args);});}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(IllegalArgumentException e){return "argument_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double v){return Long.toHexString(Double.doubleToLongBits(v));}
 static String point(Point p){if(p==null)return "N";String coords=p instanceof IntPoint q?q.x+","+q.y+",1":field(p,"x")+","+field(p,"y")+","+field(p,"z");return (p instanceof IntPoint?"I":"R")+":"+p.getId()+":"+coords;}
 static class Plain extends CompleteRoomOracle.Plain{
 State s;int serial;Plain(State s,int serial,int net){super(new CompleteRoomOracle.Data(new CompleteRoomOracle.State(),serial),net);this.s=s;this.serial=serial;}
 public int getId(){s.touch(100+serial,0);return s.objectIds[serial];}public TileShape getTreeShape(ShapeTree t,int i){return s.shapes[1];}
 }
 static class Trace extends PolylineTrace{
 State s;int serial;Trace(State s,int serial,int net){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(100,100)}),0,1,new int[]{net},1,100+serial,0,FixedState.UNFIXED,null);this.s=s;this.serial=serial;}
 public int getId(){s.touch(100+serial,0);return s.objectIds[serial];}public TileShape getTreeShape(ShapeTree t,int i){return s.shapes[1];}public int tileShapeCount(){if(s!=null)s.touch(300+serial,0);return super.tileShapeCount();}
 }
 static class Room extends CompleteFreeSpaceExpansionRoom{
 State s;int serial;Room(State s,int serial,TileShape shape){super(shape,0,serial);this.s=s;this.serial=serial;}
 public TileShape getShape(){s.touch(400+serial,0);return super.getShape();}public boolean doorExists(ExpansionRoom other){s.touch(200+serial,0);return super.doorExists(other);}
 }
 static final Class<?>[] NEIGH_PARAMS={outer,SearchTreeObject.class,TileShape.class,TileShape.class,int.class,int.class,boolean.class,boolean.class};
 static final Class<?>[] ADD_PARAMS={SearchTreeObject.class,TileShape.class,TileShape.class,int.class,int.class,boolean.class,boolean.class};
 static class State{
 TileShape[] shapes={null,new IntBox(0,0,100,100),new IntBox(-50,0,0,100),new IntBox(0,-50,100,0),new IntBox(100,0,150,100),new IntBox(0,100,100,150),new IntBox(0,0,0,0),new IntBox(0,0,100,0),new IntBox(0,0,0,100),TileShape.getInstance(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(30,100)}),new IntBox(0,0,100,100).toSimplex(),new IntBox(100,100,0,0),new IntBox(-10,0,0,1),new IntBox(-10,0,0,2)};
 State(){shapes=Arrays.copyOf(shapes,16);shapes[14]=shapes[9].intersection(new IntBox(10,10,80,80));shapes[15]=shapes[9].intersection(new IntBox(5,5,75,75));}
 int[] objectIds=new int[5];Item[] objects=new Item[5];ExpansionRoom[] rooms=new ExpansionRoom[7];Object current;Object[] neighbours=new Object[13];
 ShapeSearchTree[] trees={null,new ShapeSearchTree(OrthogonalBoundingDirections.INSTANCE,null,0),new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE,null,0),new ShapeSearchTree45Degree(null,0),new ShapeSearchTree90Degree(null,0)};
 int failure=-1,failAt=1,seen=0,next=1;boolean active=false;String events="";IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
 void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
 String invoke(Call f){events="";seen=0;active=true;String r=call(f);active=false;return r+"~"+(events.isEmpty()?"-":events);}
 int id(Object x){return x==null?0:ids.computeIfAbsent(x,k->next++);}
 String neighbour(Object x){return x==null?"N":id(x)+":"+point((Point)field(x,"precalculatedFirstCorner"))+":"+point((Point)field(x,"precalculatedLastCorner"));}
 @SuppressWarnings("unchecked")SortedSet<Object> sorted(){return (SortedSet<Object>)field(current,"sortedNeighbours");}
 String dump(){String r="S";for(Object x:sorted())r+=neighbour(x)+"/";for(int i=1;i<neighbours.length;i++)r+="N"+i+":"+neighbour(neighbours[i]);return r;}
 }
 static String run(String line){Scanner in=new Scanner(line);in.next();State s=new State();int shape=in.nextInt();for(int i=1;i<=4;i++)s.objectIds[i]=in.nextInt();int net1=in.nextInt(),net2=in.nextInt();s.objects[1]=new Plain(s,1,net1);s.objects[2]=new Plain(s,2,net2);s.objects[3]=new Trace(s,3,net1);s.objects[4]=new Trace(s,4,net2);s.rooms[1]=new Room(s,1,s.shapes[shape]);s.rooms[2]=new Room(s,2,s.shapes[1]);s.rooms[3]=new ObstacleExpansionRoom(s.objects[1],0,s.trees[1]);s.rooms[4]=new ObstacleExpansionRoom(s.objects[2],0,s.trees[1]);s.rooms[5]=new ObstacleExpansionRoom(s.objects[3],0,s.trees[1]);s.rooms[6]=new ObstacleExpansionRoom(s.objects[4],1,s.trees[1]);s.current=construct(outer,new Class<?>[]{ExpansionRoom.class,CompleteExpansionRoom.class},s.rooms[1],s.rooms[1]);
 var out=new ArrayList<String>();int steps=in.nextInt();for(int j=0;j<steps;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt(),e=in.nextInt(),f=in.nextInt(),g=in.nextInt(),h=in.nextInt();out.add(s.invoke(()->switch(op){
 case "B"->{s.current=construct(outer,new Class<?>[]{ExpansionRoom.class,CompleteExpansionRoom.class},s.rooms[a],s.rooms[b]);yield s.id(s.current);}
 case "N"->{s.neighbours[a]=construct(inner,NEIGH_PARAMS,s.current,s.objects[b],s.shapes[c],s.shapes[d],e,f,(g&1)!=0,(g&2)!=0);yield s.id(s.neighbours[a]);}
 case "E"->{method(outer,"addSortedNeighbour",s.current,ADD_PARAMS,s.objects[b],s.shapes[c],s.shapes[d],e,f,(g&1)!=0,(g&2)!=0);yield "ok";}
 case "C"->method(inner,"compareTo",s.neighbours[a],new Class<?>[]{inner},s.neighbours[b]);
 case "F"->point((Point)method(inner,"firstCorner",s.neighbours[a],new Class<?>[]{}));
 case "L"->point((Point)method(inner,"lastCorner",s.neighbours[a],new Class<?>[]{}));
 case "A"->s.sorted().add(s.neighbours[a]);case "X"->s.sorted().remove(s.neighbours[a]);
 case "M"->{s.objectIds[a]=b;yield "ok";}
 case "S"->{((Room)s.rooms[1]).setShape(s.shapes[a]);yield "ok";}
 case "D"->method(outer,"insertDoorOk",null,new Class<?>[]{ExpansionRoom.class,ExpansionRoom.class,TileShape.class},s.rooms[a],s.rooms[b],s.shapes[c]);
 case "d"->{Line l=a==0?null:s.shapes[a].borderLine(b);yield method(outer,"insertDoorOk",null,new Class<?>[]{ObstacleExpansionRoom.class,Line.class},s.rooms[c],l);}
 case "O"->{var door=new ExpansionDoor(s.rooms[a],s.rooms[b],1);s.rooms[a].addDoor(door);s.rooms[b].addDoor(door);yield "ok";}
 case "Z"->{s.rooms[a].clearDoors();yield "ok";}
 case "T"->method(outer,"selectCalculationMode",null,new Class<?>[]{ShapeSearchTree.class},s.trees[a]);
 case "P"->{s.failure=a;s.failAt=b;yield "ok";}
 default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null)System.out.println(line+" | "+run(line));}
}
