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
public class SearchTreeManagerOracle {
  static ArrayList<ShapeSearchTree> trees;
  static ArrayList<TestItem> items;
  static String events;
  static class TestItem extends Item {
    int plan=2,mode,calls,clears,copies,x;
    TestItem(int id,BasicBoard b){super(new int[]{1},1,id,0,FixedState.UNFIXED,b);x=id;items.add(this);}
    public Item copy(int id){copies++;events+="c"+getId()+",";TestItem r=new TestItem(id,board);r.plan=plan;r.mode=mode;r.x=x;r.setClearanceClassIndex(clearanceClassIndex());return r;}
    public boolean isObstacle(Item other){return !sharesNet(other);}public int tileShapeCount(){return plan;}
    protected TileShape[] calculateTreeShapes(ShapeSearchTree t){calls++;events+="s"+getId()+":"+id(t)+",";if(mode==1)throw new IllegalStateException();if(mode==2)return null;TileShape[] a=new TileShape[plan];for(int i=0;i<plan;i++){int g=t.clearanceCompensationValue(clearanceClassIndex(),0);a[i]=new IntBox(x+i*10-g,-g,x+i*10+4+g,2+g);}return a;}
    public void clearDerivedData(){clears++;events+="d"+getId()+",";if(mode==3)throw new IllegalStateException();super.clearDerivedData();}
    public boolean isOnLayer(int l){return l==0;}public int firstLayer(){return 0;}public int lastLayer(){return 0;}
    public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}
    public void translateBy(app.freerouting.geometry.planar.Vector v){events+="t"+getId()+",";x+=((IntVector)v).x;clearDerivedData();}
    public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}
    public IntBox boundingBox(){return new IntBox(x,0,x+4,2);}public int shapeLayer(int i){return 0;}public boolean isSelectedByFilter(ItemSelectionFilter f){return false;}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
  }
  static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
  static Object itemField(Item i,String name)throws Exception{Field f=Item.class.getDeclaredField(name);f.setAccessible(true);return f.get(i);}
  static int identity(List<?> p,Object o){for(int i=0;i<p.size();i++)if(p.get(i)==o)return i;return -1;}
  static int id(ShapeSearchTree t){int i=identity(trees,t);if(i<0){i=trees.size();trees.add(t);}return i;}
  interface Call{Object run()throws Exception;}
  static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(ClassCastException e){return "cast_error";}catch(IllegalStateException e){return "callback_error";}catch(Exception e){throw new RuntimeException(e);}}
  static String box(Shape s){if(s==null)return "null";IntBox b=s.boundingBox();return b.ll.x+","+b.ll.y+","+b.ur.x+","+b.ur.y;}
  static String leaf(ShapeTree.Leaf l){return l==null?"null":identity(items,l.object)+":"+l.shapeIndexInObject+":"+box(l.boundingShape);}
  static String state(SearchTreeManager manager)throws Exception{
    String s=manager.isClearanceCompensationUsed()+":"+id(manager.getDefaultTree());var active=(Collection<?>)field(manager,"compensatedSearchTrees");s+="/"+active.size();for(Object tree:active)s+=","+id((ShapeSearchTree)tree);
    s+="/"+items.size();for(TestItem i:items){if(i==null){s+="#null";continue;}s+="#"+i.getId()+":"+i.calls+":"+i.clears+":"+i.copies+":"+i.x+":"+i.clearanceClassIndex()+":"+i.isOnTheBoard()+":"+(i.board!=null);Object info=itemField(i,"searchTreesInfo");if(info==null){s+="~null";continue;}var records=(Collection<?>)field(info,"treeList");s+="~"+records.size();for(Object r:records){s+="@"+id((ShapeSearchTree)field(r,"tree"));var e=(ShapeTree.Leaf[])field(r,"entryArr");s+=";";if(e==null)s+="null";else{s+=e.length;for(var l:e)s+="!"+leaf(l);}var a=(TileShape[])field(r,"precalculatedTreeShapes");s+=";";if(a==null)s+="null";else{s+=a.length;for(var t:a)s+="!"+box(t);}}}
    s+="/"+trees.size();for(var t:trees){if(t==null){s+="#null";continue;}s+="#"+t.key+":"+t.compensatedClearanceClassNo;var a=t.toArray();s+=";"+a.length;for(var l:a)s+="!"+leaf(l);}
    return s+"/"+(events.isEmpty()?"-":events);
  }
  static String run(String input)throws Exception{
    Scanner in=new Scanner(input);if(!in.next().equals("M"))throw new AssertionError();int nullBoard=in.nextInt();events="";trees=new ArrayList<>();trees.add(null);items=new ArrayList<>();items.add(null);
    var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});matrix.setDefaultValue(12);var rules=new BoardRules(layers,matrix);BasicBoard board=new BasicBoard(new IntBox(0,0,100,100),layers,new PolylineShape[0],0,rules,new Communication());board.itemList.delete(board.getOutline());SearchTreeManager manager=new SearchTreeManager(nullBoard==0?board:null);board.searchTreeManager=manager;
    for(int i=1;i<=3;i++){TestItem item=new TestItem(i*10,board);board.itemList.insert(item);}id(manager.getDefaultTree());
    ArrayList<String> out=new ArrayList<>();int count=in.nextInt();out.add(state(manager));
    for(int step=0;step<count;step++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();out.add(call(()->switch(op){
      case "I" -> {manager.insert(items.get(a));yield "ok";}case "R" -> {manager.remove(items.get(a));yield "ok";}case "V" -> manager.validateEntries(items.get(a));case "G" -> id(manager.getAutorouteTree(a));case "C" -> {manager.setClearanceCompensationUsed(a!=0);yield "ok";}case "v" -> {manager.clearanceValueChanged();yield "ok";}case "r" -> {manager.clearanceClassRemoved(a);yield "ok";}case "Z" -> {manager.resetCompensatedTrees();yield "ok";}case "E" -> {manager.reinsertTreeItems();yield "ok";}
      case "A" -> {rules.setTraceAngleRestriction(a<0?null:AngleRestriction.valueOf(a));yield "ok";}case "P" -> {items.get(a).plan=b;items.get(a).mode=c;yield "ok";}case "B" -> {items.get(a).board=b==0?null:board;yield "ok";}case "D" -> {items.get(a).clearDerivedData();yield "ok";}case "b" -> {items.get(a).setOnTheBoard(b!=0);yield "ok";}
      case "M" -> {matrix.setValue(a,b,c);yield "ok";}case "F" -> {items.get(a).changeClearanceClassIndex(b);yield "ok";}case "x" -> {items.get(a).moveBy(new IntVector(b,c));yield "ok";}case "Q" -> {board.itemList.generateSnapshot();yield "ok";}case "z" -> {events="";yield "ok";}
      default -> throw new AssertionError(op);
    }));out.add(state(manager));}
    if(in.hasNext())throw new AssertionError("unused input");return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
