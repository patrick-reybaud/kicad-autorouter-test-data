package app.freerouting.board.trace;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.*;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class TraceUpdatesOracle {
  static String events;static int failure;static ArrayList<ShapeSearchTree> trees;static ArrayList<TestTrace> traces;
  static ArrayList<TileShape[]> shapeArrays;static ArrayList<ShapeTree.Leaf[]> entryArrays;static ArrayList<TileShape> tiles;static ArrayList<ShapeTree.Leaf> leaves;
  interface Call{Object run()throws Exception;}
  static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(NegativeArraySizeException e){return "size_error";}catch(IllegalStateException e){return "callback_error";}catch(InvocationTargetException e){return call(()->{throw (Exception)e.getCause();});}catch(Exception e){throw new RuntimeException(e);}}
  static <T> int id(ArrayList<T> p,T o){for(int i=0;i<p.size();i++)if(p.get(i)==o)return i;p.add(o);return p.size()-1;}
  static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
  static Object itemField(Item i,String n)throws Exception{Field f=Item.class.getDeclaredField(n);f.setAccessible(true);return f.get(i);}
  static String tile(TileShape t){if(t==null)return "null";IntBox b=t.boundingBox();return t.getClass().getSimpleName()+":"+t.getId()+":"+t.dimension()+":"+b.ll.x+","+b.ll.y+","+b.ur.x+","+b.ur.y;}
  static class TestTrace extends PolylineTrace {
    final int serial;int calls,clears;
    TestTrace(int serial,Polyline p,BasicBoard b){super(p,0,2,new int[]{1},1,10+serial*10,0,FixedState.UNFIXED,b);this.serial=serial;}
    protected TileShape[] calculateTreeShapes(ShapeSearchTree t){events+="s"+serial+":"+id(trees,t)+",";calls++;if(failure==serial+1)throw new IllegalStateException();return super.calculateTreeShapes(t);}
    public void clearDerivedData(){events+="d"+serial+",";clears++;if(failure==serial+4)throw new IllegalStateException();super.clearDerivedData();}
  }
  static String state()throws Exception {
    String s="";
    for(TestTrace t:traces){s+="#"+t.serial+":"+t.calls+":"+t.clears+":"+t.isOnTheBoard()+":"+(t.board!=null);Object info=itemField(t,"searchTreesInfo");if(info==null){s+="/null";continue;}var records=(Collection<?>)field(info,"treeList");s+="/"+records.size();for(Object r:records)s+="@"+id(trees,(ShapeSearchTree)field(r,"tree"))+":"+id(entryArrays,(ShapeTree.Leaf[])field(r,"entryArr"))+":"+id(shapeArrays,(TileShape[])field(r,"precalculatedTreeShapes"));}
    s+="/trees";for(ShapeSearchTree tree:trees){s+="#";if(tree==null){s+="null";continue;}s+=tree.size()+":"+call(()->{var a=tree.toArray();String text=""+a.length;for(var leaf:a)text+=","+id(leaves,leaf);return text;});}
    s+="/shapes";for(var a:shapeArrays){s+="#";if(a==null){s+="null";continue;}s+=a.length;for(var sh:a)s+=","+id(tiles,sh);}
    s+="/entries";for(var a:entryArrays){s+="#";if(a==null){s+="null";continue;}s+=a.length;for(var leaf:a)s+=","+id(leaves,leaf);}
    s+="/leaves";for(var leaf:leaves){s+="#";if(leaf==null){s+="null";continue;}s+=(leaf.object==null?-1:((TestTrace)leaf.object).serial)+":"+leaf.shapeIndexInObject+":"+tile(leaf.boundingShape);}
    s+="/tiles";for(var sh:tiles)s+="#"+tile(sh);
    return s+"/"+(events.isEmpty()?"-":events);
  }
  static Polyline polyline(Scanner in){int count=in.nextInt();ArrayList<Point> points=new ArrayList<>();for(int i=0;i<count;i++)points.add(new IntPoint(in.nextInt(),in.nextInt()));return new Polyline(points.toArray(Point[]::new));}
  static String run(String input)throws Exception {
    Scanner in=new Scanner(input);if(!in.next().equals("U"))throw new AssertionError();int scope=in.nextInt(),angle=in.nextInt();String op=in.next();int a=in.nextInt(),b=in.nextInt(),flags=in.nextInt(),fail=in.nextInt();Polyline[] polys={polyline(in),polyline(in),polyline(in),polyline(in)};if(in.hasNext())throw new AssertionError("unused input");
    events="";failure=0;trees=new ArrayList<>();trees.add(null);traces=new ArrayList<>();shapeArrays=new ArrayList<>();shapeArrays.add(null);entryArrays=new ArrayList<>();entryArrays.add(null);tiles=new ArrayList<>();tiles.add(null);leaves=new ArrayList<>();leaves.add(null);
    var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});matrix.setDefaultValue(12);matrix.setValue(1,2,0,42);var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(AngleRestriction.valueOf(angle));BasicBoard board=new BasicBoard(new IntBox(-100,-100,100,100),layers,new PolylineShape[0],0,rules,new Communication());board.itemList.delete(board.getOutline());SearchTreeManager manager=new SearchTreeManager(board);board.searchTreeManager=manager;
    trees.add(manager.getDefaultTree());trees.add(manager.getAutorouteTree(1));trees.add(manager.getAutorouteTree(2));
    for(int i=0;i<3;i++){var t=new TestTrace(i,polys[i],board);traces.add(t);if(!op.equals("R")||i==0){board.itemList.insert(t);manager.insert(t);}}
    String before=state();events="";
    TestTrace from=traces.get(0),to=traces.get(1),end=traces.get(2);
    if((flags&16)!=0)from.board=null;if((flags&32)!=0)to.board=null;
    if((flags&64)!=0)from.clearSearchTreeEntries();if((flags&128)!=0)to.clearSearchTreeEntries();
    if((flags&256)!=0)for(var t:traces)t.clearDerivedData();failure=fail;
    TestTrace f=(flags&1)!=0?null:from,t=(flags&2)!=0?null:to,e=(flags&4)!=0?null:end;Polyline joined=(flags&8)!=0?null:polys[3];ShapeSearchTree tree=trees.get(scope==1?2:1);
    String result=call(()->{
      if(scope<=1){switch(op){case "C"->tree.changeEntries(f,joined,a,b);case "F"->tree.mergeEntriesInFront(f,t,joined,a,b);case "E"->tree.mergeEntriesAtEnd(f,t,joined,a,b);case "R"->tree.reuseEntriesAfterCutout(f,t,e);default->throw new AssertionError(op);}}
      else if(scope==2||op.equals("R")){switch(op){case "C"->manager.changeEntries(f,joined,a,b);case "F"->manager.mergeEntriesInFront(f,t,joined,a,b);case "E"->manager.mergeEntriesAtEnd(f,t,joined,a,b);case "R"->{Method m=SearchTreeManager.class.getDeclaredMethod("reuseEntriesAfterCutout",PolylineTrace.class,PolylineTrace.class,PolylineTrace.class);m.setAccessible(true);m.invoke(manager,f,t,e);}default->throw new AssertionError(op);}}
      else {switch(op){case "C"->PolylineTraceSearchTreeAdapter.changeEntries(f,joined,a,b);case "F"->PolylineTraceSearchTreeAdapter.mergeEntriesInFront(f,t,joined,a,b);case "E"->PolylineTraceSearchTreeAdapter.mergeEntriesAtEnd(f,t,joined,a,b);default->throw new AssertionError(op);}}
      return "ok";
    });return before+" "+result+" "+state();
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
