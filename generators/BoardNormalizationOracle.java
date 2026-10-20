package app.freerouting.board.facade;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.util.*;
/** Actual RoutingBoard normalization, with exported pre-operation model and observable full state. */
public class BoardNormalizationOracle extends BoardConstructionOracle {
  // Avoid repeatedly copying the entire callback transcript on long source oscillation cases.
  // Each callback still executes the original observer and emits exactly its bytes/order.
  static class BufferedObservers extends Observers {
    final StringBuilder transcript=new StringBuilder();
    void flush(){transcript.append(events);events="";}
    @Override void event(String kind,Item item){try{super.event(kind,item);}finally{flush();}}
    @Override public void activate(){try{super.activate();}finally{flush();}}
    @Override public void deactivate(){try{super.deactivate();}finally{flush();}}
  }
  static ArrayList<String> observe(RoutingBoard board,BufferedObservers observer,Set<Integer> changed)throws Exception {
    observer.events=observer.transcript.toString();
    try{return state(board,observer,changed,true);}finally{observer.events="";}
  }
  static String suppression(RoutingBoard b)throws Exception {
    var values=(Set<Integer>)field(b,"normalizeSuppressedNetNos");String s="suppressed=";
    if(values==null)return s+"null";for(int n:new TreeSet<>(values))s+=n+",";return s;
  }
  static String runNormalization(String input)throws Exception {
    var in=new Scanner(input);int config=in.nextInt(),shape=in.nextInt(),mode=in.nextInt(),failure=in.nextInt();
    var layers=new LayerStructure(new Layer[]{new Layer("F.Cu",true),new Layer("In1.Cu",false),new Layer("B.Cu",true)});
    var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});matrix.setDefaultValue(12);
    var rules=new BoardRules(layers,matrix);rules.setTraceAngleRestriction(AngleRestriction.valueOf(config%3));rules.setDefaultTraceHalfWidths(30);
    var communication=new Communication();var observers=new BufferedObservers();observers.constructionMetadata=true;communication.observers=observers;
    var b=new RoutingBoard(new IntBox(-1000,-1000,10000,10000),layers,new PolylineShape[0],1,rules,communication);
    int maxNet=mode>=4?1025:2;for(int n=1;n<=maxNet;n++)rules.nets.add("N"+n,1,false);
    if(mode<4){
      for(int i=0;i<3;i++)b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(500+i*300,600),new IntPoint(800+i*300,600),new IntPoint(800+i*300,1000)}),i%2==0?0:2,20+i,new int[]{i==2?2:1},1,FixedState.UNFIXED);
      int[] nets=switch(mode){case 0->new int[]{1};case 1->new int[]{1,2};case 2->new int[]{2,1,2};default->new int[0];};
      b.insertTraceWithoutCleaning(editPolyline(shape),0,20,nets,1,FixedState.values()[config/3%4]);
    } else {
      // Colliding hash buckets, with disjoint real traces so per-net processing order is visible.
      int count=mode==4?16:30;
      for(int i=0;i<count;i++){int n=1+(i%16)*64;int x=100+i*200;var points=new Point[]{new IntPoint(x,100),new IntPoint(x+100,100)};b.insertTraceWithoutCleaning(new Polyline(points),0,20,new int[]{n},1,FixedState.UNFIXED);}
    }
    String neutral=export(b).replaceFirst("NB1","NB3")+" "+b.communication.idGenerator.maxGeneratedId()+" "+field(b.communication.idGenerator,"wrapAroundCount");
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();Set<Integer> changed=new TreeSet<>();
    var ops=new ArrayList<String>();ops.add("Q 0 0 0");ops.add("G 1 0 0");if(config>=6)ops.add("C 1 0 0");
    if(failure!=0)ops.add("f "+failure+" 1 0");
    if(config%2==0){ops.add("v 0 0 0");ops.add("u 1 0 0");}else{ops.add("u 1 0 0");ops.add("v 0 0 0");}
    ops.add("U 0 0 0");ops.add("R 0 0 0");ops.add("u 2 0 0");ops.add("v 0 0 0");ops.add("u 1 0 0");
    var output=observe(b,observers,changed);output.add(changedState(b));output.add(suppression(b));
    for(int step=0;step<ops.size();step++){
      var words=new Scanner(ops.get(step));String op=words.next();int a=words.nextInt(),c=words.nextInt(),d=words.nextInt();
      output.add("op"+step+"="+call(()->switch(op){case "u"->b.normalizeTraces(a);case "v"->b.normalizeAllTraces();case "Q"->{b.generateSnapshot();yield "ok";}case "G"->b.searchTreeManager.getAutorouteTree(a).key;case "C"->{b.searchTreeManager.setClearanceCompensationUsed(a!=0);yield "ok";}case "f"->{observers.failKind=a;observers.failAfter=c;yield "ok";}case "U"->b.undo(changed);case "R"->b.redo(changed);default->throw new AssertionError();}));
      output.addAll(observe(b,observers,changed));output.add(changedState(b));output.add(suppression(b));
    }
    return neutral+" "+ops.size()+" "+String.join(" ",ops)+" | "+String.join(" ",output);
  }
  public static void main(String[] args)throws Exception {
    try(var in=new BufferedReader(new FileReader(args[0]));var out=new PrintWriter(args[1])){for(String line;(line=in.readLine())!=null;){out.println(runNormalization(line));out.flush();}}
  }
}
