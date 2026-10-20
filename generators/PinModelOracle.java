package app.freerouting.board.facade;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.board.trace.*;
import app.freerouting.core.library.*;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.nio.file.*;
import java.util.*;
/** Computational Pin model on real RoutingBoard; source method calls only. */
public class PinModelOracle extends BoardConstructionOracle {
  static String call(Action action)throws Exception {try{return BoardConstructionOracle.call(action);}catch(NegativeArraySizeException e){return "negative_array_error";}}
  static String convex(Shape s) {if(s==null)return "null";if(s instanceof Circle c)return "C:"+point(c.center)+":"+c.radius;return shape((TileShape)s);}
  static String direction(Direction d){return d==null?"null":point(d.getVector().toFloat().round());}
  static String fpoint(FloatPoint p){return p==null?"null":bits(p.x)+","+bits(p.y);}
  static String name(String s){return s==null?"null":s;}
  static String query(Pin p)throws Exception {
    String s="pin="+p.getId()+":"+p.getPinIndex()+":"+name(p.name())+":"+fpoint(p.relativeLocation().toFloat())+":"+point(p.getCenter())+":"+p.firstLayer()+":"+p.lastLayer()+":"+p.isPlacedOnFront()+":"+p.drillAllowed()+":"+p.hasTraceExitRestrictions()+":"+p.getChangedTo().getId()+":"+ids(p.getSwappablePins());
    for(int l=0;l<3;l++){
      int layer=l; s+="#"+layer+":"+bits(p.getMinWidth(layer))+":"+bits(p.getMaxWidth(layer))+":"+p.getTraceNeckdownHalfwidth(layer);
      var exits=p.getTraceExitRestrictions(layer);s+=":"+exits.size();for(var e:exits)s+="~"+direction(e.direction)+","+bits(e.minLength);
      var center=p.getCenter();var f=center.toFloat();
      Polyline trace=new Polyline(new Point[]{center,new IntPoint((int)f.x+2000,(int)f.y),new IntPoint((int)f.x+2000,(int)f.y+1000)});
      s+=";"+call(()->direction(p.calcNearestExitRestrictionDirection(trace,10,layer)))+":"+call(()->fpoint(p.nearestTraceExitCorner(new FloatPoint(f.x+1000,f.y+500),10,layer)));
      s+=";"+call(()->direction(p.calcNearestExitRestrictionDirection(null,10,layer)))+":"+call(()->fpoint(p.nearestTraceExitCorner(null,10,layer)));
    }
    for(int i=0;i<p.tileShapeCount();i++){Shape shape=p.getShape(i);s+="/"+convex(shape)+":"+(shape==p.getShape(i));}
    s+="/"+call(()->convex(p.getShape(-1)))+":"+call(()->convex(p.getShape(p.tileShapeCount())));
    s+="/"+call(()->p.isObstacle(null));
    for(var choice:app.freerouting.board.actions.ItemSelectionFilter.SelectableChoices.values()) {var filter=new app.freerouting.board.actions.ItemSelectionFilter(choice);s+=p.isSelectedByFilter(filter)?"1":"0";}
    return s;
  }
  static ArrayList<String> snapshot(RoutingBoard b,Observers o,Set<Integer> changed,Pin[] retained)throws Exception {
    var result=state(b,o,changed);
    for(Item i:b.getItems())if(i instanceof Pin p)result.add(query(p));
    result.add("retained="+items.id(retained[0])+":"+items.id(retained[0].getChangedTo())+":"+items.id(retained[1])+":"+items.id(retained[1].getChangedTo()));
    return result;
  }
  static String policy(RoutingBoard b,Pin original)throws Exception {
    StringJoiner out=new StringJoiner("~");
    for(int n=0;n<=2;n++)for(int m=0;m<=2;m++){
      int[] first=n==0?new int[]{}:n==1?new int[]{1}:new int[]{1,2};int[] second=m==0?new int[]{}:m==1?new int[]{2}:new int[]{2,1};
      Pin p=new Pin(original.getComponentId(),0,first,1,1000+n*3+m,FixedState.UNFIXED,b),q=new Pin(original.getComponentId(),1,second,1,2000+n*3+m,FixedState.UNFIXED,b);
      out.add(""+p.swap(q)+":"+Arrays.toString(p.netNumbers).replace(" ","")+":"+Arrays.toString(q.netNumbers).replace(" ","")+":"+p.getChangedTo().getId()+":"+q.getChangedTo().getId());
      out.add(call(()->p.swap(null)));
      var copy=(Pin)p.copy(3000);out.add(""+(copy.netNumbers==p.netNumbers)+":"+(copy.getChangedTo()==copy));
      var viaSame=new Via(p.getPadstack(),p.getCenter(),new int[]{1},1,4000,0,FixedState.UNFIXED,true,b);
      var viaOther=new Via(p.getPadstack(),p.getCenter(),new int[]{2},1,4001,0,FixedState.UNFIXED,true,b);
      out.add(""+p.isObstacle(p)+":"+p.isObstacle(q)+":"+p.isObstacle(viaSame)+":"+p.isObstacle(viaOther));
      out.add(""+p.isObstacle(new PolylineTrace(new Polyline(new Point[]{p.getCenter(),p.getCenter().translateBy(new IntVector(100,0))}),0,10,new int[]{1},1,5000,0,FixedState.UNFIXED,b)));
    }
    var component=b.components.get(original.getComponentId());var part=component.getLogicalPart();component.setLogicalPart(null);out.add(ids(original.getSwappablePins()));component.setLogicalPart(part);
    return "policy="+out;
  }
  static String pinCache(Pin p)throws Exception {
    Shape[] shapes=(Shape[])field(p,"precalculatedShapes");if(shapes==null)return "null";String s=""+shapes.length;for(Shape shape:shapes)s+=":"+convex(shape);return s;
  }
  static String edge(int mode)throws Exception {
    var layers=new LayerStructure(new Layer[]{new Layer("F.Cu",true),new Layer("In1.Cu",true),new Layer("B.Cu",true)});var matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});var rules=new BoardRules(layers,matrix);
    var b=new RoutingBoard(new IntBox(-1000,-1000,1000,1000),layers,new PolylineShape[0],1,rules,new Communication());b.library.padstacks=new Padstacks(layers);b.library.packages=new Packages(b.library.padstacks);
    ConvexShape shape=mode==7?null:mode==8||mode==9?new IntBox(0,0,100,0):new IntBox(-80,-15,80,15);
    var pad=b.library.padstacks.add("pad",new ConvexShape[]{shape,null,null},true,false);
    Package.Pin pin=new Package.Pin("P",mode==6?999:pad.id,mode==4?null:new IntVector(0,0),mode==8?37:0);
    var pack=b.library.packages.add(mode==2?null:new Package.Pin[]{mode==3?null:pin});
    if(mode!=0)b.components.add(mode==5?null:new IntPoint(0,0),0,true,mode==1?null:pack);
    Pin p=new Pin(1,mode==11?-1:0,new int[]{1},1,777,FixedState.UNFIXED,mode==10?null:b);
    var out=new ArrayList<String>();out.add("pad="+call(()->p.getPadstack()==null?"null":""+p.getPadstack().id));out.add("name="+call(()->name(p.name())));out.add("relative="+call(()->fpoint(p.relativeLocation().toFloat())));
    for(int i=0;i<2;i++){out.add("shape="+call(()->convex(p.getShape(0))));out.add("cache="+pinCache(p));}
    out.add("center="+call(()->point(p.getCenter())));out.add("centerCache="+point((Point)field(p,"center")));out.add("badIndex="+call(()->convex(p.getShape(-1))));
    p.clearDerivedData();out.add("cache="+pinCache(p));out.add("centerCache="+point((Point)field(p,"center")));p.rotateApprox(0,null);out.add("centerCache="+point((Point)field(p,"center")));out.add("relativeNull="+call(()->p.relativeLocation()==null));
    return String.join(" ",out);
  }
  static String runModel(String input)throws Exception {
    var in=new Scanner(input);int config=in.nextInt();if(config>=100)return edge(config-100);double rotation=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16)),pinRotation=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));
    var layers=new LayerStructure(new Layer[]{new Layer("F.Cu",true),new Layer("In1.Cu",true),new Layer("B.Cu",true)});
    var matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});matrix.setDefaultValue(12);var rules=new BoardRules(layers,matrix);rules.setPinEdgeToTurnDist(5);rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    var com=new Communication();var o=new Observers();o.constructionMetadata=true;com.observers=o;
    var b=new RoutingBoard(new IntBox(-100000,-100000,100000,100000),layers,new PolylineShape[0],1,rules,com);rules.nets.add("A",1,false);rules.nets.add("B",1,false);
    b.library.padstacks=new Padstacks(layers);b.library.packages=new Packages(b.library.padstacks);
    int kind=config%4;ConvexShape shape=kind==2?new Circle(new IntPoint(100,20),37):kind==3?new Circle(new IntPoint(0,0),80).boundingOctagon():new IntBox(-80,-15,80,15);
    ConvexShape[] padShapes={shape,kind==2?shape:null,kind==0?null:shape};var pad=b.library.padstacks.add("pad",padShapes,true,(config&16)!=0);
    int count=(config&32)==0?2:4;Package.Pin[] pins=new Package.Pin[count];LogicalPart.PartPin[] logicalPins=new LogicalPart.PartPin[count];
    for(int i=0;i<count;i++){pins[i]=new Package.Pin("P"+i,pad.id,new IntVector(-5000+i*10000,0),pinRotation);logicalPins[i]=new LogicalPart.PartPin(i,"P"+i,"G",1,"p",i<2?1:0);}
    var pack=b.library.packages.add(pins);var component=b.components.add(new IntPoint(20000,20000),rotation,(config&4)==0,pack);component.setLogicalPart(b.library.logicalParts.add("logic",logicalPins));b.components.setFlipStyleRotateFirst((config&8)!=0);
    Pin p=b.insertPin(component.id,0,new int[]{1},1,FixedState.UNFIXED),q=b.insertPin(component.id,1,new int[]{2},1,FixedState.UNFIXED);Pin[] retained={p,q};
    componentHistory=new Pool();items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();Set<Integer> changed=new TreeSet<>();
    var out=snapshot(b,o,changed,retained);
    b.generateSnapshot();out.add("swap="+p.swap(q));out.addAll(snapshot(b,o,changed,retained));
    out.add("undoResult="+b.undo(changed));out.addAll(snapshot(b,o,changed,retained));
    out.add("redoResult="+b.redo(changed));out.addAll(snapshot(b,o,changed,retained));
    p=b.getPin(component.id,0);q=b.getPin(component.id,1);
    var copy=(Pin)p.copy(900);out.add("copy="+query(copy)+":"+(copy.getChangedTo()==copy));
    rules.setPinEdgeToTurnDist(-1);out.add(query(p));rules.setPinEdgeToTurnDist(5);
    b.generateSnapshot();p.moveBy(new IntVector(20,-30));out.addAll(snapshot(b,o,changed,retained));
    p.rotateApprox(37,null);out.add(query(p));p.turn90Degree(0,null);out.add(query(p));p.changePlacementSide(null);out.add(query(p));
    out.add("selfSwap="+p.swap(p));out.add(query(p));
    out.add(policy(b,p));
    return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception {try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String line:Files.readAllLines(Path.of(args[0])))out.write(line+" | "+runModel(line)+"\n");}}
}
