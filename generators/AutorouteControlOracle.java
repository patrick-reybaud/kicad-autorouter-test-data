package app.freerouting.autoroute.maze;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import app.freerouting.settings.RouterSettings;
import java.io.*;
import java.util.*;
public class AutorouteControlOracle {
 static String events="";static int failure;static boolean active;
 static void event(String e,int code){if(!active)return;events+=e+",";if(code==failure)throw new IllegalStateException();}
 interface Call{Object run();}
 static class Out {ArrayList<String> values=new ArrayList<>();void add(Object v){values.add(String.valueOf(v));}void call(Call f){try{add(f.run());}catch(NullPointerException e){add("null_error");}catch(IndexOutOfBoundsException|NoSuchElementException e){add("index_error");}catch(NegativeArraySizeException e){add("size_error");}catch(IllegalStateException e){add("callback_error");}}public String toString(){return String.join(" ",values);}}
 static String bits(double x){return Long.toHexString(Double.doubleToLongBits(x));}
 static double dbl(Scanner in){return Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));}
 static class Settings extends RouterSettings {
  boolean vias,neck;int viaCost;double[] bend;boolean[] enabled;AutorouteControl.ExpansionCostFactor[] costs;
  public AutorouteControl.ExpansionCostFactor[] getTraceCosts(){event("TC",1);return costs;}
  public boolean getViasAllowed(){event("V",3);return vias;}
  public int getViaCosts(){event("VC",6);return viaCost;}
  public double getBendCost(int l){event("B"+l,10+l);return bend[l];}
  public boolean getLayerActive(int l){event("A"+l,20+l);return enabled[l];}
  public boolean getAutomaticNeckdown(){event("N",4);return neck;}
 }
 static class Rules extends BoardRules {
  Rules(LayerStructure l,ClearanceMatrix m){super(l,m);}
  public int getTraceHalfWidth(int n,int l){event("W"+n+":"+l,30+l);return super.getTraceHalfWidth(n,l);}
 }
 static class Board extends RoutingBoard {
  int count,provider;Collection<Item> items=new ArrayList<>();
  Board(LayerStructure l,Rules r){super(new IntBox(-100,-100,100,100),l,new PolylineShape[0],0,r,new Communication());}
  public int getLayerCount(){if(!active)return super.getLayerCount();event("LC",2);return count;}
  public Collection<Item> getConnectableItems(int n){event("G"+n,5);if(provider==1)return new ArrayList<>();if(provider==2)return null;if(provider==3)throw new IllegalStateException();return items;}
 }
 static class TestPin extends Pin {
  int first,last;TestPin(int id,int f,int l){super(0,0,new int[]{1},0,id,FixedState.UNFIXED,null);first=f;last=l;}
  public int firstLayer(){event("F"+getId(),100+getId());return first;}public int lastLayer(){event("L"+getId(),200+getId());return last;}
 }
 static ConvexShape shape(int k){return switch(k){case 0->null;case 1->new IntBox(-6,-2,6,2);case 2->new IntOctagon(-8,-7,9,11,-12,14,-10,15);case 3->new Circle(new IntPoint(0,0),5);case 4->TileShape.getInstance(new Point[]{new IntPoint(-9,-3),new IntPoint(10,2),new IntPoint(0,17)});case 5->new IntBox(Integer.MIN_VALUE,0,Integer.MAX_VALUE,7);case 6->new Circle(new IntPoint(0,0),Integer.MAX_VALUE);default->IntBox.EMPTY;};}
 static String masks(AutorouteControl.ViaMask[] a){if(a==null)return "N";String s=""+a.length;for(var v:a)s+=v==null?",N":","+v.fromLayer+":"+v.toLayer+":"+v.attachSmdAllowed;return s;}
 static String state(AutorouteControl c,Settings s,ViaRule[] rules){if(c==null)return "N";int rule=-1;for(int i=0;i<rules.length;i++)if(c.viaRule==rules[i])rule=i;String v=c.layerCount+":"+c.netNumber+":"+c.traceClearanceClassIndex+":"+c.viaClearanceClass+":"+c.viasAllowed+":"+c.attachSmdAllowed+":"+c.withNeckdown+":"+c.viaLowerBound+":"+c.viaUpperBound+":"+bits(c.maxViaRadius)+":"+bits(c.minNormalViaCost)+":"+bits(c.minCheapViaCost)+":"+c.ripupAllowed+":"+c.ripupCosts+":"+c.ripupPassNo+":"+c.isFanout+":"+(c.fanoutStartPinName==null)+":"+(c.fanoutStartPinCenter==null)+":"+c.fanoutStartPinLayer+":"+c.removeUnconnectedVias+":"+c.tidyRegionWidth+":"+c.pullTightAccuracy+":"+c.maxShoveTraceRecursionDepth+":"+c.maxShoveViaRecursionDepth+":"+c.maxSpringOverRecursionDepth+":"+rule+":"+(c.settings==s)+":"+(c.traceCosts==s.costs);
  for(int i=0;i<c.layerCount;i++){v+="/"+c.traceHalfWidth[i]+":"+c.compensatedTraceHalfWidth[i]+":"+c.layerActive[i]+":"+bits(c.viaRadii[i])+":"+bits(c.bendCosts[i]);for(int x:c.addViaCosts[i].toLayer)v+=":"+x;}
  v+="/"+masks(c.viaInfos)+"/"+(c.traceCosts==null?"N":c.traceCosts.length);if(c.traceCosts!=null)for(var cost:c.traceCosts)v+=cost==null?",N":","+bits(cost.horizontal())+":"+bits(cost.vertical());return v;
 }
 static String run(String line){active=false;events="";Scanner in=new Scanner(line);in.next();int layers=in.nextInt(),net=in.nextInt(),flags=in.nextInt(),cl=in.nextInt(),cost=in.nextInt();failure=in.nextInt();int provider=in.nextInt();int count=Math.max(layers,1);Layer[] ls=new Layer[count];Settings settings=new Settings();settings.vias=in.nextInt()!=0;settings.neck=in.nextInt()!=0;settings.viaCost=cost;settings.bend=new double[count];settings.enabled=new boolean[count];int[] widths=new int[count],clearances=new int[count];boolean[] netActive=new boolean[count];for(int i=0;i<count;i++){ls[i]=new Layer("l"+i,in.nextInt()!=0);settings.enabled[i]=in.nextInt()!=0;netActive[i]=in.nextInt()!=0;widths[i]=in.nextInt();clearances[i]=in.nextInt();settings.bend[i]=dbl(in);}
  LayerStructure l=new LayerStructure(ls);ClearanceMatrix matrix=new ClearanceMatrix(3,l,new String[]{"none","default","other"});for(int i=0;i<count;i++)matrix.setDefaultValue(i,clearances[i]);Rules rules=new Rules(l,matrix);Board board=new Board(l,rules);board.count=layers;board.provider=provider;NetClass nc=new NetClass("class",l,matrix,false);nc.setTraceClearanceClass(cl);for(int i=0;i<count;i++){nc.setTraceHalfWidth(i,widths[i]);nc.setActiveRoutingLayer(i,netActive[i]);}for(int i=0;i<2;i++)rules.nets.add("n"+i,1,false).setClass((flags&16)!=0?null:nc);
  ViaRule[] vr=new ViaRule[4];for(int i=0;i<vr.length;i++){vr[i]=new ViaRule("v"+i);int n=in.nextInt();for(int j=0;j<n;j++){int present=in.nextInt(),clear=in.nextInt();boolean attach=in.nextInt()!=0;int size=in.nextInt();ConvexShape[] shapes=size<0?null:new ConvexShape[size];for(int k=0;k<size;k++)shapes[k]=shape(in.nextInt());Padstack pad=present==1?null:new Padstacks(l).add("p",shapes,false,false);vr[i].appendVia(present==0?null:new ViaInfo("via",pad,clear,attach,rules));}}
  nc.setViaRule((flags&32)!=0?null:vr[0]);rules.viaRules.clear();if((flags&64)==0)rules.viaRules.add(vr[0]);int items=in.nextInt();for(int i=0;i<items;i++){int type=in.nextInt(),first=in.nextInt(),last=in.nextInt();board.items.add(type==2?null:type==1?new TestPin(i+1,first,last):new Via(null,new IntPoint(0,0),new int[]{1},0,i+1,0,FixedState.UNFIXED,false,null));}
  int size=in.nextInt();settings.costs=size<0?null:new AutorouteControl.ExpansionCostFactor[size];for(int i=0;i<size;i++){int present=in.nextInt();double h=dbl(in),v=dbl(in);if(present!=0)settings.costs[i]=new AutorouteControl.ExpansionCostFactor(h,v);}
  Out out=new Out();AutorouteControl[] holder=new AutorouteControl[1];active=true;out.call(()->{holder[0]=(flags&1)!=0?new AutorouteControl((flags&2)!=0?null:board,net,(flags&4)!=0?null:settings):new AutorouteControl((flags&2)!=0?null:board,net,(flags&4)!=0?null:settings,cost,settings.costs);return "ok";});out.add(events.isEmpty()?"-":events);out.add(state(holder[0],settings,vr));
  int rounds=in.nextInt();for(int r=0;r<rounds;r++){int ri=in.nextInt(),newCost=in.nextInt(),nn=in.nextInt(),mode=in.nextInt(),fail=in.nextInt(),mutate=in.nextInt(),width=in.nextInt();double radius=dbl(in),maximum=dbl(in);failure=fail;board.provider=mode;events="";AutorouteControl c=holder[0];AutorouteControl.ViaMask[] old=c==null?null:c.viaInfos;if(c!=null){c.viaRule=ri<0?null:vr[ri];if(mutate!=0){Arrays.fill(c.traceHalfWidth,width);Arrays.fill(c.viaRadii,radius);c.maxViaRadius=maximum;}if(settings.costs!=null&&settings.costs.length>0)settings.costs[0]=new AutorouteControl.ExpansionCostFactor(radius,maximum);}
   out.call(()->{c.rebuildViaInfo(board,newCost,nn);return "ok";});out.add(events.isEmpty()?"-":events);out.add(state(c,settings,vr));out.add(masks(old));out.add(c!=null&&c.viaInfos==old);
  }
  active=false;return out.toString();
 }
 public static void main(String[] args)throws Exception {var in=new BufferedReader(new InputStreamReader(System.in));String s;while((s=in.readLine())!=null)System.out.println(s+" | "+run(s));}
}
