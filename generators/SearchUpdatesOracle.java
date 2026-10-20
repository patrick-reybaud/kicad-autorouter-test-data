package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.*;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class SearchUpdatesOracle {
  static ArrayList<ShapeTree> trees;
  static ArrayList<TileShape> tiles;
  static ArrayList<TileShape[]> shapes;
  static ArrayList<ShapeTree.Leaf[]> entries;
  static ArrayList<ShapeTree.Leaf> leaves;
  static String events;
  static int failure;
  static class Manager extends SearchTreeManager {
    boolean compensated;
    Manager(BasicBoard b){super(b);}
    public void remove(Item i){events+="r";if(failure==1)throw new IllegalStateException();}
    public void insert(Item i){events+="i";if(failure==2)throw new IllegalStateException();}
    public boolean isClearanceCompensationUsed(){return compensated;}
  }
  static class TestItem extends Item {
    int plan=2,mode,calls,clears,copies,x;
    TestItem(BasicBoard b){super(new int[]{1},1,50,0,FixedState.UNFIXED,b);}
    public Item copy(int id){copies++;events+="c";return new TestItem(board);}
    public boolean isObstacle(Item other){return !sharesNet(other);}public int tileShapeCount(){return 0;}
    protected TileShape[] calculateTreeShapes(ShapeSearchTree t){calls++;events+="s"+identity(trees,t)+",";if(mode==1)throw new IllegalStateException();if(mode==2)clearSearchTreeEntries();if(mode==3&&calls%2!=0)return null;if(mode==4&&calls%2==0)return shapes.get(1);return shapes.get(plan);}
    public void clearDerivedData(){clears++;events+="d";if(failure==5)throw new IllegalStateException();super.clearDerivedData();}
    public boolean isOnLayer(int l){return l==0;}public int firstLayer(){return 0;}public int lastLayer(){return 0;}
    public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}
    public void translateBy(app.freerouting.geometry.planar.Vector v){events+="t";if(failure==4)throw new IllegalStateException();x+=((IntVector)v).x;clearDerivedData();}
    public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}
    public IntBox boundingBox(){return new IntBox(0,0,1,1);}public int shapeLayer(int i){return 0;}public boolean isSelectedByFilter(ItemSelectionFilter f){return false;}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
  }
  static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
  static Object itemField(Item i,String name)throws Exception{Field f=Item.class.getDeclaredField(name);f.setAccessible(true);return f.get(i);}
  static int identity(List<?> p,Object o){for(int i=0;i<p.size();i++)if(p.get(i)==o)return i;return -1;}
  static <T> int id(ArrayList<T> p,T o){int i=identity(p,o);if(i<0){i=p.size();p.add(o);}return i;}
  interface Call{Object run()throws Exception;}
  static String call(Call f){try{return String.valueOf(f.run());}catch(NegativeArraySizeException e){return "size_error";}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}catch(InvocationTargetException e){return call(()->{throw (Exception)e.getCause();});}catch(Exception e){throw new RuntimeException(e);}}
  static ShapeSearchTree search(ShapeTree t){return (ShapeSearchTree)t;}
  static String state(TestItem item)throws Exception{
    String s=item.calls+":"+item.clears+":"+item.copies+":"+item.x+":"+item.clearanceClassIndex()+":"+item.isOnTheBoard()+":"+(item.board!=null)+"/"+(events.isEmpty()?"-":events);Object info=itemField(item,"searchTreesInfo");
    if(info==null)s+="/null";else{var records=(Collection<?>)field(info,"treeList");s+="/"+records.size();for(Object r:records)s+="#"+identity(trees,field(r,"tree"))+":"+id(entries,(ShapeTree.Leaf[])field(r,"entryArr"))+":"+id(shapes,(TileShape[])field(r,"precalculatedTreeShapes"));}
    String treeState="";for(var tree:trees){treeState+="#";if(tree==null){treeState+="null";continue;}treeState+=tree.size()+":"+call(()->{var a=tree.toArray();String text=""+a.length;for(var leaf:a)text+=","+id(leaves,leaf)+":"+(leaf==null?-1:leaf.shapeIndexInObject);return text;});}
    String entryState="/"+entries.size();for(var a:entries){if(a==null)entryState+="#null";else{entryState+="#"+a.length;for(var leaf:a)entryState+=","+id(leaves,leaf);}}
    s+="/"+shapes.size();for(var a:shapes){if(a==null)s+="#null";else{s+="#"+a.length;for(var v:a)s+=","+identity(tiles,v);}}
    String tracked="/"+leaves.size();for(var leaf:leaves){if(leaf==null){tracked+="#null";continue;}tracked+="#"+(leaf.object==item)+":"+leaf.shapeIndexInObject+":"+tile(leaf.boundingShape);}
    return s+entryState+"/"+treeState+tracked;
  }
  static String run(String input)throws Exception{
    Scanner in=new Scanner(input);if(!in.next().equals("C"))return offsets(input);events="";failure=0;
    var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});matrix.setDefaultValue(12);matrix.setValue(1,2,0,42);matrix.setValue(2,1,1,30);var rules=new BoardRules(layers,matrix);BasicBoard board=new BasicBoard(new IntBox(0,0,100,100),layers,new PolylineShape[0],0,rules,new Communication());board.itemList.delete(board.getOutline());Manager manager=new Manager(board);board.searchTreeManager=manager;
    var observer=new BoardObserverAdaptor(){public void notifyChanged(Item i){events+="o";if(failure==3)throw new IllegalStateException();}};board.communication.observers=observer;
    Constructor<ShapeSearchTree> ctor=ShapeSearchTree.class.getDeclaredConstructor(ShapeBoundingDirections.class,BasicBoard.class,int.class);ctor.setAccessible(true);
    trees=new ArrayList<>();trees.add(null);trees.add(manager.getDefaultTree());trees.add(ctor.newInstance(OrthogonalBoundingDirections.INSTANCE,board,1));trees.add(new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE));trees.add(ctor.newInstance(FortyfiveDegreeBoundingDirections.INSTANCE,null,2));trees.add(new ShapeSearchTree45Degree(board,1));trees.add(new ShapeSearchTree90Degree(board,1));
    tiles=new ArrayList<>();tiles.add(null);tiles.add(new IntBox(0,0,1,1));tiles.add(new IntBox(2,3,5,8));tiles.add(new IntOctagon(0,0,20,20,-20,20,0,40));
    shapes=new ArrayList<>();shapes.add(null);shapes.add(new TileShape[0]);shapes.add(new TileShape[]{tiles.get(1),tiles.get(2)});shapes.add(new TileShape[]{null,tiles.get(3),tiles.get(1)});
    entries=new ArrayList<>();entries.add(null);entries.add(new ShapeTree.Leaf[0]);entries.add(new ShapeTree.Leaf[]{null});leaves=new ArrayList<>();leaves.add(null);
    TestItem item=new TestItem(board);board.itemList.insert(item);ArrayList<String> out=new ArrayList<>();int count=in.nextInt();out.add(state(item));
    for(int step=0;step<count;step++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();out.add(call(()->switch(op){
      case "Y" -> {search(trees.get(a)).changeItemShape(item,b,tiles.get(c));yield "ok";}
      case "y" -> {search(trees.get(a)).changeItemShape(null,b,tiles.get(c));yield "ok";}
      case "n" -> item.treeShapeCount(trees.get(a));case "g" -> identity(tiles,item.getTreeShape(trees.get(a),b));case "t" -> identity(tiles,item.getTileShape(a));
      case "E" -> {item.setSearchTreeEntries(entries.get(b),trees.get(a));yield "ok";}case "e" -> id(entries,item.getSearchTreeEntries(search(trees.get(a))));case "S" -> {item.setPrecalculatedTreeShapes(shapes.get(b),search(trees.get(a)));yield "ok";}
      case "C" -> {item.clearSearchTreeEntries();yield "ok";}case "D" -> {item.clearDerivedData();yield "ok";}case "P" -> {item.plan=a;item.mode=b;yield "ok";}
      case "m" -> {shapes.get(a)[b]=tiles.get(c);yield "ok";}case "M" -> {entries.get(a)[b]=leaves.get(c);yield "ok";}
      case "I" -> {trees.get(a).insert(item);yield "ok";}case "R" -> {var tree=search(trees.get(a));tree.remove(item.getSearchTreeEntries(tree));yield "ok";}case "V" -> search(trees.get(a)).validateEntries(item);
      case "B" -> {item.board=a==0?null:board;yield "ok";}case "F" -> {failure=a;yield "ok";}case "A" -> {item.changeClearanceClassIndex(a);yield "ok";}case "x" -> {item.moveBy(new IntVector(a,b));yield "ok";}case "b" -> {item.setOnTheBoard(a!=0);yield "ok";}
      case "Q" -> {board.itemList.generateSnapshot();yield "ok";}case "p" -> {manager.compensated=a!=0;yield "ok";}case "o" -> {board.communication.observers=a==0?null:observer;yield "ok";}
      case "v" -> search(trees.get(a)).clearanceCompensationValue(b,c);case "u" -> search(trees.get(a)).isClearanceCompensationUsed();case "k" -> search(trees.get(a)).key;case "z" -> {events="";yield "ok";}
      default -> throw new AssertionError(op);
    }));out.add(state(item));}
    if(in.hasNext())throw new AssertionError("unused input");return String.join(" ",out);
  }
  static String tile(TileShape t){if(t==null)return "null";IntBox b=t.boundingBox();String s=t.getClass().getSimpleName()+":"+t.getId()+":"+t.dimension()+":"+t.borderLineCount()+":"+b.ll.x+","+b.ll.y+","+b.ur.x+","+b.ur.y;return s;}
  static String offsets(String input)throws Exception {
    Scanner in=new Scanner(input);if(!in.next().equals("O"))throw new AssertionError();int type=in.nextInt(),half=in.nextInt(),from=in.nextInt(),to=in.nextInt(),n=in.nextInt();
    Point[] points=n<0?null:new Point[n];if(points!=null)for(int i=0;i<n;i++)points[i]=new IntPoint(in.nextInt(),in.nextInt());
    Polyline p=points==null?null:new Polyline(points);Constructor<ShapeSearchTree> ctor=ShapeSearchTree.class.getDeclaredConstructor(ShapeBoundingDirections.class,BasicBoard.class,int.class);ctor.setAccessible(true);
    ShapeSearchTree tree=type==0?ctor.newInstance(FortyfiveDegreeBoundingDirections.INSTANCE,null,0):type==1?new ShapeSearchTree45Degree(null,0):new ShapeSearchTree90Degree(null,0);
    Method m=ShapeSearchTree.class.getDeclaredMethod("offsetShape",Polyline.class,int.class,int.class);m.setAccessible(true);
    String one=call(()->tile((TileShape)m.invoke(tree,p,half,from)));
    String many=call(()->{TileShape[] shapes=tree.offsetShapes(p,half,from,to);String text=""+shapes.length;for(TileShape t:shapes)text+="/"+tile(t);return text;});
    if(in.hasNext())throw new AssertionError("unused input");return one+" "+many;
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
