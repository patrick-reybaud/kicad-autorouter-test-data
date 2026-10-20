package app.freerouting.autoroute.maze;
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
public class MazeGeometryOracle {
 interface Call{Object run();}
 static Object invoke(Object o,String name,Class<?>[] types,Object...args){try{var m=MazeSearchEngine.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static void set(Object o,Class<?> c,String n,Object v){try{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalArgumentException e){return "argument_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double x){return Long.toHexString(Double.doubleToLongBits(x));}static double dbl(Scanner s){return Double.longBitsToDouble(Long.parseUnsignedLong(s.next(),16));}
 static String line(FloatLine v){return v==null?"N":bits(v.a.x)+":"+bits(v.a.y)+":"+bits(v.b.x)+":"+bits(v.b.y);}
 static int origin(FloatPoint p,FloatLine l){return (p==l.a?1:0)+(p==l.b?2:0);}
 static String projection(FloatLine p,FloatLine l){return line(p)+(p==null?"":":"+origin(p.a,l)+":"+origin(p.b,l));}
 static class Settings extends RouterSettings {public boolean getViasAllowed(){return false;}public boolean getAutomaticNeckdown(){return false;}public boolean getLayerActive(int i){return true;}public double getBendCost(int i){return 0;}}
 static class Rules extends BoardRules {
  State s;Rules(State s,LayerStructure l){super(l,new ClearanceMatrix(2,l,new String[]{"none","default"}));this.s=s;viaRules.add(new ViaRule("default"));}
  public int getTraceHalfWidth(int n,int l){return 1;}
  public AngleRestriction getTraceAngleRestriction(){s.touch(102,0);return s.angle<0?null:AngleRestriction.valueOf(s.angle);}
 }

 static class Trace extends PolylineTrace {
  State s;Trace(State s){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(100,0)}),0,10,new int[]{1},1,11,0,FixedState.UNFIXED,null);this.s=s;}
  public TileShape getTreeShape(ShapeTree t,int i){return new IntBox(-10,-10,110,10);}
  public int getHalfWidth(){s.touch(203,0);return s.halfWidth;}
  public int clearanceClassIndex(){s.touch(204,0);return s.clearance;}
 }
 static class TestVia extends Via {
  State s;TestVia(State s){super(null,new IntPoint(0,0),new int[]{1},1,12,0,FixedState.UNFIXED,false,null);this.s=s;}
  public TileShape getTreeShapeOnLayer(ShapeSearchTree t,int l){s.touch(206,l);return s.shapes[s.shape];}
 }
 static class TestPin extends Pin {
  State s;int serial;TestPin(State s,int serial){super(0,serial,new int[]{1},1,12+serial,FixedState.UNFIXED,null);this.s=s;this.serial=serial;}
  public int getTraceNeckdownHalfwidth(int l){s.touch(304+serial,l);return serial==1?s.neck1:s.neck2;}
 }
 static class Room extends CompleteFreeSpaceExpansionRoom {
  State s;Room(State s,int id){super(new IntBox(-10,-10,110,10),0,id);this.s=s;}
  public Collection<TargetItemExpansionDoor> getTargetDoors(){s.touch(303,0);return s.targets;}
  public int getLayer(){s.touch(304,0);return s.layer;}
 }
 static class Obstacle extends ObstacleExpansionRoom {
  State s;Obstacle(State s){super(s.nodes[1],0,s.tree);this.s=s;}
  public Item getItem(){s.touch(201,0);return s.nodes[s.item];}
  public int getLayer(){s.touch(202,0);return s.layer;}
 }
 static class Door extends ExpansionDoor {
  State s;Door(State s,ExpansionRoom a,ExpansionRoom b,int dim){super(a,b,dim);this.s=s;}
  public TileShape getShape(){s.touch(101,0);return s.shapes[s.shape];}
 }
 static class State {
  RoutingBoard board;AutorouteControl ctrl;AutorouteEngine engine;MazeSearchEngine search;ShapeSearchTree tree;Item[] nodes=new Item[5];Room room;Obstacle obstacle;Collection<TargetItemExpansionDoor> targets=new LinkedList<>();
  int angle,shape,item,layer,halfWidth,clearance,compensation,neck1,neck2,failure=-1,mutation=-1;boolean active;String events="";
  TileShape[] shapes={null,Simplex.EMPTY,new IntBox(0,0,0,0),new IntBox(0,0,10,0),new IntBox(-10,-10,10,10),new IntOctagon(-10,-10,10,10,-12,12,-12,12),new IntBox(-20,0,30,5),TileShape.getInstance(new Point[]{new IntPoint(0,0),new IntPoint(40,0),new IntPoint(0,15)}),new IntBox(-1073741823,-10,1073741823,10)};
  State(){var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});board=new RoutingBoard(new IntBox(-100,-100,300,300),layers,new PolylineShape[0],0,new Rules(this,layers),new Communication());ctrl=new AutorouteControl(board,1,new Settings(),0,new AutorouteControl.ExpansionCostFactor[]{new AutorouteControl.ExpansionCostFactor(1,2),new AutorouteControl.ExpansionCostFactor(2,1)});tree=new MazeGeometryTree(board,(c,l)->{touch(205,c*10+l);return compensation;});engine=new AutorouteEngine(board,0,false);set(engine,AutorouteEngine.class,"autorouteSearchTree",tree);search=new MazeSearchEngine(engine,ctrl);nodes[1]=new Trace(this);nodes[2]=new TestVia(this);nodes[3]=new TestPin(this,1);nodes[4]=new TestPin(this,2);room=new Room(this,1);obstacle=new Obstacle(this);}
  void touch(int c,int a){if(!active)return;events+=c+":"+a+",";if(c==failure)throw new IllegalStateException();if(c==mutation){layer=1;halfWidth=7;clearance=3;compensation=19;neck1=-5;neck2=88;ctrl.compensatedTraceHalfWidth[0]=2;ctrl.compensatedTraceHalfWidth[1]=17;angle=2;}}
  String run(Call f){events="";active=true;String r=call(f);active=false;return r+"~"+(events.isEmpty()?"-":events);}
  String dump(){return layer+":"+halfWidth+":"+clearance+":"+compensation+":"+neck1+":"+neck2+":"+angle+":"+bits(ctrl.compensatedTraceHalfWidth[0])+":"+bits(ctrl.compensatedTraceHalfWidth[1]);}
 }
 static String run(String input){var in=new Scanner(input);String op=in.next();
  if(op.equals("P")){FloatPoint a=new FloatPoint(dbl(in),dbl(in)),b=new FloatPoint(dbl(in),dbl(in)),c=new FloatPoint(dbl(in),dbl(in)),d=new FloatPoint(dbl(in),dbl(in));int aliases=in.nextInt();if((aliases&1)!=0)b=a;if((aliases&2)!=0)d=c;FloatLine from=new FloatLine(a,b),to=new FloatLine(c,d);FloatLine check=from.adjustDirection(to);return line(check)+" "+projection(to.segmentProjection(check),to)+" "+projection(to.segmentProjection2(check),to)+" "+line((FloatLine)invoke(null,"segmentProjection",new Class<?>[]{FloatLine.class,FloatLine.class},from,to));}
  State s=new State();s.failure=in.nextInt();s.mutation=in.nextInt();String result;
  if(op.equals("D")){int dim=in.nextInt(),rooms=in.nextInt();s.shape=in.nextInt();s.angle=in.nextInt();double width=dbl(in);ExpansionRoom a=(rooms&1)!=0?s.room:s.obstacle,b=(rooms&2)!=0?s.room:null;Door door=new Door(s,a,b,dim);result=s.run(()->invoke(s.search,"doorIsSmall",new Class<?>[]{ExpansionDoor.class,double.class},door,width));}
  else if(op.equals("R")){s.item=in.nextInt();s.layer=in.nextInt();s.halfWidth=in.nextInt();s.clearance=in.nextInt();s.compensation=in.nextInt();s.shape=in.nextInt();s.ctrl.compensatedTraceHalfWidth[0]=in.nextInt();s.ctrl.compensatedTraceHalfWidth[1]=in.nextInt();result=s.run(()->invoke(s.search,"roomShapeIsThick",new Class<?>[]{ObstacleExpansionRoom.class},s.obstacle));}
  else if(op.equals("N")){s.layer=in.nextInt();s.neck1=in.nextInt();s.neck2=in.nextInt();int count=in.nextInt();if(count<0)s.targets=null;else for(int i=0;i<count;i++){int item=in.nextInt();s.targets.add(item<0?null:new TargetItemExpansionDoor(s.nodes[item],0,null,s.tree));}result=s.run(()->bits((Double)invoke(s.search,"checkNeckDownAtDestPin",new Class<?>[]{CompleteExpansionRoom.class},s.room)));}
  else throw new AssertionError(op);
  return result+" "+s.dump();
 }
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String l;while((l=in.readLine())!=null)System.out.println(l+" | "+run(l));}
}
