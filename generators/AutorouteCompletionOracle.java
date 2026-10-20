package app.freerouting.board.searchtree;
import app.freerouting.autoroute.*;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.maze.*;
import app.freerouting.autoroute.drill.*;
import app.freerouting.board.facade.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.state.Communication;
import app.freerouting.rules.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.datastructures.*;
import java.io.*;import java.util.*;import java.lang.reflect.*;
public class AutorouteCompletionOracle {
 interface Call{Object run();}interface Reflection{Object run()throws ReflectiveOperationException;}
 static Object reflect(Reflection f){try{return f.run();}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static Object field(Class<?> c,Object o,String n){return reflect(()->{var f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);});}
 static void set(Class<?> c,Object o,String n,Object v){reflect(()->{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return null;});}
 static Object method(String n,Object o,Class<?>[] types,Object...a){return reflect(()->{var m=AutorouteEngine.class.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,a);});}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NegativeArraySizeException e){return "size_error";}catch(ConcurrentModificationException e){return "concurrent_error";}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(IllegalArgumentException e){return "argument_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double v){return Long.toHexString(Double.doubleToLongBits(v));}
 static double dbl(Scanner in){return Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));}
 static class Trace extends PolylineTrace {
  State s;int serial;int shapeIndex=1,layer=0;Trace(State s,int i){super(new Polyline(new Point[]{new IntPoint(10,10),new IntPoint(90,10)}),0,1,new int[]{i%3+1},1,100+i,0,FixedState.UNFIXED,null);this.s=s;serial=i;}
  public ItemAutorouteInfo getAutorouteInfo(){s.touch(700+serial,0);return super.getAutorouteInfo();}
  public TileShape getTreeShape(ShapeTree t,int i){s.touch(800+serial,i);return s.shapes[shapeIndex];}
  public int treeShapeCount(ShapeTree t){return 1;}public int shapeLayer(int i){return layer;}
  public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){s.touch(850+serial,i);return s.shapes[shapeIndex];}
 }
 static class Room extends CompleteFreeSpaceExpansionRoom {
  State s;int serial;Room(State s,int i,TileShape shape,int layer,int id){super(shape,layer,id);this.s=s;serial=i;}
  public TileShape getShape(){s.touch(100+serial,0);return super.getShape();}
  public int getLayer(){s.touch(200+serial,0);return super.getLayer();}
  public boolean removeDoor(ExpandableObject d){s.touch(300+serial,s.id(d));return super.removeDoor(d);}
  public void clearDoors(){s.touch(400+serial,0);super.clearDoors();}
  public void resetDoors(){s.touch(500+serial,0);super.resetDoors();}
 }
 static class InputRoom extends IncompleteFreeSpaceExpansionRoom {
  State s;InputRoom(State s,TileShape shape,int layer,TileShape contained){super(shape,layer,contained);this.s=s;}
  public TileShape getShape(){s.touch(980,0);return super.getShape();}public TileShape getContainedShape(){s.touch(981,0);return super.getContainedShape();}public int getLayer(){s.touch(982,0);return super.getLayer();}
 }
 static class Board extends RoutingBoard {
  State s;Board(State s,int size){this(s,size,new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)}));}
  Board(State s,int size,LayerStructure l){super(new IntBox(-100,-100,size==0?300:24900,size==0?300:19900),l,new PolylineShape[0],0,new BoardRules(l,new ClearanceMatrix(2,l,new String[]{"none","default"})),new Communication());this.s=s;}
  public Collection<Item> getItems(){s.touch(900,0);return s.nullItems?null:Arrays.<Item>asList(s.items).subList(1,5);}
  public void additionalUpdateAfterChange(Item i){if(s!=null)s.touch(901,s.id(i));}
  public void clearAllItemTemporaryAutorouteData(){s.touch(902,0);for(int i=1;i<5;i++)s.items[i].clearAutorouteInfo();}
 }
 static class Stop implements Stoppable {State s;boolean value;Stop(State s){this.s=s;}public void requestStop(){value=true;}public boolean isStopRequested(){s.touch(952,0);return value;}}
 static class Limit extends TimeLimit {State s;boolean value;Limit(State s){super(0);this.s=s;}public boolean limitExceeded(){s.touch(951,0);return value;}}
 static class Engine extends AutorouteEngine {
  State s;Engine(State s,int clearance,boolean maintain){super(s.board,clearance,maintain);this.s=s;}
  public int getNetNumber(){s.touch(971,0);return super.getNetNumber();}
  public int generateRoomIdNo(){s.touch(970,0);return super.generateRoomIdNo();}
  public void removeAllDoors(ExpansionRoom room){s.touch(972,s.id(room));super.removeAllDoors(room);}
  public IncompleteFreeSpaceExpansionRoom addIncompleteExpansionRoom(TileShape shape,int layer,TileShape contained){s.touch(973,layer);return super.addIncompleteExpansionRoom(shape,layer,contained);}
 }
 static class State {
  TileShape[] shapes={null,new IntBox(0,0,100,100),new IntBox(100,0,200,100),new IntBox(20,20,80,80),new IntBox(50,50,50,50),new IntBox(-50,0,0,100),Simplex.EMPTY,new IntBox(-10,-40,200,-20),new IntBox(-10,120,200,140),new IntBox(0,0,40,100),new IntBox(20,10,20,10),new IntBox(-80,-80,220,220),new IntBox(0,0,100,0)};
  ExpansionRoom[] rooms=new ExpansionRoom[9];Trace[] items=new Trace[5];Board board;AutorouteEngine engine;Stop stop=new Stop(this);Limit limit=new Limit(this);boolean nullItems;
  Collection<CompleteFreeSpaceExpansionRoom> completed;String events="";boolean active;int failure=-1,failAt=1,seen,next=1;IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
  int id(Object o){return o==null?0:ids.computeIfAbsent(o,k->next++);}
  void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
  String invoke(Call f){events="";seen=0;active=true;String v=call(f);active=false;return v+"~"+(events.isEmpty()?"-":events);}
  String shape(TileShape x){if(x==null)return "N";String s=x.getClass().getSimpleName()+":"+x.getId()+":"+x.dimension();for(int i=0;i<x.borderLineCount();i++)s+=","+x.borderLine(i).getId();return s;}
  String room(ExpansionRoom r){if(r==null)return "N";String s=id(r)+":"+shape(r.getShape())+":"+r.getLayer();if(r instanceof IncompleteFreeSpaceExpansionRoom x)s+=":"+shape(x.getContainedShape());if(r instanceof CompleteFreeSpaceExpansionRoom x){s+=":"+x.isNetDependent()+"T";for(var d:x.getTargetDoors())s+=id(d)+":"+id(d.item)+":"+d.getMazeSearchElement(0).isOccupied+",";}if(r instanceof ObstacleExpansionRoom x)s+="O"+x.allDoorsCalculated();s+="D";for(var d:r.getDoors())s+=id(d)+":"+id(d.firstRoom)+":"+id(d.secondRoom)+":"+d.dimension+",";return s;}
  String list(String name){@SuppressWarnings("unchecked")var list=(List<ExpansionRoom>)field(AutorouteEngine.class,engine,name);if(list==null)return "N";String s=id(list)+":";for(var r:list)s+=room(r)+"/";return s;}
  String dump(){String s="N"+engine.getNetNumber()+":"+field(AutorouteEngine.class,engine,"expansionRoomInstanceCount")+":"+(engine.stoppableThread==stop)+":"+(field(AutorouteEngine.class,engine,"timeLimit")==limit);s+="I"+list("incompleteExpansionRooms")+"C"+list("completeExpansionRooms");for(int i=1;i<rooms.length;i++)s+="R"+i+":"+room(rooms[i]);s+="A";for(int i=1;i<5;i++)s+=(items[i].getAutorouteInfoPur()!=null)+",";s+="E"+call(()->{String t="";for(var leaf:engine.autorouteSearchTree.toArray())t+=id(leaf.object)+":"+leaf.shapeIndexInObject+",";return t;});var pages=(DrillPageArray)field(AutorouteEngine.class,engine,"drillPageArray");s+="P";for(var page:pages.overlappingPages(board.boundingBox)){s+=shape(page.shape)+":";for(int i=0;i<page.mazeSearchElementCount();i++)s+=page.getMazeSearchElement(i).isOccupied+",";}s+="V";if(completed==null)s+="N";else{s+=id(completed)+":";for(var room:completed)s+=room(room)+"/";}return s;}
 }
 static String runEngine(Scanner in){State s=new State();boolean maintain=in.nextInt()!=0;int size=in.nextInt(),kind=in.nextInt();s.board=new Board(s,size);s.board.rules.setTraceAngleRestriction(kind==2?AngleRestriction.NINETY_DEGREE:kind==3?AngleRestriction.FORTYFIVE_DEGREE:AngleRestriction.NONE);for(int i=1;i<=4;i++)s.items[i]=new Trace(s,i);s.engine=new Engine(s,kind==0?0:1,maintain);for(int i=1;i<=3;i++)s.rooms[i]=new Room(s,i,s.shapes[in.nextInt()],0,in.nextInt());var out=new ArrayList<String>();out.add(s.dump());int n=in.nextInt();for(int k=0;k<n;k++){String op=in.next();if(Boolean.getBoolean("oracle.trace"))System.err.println("step="+k+" op="+op);int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();double value=dbl(in);out.add(s.invoke(()->switch(op){
 case "r"->{s.engine.addIncompleteExpansionRoom(s.shapes[b],c,s.shapes[d]);s.rooms[a]=new InputRoom(s,s.shapes[b],c,s.shapes[d]);@SuppressWarnings("unchecked")var rooms=(List<IncompleteFreeSpaceExpansionRoom>)field(AutorouteEngine.class,s.engine,"incompleteExpansionRooms");rooms.set(rooms.size()-1,(IncompleteFreeSpaceExpansionRoom)s.rooms[a]);yield s.id(s.rooms[a]);}
 case "i"->{s.engine.autorouteSearchTree.remove(s.items[a].getSearchTreeEntries(s.engine.autorouteSearchTree));s.items[a].shapeIndex=b;s.items[a].layer=c;s.engine.autorouteSearchTree.insert(s.items[a]);yield "ok";}
 case "c"->{s.completed=s.engine.completeExpansionRoom((IncompleteFreeSpaceExpansionRoom)s.rooms[a]);s.rooms[6]=s.completed.isEmpty()?null:s.completed.iterator().next();yield s.id(s.completed)+":"+s.completed.size();}
 case "n"->{s.engine.completeNeighbourRooms((CompleteExpansionRoom)s.rooms[a]);yield "ok";}
 case "a"->{s.rooms[7]=(ExpansionRoom)method("addCompleteRoom",s.engine,new Class<?>[]{IncompleteFreeSpaceExpansionRoom.class},(IncompleteFreeSpaceExpansionRoom)s.rooms[a]);yield s.id(s.rooms[7]);}
 case "d"->{s.rooms[7]=(ExpansionRoom)method("calculateDoors",s.engine,new Class<?>[]{ExpansionRoom.class},s.rooms[a]);yield s.id(s.rooms[7]);}
 case "o"->{s.rooms[8]=s.items[a].getAutorouteInfo().getExpansionRoom(b,s.engine.autorouteSearchTree);yield s.id(s.rooms[8]);}
 case "h"->{s.rooms[5]=s.engine.getFirstIncompleteExpansionRoom();yield s.id(s.rooms[5]);}
 case "A"->{s.rooms[a]=s.engine.addIncompleteExpansionRoom(s.shapes[b],c,s.shapes[d]);yield s.id(s.rooms[a]);}
 case "C"->{@SuppressWarnings("unchecked")var rooms=(List<CompleteFreeSpaceExpansionRoom>)field(AutorouteEngine.class,s.engine,"completeExpansionRooms");if(rooms==null){rooms=new ArrayList<>();set(AutorouteEngine.class,s.engine,"completeExpansionRooms",rooms);}rooms.add((CompleteFreeSpaceExpansionRoom)s.rooms[a]);if(b!=0)s.engine.autorouteSearchTree.insert((CompleteFreeSpaceExpansionRoom)s.rooms[a]);yield "ok";}
 case "D"->{var door=new ExpansionDoor(s.rooms[a],s.rooms[b],c);if((d&1)!=0)s.rooms[a].addDoor(door);if((d&2)!=0)s.rooms[b].addDoor(door);yield s.id(door);}
 case "R"->{s.engine.removeIncompleteExpansionRoom((IncompleteFreeSpaceExpansionRoom)s.rooms[a]);yield "ok";}
 case "X"->{s.engine.removeCompleteExpansionRoom((CompleteFreeSpaceExpansionRoom)s.rooms[a]);yield "ok";}
 case "Z"->{s.engine.removeAllDoors(s.rooms[a]);yield "ok";}
 case "N"->{s.engine.initConnection(a,b==0?null:s.stop,c==0?null:s.limit);yield "ok";}
 case "S"->{s.stop.value=a!=0;s.limit.value=b!=0;yield "ok";}
 case "Q"->s.engine.isStopRequested();
 case "G"->s.engine.generateRoomIdNo();
 case "g"->{set(AutorouteEngine.class,s.engine,"expansionRoomInstanceCount",a);yield "ok";}
 case "H"->s.id(s.engine.getFirstIncompleteExpansionRoom());
 case "K"->{s.engine.clear();yield "ok";}
 case "B"->{method("resetAllDoors",s.engine,new Class<?>[0]);yield "ok";}
 case "M"->{((CompleteFreeSpaceExpansionRoom)s.rooms[a]).setNetDependent();yield "ok";}
 case "F"->{s.failure=a;s.failAt=b;yield "ok";}
 case "V"->s.engine.validate();
 case "U"->{s.engine.invalidateDrillPages(s.shapes[a]);yield "ok";}
 case "T"->{var room=(CompleteFreeSpaceExpansionRoom)s.rooms[a];room.calculateTargetDoors(new ShapeTree.TreeEntry(s.items[b],0),s.items[b].getNetNumber(0),s.engine.autorouteSearchTree);var door=room.getTargetDoors().iterator().next();door.getMazeSearchElement(0).isOccupied=true;yield "ok";}
 case "L"->{Set<Item> selected=a<0?null:new TreeSet<>();if(selected!=null)for(int i=1;i<5;i++)if((a&(1<<i))!=0)selected.add(s.items[i]);@SuppressWarnings("unchecked")var rooms=(Set<CompleteFreeSpaceExpansionRoom>)method("getRoomsWithTargetItems",s.engine,new Class<?>[]{Set.class},selected);String r="";for(var room:rooms)r+=s.id(room)+",";yield r.isEmpty()?"-":r;}
 case "J"->{s.nullItems=a!=0;yield "ok";}
 case "P"->{var pages=(DrillPageArray)field(AutorouteEngine.class,s.engine,"drillPageArray");for(var page:pages.overlappingPages(s.board.boundingBox))for(int i=0;i<page.mazeSearchElementCount();i++)page.getMazeSearchElement(i).isOccupied=a!=0;yield "ok";}
 case "E"->{AutorouteDiagnostic.Sink sink=a==0?null:x->{s.touch(950,x.layer());s.events+="diag:"+x.kind().ordinal()+":"+x.shape().getId()+":"+bits(x.intensity())+",";};s.engine.emitDiagnostics(sink,value);yield "ok";}
 default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 static String runTime(Scanner in){int millis=in.nextInt();var t=new TimeLimit(millis);int n=in.nextInt();var out=new ArrayList<String>();for(int i=0;i<n;i++){t.multiply(dbl(in));out.add(String.valueOf(field(TimeLimit.class,t,"timeLimit")));}long now=new Date().getTime();for(long stamp:new long[]{now-10000000000L,now+10000000000L,Long.MIN_VALUE,Long.MAX_VALUE}){set(TimeLimit.class,t,"timeStamp",stamp);out.add(String.valueOf(t.limitExceeded()));}return String.join(" ",out);}
 static String runIterator(Scanner in){List<Integer> values=new ArrayList<>();Iterator<Integer> it=values.iterator();var out=new ArrayList<String>();int n=in.nextInt();for(int i=0;i<n;i++){String op=in.next();int a=in.nextInt();final var current=it;if(op.equals("I")){it=values.iterator();out.add("ok");}else out.add(call(()->switch(op){case "A"->{values.add(a);yield "ok";}case "S"->{values.set(a%3,a);yield "ok";}case "R"->values.remove(Integer.valueOf(a));case "C"->{values.clear();yield "ok";}case "H"->current.hasNext();case "N"->current.next();default->throw new AssertionError(op);}));String s="";for(int x:values)s+=x+",";out.add(s.isEmpty()?"-":s);}return String.join(" ",out);}
 static String run(String line){var in=new Scanner(line);return switch(in.next()){case "E"->runEngine(in);case "T"->runTime(in);case "I"->runIterator(in);default->throw new AssertionError();};}
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String s;while((s=in.readLine())!=null)System.out.println(s+" | "+run(s));}
}
