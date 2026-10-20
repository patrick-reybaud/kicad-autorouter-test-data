package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import app.freerouting.datastructures.UndoableObjects;
public class BoardQueriesOracle {
 static String events;static ArrayList<Item> items;static ArrayList<Spec[]> specs;static int failure;static Spec supplied;
 record Spec(int type,int[] ids){}
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IllegalStateException e){return "callback_error";}catch(ClassCastException e){return "cast_error";}}
 static Spec read(Scanner in){int type=in.nextInt(),count=in.nextInt();int[] ids=new int[count];for(int i=0;i<count;i++)ids[i]=in.nextInt();return new Spec(type,ids);}
 static Set<Item> set(Spec s){if(s.type==2)return null;Set<Item> result=s.type==0?new TreeSet<>():new LinkedHashSet<>();for(int i:s.ids)result.add(items.get(i));return result;}
 static Set<Item> contacts(int index,int kind){events+=(kind==0?"n":kind==1?"s":"e")+index+",";if(failure==index*3+kind)throw new IllegalStateException();return set(specs.get(index)[kind]);}
 static String list(Collection<? extends Item> s){if(s==null)return "null";String r=""+s.size();for(Item i:s)r+=","+items.indexOf(i);return r;}
 static String membership(Set<Item> s){String r=list(s);for(Item i:items)r+="/"+call(()->s.contains(i));return r;}
 static class Board extends BasicBoard {
  Board(LayerStructure l,BoardRules r){super(new IntBox(-1000,-1000,1000,1000),l,new PolylineShape[0],0,r,new Communication());var outline=getOutline();if(outline!=null)itemList.delete(outline);}
 }
 static class NotItem implements UndoableObjects.Storable {public Object clone(){return this;}public int compareTo(Object o){return o==this?0:1;}}
 static class TestPin extends Pin {
  int index;TestPin(int i,int id,int comp,int pin,int[]nn,FixedState f,BasicBoard b){super(comp,pin,nn,1,id,f,b);index=i;}
  public Set<Item> getNormalContacts(){return contacts(index,0);}
 }
 static class Plain extends Item {
  int index;Plain(int i,int id,int comp,int[] nets,FixedState f,BasicBoard b){super(nets,1,id,comp,f,b);index=i;}
  public Set<Item> getNormalContacts(){return contacts(index,0);}
  public Item copy(int id){throw new UnsupportedOperationException();}public boolean isObstacle(Item other){throw new UnsupportedOperationException();}public int tileShapeCount(){throw new UnsupportedOperationException();}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){throw new UnsupportedOperationException();}public boolean isOnLayer(int l){return l==0;}public int firstLayer(){return 0;}public int lastLayer(){return 0;}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}public IntBox boundingBox(){throw new UnsupportedOperationException();}public int shapeLayer(int i){return 0;}public boolean isSelectedByFilter(ItemSelectionFilter f){return false;}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static class Area extends ConductionArea {
  int index;Area(int i,int id,int comp,int[]nets,FixedState f,BasicBoard b){super(null,0,app.freerouting.geometry.planar.Vector.ZERO,0,false,nets,1,id,comp,null,false,f,b);index=i;}
  public Set<Item> getNormalContacts(){return contacts(index,0);}
 }
 static class TestTrace extends PolylineTrace {
  int index;TestTrace(int i,int id,int comp,int[] nets,FixedState f,BasicBoard b){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(10,0)}),0,1,nets,1,id,comp,f,b);index=i;}
  public Set<Item> getNormalContacts(){return contacts(index,0);}public Set<Item> getStartContacts(){return contacts(index,1);}public Set<Item> getEndContacts(){return contacts(index,2);}
  public Point firstCorner(){events+="f"+index+",";return new IntPoint(0,0);}public Point lastCorner(){events+="l"+index+",";return new IntPoint(10,0);}
 }
 static String groups(Collection<Collection<Item>> groups){String s=""+groups.size();for(var group:groups)s+="/"+list(group);return s;}
 static String netlist(List<Net> nets){String s=""+nets.size();for(Net net:nets)s+="/"+net.netNumber;return s;}
 static String hex(String s){if(s.isEmpty())return "-";StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++)b.append(String.format(Locale.ROOT,"%04x",(int)s.charAt(i)));return b.toString();}
 static String run(String input)throws Exception{
  Scanner in=new Scanner(input);in.next();int flags=in.nextInt();failure=in.nextInt();int n=in.nextInt();int[] codes=new int[4];for(int i=0;i<4;i++)codes[i]=in.nextInt();
  events="";items=new ArrayList<>();items.add(null);specs=new ArrayList<>();specs.add(null);
  var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});var rules=new BoardRules(layers,matrix);Board board=new Board(layers,rules);for(int i=1;i<=50;i++)rules.nets.add(i==2?"Ω":i==49?null:"n"+i,1,false);
  Field nf=Nets.class.getDeclaredField("nets");nf.setAccessible(true);@SuppressWarnings("unchecked")var nets=(java.util.Vector<Net>)nf.get(rules.nets);int[] numbers={1,2,3,49};
  for(int i=0;i<4;i++){int code=codes[i];if(code==-2)nets.set(numbers[i]-1,null);else if(code==-1)rules.nets.get(numbers[i]).setClass(null);else{var c=new NetClass("c",layers,matrix,(code&1)!=0);c.setShoveFixed((code&1)!=0);c.setIgnoreCyclesWithAreas((code&2)!=0);rules.nets.get(numbers[i]).setClass(c);}}
  for(int i=1;i<=n;i++){int type=in.nextInt(),id=in.nextInt(),comp=in.nextInt(),pin=in.nextInt(),fixed=in.nextInt(),nulls=in.nextInt(),nc=in.nextInt();int[] nn=new int[nc];for(int j=0;j<nc;j++)nn[j]=in.nextInt();FixedState f=fixed<0?null:FixedState.values()[fixed];BasicBoard b=(flags&1)!=0?null:board;Item item=type==0?new Plain(i,id,comp,nn,f,b):type==1?new Area(i,id,comp,nn,f,b):type==2?new TestTrace(i,id,comp,nn,f,b):new TestPin(i,id,comp,pin,nn,f,b);if(nulls!=0)item.netNumbers=null;items.add(item);board.itemList.insert(item);specs.add(new Spec[]{read(in),read(in),read(in)});}
  if((flags&16)!=0||(flags&32)!=0){Field f=BasicBoard.class.getDeclaredField("itemList");f.setAccessible(true);f.set(board,(flags&16)!=0?null:new UndoableObjects());if((flags&16)==0)board.itemList.insert(new NotItem());}
  if((flags&2)!=0){Field f=BasicBoard.class.getDeclaredField("rules");f.setAccessible(true);f.set(board,null);}else if((flags&4)!=0){Field f=BoardRules.class.getDeclaredField("nets");f.setAccessible(true);f.set(rules,null);}
  ArrayList<String> out=new ArrayList<>();ArrayList<Call> calls=new ArrayList<>();
  for(int net:new int[]{-1,0,1,2,3,49,10000001}){calls.add(()->list(board.getConnectableItems(net)));calls.add(()->board.connectableItemCount(net));calls.add(()->groups(board.getConnectedSets(net)));}
  for(int comp:new int[]{-1,0,1,2,3}){calls.add(()->list(board.getComponentItems(comp)));calls.add(()->list(board.getComponentPins(comp)));for(int pin:new int[]{-1,0,1,2,99})calls.add(()->items.indexOf(board.getPin(comp,pin)));}
  for(Item item:items){if(item==null)continue;calls.add(item::hasIgnoredNets);calls.add(()->netlist(item.getAllNets()));calls.add(()->hex(item.getAllNetNames()));}
  for(Call c:calls){events="";out.add(call(c));out.add(events.isEmpty()?"-":events);}
  if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[]args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
