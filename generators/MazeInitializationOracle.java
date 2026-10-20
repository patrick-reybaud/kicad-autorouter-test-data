package app.freerouting.autoroute.maze;
import app.freerouting.autoroute.*;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.board.facade.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.Communication;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.searchtree.*;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import app.freerouting.settings.*;
import java.io.*;import java.util.*;import java.lang.reflect.*;
public class MazeInitializationOracle {
 interface Call{Object run();}interface Reflect{Object run()throws ReflectiveOperationException;}
 static Object reflect(Reflect f){try{return f.run();}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static Object field(Object o,Class<?> c,String n){return reflect(()->{var f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);});}
 static void set(Object o,Class<?> c,String n,Object v){reflect(()->{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return null;});}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalArgumentException e){return "argument_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double x){return Long.toHexString(Double.doubleToLongBits(x));}static double dbl(Scanner s){return Double.longBitsToDouble(Long.parseUnsignedLong(s.next(),16));}
 static class Settings extends RouterSettings {public boolean getViasAllowed(){return false;}public boolean getAutomaticNeckdown(){return false;}public boolean getLayerActive(int i){return true;}public double getBendCost(int i){return 0;}}
 static class Rules extends BoardRules {Rules(LayerStructure l){super(l,new ClearanceMatrix(2,l,new String[]{"none","default"}));viaRules.add(new ViaRule("default"));}public int getTraceHalfWidth(int n,int l){return 1;}}
 static class Comm extends Communication {public double getResolution(Unit u){if(u!=Unit.UM)throw new AssertionError();return 1;}}
 static class Trace extends PolylineTrace {
  State s;int serial;Trace(State s,int i){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(100,0)}),0,10,new int[]{i==1?2:1},1,10+i,0,FixedState.UNFIXED,s.board);this.s=s;serial=i;}
  public ItemAutorouteInfo getAutorouteInfo(){s.touch(100+serial,0);return super.getAutorouteInfo();}
  public int treeShapeCount(ShapeTree t){s.touch(200+serial,0);return super.treeShapeCount(t);}
  public TileShape getTreeShape(ShapeTree t,int i){s.touch(300+serial,i);return super.getTreeShape(t,i);}
  public int shapeLayer(int i){s.touch(400+serial,i);return serial-1;}
  public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){s.touch(500+serial,i);return super.getTreeShape(t,i);}
  TileShape raw(ShapeTree t){return super.getTreeShape(t,0);}
 }
 static class TestPin extends Pin {
  State s;TestPin(State s){super(0,0,new int[]{1,2},1,13,FixedState.UNFIXED,s.board);this.s=s;}
  public Point getCenter(){s.touch(600,0);return s.pinCenter==0?new IntPoint(0,0):new IntPoint(100,0);}
  public TileShape getTreeShapeOnLayer(ShapeSearchTree t,int l){s.touch(601,l);return s.shapes[s.pinShape];}
  public Set<Item> getNormalContacts(){s.touch(602,0);return s.items(s.contactMask);}
  public ItemAutorouteInfo getAutorouteInfo(){s.touch(103,0);return super.getAutorouteInfo();}
  public int treeShapeCount(ShapeTree t){s.touch(203,0);return super.treeShapeCount(t);}
  public TileShape getTreeShape(ShapeTree t,int i){s.touch(303,i);return super.getTreeShape(t,i);}
  public int shapeLayer(int i){s.touch(403,i);return 0;}
  public TileShape getTraceConnectionShape(ShapeSearchTree t,int i){s.touch(503,i);return super.getTreeShape(t,i);}
 }
 static class Room extends CompleteFreeSpaceExpansionRoom {
  State s;int serial;Room(State s,int i){super(new IntBox(-50,-50,150,50),i-1,i);this.s=s;serial=i;}
  public Collection<TargetItemExpansionDoor> getTargetDoors(){s.touch(800+serial,0);return super.getTargetDoors();}
  public TileShape getShape(){s.touch(810+serial,0);return super.getShape();}
  public int getLayer(){s.touch(820+serial,0);return super.getLayer();}
 }
 static class Engine extends AutorouteEngine {
  State s;Engine(State s,boolean maintain){super(s.board,0,maintain);this.s=s;}
  public boolean isStopRequested(){int i=++s.stopCalls;s.touch(900,i);return s.stopAt>0&&i>=s.stopAt;}
  public IncompleteFreeSpaceExpansionRoom addIncompleteExpansionRoom(TileShape shape,int layer,TileShape contained){s.touch(700,layer);return super.addIncompleteExpansionRoom(shape,layer,contained);}
  public Collection<CompleteFreeSpaceExpansionRoom> completeExpansionRoom(IncompleteFreeSpaceExpansionRoom room){s.touch(701,++s.completeCalls);return switch(s.completeMode){case 0->new ArrayList<>(List.of(s.rooms[1]));case 1->new ArrayList<>(List.of(s.rooms[2],s.rooms[1],s.rooms[2]));case 2->new ArrayList<>();case 3->null;default->throw new AssertionError();};}
 }
 static class State {
  RoutingBoard board;Settings settings=new Settings();AutorouteControl ctrl;Engine engine;MazeSearchEngine search;Item[] nodes=new Item[4];Room[] rooms=new Room[3];int failure=-1,failAt=1,seen,stopAt,stopCalls,completeCalls,completeMode,contactMask=6,pinCenter,pinShape=2;boolean active;String events="";IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();int nextId=1;
  TileShape[] shapes={null,new IntBox(-10,-10,120,10),new IntBox(-5,-5,5,5),new IntBox(95,-5,105,5),new IntBox(-30,-30,130,30),Simplex.EMPTY};
  State(boolean maintain,int mode,int seed){var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});board=new RoutingBoard(new IntBox(-100,-100,300,300),layers,new PolylineShape[0],0,new Rules(layers),new Comm());ctrl=new AutorouteControl(board,1,settings,0,new AutorouteControl.ExpansionCostFactor[]{new AutorouteControl.ExpansionCostFactor(1,2),new AutorouteControl.ExpansionCostFactor(2,1)});ctrl.ripupCosts=seed;engine=new Engine(this,maintain);engine.initConnection(1,null,null);completeMode=mode;nodes[1]=new Trace(this,1);nodes[2]=new Trace(this,2);nodes[3]=new TestPin(this);for(int i=1;i<4;i++){nodes[i].setSearchTreeEntries(new ShapeTree.Leaf[1],engine.autorouteSearchTree);nodes[i].setPrecalculatedTreeShapes(new TileShape[]{shapes[i==3?2:1]},engine.autorouteSearchTree);}for(int i=1;i<3;i++){rooms[i]=new Room(this,i);rooms[i].getTargetDoors().add(new TargetItemExpansionDoor(nodes[i],0,rooms[i],engine.autorouteSearchTree));rooms[i].getTargetDoors().add(new TargetItemExpansionDoor(nodes[3],0,rooms[i],engine.autorouteSearchTree));}set(engine,AutorouteEngine.class,"completeExpansionRooms",new ArrayList<>(List.of(rooms[2],rooms[1])));search=new MazeSearchEngine(engine,ctrl);}
  Set<Item> items(int mask){if(mask<0)return null;var r=new LinkedHashSet<Item>();for(int i=0;i<4;i++)if((mask&(1<<i))!=0)r.add(nodes[i]);return r;}
  void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
  int id(Object o){return o==null?0:ids.computeIfAbsent(o,k->nextId++);}
  String invoke(Call f){seen=0;stopCalls=0;completeCalls=0;events="";active=true;String v=call(f);active=false;return v+"~"+(events.isEmpty()?"-":events);}
  String shape(TileShape t){if(t==null)return "N";String s=t.getClass().getSimpleName()+":"+t.getId()+":"+t.dimension();for(int i=0;i<t.borderLineCount();i++)s+=","+t.borderLine(i).getId();return s;}
  String dump(){String s="I";for(int i=1;i<4;i++){var info=nodes[i].getAutorouteInfoPur();s+=info==null?"N,":info.isStartInfo()+",";}@SuppressWarnings("unchecked")var incomplete=(List<IncompleteFreeSpaceExpansionRoom>)field(engine,AutorouteEngine.class,"incompleteExpansionRooms");s+="R";if(incomplete==null)s+="N";else for(var room:incomplete)s+=id(room)+":"+room.getLayer()+":"+shape(room.getContainedShape())+"/";s+="T";for(int i=1;i<3;i++)s+=shape(((Trace)nodes[i]).raw(engine.autorouteSearchTree))+"/";s+="S"+id(search);if(search!=null){s+="D";for(int layer=0;layer<2;layer++){final int l=layer;for(var p:new FloatPoint[]{new FloatPoint(0,0),new FloatPoint(200,200)})s+=call(()->bits(search.destinationDistance.calculate(p,l)))+",";}s+="Q";for(var e:search.mazeExpansionList)s+=id(e)+":"+e.door.getId()+":"+e.sectionNoOfDoor+":"+bits(e.expansionValue)+":"+bits(e.sortingValue)+":"+e.nextRoom.getLayer()+":"+bits(e.shapeEntry.a.x)+":"+bits(e.shapeEntry.a.y)+",";}return s;}
 }
 static String run(String line){var in=new Scanner(line);in.next();State s=new State(in.nextInt()!=0,in.nextInt(),in.nextInt());int n=in.nextInt();var out=new ArrayList<String>();out.add(s.dump());for(int k=0;k<n;k++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();double x=dbl(in),y=dbl(in);out.add(s.invoke(()->switch(op){
 case "I"->{if(c==0)yield reflect(()->{var m=MazeSearchEngine.class.getDeclaredMethod("init",Set.class,Set.class);m.setAccessible(true);return m.invoke(s.search,s.items(a),s.items(b));});s.search=MazeSearchEngine.getInstance(s.items(a),s.items(b),s.engine,s.ctrl);yield s.id(s.search);}
 case "Q"->{s.ctrl.ripupCosts=a;s.ctrl.minNormalViaCost=x;s.ctrl.minCheapViaCost=y;s.search=new MazeSearchEngine(s.engine,s.ctrl);yield s.id(s.search);}
 case "F"->{s.failure=a;s.failAt=b;yield "ok";}
 case "S"->{s.stopAt=a;yield "ok";}
 case "M"->{s.completeMode=a;yield "ok";}
 case "C"->{s.ctrl.isFanout=a!=0;s.ctrl.layerActive[0]=(b&1)!=0;s.ctrl.layerActive[1]=(b&2)!=0;s.ctrl.fanoutStartPinCenter=new IntPoint(0,0);s.ctrl.fanoutStartPinLayer=c;s.settings.fanout=new FanoutSettings();s.settings.fanout.minEscapeLengthMm=x;s.settings.fanout.maxEscapeLengthMm=y;yield "ok";}
 case "P"->{s.contactMask=a;s.pinCenter=b;s.pinShape=c;yield "ok";}
 case "B"->{for(int i=1;i<4;i++)if((a&(1<<i))!=0)s.nodes[i].getAutorouteInfo().setStartInfo(b!=0);yield "ok";}
 case "R"->s.search.randomGenerator.nextInt(a);
 default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null)System.out.println(line+" | "+run(line));}
}
