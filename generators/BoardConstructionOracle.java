package app.freerouting.board.facade;

import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.searchtree.*;
import app.freerouting.board.state.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Test-only exporter and operation oracle. All mutations use the pinned, real RoutingBoard. */
public class BoardConstructionOracle {
  static Object field(Object o,String name)throws Exception {
    for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){}
    throw new NoSuchFieldException(name);
  }
  static String bits(double v){return Long.toUnsignedString(Double.doubleToLongBits(v),16);}
  static String point(Point p){if(p==null)return "null";var f=p.toFloat();return bits(f.x)+","+bits(f.y);}
  static String box(IntBox b){return b==null?"null":b.ll.x+","+b.ll.y+","+b.ur.x+","+b.ur.y;}
  static String shape(TileShape s){if(s==null)return "null";if(s instanceof IntBox b)return "B:"+box(b);if(s instanceof IntOctagon o)return "O:"+o.leftX+","+o.bottomY+","+o.rightX+","+o.topY+","+o.upperLeftDiagonalX+","+o.lowerRightDiagonalX+","+o.lowerLeftDiagonalX+","+o.upperRightDiagonalX;String v="S:"+s.borderLineCount();for(int i=0;i<s.borderLineCount();i++){Line l=s.borderLine(i);v+="~"+point(l.a)+":"+point(l.b);}return v;}
  static String ids(Collection<? extends Item> items){String s=""+items.size();for(Item i:items)s+=","+i.getId();return s;}
  static class CallbackFailure extends RuntimeException {}
  static class Observers extends BoardObserverAdaptor {
    boolean constructionMetadata; String events=""; Collection<Item> mutate; int mutation; int failKind,failAfter;
    void fail(int kind){if(failKind==kind&&--failAfter==0){failKind=0;events+="!;";throw new CallbackFailure();}}
    void event(String k,Item i){events+=k+i.getId()+":"+i.board.getRevision()+":"+i.isOnTheBoard()+":"+ids(i.board.getItems())+";";if(constructionMetadata){try{var routing=(RoutingBoard)i.board;events+="{true:"+(routing.failureLog==null)+":"+field(routing,"shoveFailingLayer")+"}";}catch(ReflectiveOperationException e){throw new RuntimeException(e);}catch(Exception e){throw new RuntimeException(e);}}fail(k.equals("N")?1:k.equals("D")?2:3);}
    public void notifyNew(Item i){event("N",i);}public void notifyDeleted(Item i){event("D",i);if(mutate!=null){var target=mutate;mutate=null;if(mutation==0)target.clear();else target.add(i);}}public void notifyChanged(Item i){event("C",i);}
    public void activate(){events+="A;";super.activate();fail(4);}public void deactivate(){events+="Z;";super.deactivate();fail(5);}
  }
  static class Pool {
    ArrayList<Object> values=new ArrayList<>();
    int id(Object value){if(value==null)return 0;for(int i=0;i<values.size();i++)if(values.get(i)==value)return i+1;values.add(value);return values.size();}
  }
  static Pool items,trees,shapes,arrays;
  static Pool componentHistory = new Pool();
  static int historyId(Object o) { return o instanceof Component ? componentHistory.id(o) : items.id(o); }
  static java.util.function.Function<ShapeTree.Storable,String> extraTreeObjectId;
  static String leaf(ShapeTree.Leaf l){return l==null?"null":(extraTreeObjectId!=null && !(l.object instanceof Item)?extraTreeObjectId.apply(l.object):""+items.id(l.object))+":"+l.shapeIndexInObject+":"+shape(l.boundingShape);}
  static String treeNode(Object n)throws Exception{if(n==null)return "null";if(n instanceof ShapeTree.Leaf l)return "L("+leaf(l)+")";var b=(ShapeTree.InnerNode)n;return "I("+shape(b.boundingShape)+","+treeNode(b.firstChild)+","+treeNode(b.secondChild)+")";}
  static java.util.function.Function<PolylineShape,String> extraOutlineShape;
  static String outlineShape(PolylineShape s){if(s instanceof TileShape t)return shape(t);if(extraOutlineShape!=null)return extraOutlineShape.apply(s);throw new ClassCastException("Outline requires a bench formatter for its non-tile shape");}
  static String item(Item i)throws Exception {
    String s=items.id(i)+":"+i.getId()+":"+i.getClass().getSimpleName()+":"+i.getComponentId()+":"+i.clearanceClassIndex()+":"+i.getFixedState().ordinal()+":"+i.isOnTheBoard()+":"+box(i.boundingBox())+":"+i.firstLayer()+":"+i.lastLayer();
    s+="/"+i.netNumbers.length;for(int n:i.netNumbers)s+=","+n;
    if(i instanceof PolylineTrace t){s+="/"+t.getHalfWidth()+":"+t.cornerCount();for(int c=0;c<t.cornerCount();c++)s+="~"+point(t.polyline().corner(c));s+="/"+bits(t.getLength());}
    if(i instanceof BoardOutline o){s+="/"+o.keepoutOutsideOutlineGenerated()+":"+o.shapeCount()+":"+o.lineCount()+":"+o.tileShapeCount();for(int q=0;q<o.shapeCount();q++)s+="~"+outlineShape(o.getShape(q));s+="/"+(field(o,"keepoutArea")==null?"null":box(o.getKeepoutArea().boundingBox()));}
    Object info=field(i,"searchTreesInfo");if(info==null)return s+"/null";
    var records=(Collection<?>)field(info,"treeList");s+="/"+records.size();for(Object r:records){s+="@"+trees.id(field(r,"tree"));var entries=(ShapeTree.Leaf[])field(r,"entryArr");s+=";"+(entries==null?"null":""+entries.length);if(entries!=null)for(var l:entries)s+="!"+leaf(l);var a=(TileShape[])field(r,"precalculatedTreeShapes");s+=";"+arrays.id(a);if(a!=null){s+=":"+a.length;for(var t:a)s+="!"+shapes.id(t)+":"+shape(t);}}
    return s;
  }
  static String history(UndoableObjects list)throws Exception {
    Pool nodes=new Pool();var objects=(Map<?,?>)field(list,"objects");String s=field(list,"stackLevel")+":"+field(list,"redoPossible")+"/"+objects.size();
    for(var e:objects.entrySet())s+=","+historyId(e.getKey())+":"+nodes.id(e.getValue());
    var deleted=(List<?>)field(list,"deletedObjectsStack");s+="/"+deleted.size();for(var row:deleted){s+="#"+((Collection<?>)row).size();for(Object n:(Collection<?>)row)s+=","+nodes.id(n);}
    String ns="";for(int n=0;n<nodes.values.size();n++){Object node=nodes.values.get(n);ns+="#"+historyId(field(node,"object"))+":"+field(node,"level")+":"+nodes.id(field(node,"undoObject"))+":"+nodes.id(field(node,"redoObject"));}
    s+="/"+nodes.values.size()+ns;return s;
  }
  static ArrayList<String> state(RoutingBoard b,Observers o,Set<Integer> changed)throws Exception {return state(b,o,changed,false);}
  static ArrayList<String> state(RoutingBoard b,Observers o,Set<Integer> changed,boolean captureConnectionErrors)throws Exception {
    var result=new ArrayList<String>();String s="board="+b.getRevision()+":"+b.communication.idGenerator.maxGeneratedId()+":"+b.getMinTraceHalfWidth()+":"+b.getMaxTraceHalfWidth()+":"+b.observersActive()+":"+box(b.getGraphicsUpdateBox())+":"+(b.library.padstacks==null)+":"+(b.library.packages==null);
    for(Item i:b.getItems())s+="~"+items.id(i);result.add(s);result.add("undo="+history(b.itemList));result.add("componentUndo="+(field(b.components,"undoList")==null?"null":history((UndoableObjects)field(b.components,"undoList"))));
    var active=(Collection<?>)field(b.searchTreeManager,"compensatedSearchTrees");s="trees="+b.searchTreeManager.isClearanceCompensationUsed()+":"+trees.id(b.searchTreeManager.getDefaultTree());for(Object t:active){var tree=(ShapeSearchTree)t;s+="#"+trees.id(tree)+":"+tree.key+":"+tree.compensatedClearanceClassNo+":"+treeNode(field(tree,"root"));}result.add(s);
    // Item/cache IDs are assigned by deterministic traversal, preserving aliases across operations.
    s="items=";for(int j=0;j<items.values.size();j++)s+="#"+item((Item)items.values.get(j));result.add(s);
    s="connections=";for(int n=1;n<=2;n++){s+="#"+n+":"+ids(b.getConnectableItems(n));try{for(var connected:b.getConnectedSets(n))s+="~"+ids(connected);}catch(NullPointerException e){if(!captureConnectionErrors)throw e;s+="!null_error";}catch(IndexOutOfBoundsException e){if(!captureConnectionErrors)throw e;s+="!index_error";}}result.add(s);
    Object log=field(b,"changeLog");s="changes=";if(log==null)s+="null";else{s+=field(log,"next")+":"+field(log,"size");int[] rev=(int[])field(log,"revisions"),first=(int[])field(log,"firstLayers"),last=(int[])field(log,"lastLayers");IntBox[] boxes=(IntBox[])field(log,"boxes");for(int n=0;n<rev.length;n++)if(rev[n]!=0||boxes[n]!=null)s+="#"+n+":"+rev[n]+":"+box(boxes[n])+":"+first[n]+":"+last[n];}result.add(s);
    s="changedNets="+changed.size();for(int n:changed)s+=","+n;result.add(s);result.add("events="+(o.events.isEmpty()?"-":o.events));return result;
  }
  // Neutral state is exported from the constructed model, not from the input recipe.
  static String export(RoutingBoard b)throws Exception {
    var tokens=new ArrayList<String>();tokens.add("NB1");tokens.add(box(b.boundingBox).replace(',',' '));tokens.add(""+b.layerStructure.layers.length);for(var l:b.layerStructure.layers){tokens.add(l.name);tokens.add(l.isSignal?"1":"0");}
    var m=b.rules.clearanceMatrix;tokens.add(""+m.getClassCount());for(int c=0;c<m.getClassCount();c++)tokens.add(m.getName(c));for(int a=0;a<m.getClassCount();a++)for(int d=0;d<m.getClassCount();d++)for(int l=0;l<b.layerStructure.layers.length;l++)tokens.add(""+m.getValue(a,d,l,false));
    tokens.add(""+b.rules.getTraceAngleRestriction().getValue());for(int l=0;l<b.layerStructure.layers.length;l++)tokens.add(""+b.rules.getDefaultTraceHalfWidth(l));tokens.add(""+b.rules.nets.maxNetNumber());for(int n=1;n<=b.rules.nets.maxNetNumber();n++){var net=b.rules.nets.get(n);tokens.add(net.name);tokens.add(""+net.subnetNumber);tokens.add(net.containsPlane()?"1":"0");}
    BoardOutline outline=b.getOutline();tokens.add(""+outline.clearanceClassIndex());tokens.add(""+outline.shapeCount());for(int q=0;q<outline.shapeCount();q++)tokens.add(box(outline.getShape(q).boundingBox()).replace(',',' '));
    var traces=new ArrayList<PolylineTrace>();for(Item i:b.getItems())if(i instanceof PolylineTrace t)traces.add(t);traces.sort(Comparator.comparingInt(Item::getId));tokens.add(""+traces.size());for(var t:traces){tokens.add(""+t.getLayer());tokens.add(""+t.getHalfWidth());tokens.add(""+t.netNumbers.length);for(int n:t.netNumbers)tokens.add(""+n);tokens.add(""+t.clearanceClassIndex());tokens.add(""+t.getFixedState().ordinal());tokens.add(""+t.cornerCount());for(int q=0;q<t.cornerCount();q++){var p=(IntPoint)t.polyline().corner(q);tokens.add(p.x+" "+p.y);}}
    return String.join(" ",tokens);
  }
  static Polyline editPolyline(int kind) {
    int[][] coordinates=switch(kind){
      case 0->new int[][]{{800,1000},{1200,1000}};case 1->new int[][]{{1200,1000},{800,1000}};
      case 2->new int[][]{{200,600},{500,600}};case 3->new int[][]{{500,600},{200,600}};
      case 4->new int[][]{{650,400},{650,900}};case 5->new int[][]{{500,600},{700,600}};
      case 6->new int[][]{{500,600},{500,1000},{800,1000}};case 7->new int[][]{{500,600}};
      case 8->new int[][]{{500,600},{800,600},{800,1000},{500,600}};
      case 9->new int[][]{{500,600},{600,800}};case 10->new int[][]{{800,1000},{800,1400}};
      case 11->new int[][]{{1400,1000},{1400,1400}};default->throw new IllegalArgumentException();};
    Point[] points=new Point[coordinates.length];for(int i=0;i<points.length;i++)points[i]=new IntPoint(coordinates[i][0],coordinates[i][1]);return new Polyline(points);
  }
  static String changedState(RoutingBoard b){String s="changedArea=";if(b.changedArea==null)return s+"null";s+=box(b.changedArea.surroundingBox());for(int l=0;l<b.getLayerCount();l++)s+="#"+shape(b.changedArea.getArea(l));return s;}
  interface Action{Object run()throws Exception;}
  static String call(Action action)throws Exception {try{return String.valueOf(action.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(ConcurrentModificationException e){return "concurrent_error";}catch(CallbackFailure e){return "callback_error";}}
  static String run(String input)throws Exception {
    Scanner in=new Scanner(input);int config=in.nextInt();boolean editing=config>=1000;if(editing)config-=1000;var layers=new LayerStructure(new Layer[]{new Layer("F.Cu",true),new Layer("In1.Cu",false),new Layer("B.Cu",true)});var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});matrix.setDefaultValue(12+config%5);var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(AngleRestriction.valueOf(config%3));var communication=new Communication();var observers=new Observers();observers.constructionMetadata=editing;communication.observers=observers;
    PolylineShape[] outlines=(config/3)%3==0?new PolylineShape[0]:(config/3)%3==1?new PolylineShape[]{new IntBox(0,0,9000,9000)}:new PolylineShape[]{new IntBox(0,0,9000,9000),new IntBox(4000,4000,4500,4500)};
    var b=new RoutingBoard(new IntBox(-1000,-1000,10000,10000),layers,outlines,config%3,rules,communication);rules.setDefaultTraceHalfWidths(30+config);rules.nets.add("A",1,false);rules.nets.add("B",1,config%2==1);
    for(int i=0;i<3;i++)b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(500+i*300,600),new IntPoint(800+i*300,600),new IntPoint(800+i*300,1000)}),i%2==0?0:2,20+i,new int[]{i==2?2:1},1,FixedState.UNFIXED);
    String neutral=export(b);items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();Set<Integer> changed=new TreeSet<>();var out=state(b,observers,changed);if(editing)out.add(changedState(b));int count=in.nextInt();String operations=" "+count;
    @SuppressWarnings("unchecked") Iterator<UndoableObjects.UndoableObjectNode>[] cursors=new Iterator[3];
    for(int step=0;step<count;step++){String op=in.next();int a=in.nextInt(),c=in.nextInt(),d=in.nextInt();operations+=" "+op+" "+a+" "+c+" "+d;out.add("op"+step+"="+call(()->switch(op){
      case "f"->{observers.failKind=a;observers.failAfter=c;yield "ok";}
      case "N"->{b.insertTrace(editPolyline(a),0,20,new int[]{c==0?1:c},1,FixedState.values()[Math.floorMod(d,4)]);yield "ok";}
      case "n"->{var trace=(PolylineTrace)b.getItem(a);yield trace.normalize(c==0?null:new IntBox(400,400,900,900).boundingOctagon());}
      case "s"->{var trace=(PolylineTrace)b.getItem(a);var pieces=trace.split(new IntPoint(c,d));String v=pieces==null?"null":""+pieces.length;if(pieces!=null)for(var piece:pieces)v+=","+(piece==null?0:piece.getId());yield v;}
      case "c"->((PolylineTrace)b.getItem(a)).combine();
      case "g"->b.combineTraces(a);
      case "t"->b.splitTraces(new IntPoint(a,c),0,d);
      case "r"->b.removeIfCycle((PolylineTrace)b.getItem(a));
      case "h"->{b.startMarkingChangedArea();yield "ok";}
      case "j"->{b.joinChangedArea(new FloatPoint(a*0.5,c*0.5),d);yield "ok";}
      case "a"->{b.markAllChangedArea();yield "ok";}
      case "e"->{b.changedArea.setEmpty(a);yield "ok";}
      case "I"->{var t=b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(a,c),new IntPoint(a+d,c),new IntPoint(a+d,c+200)}),Math.floorMod(a,3),Math.floorMod(c,70)+1,new int[]{Math.floorMod(a,2)+1},Math.floorMod(d,5)-1,FixedState.values()[Math.floorMod(d,4)]);yield t==null?0:t.getId();}
      case "D"->{b.removeItem(b.getItem(a));yield "ok";}
      case "M"->{Item i=b.getItem(a);if(i!=null)i.moveBy(new IntVector(c,d));yield "ok";}
      case "Q"->{b.generateSnapshot();yield "ok";}case "U"->b.undo(changed);case "R"->b.redo(changed);case "P"->b.popSnapshot();
      case "G"->b.searchTreeManager.getAutorouteTree(a).key;case "C"->{b.searchTreeManager.setClearanceCompensationUsed(a!=0);yield "ok";}
      case "V"->{matrix.setValue(a,c,d);b.searchTreeManager.clearanceValueChanged();yield "ok";}
      case "O"->{b.getOutline().generateKeepoutOutside(a!=0);yield "ok";}
      case "H"->{b.getOutline().getKeepoutArea();yield "ok";}
      case "Y"->{b.getOutline().turn90Degree(a,new IntPoint(c,d));yield "ok";}
      case "F"->{b.getOutline().changePlacementSide(new IntPoint(c,d));yield "ok";}
      case "A"->{b.startNotifyObservers();yield "ok";}case "Z"->{b.endNotifyObservers();yield "ok";}
      case "B"->{cursors[a]=b.itemList.startReadObject();yield "ok";}case "T"->{var i=(Item)b.itemList.readObject(cursors[a]);yield i==null?0:i.getId();}
      case "X"->b.itemChangesTouch(a,new IntBox(c,c,c+100,d+100),d%3);
      case "E"->{b.incrementRevision();yield "ok";}
      case "J"->{b.joinGraphicsUpdateBox(new IntBox(a,c,a+d,c+d));yield "ok";}case "K"->{b.resetGraphicsUpdateBox();yield "ok";}
      case "w"->{Collection<Item> targets=a==0?new LinkedList<>(b.getItems()):new TreeSet<>(b.getItems());observers.mutate=targets;observers.mutation=c;yield b.removeItems(targets);}
      case "W"->b.removeItems(b.getItems());default->throw new AssertionError(op);
    }));out.addAll(state(b,observers,changed));if(editing)out.add(changedState(b));}
    if(in.hasNext())throw new AssertionError("unused input");return (editing?neutral.replaceFirst("NB1","NB2"):neutral)+operations+" | "+String.join(" ",out);
  }
  static String logRun(String input)throws Exception {
    Scanner in=new Scanner(input);in.next();int capacity=in.nextInt(),count=in.nextInt();var output=new ArrayList<String>();BoardChangeLog log=null;
    try {log=new BoardChangeLog(capacity);output.add("ok");}catch(NegativeArraySizeException e){output.add("negative_array_error");}
    for(int k=0;k<count;k++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt(),d=in.nextInt(),e=in.nextInt();BoardChangeLog selected=log;
      output.add(call(()->{if(op.equals("R")){selected.record(a,e==0?null:new IntBox(b,b,b+10,b+10),c,d);return "ok";}return selected.touches(a,b,e==0?null:new IntBox(c,c,c+10,c+10),d);}));
      String state="null";if(log!=null){state=field(log,"next")+":"+field(log,"size");int[] rev=(int[])field(log,"revisions"),first=(int[])field(log,"firstLayers"),last=(int[])field(log,"lastLayers");IntBox[] boxes=(IntBox[])field(log,"boxes");for(int n=0;n<rev.length;n++)state+="#"+rev[n]+":"+box(boxes[n])+":"+first[n]+":"+last[n];}output.add(state);
    }
    return input+" | "+String.join(" ",output);
  }
  public static void main(String[] args)throws Exception{var input=new BufferedReader(new InputStreamReader(System.in));for(String line;(line=input.readLine())!=null;)System.out.println(line.startsWith("L1 ")?logRun(line):run(line));}
}
