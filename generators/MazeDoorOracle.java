package app.freerouting.autoroute.maze;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.drill.ExpansionDrill;
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
public class MazeDoorOracle {
 interface Call{Object run();}
 static Object invoke(Object o,String name,Class<?>[] types,Object...args){try{var m=MazeSearchEngine.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static void set(Object o,Class<?> c,String n,Object v){try{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(ClassCastException e){return "cast_error";}catch(NegativeArraySizeException e){return "argument_error";}catch(IllegalArgumentException e){return "argument_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double x){return Long.toHexString(Double.doubleToLongBits(x));}static double dbl(Scanner s){return Double.longBitsToDouble(Long.parseUnsignedLong(s.next(),16));}
 static String line(FloatLine v){return v==null?"N":bits(v.a.x)+":"+bits(v.a.y)+":"+bits(v.b.x)+":"+bits(v.b.y);}
 static int origin(FloatPoint p,FloatLine l){return (p==l.a?1:0)+(p==l.b?2:0);}
 static String projection(FloatLine p,FloatLine l){return line(p)+(p==null?"":":"+origin(p.a,l)+":"+origin(p.b,l));}
 static class Settings extends RouterSettings {public boolean getViasAllowed(){return false;}public boolean getAutomaticNeckdown(){return false;}public boolean getLayerActive(int i){return true;}public double getBendCost(int i){return 0;}}
 static class Rules extends BoardRules {Rules(LayerStructure l){super(l,new ClearanceMatrix(2,l,new String[]{"none","default"}));viaRules.add(new ViaRule("default"));}public int getTraceHalfWidth(int n,int l){return 1;}}
 static class Comm extends Communication {State s;Comm(State s){this.s=s;}public double getResolution(Unit u){s.touch(400,0);return 1;}}
 static class TestTrace extends PolylineTrace {State s;TestTrace(State s){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(10,0)}),0,1,new int[]{1},1,10,0,FixedState.UNFIXED,null);this.s=s;}public int getId(){s.touch(200,0);return 10;}}
 static class Room extends CompleteFreeSpaceExpansionRoom {State s;int serial;Room(State s,int i){super(new IntBox(-100,-100,200,200),i-1,i);this.s=s;serial=i;}public int getLayer(){s.touch(100+serial,0);return s.layers[serial];}public TileShape getShape(){s.touch(110+serial,0);return s.shapes[s.roomShapes[serial]];}}
 static class Door extends ExpansionDoor {
  State s;int serial;Door(State s,int i){super((s.mask&1)!=0?s.rooms[1]:null,(s.mask&2)!=0?s.rooms[2]:null,s.dimension);this.s=s;serial=i;}
  public TileShape getShape(){s.touch(serial*10+1,0);return s.shapes[s.shapeIndex[serial]];}
  public int getDimension(){s.touch(serial*10+2,0);return serial>2?2:1;}
  public int mazeSearchElementCount(){s.touch(serial*10+3,0);return super.mazeSearchElementCount();}
  public MazeSearchElement getMazeSearchElement(int index){s.touch(serial*10+4,index);return super.getMazeSearchElement(index);}
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom room){s.touch(serial*10+5,room==null?0:((Room)room).serial);return s.rooms[s.other[serial]];}
  public int getId(){s.touch(serial*10+6,0);return serial;}
  public void reset(){throw new AssertionError();}
 }
 static class Target extends TargetItemExpansionDoor {
  State s;int serial;Target(State s,int i){super(s.item,0,null,s.engine.autorouteSearchTree);this.s=s;serial=i;}
  public TileShape getShape(){s.touch(serial*10+1,0);return s.shapes[s.shapeIndex[serial]];}
  public int getDimension(){s.touch(serial*10+2,0);return serial>2?2:1;}
  public int mazeSearchElementCount(){s.touch(serial*10+3,0);return 3;}
  public MazeSearchElement getMazeSearchElement(int index){s.touch(serial*10+4,index);return s.sections[serial][index];}
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom room){s.touch(serial*10+5,room==null?0:((Room)room).serial);return s.rooms[s.other[serial]];}
  public int getId(){s.touch(serial*10+6,0);return serial;}
  public void reset(){throw new AssertionError();}
 }
 static class Drill extends ExpansionDrill {
  State s;int serial;Drill(State s,int i){super(new IntBox(-5,-5,5,5),new IntPoint(0,0),0,1);this.s=s;serial=i;}
  public TileShape getShape(){s.touch(serial*10+1,0);return s.shapes[s.shapeIndex[serial]];}
  public int getDimension(){s.touch(serial*10+2,0);return serial>2?2:1;}
  public int mazeSearchElementCount(){s.touch(serial*10+3,0);return 3;}
  public MazeSearchElement getMazeSearchElement(int index){s.touch(serial*10+4,index);return s.sections[serial][index];}
  public CompleteExpansionRoom otherRoom(CompleteExpansionRoom room){s.touch(serial*10+5,room==null?0:((Room)room).serial);return s.rooms[s.other[serial]];}
  public int getId(){s.touch(serial*10+6,0);return serial;}
  public void reset(){throw new AssertionError();}
 }
 static class State {
  RoutingBoard board;Settings settings=new Settings();AutorouteControl ctrl;AutorouteEngine engine;MazeSearchEngine search;TestTrace item;ExpandableObject[] doors=new ExpandableObject[5];Room[] rooms=new Room[3];MazeSearchElement[][] sections=new MazeSearchElement[5][3];MazeListElement from;
  int[] roomShapes={0,7,8};int dimension,mask;int[] layers={0,0,1},shapeIndex={0,1,2,3,4},other={0,2,2,0,0};int failure=-1,failAt=1,seen,mutation=-1,mutateAt=1,mutations;boolean active;String events="";
  TileShape[] shapes={null,new IntBox(-5,-5,5,5),new IntBox(10,-5,20,5),new IntBox(-20,-5,-10,5),new IntBox(0,10,10,20),Simplex.EMPTY,new IntBox(1,1,1,1),new IntBox(-100,-100,100,100),new IntBox(100,-100,300,100),new IntBox(-100,0,100,1),TileShape.getInstance(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(0,10)})};
  FloatLine[] lines={null,new FloatLine(new FloatPoint(0,0),new FloatPoint(0,0)),new FloatLine(new FloatPoint(10,0),new FloatPoint(10,0)),new FloatLine(new FloatPoint(10,10),new FloatPoint(10,20)),new FloatLine(new FloatPoint(-5,0),new FloatPoint(5,0)),new FloatLine(new FloatPoint(Double.NaN,0),new FloatPoint(Double.POSITIVE_INFINITY,-0.0)),new FloatLine(new FloatPoint(10,1),new FloatPoint(10,1)),new FloatLine(new FloatPoint(10,2),new FloatPoint(10,2))};
  IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();int nextId=1;
  State(int dimension,int mask){this.dimension=dimension;this.mask=mask;var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});board=new RoutingBoard(new IntBox(-100,-100,300,300),layers,new PolylineShape[0],0,new Rules(layers),new Comm(this));ctrl=new AutorouteControl(board,1,settings,0,new AutorouteControl.ExpansionCostFactor[]{new AutorouteControl.ExpansionCostFactor(1,2),new AutorouteControl.ExpansionCostFactor(2,1)});engine=new AutorouteEngine(board,0,false);search=new MazeSearchEngine(engine,ctrl);search.destinationDistance.join(new IntBox(100,100,110,110),0);item=new TestTrace(this);for(int i=1;i<3;i++)rooms[i]=new Room(this,i);doors[1]=new Door(this,1);doors[2]=new Door(this,2);doors[3]=new Target(this,3);doors[4]=new Drill(this,4);for(int i=1;i<5;i++)for(int j=0;j<3;j++)sections[i][j]=new MazeSearchElement();from=new MazeListElement(doors[2],0,doors[1],0,0,0,rooms[1],lines[1],false,MazeSearchElement.Adjustment.NONE,false);}
  void touch(int c,int a){if(!active)return;events+=c+":"+a+",";if(c==failure&&++seen==failAt)throw new IllegalStateException();if(c==mutation&&++mutations==mutateAt){ctrl.bendCosts[0]=17;ctrl.bendCosts[1]=41;ctrl.traceCosts[0]=new AutorouteControl.ExpansionCostFactor(3,5);ctrl.traceCosts[1]=new AutorouteControl.ExpansionCostFactor(7,11);layers[1]=1;}}
  String run(Call f){events="";seen=0;mutations=0;active=true;String r=call(f);active=false;return r+"~"+(events.isEmpty()?"-":events);}
  int id(Object o){return o==null?0:ids.computeIfAbsent(o,k->nextId++);}
  int doorId(ExpandableObject d){for(int i=0;i<doors.length;i++)if(doors[i]==d)return i;throw new AssertionError();}
  int lineId(FloatLine l){for(int i=0;i<lines.length;i++)if(lines[i]==l)return i;throw new AssertionError();}
  String dump(){String out="Q";for(var e:search.mazeExpansionList)out+=id(e)+":"+doorId(e.door)+":"+e.sectionNoOfDoor+":"+doorId(e.backtrackDoor)+":"+e.sectionNoOfBacktrackDoor+":"+bits(e.expansionValue)+":"+bits(e.sortingValue)+":"+(e.nextRoom==null?0:((Room)e.nextRoom).serial)+":"+line(e.shapeEntry)+":"+e.roomRipped+":"+(e.adjustment==null?-1:e.adjustment.ordinal())+":"+e.alreadyChecked+":"+e.ripupCost+"/";out+="C";for(int i=0;i<2;i++)out+=bits(ctrl.bendCosts[i])+":"+(ctrl.traceCosts[i]==null?"N":bits(ctrl.traceCosts[i].horizontal())+":"+bits(ctrl.traceCosts[i].vertical()))+",";out+="A";for(int i=1;i<3;i++){MazeSearchElement[] cells=((ExpansionDoor)doors[i]).sectionArr;out+=cells==null?"N":Arrays.stream(cells).map(e->e==null?"N":String.valueOf(e.isOccupied)).reduce("",(a,b)->a+b+",");out+="/";}return out+"L"+layers[1]+":"+layers[2];}
 }
 static String run(String input){var in=new Scanner(input);in.next();State s=new State(in.nextInt(),in.nextInt());int count=in.nextInt();var out=new ArrayList<String>();out.add(s.dump());for(int j=0;j<count;j++){String op=in.next();int[] a=new int[8];double[] d=new double[4];for(int i=0;i<8;i++)a[i]=in.nextInt();for(int i=0;i<4;i++)d[i]=dbl(in);out.add(s.run(()->switch(op){
 case "S"->invoke(s.search,"expandToDoor",new Class<?>[]{ExpansionDoor.class,MazeListElement.class,int.class,boolean.class,MazeSearchElement.Adjustment.class},(ExpansionDoor)s.doors[a[0]],a[3]==0?s.from:null,a[4],a[2]!=0,a[5]<0?null:MazeSearchElement.Adjustment.values()[a[5]]);
 case "P"->{s.from=new MazeListElement(s.doors[a[0]],a[1],s.doors[a[2]],a[3],d[0],d[1],s.rooms[a[4]],s.lines[a[5]],(a[6]&1)!=0,a[7]<0?null:MazeSearchElement.Adjustment.values()[a[7]],(a[6]&2)!=0);yield "ok";}
 case "C"->{s.ctrl.bendCosts[a[0]]=d[0];s.ctrl.traceCosts[a[0]]=a[1]==0?new AutorouteControl.ExpansionCostFactor(d[1],d[2]):null;yield "ok";}
 case "F"->{s.failure=a[0];s.failAt=a[1];yield "ok";}
 case "M"->{s.mutation=a[0];s.mutateAt=a[1];yield "ok";}
 case "H"->{s.shapeIndex[a[0]]=a[1];s.other[a[0]]=a[2];yield "ok";}
 case "L"->{s.layers[a[0]]=a[1];yield "ok";}
 case "O"->{MazeSearchElement[] cells=((ExpansionDoor)s.doors[a[0]]).sectionArr;if(a[2]<0)cells[a[1]]=null;else {cells[a[1]]=new MazeSearchElement();cells[a[1]].isOccupied=a[2]!=0;}yield "ok";}
 case "W"->{s.ctrl.compensatedTraceHalfWidth[a[0]]=a[1];s.roomShapes[1]=a[2];s.roomShapes[2]=a[3];yield "ok";}
 case "V"->{s.ctrl.isFanout=a[0]!=0;s.ctrl.fanoutStartPinLayer=a[1];s.ctrl.fanoutStartPinCenter=a[2]!=0?new IntPoint(0,0):null;s.settings.fanout=new FanoutSettings();s.settings.fanout.minEscapeLengthMm=d[0];s.settings.fanout.maxEscapeLengthMm=d[1];yield "ok";}
 case "J"->{s.search.destinationDistance.join(new IntBox(a[1],a[2],a[3],a[4]),a[0]);yield "ok";}
 case "R"->{if(a[0]==0){s.search.mazeExpansionList.clear();yield "ok";}var it=s.search.mazeExpansionList.iterator();if(!it.hasNext())yield "empty";var e=it.next();it.remove();yield s.id(e);}
 default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String l;while((l=in.readLine())!=null)System.out.println(l+" | "+run(l));}
}
