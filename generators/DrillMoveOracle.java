package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.*;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
public class DrillMoveOracle {
 static String events;static int failure,insertFailure,mutate,insertCalls;static boolean nullContacts;static Set<Item> contacts;static ArrayList<String> clones,inserts;
 interface Call{Object run();}
 static String call(Call c){try{return String.valueOf(c.run());}catch(NullPointerException e){return "null_error";}catch(IllegalStateException e){return "callback_error";}}
 static Point point(int n){return switch(n){case 0->null;case 1->new IntPoint(0,0);case 2->new IntPoint(5,7);case 3->Point.getInstance(BigInteger.valueOf(5),BigInteger.valueOf(-7),BigInteger.valueOf(3));default->new IntPoint(-12,8);};}
 static String point(Point p){if(p==null)return "null";var f=p.toFloat();return p.getId()+":"+Long.toHexString(Double.doubleToLongBits(f.x))+","+Long.toHexString(Double.doubleToLongBits(f.y));}
 static String nets(int[] n){if(n==null)return "null";String s=""+n.length;for(int i:n)s+=","+i;return s;}
 static class Board extends BasicBoard {
  TestDrill item;
  Board(LayerStructure layers,BoardRules rules){super(new IntBox(-1000,-1000,1000,1000),layers,new PolylineShape[0],0,rules,new Communication());}
  public void insertTrace(Point[] points,int layer,int width,int[] nets,int clearance,FixedState fixed){events+="x"+layer+",";insertCalls++;String s=layer+":"+width+":"+clearance+":"+fixed.ordinal()+":"+nets(nets);for(Point p:points)s+="/"+point(p);inserts.add(s);if(failure==8&&insertCalls==insertFailure)throw new IllegalStateException();if((mutate&2)!=0)item.netNumbers=new int[]{insertCalls+10};}
 }
 static class Manager extends SearchTreeManager {
  Manager(Board b){super(b);}public void remove(Item i){events+="r,";if(failure==3)throw new IllegalStateException();super.remove(i);}public void insert(Item i){events+="i,";if(failure==5)throw new IllegalStateException();super.insert(i);}
 }
 static class TestDrill extends DrillItem {
  TestDrill(Point c,int[] nets,BasicBoard b,int id){super(c,nets,1,id,0,FixedState.UNFIXED,b);}
  public Set<Item> getNormalContacts(){events+="n,";if(failure==1)throw new IllegalStateException();return nullContacts?null:contacts;}
  public Item copy(int id){events+="c,";if(failure==2)throw new IllegalStateException();clones.add(point(getCenter())+":"+nets(netNumbers));return new TestDrill(getCenter(),netNumbers,board,id);}
  public void translateBy(app.freerouting.geometry.planar.Vector v){events+="t,";if(failure==4)throw new IllegalStateException();super.translateBy(v);}
  public void clearDerivedData(){events+="d,";if(failure==7)throw new IllegalStateException();super.clearDerivedData();}
  void center(Point p){setCenter(p);}
  public int firstLayer(){return 0;}public int lastLayer(){return 2;}public int tileShapeCount(){return 0;}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return new TileShape[0];}
  public Shape getShape(int i){throw new UnsupportedOperationException();}public Padstack getPadstack(){throw new UnsupportedOperationException();}public boolean isObstacle(Item o){return false;}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public boolean isSelectedByFilter(ItemSelectionFilter f){throw new UnsupportedOperationException();}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static String state(TestDrill d){return point(d.getCenter())+":"+nets(d.netNumbers)+":"+d.isOnTheBoard()+":"+insertCalls+":"+String.join(";",clones)+":"+String.join(";",inserts)+":"+(events.isEmpty()?"-":events);}
 static String run(String input)throws Exception{
  Scanner in=new Scanner(input);in.next();int angle=in.nextInt(),flags=in.nextInt(),mode=in.nextInt(),center=in.nextInt(),fail=in.nextInt();insertFailure=in.nextInt();mutate=in.nextInt();int count=in.nextInt();events="";failure=0;insertCalls=0;clones=new ArrayList<>();inserts=new ArrayList<>();contacts=new LinkedHashSet<>();nullContacts=(flags&2)!=0;
  var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("I",true),new Layer("B",true)});var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(angle<0?null:AngleRestriction.values()[angle]);Board board=new Board(layers,rules);var outline=board.getOutline();if(outline!=null)board.itemList.delete(outline);board.searchTreeManager=new Manager(board);
  for(int i=0;i<count;i++){int type=in.nextInt(),id=in.nextInt(),layer=in.nextInt(),width=in.nextInt(),clearance=in.nextInt();contacts.add(type==0?null:type==1?new TestDrill(point(1),new int[]{1},board,id):new PolylineTrace(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(10,0)}),layer,width,new int[]{1},clearance,id,0,FixedState.UNFIXED,board));}
  TestDrill d=new TestDrill(point(center),new int[]{1,2},(flags&1)!=0?null:board,50);board.item=d;if((flags&16)!=0)d.netNumbers=null;
  board.communication.observers=(flags&8)!=0?null:new BoardObserverAdaptor(){public void notifyChanged(Item i){events+="o,";if(failure==6)throw new IllegalStateException();if((mutate&1)!=0)d.center(point(2));}};
  if((flags&4)!=0){Field f=BasicBoard.class.getDeclaredField("rules");f.setAccessible(true);f.set(board,null);}
  if(mode>0)board.itemList.insert(d);if(mode>=2)board.itemList.generateSnapshot();board.searchTreeManager.insert(d);failure=fail;ArrayList<String> out=new ArrayList<>();
  for(int i=0;i<3;i++){int x=in.nextInt(),y=in.nextInt(),z=in.nextInt();var vector=app.freerouting.geometry.planar.Vector.getInstance(BigInteger.valueOf(x),BigInteger.valueOf(y),BigInteger.valueOf(z));if(mode==3&&i>0)board.itemList.generateSnapshot();events="";out.add(call(()->{d.moveBy(vector);return "ok";}));out.add(state(d));}
  if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[] args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
