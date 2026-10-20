package app.freerouting.board.facade;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.board.trace.*;
import app.freerouting.core.library.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.nio.file.*;
import java.util.*;
/** Real trace/via rotations with their observable caches and board state. */
public class ModelRotationOracle extends BoardConstructionOracle {
  static String convex(Shape s){if(s==null)return "null";if(s instanceof Circle c)return "C:"+point(c.center)+":"+c.radius;return shape((TileShape)s);}
  static Object traceCache(PolylineTrace t,Object tree)throws Exception {
    Object info=field(t,"searchTreesInfo");if(info==null)return null;for(Object record:(Collection<?>)field(info,"treeList"))if(field(record,"tree")==tree)return field(record,"precalculatedTreeShapes");return null;
  }
  static String via(Via v)throws Exception {
    String s="via="+point(v.getCenter())+":"+bits((double)field(v,"precalculatedMinWidth"))+":"+field(v,"precalculatedFirstLayer")+":"+field(v,"precalculatedLastLayer");
    Shape[] shapes=(Shape[])field(v,"precalculatedShapes");s+="/"+(shapes==null?"null":""+shapes.length);if(shapes!=null)for(Shape shape:shapes)s+="~"+convex(shape);
    return s;
  }
  static ArrayList<String> snapshot(RoutingBoard b,Observers o,Via v,PolylineTrace t)throws Exception {
    var result=state(b,o,new TreeSet<Integer>());result.add(via(v));result.add("polyline="+t.cornerCount()+":"+bits(t.getLength()));return result;
  }
  static String runModel(String line)throws Exception {
    var in=new Scanner(line);int config=in.nextInt();double angle=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));
    var layers=new LayerStructure(new Layer[]{new Layer("F.Cu",true),new Layer("B.Cu",true)});var matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});matrix.setDefaultValue(12);var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(AngleRestriction.valueOf(config%3));var com=new Communication();var o=new Observers();o.constructionMetadata=true;com.observers=o;
    var b=new RoutingBoard(new IntBox(-100000,-100000,100000,100000),layers,new PolylineShape[0],1,rules,com);rules.nets.add("A",1,false);rules.nets.add("B",1,false);
    b.library.padstacks=new Padstacks(layers);ConvexShape padShape=config%2==0?new Circle(new IntPoint(0,0),50):new IntBox(-50,-30,50,30);var pad=b.library.padstacks.add("pad",new ConvexShape[]{padShape,padShape},true,false);
    var v=new Via(pad,new IntPoint(5000,0),new int[]{1},1,0,0,FixedState.UNFIXED,true,b);b.insertItem(v);
    var t=b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(5000,0),new IntPoint(5000,500),new IntPoint(8000,500)}),0,10,new int[]{1},1,FixedState.UNFIXED);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();
    v.minWidth();v.getShape(0);t.getTileShape(0);var traceShapes=traceCache(t,b.searchTreeManager.getDefaultTree());
    var out=snapshot(b,o,v,t);
    for(int step=0;step<6;step++){
      double turn=step==0?angle:step==1?0:step==2?-angle:step==3?37:step==4?-0.0:90;
      FloatPoint pole=step==1||step==3?null:step==5?new FloatPoint(.5,-.5):FloatPoint.ZERO;
      out.add("traceRotate="+call(()->{t.rotateApprox(turn,pole);return "ok";}));
      out.add("traceCache="+(traceShapes==traceCache(t,b.searchTreeManager.getDefaultTree())));
      out.add("viaRotate="+call(()->{v.rotateApprox(turn,pole);return "ok";}));
      out.addAll(snapshot(b,o,v,t));
    }
    // Null center: the base rotation skips geometry but still invalidates derived data.
    Via absent=new Via(pad,null,new int[]{1},1,999,0,FixedState.UNFIXED,true,b);
    out.add("nullShape="+call(()->convex(absent.getShape(0))));out.add(via(absent));absent.rotateApprox(angle,null);out.add(via(absent));
    return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception {try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String line:Files.readAllLines(Path.of(args[0])))out.write(line+" | "+runModel(line)+"\n");}}
}
