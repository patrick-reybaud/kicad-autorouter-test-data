package app.freerouting.board.searchtree;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.library.*;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class ClearanceViolationsOracle {
 static String events;static int failure;static ArrayList<Item> items;static ArrayList<Node> nodes;static TileShape[] pool;
 interface Call{Object run()throws Exception;}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}catch(Exception e){throw new RuntimeException(e);}}
 static String number(double d){return "d"+Long.toHexString(Double.doubleToLongBits(d));}
 static double number(Scanner in){return Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));}
 static String point(Point p){FloatPoint f=p.toFloat();return p.getId()+":"+number(f.x)+","+number(f.y);}
 static String shape(ConvexShape s){if(s==null)return "null";TileShape t=(TileShape)s;IntBox b=t.boundingBox();String v=t.getClass().getSimpleName()+":"+t.dimension()+":"+b.ll.x+","+b.ll.y+","+b.ur.x+","+b.ur.y;for(int i=0;i<t.borderLineCount();i++)v+=";"+point(t.corner(i));return v;}
 static TileShape[] pool(){return new TileShape[]{null,new IntBox(-10,-10,10,10),new IntBox(14,-10,30,10),new IntBox(0,0,20,20),new IntOctagon(-8,-8,8,8,-12,12,-12,12),TileShape.getInstance(new Point[]{new IntPoint(-12,-8),new IntPoint(13,-8),new IntPoint(1,17)}),new IntBox(0,0,0,10),IntBox.EMPTY};}
 record Spec(int type,int[] ids){}
 record Node(int type,int cl,int layer,int obstacle,boolean escape,int escapeLayer,int[] nets,TileShape[] shapes,double smallest,Spec start,Spec end){}
 static int[] ints(Scanner in){int n=in.nextInt();int[]a=n<0?null:new int[n];for(int i=0;i<n;i++)a[i]=in.nextInt();return a;}
 static Spec spec(Scanner in){return new Spec(in.nextInt(),ints(in));}
 static void event(int i,int kind,String text){events+=text;if(failure==i*8+kind)throw new IllegalStateException();}
 static int count(int i){event(i,0,"n"+i+",");return nodes.get(i).shapes.length;}
 static TileShape tile(int i,int index){event(i,1,"s"+i+":"+index+",");return nodes.get(i).shapes[index];}
 static int layer(int i,int index){event(i,2,"l"+i+":"+index+",");return nodes.get(i).layer+index%2;}
 static boolean obstacle(int i,Item other){event(i,3,"o"+i+",");int mode=nodes.get(i).obstacle;return mode==0?false:mode==1?true:!items.get(i).sharesNet(other);}
 static Set<Item> contacts(int i,Point p){event(i,4,"p"+i+":"+((IntPoint)p).x+",");Spec spec=((IntPoint)p).x==0?nodes.get(i).start:nodes.get(i).end;if(spec.type==2)return null;Set<Item> result=spec.type==0?new TreeSet<>():new LinkedHashSet<>();for(int j:spec.ids)result.add(items.get(j));return result;}
 static class Plain extends Item {
  int index;Plain(int i,Node n,BasicBoard b){super(n.nets,n.cl,i,0,FixedState.UNFIXED,b);index=i;}
  public int tileShapeCount(){return count(index);}public TileShape getTileShape(int i){return tile(index,i);}public int shapeLayer(int i){return layer(index,i);}public boolean isObstacle(Item other){return obstacle(index,other);}
  public Item copy(int id){throw new UnsupportedOperationException();}protected TileShape[] calculateTreeShapes(ShapeSearchTree t){throw new UnsupportedOperationException();}public int firstLayer(){return 0;}public int lastLayer(){return 1;}public boolean isOnLayer(int l){return true;}public boolean write(ObjectOutputStream s){return false;}public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}public IntBox boundingBox(){throw new UnsupportedOperationException();}public boolean isSelectedByFilter(ItemSelectionFilter f){return false;}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
 }
 static class Trace extends PolylineTrace {
  int index;Trace(int i,Node n,BasicBoard b){super(new Polyline(new Point[]{Point.ZERO,new IntPoint(10,0)}),0,1,n.nets,n.cl,i,0,FixedState.UNFIXED,b);index=i;}
  public int tileShapeCount(){return count(index);}public TileShape getTileShape(int i){return tile(index,i);}public int shapeLayer(int i){return layer(index,i);}public boolean isObstacle(Item other){return obstacle(index,other);}
  public Point firstCorner(){event(index,5,"f"+index+",");return Point.ZERO;}public Point lastCorner(){event(index,6,"e"+index+",");return new IntPoint(10,0);}public Set<Item> getNormalContacts(Point p,boolean ignore){if(!ignore)throw new AssertionError();return contacts(index,p);}
 }
 static class TestVia extends Via {
  int index;TestVia(int i,Node n,BasicBoard b){super(null,Point.ZERO,n.nets,n.cl,i,0,FixedState.UNFIXED,false,b);index=i;isEscapeVia=n.escape;escapeViaSmdLayer=n.escapeLayer;}
  public int tileShapeCount(){return count(index);}public TileShape getTileShape(int i){return tile(index,i);}public int shapeLayer(int i){return layer(index,i);}public boolean isObstacle(Item other){return obstacle(index,other);}
 }
 static class TestPin extends Pin {
  int index;TestPin(int i,Node n,BasicBoard b){super(0,0,n.nets,n.cl,i,FixedState.UNFIXED,b);index=i;}
  public int tileShapeCount(){return count(index);}public TileShape getTileShape(int i){return tile(index,i);}public int shapeLayer(int i){return layer(index,i);}public boolean isObstacle(Item other){return obstacle(index,other);}
 }
 static class Generic implements ShapeTree.Storable {
  public int compareTo(Object o){return 0;}public int treeShapeCount(ShapeTree t){throw new UnsupportedOperationException();}public TileShape getTreeShape(ShapeTree t,int i){throw new UnsupportedOperationException();}public void setSearchTreeEntries(ShapeTree.Leaf[] e,ShapeTree t){throw new UnsupportedOperationException();}
 }
 static class Tree extends ShapeSearchTree {
  Collection<ShapeTree.TreeEntry> entries;
  Tree(BasicBoard b,int comp){super(FortyfiveDegreeBoundingDirections.INSTANCE,b,comp);}
  public Collection<ShapeTree.TreeEntry> overlappingTreeEntriesWithClearance(ConvexShape s,int layer,int[]ignored,int cl){events+="q"+layer+":"+cl+":"+ignored.length+",";return new LinkedList<>(entries);}
 }
 static class Manager extends SearchTreeManager {
  Tree tree;boolean compensate;
  Manager(BasicBoard b,Tree t,boolean c){super(b);tree=t;compensate=c;}
  public ShapeSearchTree getDefaultTree(){events+="d,";return tree;}
  public boolean isClearanceCompensationUsed(){events+="m,";return compensate;}
 }
 static String violations(Collection<ClearanceViolation> vs){if(vs==null)return "null";String s=""+vs.size();for(var v:vs)s+="/"+items.indexOf(v.firstItem)+","+items.indexOf(v.secondItem)+","+v.layer+","+number(v.expectedClearance)+","+number(v.actualClearance)+","+shape(v.shape);return s;}
 static String smallest(){String s=""+(items.size()-1);for(int i=1;i<items.size();i++)s+=","+number(items.get(i).smallestClearance);return s;}
 static class Supplier extends Plain {
  Collection<ClearanceViolation> supplied;double after;int mode;
  Supplier(int i,double smallest,double after,int mode){super(i,new Node(0,0,0,0,false,-1,new int[0],new TileShape[0],smallest,null,null),null);this.smallestClearance=smallest;this.after=after;this.mode=mode;}
  public Collection<ClearanceViolation> clearanceViolations(){events+="v"+index+",";smallestClearance=after;if(mode!=0)throw new IllegalStateException();return supplied;}
 }
 static String aggregate(Scanner in){
  int count=in.nextInt();ArrayList<ClearanceViolation> pool=new ArrayList<>();pool.add(null);
  for(int i=0;i<count;i++)pool.add(new ClearanceViolation(null,null,null,in.nextInt(),number(in),number(in)));
  int n=in.nextInt();items=new ArrayList<>();items.add(null);
  for(int i=1;i<=n;i++){double before=number(in),after=number(in);int mode=in.nextInt();Supplier item=new Supplier(i,before,after,mode);int[] ids=ints(in);if(ids!=null){item.supplied=new ArrayList<>();for(int id:ids)item.supplied.add(pool.get(id));}items.add(item);}
  int[] order=ints(in);Collection<Item> selected=null;if(order!=null){selected=new ArrayList<>();for(int i:order)selected.add(items.get(i));}final Collection<Item> input=selected;
  ArrayList<String> out=new ArrayList<>();events="";out.add(call(()->{List<ClearanceViolation> result=ClearanceViolation.aggregateSortedBySeverity(input);String s=""+result.size();for(var v:result)s+=","+pool.indexOf(v);return s;}));out.add(events.isEmpty()?"-":events);out.add(smallest());out.add(call(()->number(ClearanceViolation.smallestClearance(input))));return String.join(" ",out);
 }
 static String run(String input)throws Exception{
  Scanner in=new Scanner(input);String kind=in.next();if(kind.equals("A")){String result=aggregate(in);if(in.hasNext())throw new AssertionError();return result;}int flags=in.nextInt(),comp=in.nextInt(),minimum=in.nextInt(),subject=in.nextInt();failure=in.nextInt();int n=in.nextInt();
  var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var matrix=new ClearanceMatrix(4,layers,new String[]{"none","default","x","y"});matrix.setDefaultValue(minimum);matrix.setValue(1,2,0,minimum+6);matrix.setValue(2,1,0,minimum+2);BasicBoard board=new BasicBoard(new IntBox(-100,-100,100,100),layers,new PolylineShape[0],0,new BoardRules(layers,matrix),new Communication());
  pool=pool();items=new ArrayList<>();items.add(null);nodes=new ArrayList<>();nodes.add(null);events="";
  for(int i=1;i<=n;i++){int type=in.nextInt(),cl=in.nextInt(),layer=in.nextInt(),obs=in.nextInt();boolean escape=in.nextInt()!=0;int el=in.nextInt();int[]nets=ints(in);int[]indices=ints(in);TileShape[] shapes=indices==null?null:new TileShape[indices.length];if(shapes!=null)for(int j=0;j<shapes.length;j++)shapes[j]=pool[indices[j]];Node node=new Node(type,cl,layer,obs,escape,el,nets,shapes,number(in),spec(in),spec(in));nodes.add(node);BasicBoard b=(flags&1)!=0?null:board;Item item=switch(type){case 0->new Plain(i,node,b);case 1->new Trace(i,node,b);case 2->new TestVia(i,node,b);default->new TestPin(i,node,b);};item.netNumbers=nets;item.smallestClearance=node.smallest;items.add(item);}
  Tree tree=new Tree(board,comp);tree.entries=new LinkedList<>();int count=in.nextInt();for(int i=0;i<count;i++){int object=in.nextInt(),index=in.nextInt();tree.entries.add(new ShapeTree.TreeEntry(object<0?new Generic():items.get(object),index));}
  var mf=BasicBoard.class.getDeclaredField("searchTreeManager");mf.setAccessible(true);mf.set(board,(flags&2)!=0?null:new Manager(board,(flags&4)!=0?null:tree,(flags&8)!=0));if((flags&16)!=0){var rf=BasicBoard.class.getDeclaredField("rules");rf.setAccessible(true);rf.set(board,null);}
  Item item=items.get(subject);ArrayList<String> out=new ArrayList<>();ArrayList<Call> calls=new ArrayList<>();calls.add(()->violations(item.clearanceViolations()));calls.add(item::clearanceViolationCount);calls.add(()->violations(ClearanceViolation.aggregateSortedBySeverity(items.subList(1,items.size()))));calls.add(()->number(ClearanceViolation.smallestClearance(items.subList(1,items.size()))));
  for(Call call:calls){events="";out.add(call(call));out.add(smallest());out.add(events.isEmpty()?"-":events);}
  if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[]args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
