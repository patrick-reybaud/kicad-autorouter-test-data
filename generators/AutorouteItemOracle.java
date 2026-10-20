package app.freerouting.board.model.items;
import app.freerouting.autoroute.*;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.maze.*;
import app.freerouting.autoroute.path.Connection;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.actions.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.datastructures.ShapeTree;
import java.io.*;
import java.util.*;
public class AutorouteItemOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(NegativeArraySizeException e){return "size_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}catch(IllegalArgumentException e){return "argument_error";}}
 static String bits(double d){return Long.toHexString(Double.doubleToLongBits(d));}
 static double dbl(Scanner in){return Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));}
 static class Data{
  State state;int serial,id,count,layer;boolean route;TileShape[] shapes=new TileShape[6];
  Data(State s,int serial,Scanner in){state=s;this.serial=serial;id=in.nextInt();count=in.nextInt();layer=in.nextInt();route=in.nextInt()!=0;for(int i=0;i<6;i++)shapes[i]=s.shapes[in.nextInt()];}
  void touch(int kind,int arg){if(state.active){int code=serial*10+kind;state.events+=code+":"+arg+",";if(state.failure==code)throw new IllegalStateException();}}
  int count(){touch(0,0);return count;}TileShape shape(int i){touch(1,i);return shapes[i];}int layer(int i){touch(2,i);return layer+i;}boolean route(){touch(3,0);return route;}int id(){touch(4,0);return id;}
 }
 static class Plain extends Item{
  Data d;Plain(Data d,int[] nets){super(nets,1,100+d.serial,0,FixedState.UNFIXED,null);this.d=d;}
  public int getId(){return d.id();}public int treeShapeCount(ShapeTree t){return d.count();}public TileShape getTreeShape(ShapeTree t,int i){return d.shape(i);}public int shapeLayer(int i){return d.layer(i);}public boolean isRoutable(){return d.route();}
  public Item copy(int i){throw new UnsupportedOperationException();}public boolean isObstacle(Item i){return true;}public int tileShapeCount(){return d.count;}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return d.shapes;}public boolean isOnLayer(int l){return true;}public int firstLayer(){return 0;}public int lastLayer(){return 0;}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public void translateBy(app.freerouting.geometry.planar.Vector v){}public void turn90Degree(int f,IntPoint p){}public void rotateApprox(double a,FloatPoint p){}public void changePlacementSide(IntPoint p){}public IntBox boundingBox(){return new IntBox(0,0,10,10);}public boolean isSelectedByFilter(ItemSelectionFilter f){return true;}public void printInfo(ItemInfoPrinter p,Locale l){}
 }
 static class TraceItem extends PolylineTrace{
  Data d;TraceItem(Data d,int[] nets){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(10,0)}),0,1,nets,1,100+d.serial,0,FixedState.UNFIXED,null);this.d=d;}
  public int getId(){return d.id();}public int treeShapeCount(ShapeTree t){return d.count();}public TileShape getTreeShape(ShapeTree t,int i){return d.shape(i);}public int shapeLayer(int i){return d.layer(i);}public boolean isRoutable(){return d.route();}
 }
 static class State{
  TileShape[] shapes={null,new IntBox(0,0,100,100),new IntBox(20,30,200,150).toIntOctagon(),Simplex.EMPTY,new IntBox(100,0,120,100),new IntBox(-20,-10,10,5).toSimplex()};
  Item[] items=new Item[5];Data[] data=new Data[5];ItemAutorouteInfo[] infos=new ItemAutorouteInfo[5];ObstacleExpansionRoom[] rooms=new ObstacleExpansionRoom[9];ExpandableObject[] doors=new ExpandableObject[9];Connection[] connections=new Connection[3];List<ExpansionDoor> oldDoors;
  IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();int next=1,failure=-1;boolean active;String events="";
  int id(Object o){return o==null?0:ids.computeIfAbsent(o,k->next++);}
  String invoke(Call f){events="";active=true;String s=call(f);active=false;return s+"~"+(events.isEmpty()?"-":events);}
  String shape(TileShape s){return s==null?"N":""+s.getId();}
  String list(List<ExpansionDoor> a){if(a==null)return "N";String s=id(a)+":"+a.size();for(var d:a)s+=","+id(d);return s;}
  String section(MazeSearchElement e){return e==null?"N":id(e)+":"+e.isOccupied+":"+id(e.backtrackDoor)+":"+e.sectionNoOfBacktrackDoor+":"+e.roomRipped+":"+(e.adjustment==null?-1:e.adjustment.ordinal())+":"+e.ripupCost;}
  String info(ItemAutorouteInfo i){return i==null?"N":id(i)+":"+i.isStartInfo()+":"+id(i.getPrecalculatedConnection());}
  String dump(){String s="";for(int i=1;i<=4;i++)s+="I"+i+":"+info(items[i].getAutorouteInfoPur())+"/"+info(infos[i]);for(int i=1;i<rooms.length;i++){var r=rooms[i];s+="R"+i+":"+(r==null?"N":id(r)+":"+Arrays.asList(items).indexOf(r.getItem())+":"+r.getIndexInItem()+":"+shape(r.getShape())+":"+r.allDoorsCalculated()+":"+list(r.getDoors()));}for(int i=1;i<doors.length;i++){var d=doors[i];s+="D"+i+":"+(d==null?"N":id(d)+":"+call(()->shape(d.getShape()))+":"+call(()->d.mazeSearchElementCount())+":"+call(()->section(d.getMazeSearchElement(0))));}return s+"O"+list(oldDoors);}
 }
 static Connection connection()throws Exception{var c=Connection.class.getDeclaredConstructor(Point.class,int.class,Point.class,int.class,Set.class);c.setAccessible(true);return c.newInstance(null,0,null,0,new TreeSet<Item>());}
 static String run(String line)throws Exception{
  Scanner in=new Scanner(line);in.next();State s=new State();for(int i=1;i<=4;i++){int kind=in.nextInt();var d=new Data(s,i,in);s.data[i]=d;int net=in.nextInt();int[] nets=net<0?new int[0]:new int[]{net};s.items[i]=kind==0?new Plain(d,nets):new TraceItem(d,nets);s.infos[i]=new ItemAutorouteInfo(s.items[i]);}
  s.connections[1]=connection();s.connections[2]=connection();ArrayList<String> out=new ArrayList<>();out.add(s.dump());int n=in.nextInt();
  for(int step=0;step<n;step++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();double value=dbl(in);
   out.add(s.invoke(()->switch(op){
    case "I"->{s.infos[a]=s.items[b].getAutorouteInfo();yield s.id(s.infos[a]);}case "i"->{s.infos[a]=new ItemAutorouteInfo(s.items[b]);yield s.id(s.infos[a]);}case "p"->{s.infos[a]=s.items[b].getAutorouteInfoPur();yield s.id(s.infos[a]);}
    case "C"->{s.items[a].clearAutorouteInfo();yield "ok";}case "d"->{s.items[a].clearDerivedData();yield "ok";}
    case "S"->{s.infos[a].setStartInfo(b!=0);yield "ok";}case "c"->{s.infos[a].setPrecalculatedConnection(s.connections[b]);yield "ok";}
    case "G"->{s.rooms[a]=s.infos[b].getExpansionRoom(c,null);yield s.id(s.rooms[a]);}case "M"->{s.rooms[a]=new ObstacleExpansionRoom(s.items[b],c,null);yield s.id(s.rooms[a]);}
    case "T"->{s.data[a].count=b;yield "ok";}case "s"->{s.data[a].shapes[b]=s.shapes[c];yield "ok";}case "r"->{s.data[a].route=b!=0;yield "ok";}case "n"->{s.items[a].netNumbers=b<0?null:new int[]{b};yield "ok";}
    case "O"->s.rooms[a].createOverlapDoor(s.rooms[b]);case "A"->{s.rooms[a].addDoor((ExpansionDoor)s.doors[b]);yield "ok";}
    case "J"->{s.doors[a]=s.rooms[b].getDoors().get(c);yield s.id(s.doors[a]);}case "E"->s.rooms[a].doorExists(s.rooms[b]);case "R"->s.rooms[a].removeDoor(s.doors[b]);case "L"->{s.rooms[a].clearDoors();yield "ok";}
    case "F"->{s.rooms[a].setDoorsCalculated(b!=0);yield "ok";}case "U"->{s.oldDoors=s.rooms[a].getDoors();yield "ok";}case "V"->{s.oldDoors.clear();yield "ok";}
    case "N"->{s.doors[a]=new TargetItemExpansionDoor(s.items[b],c,s.rooms[d],null);yield s.id(s.doors[a]);}
    case "Q"->((TargetItemExpansionDoor)s.doors[a]).isDestinationDoor();case "H"->s.doors[a].getId();case "Y"->s.rooms[a].getId();case "l"->s.rooms[a].getLayer();
    case "e"->s.section(s.doors[a].getMazeSearchElement(b));case "Z"->{s.doors[a].reset();yield "ok";}case "P"->{s.infos[a].resetDoors();yield "ok";}case "B"->{s.rooms[a].resetDoors();yield "ok";}
    case "t"->{var e=s.doors[a].getMazeSearchElement(b);e.isOccupied=true;e.backtrackDoor=s.doors[c];e.sectionNoOfBacktrackDoor=d;e.roomRipped=true;e.adjustment=null;e.ripupCost=(int)value;yield "ok";}
    case "K"->{s.failure=a;yield "ok";}case "D","b"->{int[] count={0};AutorouteDiagnostic.Sink sink=b==0?null:x->{s.events+="sink:"+x.kind().ordinal()+":"+s.shape(x.shape())+":"+x.layer()+":"+bits(x.intensity())+",";if(++count[0]==c)throw new IllegalStateException();};if(op.equals("D"))s.infos[a].emitDiagnostics(sink,value);else s.rooms[a].emitDiagnostic(sink,value);yield "ok";}
    case "v"->{var x=new AutorouteDiagnostic(a<0?null:AutorouteDiagnostic.Kind.values()[a],s.shapes[b],c,value);yield x.kind().ordinal()+":"+s.shape(x.shape())+":"+x.layer()+":"+bits(x.intensity());}
    case "f"->{var x=s.rooms[a].getTargetDoors();var y=s.rooms[a].getTargetDoors();yield x.size()+":"+y.size()+":"+(x==y)+":"+(s.rooms[a].getObject()==s.rooms[a].getItem());}
    default->throw new AssertionError(op);
   }));out.add(s.dump());
  }return String.join(" ",out);
 }
 public static void main(String[] args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String s;while((s=in.readLine())!=null)System.out.println(s+" | "+run(s));}
}
