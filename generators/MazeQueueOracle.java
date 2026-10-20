package app.freerouting.autoroute.maze;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.drill.*;
import app.freerouting.board.facade.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.Communication;
import app.freerouting.rules.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.settings.*;
import java.io.*;import java.util.*;
public class MazeQueueOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(NoSuchElementException|IndexOutOfBoundsException e){return "index_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double x){return Long.toHexString(Double.doubleToLongBits(x));}
 static double dbl(Scanner in){return Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));}
 static class Settings extends RouterSettings {
  public boolean getViasAllowed(){return false;}public boolean getAutomaticNeckdown(){return false;}public boolean getLayerActive(int i){return true;}public double getBendCost(int i){return 0;}
 }
 static class Rules extends BoardRules {
  Rules(LayerStructure l){super(l,new ClearanceMatrix(2,l,new String[]{"none","default"}));viaRules.add(new ViaRule("default"));}
  public int getTraceHalfWidth(int net,int layer){return 1;}
 }
 static class Comm extends Communication {
  State s;Comm(State s){this.s=s;}
  public double getResolution(Unit u){if(u!=Unit.UM)throw new AssertionError();int i=s.resCalls++;s.touch(200,i);return s.resolutions[i%2];}
 }
 static class Room extends CompleteFreeSpaceExpansionRoom {
  State s;int serial;Room(State s,int i){super(new IntBox(0,0,100,100),i-1,i);this.s=s;serial=i;}
  public int getLayer(){s.touch(100+serial,serial-1);return super.getLayer();}
 }
 static class Door extends ExpansionDoor {
  State s;int serial;Door(State s,int i){super(null,null,2);this.s=s;serial=i;}
  public int getId(){s.touch(300+serial,0);return serial;}
 }
 static class Drill extends ExpansionDrill {
  State s;int serial;Drill(State s,int i,Point p){super(new IntBox(0,0,100,100),p,0,1);this.s=s;serial=i;}
  public int getId(){s.touch(300+serial,0);return serial;}
 }
 static class State {
  Settings settings=new Settings();AutorouteControl ctrl;SortedSet<MazeListElement> queue;MazeListElement[] elements=new MazeListElement[16];Room[] rooms=new Room[4];double[] resolutions={1,1};int resCalls,failure=-1,failAt=1,seen,mutation,nextId=1;boolean active;String events="";IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
  State(boolean nullCommunication){var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var board=new RoutingBoard(new IntBox(-100,-100,100,100),layers,new PolylineShape[0],0,new Rules(layers),new Comm(this));ctrl=new AutorouteControl(board,1,settings,0,new AutorouteControl.ExpansionCostFactor[]{new AutorouteControl.ExpansionCostFactor(1,2),new AutorouteControl.ExpansionCostFactor(1,2)});var engine=new AutorouteEngine(board,0,false);queue=new MazeSearchEngine(engine,ctrl).mazeExpansionList;if(nullCommunication)try{var field=BasicBoard.class.getField("communication");field.setAccessible(true);field.set(board,null);}catch(ReflectiveOperationException e){throw new AssertionError(e);}for(int i=1;i<4;i++)rooms[i]=new Room(this,i);}
  void configure(int mode,double min,double max){settings.fanout=mode==0?null:new FanoutSettings();if(mode>=2)settings.fanout.minEscapeLengthMm=min;if(mode>=3)settings.fanout.maxEscapeLengthMm=max;if(mode==3)settings.fanout.minEscapeLengthMm=null;}
  void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code>=100&&code<200){if(mutation==1)ctrl.fanoutStartPinLayer=arg;if(mutation==2)ctrl.fanoutStartPinCenter=null;if(mutation==3)configure(4,0,0);if(mutation==4)ctrl.isFanout=false;}if(code==200){if(mutation==11)settings.fanout=null;if(mutation==12)configure(4,0,0);if(mutation==13)configure(4,1000,1000);if(mutation==14)ctrl.fanoutStartPinCenter=null;if(mutation==15)ctrl.isFanout=false;}if(code==failure&&++seen==failAt)throw new IllegalStateException();}
  int id(Object o){return o==null?0:ids.get(o);}
  String invoke(Call f){events="";seen=0;resCalls=0;active=true;String v=call(f);active=false;return v+"~"+(events.isEmpty()?"-":events);}
  String dump(){String s=ctrl.isFanout+":"+(ctrl.fanoutStartPinCenter==null?"N":ctrl.fanoutStartPinCenter.getId())+":"+ctrl.fanoutStartPinLayer+":";var f=settings.fanout;s+=f==null?"N":(f.minEscapeLengthMm==null?"N":bits(f.minEscapeLengthMm))+","+(f.maxEscapeLengthMm==null?"N":bits(f.maxEscapeLengthMm));s+="Q"+queue.isEmpty()+":"+queue.size()+":";for(var e:queue)s+=id(e)+",";return s;}
 }
 static String run(String line){var in=new Scanner(line);in.next();State s=new State(in.nextInt()!=0);var out=new ArrayList<String>();int n=in.nextInt();for(int k=0;k<n;k++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();double x=dbl(in),y=dbl(in),z=dbl(in),w=dbl(in);out.add(s.invoke(()->switch(op){
 case "F"->{s.ctrl.isFanout=a!=0;s.ctrl.fanoutStartPinCenter=b==0?null:new IntPoint((int)x,(int)y);s.ctrl.fanoutStartPinLayer=c;yield "ok";}
 case "Q"->{s.configure(a,x,y);yield "ok";}
 case "R"->{s.resolutions[0]=x;s.resolutions[1]=y;yield "ok";}
 case "X"->{s.mutation=a;s.failure=b;s.failAt=c;yield "ok";}
 case "E"->{ExpandableObject door=b==0?null:b==1?new Door(s,d%3):new Drill(s,d%3,(d&8)!=0?null:new IntPoint((int)x,(int)y));FloatLine entry=(d&4)!=0?null:new FloatLine(new FloatPoint(x,y),new FloatPoint(x,y));var element=new MazeListElement(door,d%2,null,0,z,w,s.rooms[c],entry,false,MazeSearchElement.Adjustment.NONE,false);s.elements[a]=element;s.ids.put(element,s.nextId++);yield s.id(element);}
 case "A"->s.queue.add(s.elements[a]);
 case "P"->{var it=s.queue.iterator();var e=it.next();it.remove();yield s.id(e);}
 case "C"->{s.queue.clear();yield "ok";}
 default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null)System.out.println(line+" | "+run(line));}
}
