package app.freerouting.board.searchtree;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.board.facade.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.Communication;
import app.freerouting.rules.*;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import java.io.*;import java.util.*;import java.lang.reflect.*;
public class RoomCompletionOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(IllegalArgumentException e){return "argument_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double v){return Long.toHexString(Double.doubleToLongBits(v));}
 static class Data{State s;int serial,shape,layer;boolean obstacle;Data(State s,int serial,int shape,int layer,boolean obstacle){this.s=s;this.serial=serial;this.shape=shape;this.layer=layer;this.obstacle=obstacle;}TileShape shape(int i){s.touch(200+serial,i);return s.shapes[shape];}int layer(int i){s.touch(300+serial,i);return layer;}boolean obstacle(int n){s.touch(100+serial,n);return obstacle;}void compare(Object o){s.touch(400+serial,s.serial(o));}}
 static class Plain extends CompleteRoomOracle.Plain {
 Data x;Plain(Data x,int id){super(new CompleteRoomOracle.Data(new CompleteRoomOracle.State(),x.serial),1);this.x=x;try{var f=Item.class.getDeclaredField("id");f.setAccessible(true);f.setInt(this,id);}catch(Exception e){throw new AssertionError(e);}}
 public TileShape getTreeShape(ShapeTree t,int i){return x.shape(i);}public int shapeLayer(int i){return x.layer(i);}public boolean isTraceObstacle(int n){return x.obstacle(n);}public int compareTo(Object o){x.compare(o);return super.compareTo(o);}
 }
 static class Complete extends CompleteFreeSpaceExpansionRoom {
 Data x;Complete(Data x,int id){super(x.s.shapes[x.shape],x.layer,id);this.x=x;}
 public TileShape getTreeShape(ShapeTree t,int i){return x.shape(i);}public int shapeLayer(int i){return x.layer(i);}public boolean isTraceObstacle(int n){return x.obstacle(n);}public int compareTo(Object o){x.compare(o);return super.compareTo(o);}
 }
 static class Room extends IncompleteFreeSpaceExpansionRoom {
 State s;int serial;Room(State s,int n,TileShape shape,int layer,TileShape contained){super(shape,layer,contained);this.s=s;serial=n;}
 public TileShape getShape(){s.touch(700+serial,0);return super.getShape();}public TileShape getContainedShape(){s.touch(710+serial,0);return super.getContainedShape();}public int getLayer(){s.touch(720+serial,0);return super.getLayer();}
 }
 static class Board extends RoutingBoard {
 State s;Board(State s){this(s,new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)}));}Board(State s,LayerStructure l){super(new IntBox(0,0,200,200),l,new PolylineShape[0],0,new BoardRules(l,new ClearanceMatrix(2,l,new String[]{"none","default"})),new Communication());this.s=s;}
 public IntBox getBoundingBox(){if(s==null)return super.getBoundingBox();s.touch(900,0);return s.bounds;}
 }
 static class Tree extends ShapeSearchTree {
 Tree(Board b,boolean orthogonal){super(orthogonal?OrthogonalBoundingDirections.INSTANCE:FortyfiveDegreeBoundingDirections.INSTANCE,b,0);}
 Collection<IncompleteFreeSpaceExpansionRoom> divide(Collection<IncompleteFreeSpaceExpansionRoom> rooms,IntBox b){return super.divideLargeRoom(rooms,b);}
 Collection<IncompleteFreeSpaceExpansionRoom> restrain(IncompleteFreeSpaceExpansionRoom room,TileShape obstacle){try{var m=ShapeSearchTree.class.getDeclaredMethod("restrainShape",IncompleteFreeSpaceExpansionRoom.class,TileShape.class);m.setAccessible(true);@SuppressWarnings("unchecked")var r=(Collection<IncompleteFreeSpaceExpansionRoom>)m.invoke(this,room,obstacle);return r;}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 }
 static class State {
 TileShape[] shapes=new TileShape[20];IntBox bounds=new IntBox(0,0,200,200);SearchTreeObject[] objects;Data[] data;Room[] rooms=new Room[3];Collection<IncompleteFreeSpaceExpansionRoom> input,result;Tree tree;
 String events="";boolean active=false;int failure=-1,failAt=1,seen=0,next=1;IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
 int id(Object x){return x==null?0:ids.computeIfAbsent(x,k->next++);}int serial(Object x){if(x==null)return 0;for(int i=1;i<objects.length;i++)if(objects[i]==x)return i;return -1;}
 void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
 String invoke(Call f){events="";seen=0;active=true;String r=call(f);active=false;return r+"~"+(events.isEmpty()?"-":events);}
 String shape(TileShape x){if(x==null)return "N";String r=x.getClass().getSimpleName()+":"+x.getId()+":"+x.dimension()+":"+x.borderLineCount();for(int i=0;i<x.borderLineCount();i++)r+=","+x.borderLine(i).getId();return r;}
 String list(Collection<IncompleteFreeSpaceExpansionRoom> a){if(a==null)return "N";String r=id(a)+":"+a.size();for(var x:a)r+=x==null?"/N":"/"+id(x)+":"+shape(x.getShape())+":"+x.getLayer()+":"+shape(x.getContainedShape());return r;}
 String dump(){String r="R"+list(result);return r+"I"+list(input);}
 }
 static String run(String line)throws Exception{Scanner in=new Scanner(line);in.next();State s=new State();boolean orthogonal=in.nextInt()!=0;for(int i=0;i<20;i++){int kind=in.nextInt(),a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();IntBox box=new IntBox(a,b,c,d);int cut=Math.min(c-a,d-b)/3;IntOctagon oct=new IntOctagon(a,b,c,d,a-d+cut,c-b-cut,a+b+cut,c+d-cut);s.shapes[i]=switch(kind){case 0->null;case 1->box;case 2->box.toIntOctagon();case 3->box.toSimplex();case 4->oct;case 5->oct.toSimplex();default->TileShape.getInstance(new Point[]{new IntPoint(a,b),new IntPoint(c,b),new IntPoint(a+(c-a)/3,d)});};}
 for(int i=1;i<=2;i++)s.rooms[i]=new Room(s,i,s.shapes[in.nextInt()],in.nextInt(),s.shapes[in.nextInt()]);int n=in.nextInt();s.objects=new SearchTreeObject[n+1];s.data=new Data[n+1];for(int i=1;i<=n;i++){int kind=in.nextInt(),id=in.nextInt();var data=new Data(s,i,in.nextInt(),in.nextInt(),in.nextInt()!=0);s.data[i]=data;s.objects[i]=kind==0?new Plain(data,id):new Complete(data,id);}
 s.tree=new Tree(new Board(s),orthogonal);for(int i=1;i<=n;i++)s.tree.insert(s.objects[i]);var out=new ArrayList<String>();int steps=in.nextInt();for(int j=0;j<steps;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt();out.add(s.invoke(()->switch(op){
 case "C"->{s.result=s.tree.completeShape(s.rooms[a],b,s.objects[c],s.shapes[d]);yield s.id(s.result);}
 case "R"->{s.result=s.tree.restrain(s.rooms[a],s.shapes[b]);yield s.id(s.result);}
 case "D"->{s.input=new LinkedList<>();if(a>=0)s.input.add(s.rooms[a]);if(c!=0)s.input.add(s.rooms[c]);s.result=s.tree.divide(s.input,b==0?null:s.bounds);yield s.id(s.result)+":"+(s.result==s.input);}
 case "S"->{s.rooms[a].setShape(s.shapes[b]);s.rooms[a].setContainedShape(s.shapes[c]);yield "ok";}
 case "B"->{s.bounds=a==0?null:new IntBox(a,b,c,d);yield "ok";}
 case "F"->{s.failure=a;s.failAt=b;yield "ok";}
 default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[] args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null)System.out.println(line+" | "+run(line));}
}
