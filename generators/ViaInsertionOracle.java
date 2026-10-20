package app.freerouting.board.facade;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.core.library.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.nio.file.*;
import java.util.*;
/** Real ordinary/escape via insertion and the source trace splits, callbacks and undo. */
public class ViaInsertionOracle extends ModelRotationOracle {
  static class ViaObservers extends Observers {
    int[] inputNets;boolean mutateNets;
    @Override public void notifyNew(Item item){if(item instanceof Via v){events+="V"+v.getId()+":"+v.attachAllowed+":"+v.isEscapeVia+":"+v.escapeViaSmdLayer+";";if(mutateNets&&inputNets!=null&&inputNets.length>0)inputNets[0]=2;}super.notifyNew(item);}
  }
  static String invoke(Action a)throws Exception{try{return call(a);}catch(NegativeArraySizeException e){return "negative_array_error";}catch(ClassCastException e){return "cast_error";}}
  static ArrayList<String> snapshot(RoutingBoard b,ViaObservers o,Set<Integer> changed)throws Exception {
    var out=state(b,o,changed,true);String s="viaState=";for(Item i:b.getItems())if(i instanceof Via v)s+="#"+v.getId()+":"+v.attachAllowed+":"+v.isEscapeVia+":"+v.escapeViaSmdLayer+":"+via(v);out.add(s);return out;
  }
  static String runVia(String line)throws Exception {
    var in=new Scanner(line);int config=in.nextInt(),netsKind=in.nextInt(),escape=in.nextInt(),centerKind=in.nextInt(),callback=in.nextInt(),clearance=in.nextInt();
    var layers=new LayerStructure(new Layer[]{new Layer("F.Cu",true),new Layer("In1.Cu",false),new Layer("In2.Cu",true),new Layer("B.Cu",true)});var matrix=new ClearanceMatrix(2,layers,new String[]{"none","default"});matrix.setDefaultValue(12);var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(AngleRestriction.valueOf(config%3));var o=new ViaObservers();o.constructionMetadata=true;var com=new Communication();com.observers=o;
    var b=new RoutingBoard(new IntBox(-10000,-10000,10000,10000),layers,new PolylineShape[0],1,rules,com);rules.nets.add("A",1,false);rules.nets.add("B",1,false);b.library.padstacks=new Padstacks(layers);
    int padKind=config/3;ConvexShape[] shapesForPad=new ConvexShape[4];for(int l=0;l<4;l++){boolean enabled=switch(padKind){case 0,5->true;case 1->l==0;case 2->l==3;case 3->l==1||l==2;case 4->l==0||l==3;default->false;};if(enabled)shapesForPad[l]=padKind==5&&l%2==1?new IntBox(-45,-35,45,35):new Circle(new IntPoint(0,0),50);}
    var pad=padKind==7?null:b.library.padstacks.add("via",shapesForPad,true,false);
    for(int l=0;l<4;l++){b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(500,1000),new IntPoint(1500,1000)}),l,10,new int[]{1},1,FixedState.UNFIXED);b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(1000,500),new IntPoint(1000,1500)}),l,10,new int[]{2},1,FixedState.UNFIXED);}
    b.searchTreeManager.getAutorouteTree(1);
    Point center=centerKind==1?null:centerKind==2?Point.getInstance(java.math.BigInteger.valueOf(2001),java.math.BigInteger.valueOf(2001),java.math.BigInteger.valueOf(2)):new IntPoint(1000,1000);
    int[] nets=switch(netsKind){case 0->new int[0];case 1->new int[]{1};case 2->new int[]{1,2};case 3->null;case 4->new int[]{1,1,2};default->throw new AssertionError();};o.inputNets=nets;o.mutateNets=callback==5;
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();Set<Integer> changed=new TreeSet<>();var out=snapshot(b,o,changed);b.generateSnapshot();
    if(callback>=1&&callback<=4){o.failKind=callback<=2?1:callback==3?2:3;o.failAfter=callback==2?2:1;}
    out.add("insert="+invoke(()->{Via via=escape!=0?b.insertEscapeVia(pad,center,nets,clearance,FixedState.values()[config/3%4],config%6-1):b.insertVia(pad,center,nets,clearance,FixedState.values()[config/3%4],netsKind%2==0);return via.getId();}));
    out.add("inputNets="+(nets==null?"null":Arrays.toString(nets).replace(" ","")));out.addAll(snapshot(b,o,changed));
    out.add("undo="+invoke(()->b.undo(changed)));out.addAll(snapshot(b,o,changed));out.add("redo="+invoke(()->b.redo(changed)));out.addAll(snapshot(b,o,changed));
    Via selected=null;for(Item item:b.getItems())if(item instanceof Via via){selected=via;break;}Via move=selected;
    out.add("move="+invoke(()->{if(move!=null)move.moveBy(new IntVector(50,20));return "ok";}));out.addAll(snapshot(b,o,changed));
    out.add("remove="+invoke(()->{b.removeItem(move);return "ok";}));out.addAll(snapshot(b,o,changed));return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception{try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String line:Files.readAllLines(Path.of(args[0])))out.write(line+" | "+runVia(line)+"\n");}}
}
