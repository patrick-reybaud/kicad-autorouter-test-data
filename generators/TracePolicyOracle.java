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
public class TracePolicyOracle {
 static String events;static ArrayList<Item> items;static ArrayList<Spec[]> specs;static int failure;static Spec supplied;
 record Spec(int type,int[] ids){}
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IllegalStateException e){return "callback_error";}}
 static Spec read(Scanner in){int type=in.nextInt(),count=in.nextInt();int[] ids=new int[count];for(int i=0;i<count;i++)ids[i]=in.nextInt();return new Spec(type,ids);}
 static Set<Item> set(Spec s){if(s.type==2)return null;Set<Item> result=s.type==0?new TreeSet<>():new LinkedHashSet<>();for(int i:s.ids)result.add(items.get(i));return result;}
 static Set<Item> contacts(int index,int kind){events+=(kind==0?"n":kind==1?"s":"e")+index+",";if(failure==index*3+kind)throw new IllegalStateException();return set(specs.get(index)[kind]);}
 static String list(Collection<Item> s){if(s==null)return "null";String r=""+s.size();for(Item i:s)r+=","+items.indexOf(i);return r;}
 static String membership(Set<Item> s){String r=list(s);for(Item i:items)r+="/"+call(()->s.contains(i));return r;}
 static class Board extends BasicBoard {
  int provider,mutate;Board(LayerStructure l,BoardRules r){super(new IntBox(-1000,-1000,1000,1000),l,new PolylineShape[0],0,r,new Communication());var outline=getOutline();if(outline!=null)itemList.delete(outline);}
  public Collection<Item> getConnectableItems(int net){events+="G"+net+",";if(mutate!=0){items.get(1).netNumbers=new int[]{49,net+100};events+="m,";}if(provider==0)return super.getConnectableItems(net);if(provider==3)return null;if(provider==4)throw new IllegalStateException();if(provider==1)return set(supplied);Collection<Item> r=new LinkedList<>();for(int i:supplied.ids)r.add(items.get(i));return r;}
 }
 static class Plain extends Item {
  int index;Plain(int i,int id,int[] nets,FixedState f,BasicBoard b){super(nets,1,id,0,f,b);index=i;}
  public Set<Item> getNormalContacts(){return contacts(index,0);}
  public Item copy(int id){throw new UnsupportedOperationException();}public boolean isObstacle(Item other){throw new UnsupportedOperationException();}public int tileShapeCount(){throw new UnsupportedOperationException();}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){throw new UnsupportedOperationException();}public boolean isOnLayer(int l){return l==0;}public int firstLayer(){return 0;}public int lastLayer(){return 0;}public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}public IntBox boundingBox(){throw new UnsupportedOperationException();}public int shapeLayer(int i){return 0;}public boolean isSelectedByFilter(ItemSelectionFilter f){return false;}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static class Area extends ConductionArea {
  int index;Area(int i,int id,int[]nets,FixedState f,BasicBoard b){super(null,0,app.freerouting.geometry.planar.Vector.ZERO,0,false,nets,1,id,0,null,false,f,b);index=i;}
  public Set<Item> getNormalContacts(){return contacts(index,0);}
 }
 static class TestTrace extends PolylineTrace {
  int index;TestTrace(int i,int id,int[] nets,FixedState f,BasicBoard b){super(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(10,0)}),0,1,nets,1,id,0,f,b);index=i;}
  public Set<Item> getNormalContacts(){return contacts(index,0);}public Set<Item> getStartContacts(){return contacts(index,1);}public Set<Item> getEndContacts(){return contacts(index,2);}
  public Point firstCorner(){events+="f"+index+",";return new IntPoint(0,0);}public Point lastCorner(){events+="l"+index+",";return new IntPoint(10,0);}
 }
 static String run(String input)throws Exception{
  Scanner in=new Scanner(input);in.next();int flags=in.nextInt(),provider=in.nextInt();failure=in.nextInt();int n=in.nextInt();int[] codes=new int[4];for(int i=0;i<4;i++)codes[i]=in.nextInt();
  events="";items=new ArrayList<>();items.add(null);specs=new ArrayList<>();specs.add(null);
  var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});var rules=new BoardRules(layers,matrix);Board board=new Board(layers,rules);board.provider=provider;board.mutate=flags&8;for(int i=1;i<=50;i++)rules.nets.add("n"+i,1,false);
  Field nf=Nets.class.getDeclaredField("nets");nf.setAccessible(true);@SuppressWarnings("unchecked")var nets=(java.util.Vector<Net>)nf.get(rules.nets);int[] numbers={1,2,3,49};
  for(int i=0;i<4;i++){int code=codes[i];if(code==-2)nets.set(numbers[i]-1,null);else if(code==-1)rules.nets.get(numbers[i]).setClass(null);else{var c=new NetClass("c",layers,matrix,false);c.setShoveFixed((code&1)!=0);c.setIgnoreCyclesWithAreas((code&2)!=0);rules.nets.get(numbers[i]).setClass(c);}}
  for(int i=1;i<=n;i++){int type=in.nextInt(),id=in.nextInt(),fixed=in.nextInt(),nulls=in.nextInt(),nc=in.nextInt();int[] nn=new int[nc];for(int j=0;j<nc;j++)nn[j]=in.nextInt();FixedState f=fixed<0?null:FixedState.values()[fixed];BasicBoard b=(flags&1)!=0?null:board;Item item=type==0?new Plain(i,id,nn,f,b):type==1?new Area(i,id,nn,f,b):new TestTrace(i,id,nn,f,b);if(nulls!=0)item.netNumbers=null;items.add(item);board.itemList.insert(item);specs.add(new Spec[]{read(in),read(in),read(in)});}
  supplied=read(in);
  if((flags&2)!=0){Field f=BasicBoard.class.getDeclaredField("rules");f.setAccessible(true);f.set(board,null);}else if((flags&4)!=0){Field f=BoardRules.class.getDeclaredField("nets");f.setAccessible(true);f.set(rules,null);}
  ArrayList<String> out=new ArrayList<>();
  for(Item item:items){if(item==null)continue;ArrayList<Call> calls=new ArrayList<>();if(item instanceof Trace t){calls.add(t::isShoveFixed);calls.add(t::isOverlap);calls.add(t::isCycle);}for(int net:new int[]{-1,0,1,2,49,10000001})calls.add(()->membership(item.getUnconnectedSet(net)));calls.add(()->membership(item.getNormalContacts()));
   for(Call c:calls){events="";out.add(call(c));out.add(events.isEmpty()?"-":events);}
  }
  if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[]args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
