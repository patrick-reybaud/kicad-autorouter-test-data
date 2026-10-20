package app.freerouting.board.searchtree;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.*;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.state.Communication;
import app.freerouting.rules.*;
import java.io.*;import java.util.*;
public class TiePinOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NegativeArraySizeException e){return "size_error";}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(IllegalStateException e){return "callback_error";}}
 static class TestPin extends Pin {
  State s;TestPin(State s){super(0,0,new int[]{1,2},1,20,FixedState.UNFIXED,null);this.s=s;}
  public Point getCenter(){s.touch(2,0);return s.centers[s.center];}
  public TileShape getTreeShapeOnLayer(ShapeSearchTree t,int l){s.touch(1,l);return s.tiles[s.pinShape];}
 }
 static class Trace extends PolylineTrace {
  State s;Trace(State s,Polyline p){super(p,0,10,new int[]{1},1,10,0,FixedState.UNFIXED,s.board);this.s=s;}
  public int getLayer(){if(s!=null)s.touch(8,0);return super.getLayer();}
  public Point firstCorner(){s.touch(3,0);return (s.flags&4)!=0?null:super.firstCorner();}
  public Point lastCorner(){s.touch(4,0);return (s.flags&8)!=0?null:super.lastCorner();}
  public int cornerCount(){s.touch(6,0);return super.cornerCount();}
  public Polyline polyline(){s.touch(7,0);return (s.flags&16)!=0?null:super.polyline();}
  public TileShape getTreeShape(ShapeTree t,int i){s.touch(5,i);return (s.flags&32)!=0?null:super.getTreeShape(t,i);}
  TileShape raw(ShapeTree t,int i){return super.getTreeShape(t,i);}
 }
 static class State {
  TileShape[] tiles={null,new IntBox(-20,-20,220,120),new IntBox(-5,-5,5,5),new IntBox(95,95,105,105),new IntBox(195,95,205,105),new IntBox(-40,-40,240,140),new IntBox(-10,0,10,0),Simplex.EMPTY,new IntOctagon(-30,-30,30,30,-40,40,-40,40),new IntBox(-20,-20,20,20),new IntBox(180,80,220,120)};
  Point[] centers={null,new IntPoint(0,0),new IntPoint(100,0),new IntPoint(100,100),new IntPoint(200,100),new IntPoint(777,333)};
  static BasicBoard board(){var l=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});return new BasicBoard(new IntBox(-100,-100,300,300),l,new PolylineShape[0],0,new BoardRules(l,new ClearanceMatrix(2,l,new String[]{"none","default"})),new Communication());}
  BasicBoard board=board();
  ShapeSearchTree tree=new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE,null,0);TestPin pin=new TestPin(this);Trace trace;int center=1,pinShape=2,flags,failure=-1,failAt=1,seen;boolean active;String events="";int nextId=1;IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();
  State(int poly){Point[][] points={{centers[1],centers[2],centers[3],centers[4]},{centers[4],centers[3],centers[2],centers[1]},{centers[1],centers[2],centers[3],new IntPoint(0,100),centers[1]}};trace=new Trace(this,new Polyline(points[poly]));TileShape[] shapes=new TileShape[trace.cornerCount()-1];Arrays.fill(shapes,tiles[1]);trace.setSearchTreeEntries(new ShapeTree.Leaf[shapes.length],tree);trace.setPrecalculatedTreeShapes(shapes,tree);tree.insert(trace);}
  int id(Object o){return o==null?0:ids.computeIfAbsent(o,k->nextId++);}
  void touch(int code,int arg){if(!active)return;events+=code+":"+arg+",";if(code==failure&&++seen==failAt)throw new IllegalStateException();}
  String shape(TileShape t){if(t==null)return "N";String s=t.getClass().getSimpleName()+":"+t.getId()+":"+t.dimension();for(int i=0;i<t.borderLineCount();i++)s+=","+t.borderLine(i).getId();return s;}
  String dump(){String s="T"+tree.size()+":";s+=call(()->{String text="";for(var leaf:tree.toArray())text+=leaf==null?"N,":id(leaf)+":"+leaf.shapeIndexInObject+":"+shape((TileShape)leaf.boundingShape)+",";return text;});var entries=trace.getSearchTreeEntries(tree);s+="E"+(entries==null?"N":id(entries)+":");if(entries!=null)for(var leaf:entries)s+=id(leaf)+",";s+="S";for(int i=0;i<trace.cornerCount()-1;i++)s+=shape(trace.raw(tree,i))+"/";return s;}
  String invoke(){events="";seen=0;active=true;String v=call(()->{tree.reduceTraceShapeAtTiePin((flags&1)!=0?null:pin,(flags&2)!=0?null:trace);return "ok";});active=false;return v+"~"+(events.isEmpty()?"-":events);}
 }
 static String run(String line){var in=new Scanner(line);in.next();State s=new State(in.nextInt());int n=in.nextInt();var out=new ArrayList<String>();out.add(s.dump());for(int k=0;k<n;k++){s.center=in.nextInt();s.pinShape=in.nextInt();s.flags=in.nextInt();s.failure=in.nextInt();s.failAt=in.nextInt();if((s.flags&64)!=0)s.trace.setSearchTreeEntries(null,s.tree);out.add(s.invoke());out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String line;while((line=in.readLine())!=null)System.out.println(line+" | "+run(line));}
}
