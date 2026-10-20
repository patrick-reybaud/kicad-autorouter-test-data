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
public class BoardTraceOracle {
  static String events;static int failure;static ArrayList<Polyline> polylines;static ArrayList<ShapeSearchTree> trees;
  interface Call{Object run()throws Exception;}
  static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(NegativeArraySizeException e){return "size_error";}catch(IllegalStateException e){return "callback_error";}catch(Exception e){throw new RuntimeException(e);}}
  static <T> int id(ArrayList<T> p,T o){for(int i=0;i<p.size();i++)if(p.get(i)==o)return i;p.add(o);return p.size()-1;}
  static String number(double v){return "d"+Long.toHexString(Double.doubleToLongBits(v));}
  static String point(Point p){if(p==null)return "null";FloatPoint f=p.toFloat();return p.getId()+":"+number(f.x)+","+number(f.y);}
  static String box(IntBox b){return b.ll.x+","+b.ll.y+","+b.ur.x+","+b.ur.y;}
  static String tile(TileShape t){if(t==null)return "null";return t.getClass().getSimpleName()+":"+t.getId()+":"+t.dimension()+":"+t.borderLineCount()+":"+box(t.boundingBox());}
  static Object field(Item i,String n)throws Exception{Field f=Item.class.getDeclaredField(n);f.setAccessible(true);return f.get(i);}
  static class Manager extends SearchTreeManager {
    Manager(BasicBoard b){super(b);}
    public void remove(Item i){events+="r,";if(failure==1)throw new IllegalStateException();super.remove(i);}
    public void insert(Item i){events+="i,";if(failure==2)throw new IllegalStateException();super.insert(i);}
  }
  static class TestTrace extends PolylineTrace {
    int clears,calls;
    TestTrace(Polyline p,int l,int half,int[] nets,FixedState fixed,BasicBoard b){super(p,l,half,nets,1,40,2,fixed,b);}
    public void clearDerivedData(){events+="d,";clears++;if(failure==3)throw new IllegalStateException();super.clearDerivedData();}
    protected TileShape[] calculateTreeShapes(ShapeSearchTree t){events+="s"+id(trees,t)+",";calls++;return super.calculateTreeShapes(t);}
  }
  static String state(TestTrace t)throws Exception {
    String s=t.getLayer()+":"+t.getHalfWidth()+":"+t.firstLayer()+":"+t.lastLayer()+":"+t.shapeLayer(-7)+":"+t.isOnTheBoard()+":"+(t.board!=null)+":"+t.clears+":"+t.calls+":"+id(polylines,t.polyline());
    s+="/"+call(()->point(t.firstCorner()))+"/"+call(()->point(t.lastCorner()))+"/"+call(()->t.cornerCount())+"/"+call(()->t.tileShapeCount())+"/"+call(()->number(t.getLength()))+"/"+call(()->box(t.boundingBox()))+"/"+call(()->t.isRoutable())+"/"+t.isDrillable(1);
    Object info=field(t,"searchTreesInfo");s+="/"+(info==null?"null":"cache");
    for(var tree:trees){s+="#";if(tree==null){s+="null";continue;}s+=call(()->{var a=tree.toArray();String v=""+a.length;for(var leaf:a)v+=":"+(leaf.object==t)+","+leaf.shapeIndexInObject+","+tile(leaf.boundingShape);return v;});}
    return s+"/"+(events.isEmpty()?"-":events);
  }
  static String run(String input)throws Exception {
    Scanner in=new Scanner(input);if(!in.next().equals("T"))throw new AssertionError();int nullBoard=in.nextInt(),layer=in.nextInt(),half=in.nextInt(),fixed=in.nextInt(),nc=in.nextInt();int[] nets=nc<0?null:new int[nc];if(nets!=null)for(int i=0;i<nc;i++)nets[i]=in.nextInt();int pc=in.nextInt();Point[] points=pc<0?null:new Point[pc];if(points!=null)for(int i=0;i<pc;i++)points[i]=new IntPoint(in.nextInt(),in.nextInt());Polyline p=points==null?null:new Polyline(points);
    events="";failure=0;polylines=new ArrayList<>();polylines.add(null);polylines.add(p);polylines.add(new Polyline(new Point[]{new IntPoint(0,0),new IntPoint(8,0),new IntPoint(8,9)}));polylines.add(new Polyline(new Point[]{new IntPoint(1,2),new IntPoint(13,6)}));
    var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});matrix.setDefaultValue(12);matrix.setValue(1,2,0,42);var rules=new BoardRules(layers,matrix);BasicBoard board=new BasicBoard(new IntBox(-100,-100,100,100),layers,new PolylineShape[0],0,rules,new Communication());board.itemList.delete(board.getOutline());Manager manager=new Manager(board);board.searchTreeManager=manager;
    trees=new ArrayList<>();trees.add(null);trees.add(manager.getDefaultTree());trees.add(new ShapeSearchTree45Degree(board,1));trees.add(new ShapeSearchTree90Degree(board,2));trees.add(new ShapeSearchTree90Degree(null,1));
    TestTrace[] holder=new TestTrace[1];String creation=call(()->{holder[0]=new TestTrace(p,layer,half,nets,fixed<0?null:FixedState.values()[fixed],nullBoard==0?board:null);return "ok";});TestTrace t=holder[0];if(t==null)return creation;
    ArrayList<String> out=new ArrayList<>();out.add(creation);out.add(state(t));int count=in.nextInt();
    for(int i=0;i<count;i++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();out.add(call(()->switch(op){
      case "I"->{manager.insert(t);yield "ok";}case "R"->{manager.remove(t);yield "ok";}case "T"->{t.translateBy(new IntVector(a,b));yield "ok";}case "Q"->{t.turn90Degree(a,new IntPoint(b,c));yield "ok";}case "M"->{t.changePlacementSide(new IntPoint(a,b));yield "ok";}
      case "L"->{t.setLayer(a);yield "ok";}case "B"->{t.board=a==0?null:board;yield "ok";}case "F"->{failure=a;yield "ok";}case "P"->{PolylineTraceSearchTreeAdapter.replaceGeometry(t,polylines.get(a));yield "ok";}
      case "C"->tile(t.getTraceConnectionShape(trees.get(a),b));case "W"->t.getCompensatedHalfWidth(trees.get(a));case "N"->point(t.nearestEndPoint(c==0?new IntPoint(a,b):null));case "A"->t.isOnLayer(a);
      case "S"->{t.setFixedState(a<0?null:FixedState.values()[a]);yield "ok";}case "n"->{t.netNumbers=a==0?null:a==1?new int[0]:new int[]{1,0,2};yield "ok";}
      case "g"->tile(t.getTreeShape(trees.get(a),b));case "e"->PolylineTraceSearchTreeAdapter.hasDefaultEntries(t,a==0?null:t);case "s"->{var ar=trees.get(a).calculateTreeShapes(b==0?null:t);String v=""+ar.length;for(var sh:ar)v+="/"+tile(sh);yield v;}
      case "f"->{ItemSelectionFilter filter=new ItemSelectionFilter();filter.setSelected(ItemSelectionFilter.SelectableChoices.TRACES,a!=0);filter.setSelected(ItemSelectionFilter.SelectableChoices.FIXED,b!=0);filter.setSelected(ItemSelectionFilter.SelectableChoices.UNFIXED,c!=0);yield t.isSelectedByFilter(filter);}
      case "z"->{events="";yield "ok";}default->throw new AssertionError(op);
    }));out.add(state(t));}
    if(in.hasNext())throw new AssertionError("unused input");return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
