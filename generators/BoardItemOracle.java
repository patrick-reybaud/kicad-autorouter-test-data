package app.freerouting.board.model.items;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.Communication;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class BoardItemOracle {
  static ArrayList<TestItem> all;
  static ArrayList<int[]> arrays;
  static ArrayList<ItemSelectionFilter> filters;
  static int cloneCalls;
  static class TestItem extends Item {
    int first,last,choice,copyMode;
    TestItem(int[] n,int c,int id,int component,FixedState fixed,BasicBoard b,int first,int last,int choice){super(n,c,id,component,fixed,b);this.first=first;this.last=last;this.choice=choice;all.add(this);}
    public Item copy(int id){cloneCalls++;if(copyMode==1)return null;if(copyMode==2)throw new IllegalStateException();if(copyMode==3)return this;return new TestItem(netNumbers,clearanceClassIndex(),id,getComponentId(),getFixedState(),board,first,last,choice);}
    public boolean isObstacle(Item other){return !sharesNet(other);}public int tileShapeCount(){return 0;}
    protected TileShape[] calculateTreeShapes(ShapeSearchTree tree){return new TileShape[0];}
    public boolean isOnLayer(int layer){return first<=layer&&layer<=last;}public int firstLayer(){return first;}public int lastLayer(){return last;}
    public boolean write(ObjectOutputStream stream){throw new UnsupportedOperationException();}
    public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}
    public IntBox boundingBox(){return new IntBox(0,0,1,1);}public int shapeLayer(int i){return first;}
    public boolean isSelectedByFilter(ItemSelectionFilter f){return isSelectedByFixedFilter(f)&&f.isSelected(choice<0?null:ItemSelectionFilter.SelectableChoices.values()[choice]);}
    boolean fixedFilter(ItemSelectionFilter f){return isSelectedByFixedFilter(f);}
    public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
  }
  interface Call{Object run()throws Exception;}
  static String call(Call c){try{return String.valueOf(c.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(IllegalStateException e){return "clone_error";}catch(Exception e){throw new RuntimeException(e);}}
  static int identity(List<?> list,Object value){for(int i=0;i<list.size();i++)if(list.get(i)==value)return i;return -1;}
  static int arrayId(int[] a){int id=identity(arrays,a);if(id<0){id=arrays.size();arrays.add(a);}return id;}
  static String array(int[] a){if(a==null)return "null";String s=""+a.length;for(int i:a)s+=","+i;return s;}
  static String items(Collection<?> items){String s=""+items.size();for(Object i:items)s+=","+identity(all,i);return s;}
  static String state(BasicBoard board,List<UndoableObjects.Storable> cancelled,List<UndoableObjects.Storable> restored)throws Exception{
    String s=board.communication.idGenerator.maxGeneratedId()+":"+cloneCalls+"/"+all.size();
    for(TestItem i:all){if(i==null){s+="#null";continue;}s+="#"+i.getId()+":"+arrayId(i.netNumbers)+":"+i.clearanceClassIndex()+":"+i.getComponentId()+":"+(i.getFixedState()==null?-1:i.getFixedState().ordinal())+":"+i.isOnTheBoard()+":"+(i.board!=null)+":"+i.copyMode+":"+Long.toHexString(Double.doubleToRawLongBits(i.smallestClearance));}
    s+="/"+arrays.size();for(int[] a:arrays)s+="#"+array(a);
    s+="/"+filters.size();for(var f:filters){s+="#";if(f==null)s+="null";else for(var c:ItemSelectionFilter.SelectableChoices.values())s+=f.isSelected(c)?"1":"0";}
    ArrayList<Object> visible=new ArrayList<>();var it=board.itemList.startReadObject();Object o;while((o=board.itemList.readObject(it))!=null)visible.add(o);
    return s+"/"+items(visible)+"/"+items(cancelled)+"/"+items(restored);
  }
  static FixedState fixed(int n){return n<0?null:FixedState.values()[n];}
  static ItemSelectionFilter.SelectableChoices choice(int n){return n<0?null:ItemSelectionFilter.SelectableChoices.values()[n];}
  static String run(String input)throws Exception{
    Scanner in=new Scanner(input);if(!in.next().equals("I"))throw new AssertionError();
    var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("P",false),new Layer("B",true)});var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});var rules=new BoardRules(layers,matrix);BasicBoard board=new BasicBoard(new IntBox(0,0,100,100),layers,new PolylineShape[0],0,rules,new Communication());board.itemList.delete(board.getOutline());for(int n=1;n<=8;n++)rules.nets.add("n"+n,1,false);
    all=new ArrayList<>();all.add(null);arrays=new ArrayList<>();arrays.add(null);arrays.add(new int[0]);arrays.add(new int[]{1,2,1});arrays.add(new int[]{0,-1,9999999,10000000});filters=new ArrayList<>();filters.add(null);filters.add(new ItemSelectionFilter());cloneCalls=0;
    ArrayList<UndoableObjects.Storable> cancelled=new ArrayList<>(),restored=new ArrayList<>();ArrayList<String> out=new ArrayList<>();int n=in.nextInt();
    for(int j=0;j<n;j++){int arr=in.nextInt(),cl=in.nextInt(),id=in.nextInt(),comp=in.nextInt(),fx=in.nextInt(),bo=in.nextInt(),first=in.nextInt(),last=in.nextInt(),ch=in.nextInt();out.add(call(()->identity(all,new TestItem(arrays.get(arr),cl,id,comp,fixed(fx),bo==0?null:board,first,last,ch))));}
    out.add(state(board,cancelled,restored));int count=in.nextInt();
    for(int step=0;step<count;step++){String op=in.next();int i=in.nextInt(),j=in.nextInt(),k=in.nextInt();out.add(call(()->switch(op){
      case "C" -> all.get(i).containsNet(j);case "O" -> all.get(i).isObstacle(j);case "T" -> all.get(i).isTraceObstacle(j);case "S" -> all.get(i).sharesNet(all.get(j));case "s" -> all.get(i).sharesNetNo(arrays.get(j));case "E" -> all.get(i).netsEqual(all.get(j));case "e" -> all.get(i).netsEqual(arrays.get(j));case "N" -> all.get(i).netsNormal();case "n" -> all.get(i).netCount();case "G" -> all.get(i).getNetNumber(j);case "R" -> all.get(i).removeFromNet(j);case "A" -> {all.get(i).assignNetNo(j);yield "ok";}
      case "D" -> all.get(i).compareTo(j<0?new Object():all.get(j));case "L" -> all.get(i).sharesLayer(all.get(j));case "l" -> all.get(i).firstCommonLayer(all.get(j));case "r" -> all.get(i).lastCommonLayer(all.get(j));
      case "F" -> {all.get(i).setFixedState(fixed(j));yield "ok";}case "U" -> {all.get(i).unfix();yield "ok";}case "f" -> all.get(i).isUserFixed();case "h" -> all.get(i).isShoveFixed();case "B" -> {all.get(i).board=j==0?null:board;yield "ok";}case "b" -> {all.get(i).setOnTheBoard(j!=0);yield "ok";}case "c" -> {all.get(i).assignComponentId(j);yield "ok";}case "X" -> {all.get(i).setClearanceClassIndex(j);yield "ok";}
      case "M" -> {all.get(i).netNumbers=arrays.get(j);yield "ok";}case "m" -> {arrays.get(i)[j]=k;yield "ok";}case "V" -> arrayId(all.get(i).netNumbers);case "v" -> {all.get(i).smallestClearance=j;yield "ok";}
      case "K" -> identity(all,all.get(i).clone());case "k" -> identity(all,all.get(i).copy(j));case "Z" -> {all.get(i).copyMode=j;yield "ok";}
      case "I" -> {board.itemList.insert(all.get(i));yield "ok";}case "Q" -> {board.itemList.generateSnapshot();yield "ok";}case "u" -> board.itemList.undo(cancelled,restored);case "d" -> board.itemList.redo(cancelled,restored);case "p" -> board.itemList.popSnapshot();
      case "W" -> {ItemSelectionFilter f=switch(i){case 0->new ItemSelectionFilter();case 1->new ItemSelectionFilter(choice(j));case 2->new ItemSelectionFilter(new ItemSelectionFilter.SelectableChoices[]{choice(j),choice(k)});case 3->new ItemSelectionFilter(filters.get(j));case 4->new ItemSelectionFilter((ItemSelectionFilter.SelectableChoices[])null);default->throw new AssertionError();};filters.add(f);yield filters.size()-1;}
      case "w" -> {filters.get(i).setSelected(choice(j),k!=0);yield "ok";}case "a" -> {filters.get(i).selectAll();yield "ok";}case "z" -> {filters.get(i).deselectAll();yield "ok";}case "t" -> filters.get(i).isSelected(choice(j));case "H" -> all.get(i).fixedFilter(filters.get(j));case "J" -> all.get(i).isSelectedByFilter(filters.get(j));
      case "Y" -> {Set<Item> items=new TreeSet<>();for(int x=1;x<all.size();x++)items.add(all.get(x));yield items(filters.get(i).filter(items));}case "y" -> items(filters.get(i).filter(null));
      default -> throw new AssertionError(op);
    }));out.add(state(board,cancelled,restored));}
    if(in.hasNext())throw new AssertionError("unused input");return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
