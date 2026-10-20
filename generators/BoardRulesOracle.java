package app.freerouting.rules;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.*;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.util.*;
public class BoardRulesOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(NegativeArraySizeException e){return "size_error";}catch(IllegalStateException e){return "callback_error";}}
 static String hex(String s){if(s==null)return "N";if(s.isEmpty())return "-";StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(String.format(Locale.ROOT,"%04x",(int)c));return b.toString();}
 static String dec(String s){if(s.equals("N"))return null;if(s.equals("-"))return "";StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i+=4)b.append((char)Integer.parseInt(s.substring(i,i+4),16));return b.toString();}
 static String bits(double x){return Long.toHexString(Double.doubleToLongBits(x));}
 static class Rules extends BoardRules {
  String events="";int mode;boolean active;
  Rules(LayerStructure l,ClearanceMatrix m){super(l,m);}
  public NetClass getDefaultNetClass(){if(active){events+="D,";if(mode==1)return null;if(mode==2)throw new IllegalStateException();}return super.getDefaultNetClass();}
  public ViaRule getDefaultViaRule(){if(active){events+="V,";if(mode==3)throw new IllegalStateException();}return super.getDefaultViaRule();}
  public int getTraceHalfWidth(int n,int l){if(active){events+="W"+n+":"+l+",";if(mode==4&&l==1)throw new IllegalStateException();}return super.getTraceHalfWidth(n,l);}
 }
 static class State {
  IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();int next=1;
  int id(Object x){return x==null?0:ids.computeIfAbsent(x,k->next++);}
  String rule(ViaRule r){if(r==null)return "N";String s=id(r)+":"+hex(r.name);for(int i=0;i<r.viaCount();i++)s+=","+id(r.getVia(i));return s;}
  String cls(NetClass c,int layers){if(c==null)return "N";String s=id(c)+":"+hex(c.getName())+":"+c.getTraceClearanceClass()+":"+id(c.getViaRule())+":"+id(c.defaultItemClearanceClasses);for(int i=-1;i<=layers;i++)s+=","+c.getTraceHalfWidth(i);if(c.defaultItemClearanceClasses!=null)for(var type:DefaultItemClearanceClasses.ItemClass.values())s+=","+c.defaultItemClearanceClasses.get(type);return s;}
  String dump(Rules r,int layers,Collection<Item> items){String s=r.getMinTraceHalfWidth()+":"+r.getMaxTraceHalfWidth()+":"+r.getHoleClearance()+":"+bits(r.getPinEdgeToTurnDist())+":"+r.getIgnoreConduction()+":"+(r.getTraceAngleRestriction()==null?-1:r.getTraceAngleRestriction().ordinal())+":"+r.getUseSlowAutorouteAlgorithm()+":"+BoardRules.defaultClearanceClass()+":"+BoardRules.clearanceClassNone();s+="/"+r.netClasses.count();for(int i=0;i<r.netClasses.count();i++)s+="/"+cls(r.netClasses.get(i),layers);s+="/V"+r.viaInfos.count();for(int i=0;i<r.viaInfos.count();i++){var v=r.viaInfos.get(i);s+=","+id(v)+":"+v.getClearanceClassIndex();}s+="/R"+r.viaRules.size();for(var v:r.viaRules)s+="/"+rule(v);s+="/N"+r.nets.maxNetNumber();for(int i=1;i<=r.nets.maxNetNumber();i++)s+=","+id(r.nets.get(i).getNetClass());s+="/I"+(items==null?"N":items.size());if(items!=null)for(Item i:items)s+=i==null?",N":","+i.getId()+":"+i.clearanceClassIndex();s+="/M"+r.clearanceMatrix.getClassCount();for(int i=0;i<r.clearanceMatrix.getClassCount();i++)s+=","+hex(r.clearanceMatrix.getName(i));return s;}
 }
 static ConvexShape shape(int type,int w){return switch(type){case 0->null;case 1->new IntBox(-w,-2,w,2);case 2->new Circle(new IntPoint(0,0),w);default->new IntBox(-2,-w,2,w);};}
 static String run(String line){Scanner in=new Scanner(line);in.next();int layers=in.nextInt(),classes=in.nextInt();Layer[] ls=new Layer[layers];for(int i=0;i<layers;i++)ls[i]=new Layer("l"+i,in.nextInt()!=0);LayerStructure l=new LayerStructure(ls);String[] names=new String[classes];for(int i=0;i<classes;i++)names[i]="c"+i;ClearanceMatrix matrix=new ClearanceMatrix(classes,l,names);Rules r=new Rules(l,matrix);BasicBoard board=new BasicBoard(new IntBox(-100,-100,100,100),l,new PolylineShape[0],0,r,new Communication());State s=new State();ArrayList<String> out=new ArrayList<>();
  int vias=in.nextInt();for(int i=0;i<vias;i++){int cl=in.nextInt(),size=in.nextInt();ConvexShape[] sh=size<0?null:new ConvexShape[size];for(int j=0;j<size;j++)sh[j]=shape(in.nextInt(),in.nextInt());Padstack pad=new Padstacks(l).add("p"+i,sh,false,false);var via=new ViaInfo("via"+i,pad,cl,false,r);r.viaInfos.add(via);s.id(via);}
  ArrayList<Item> pool=new ArrayList<>();pool.add(null);int count=in.nextInt();for(int i=1;i<=count;i++){int cl=in.nextInt(),valid=in.nextInt();pool.add(new Via(null,new IntPoint(0,0),new int[0],cl,i,0,FixedState.UNFIXED,false,valid==0?null:board));}Collection<Item> items=new ArrayList<>(pool.subList(1,pool.size()));int n=in.nextInt();r.active=true;
  for(int step=0;step<n;step++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();double d=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));String name=dec(in.next());r.events="";final Collection<Item> queryItems=items;
   out.add(call(()->switch(op){
    case "D"->s.id(r.getDefaultNetClass());case "C"->{r.createDefaultNetClass();yield "ok";}
    case "N"->s.id(a==0?r.getNewNetClass():r.getNewNetClass(name));case "A"->s.id(a==0?r.appendNetClass():r.appendNetClass(name));
    case "W"->{r.setDefaultTraceHalfWidth(a,b);yield "ok";}case "S"->{r.setDefaultTraceHalfWidths(a);yield "ok";}case "G"->r.getDefaultTraceHalfWidth(a);
    case "H"->r.getTraceHalfWidth(a,b);case "L"->r.traceWidthsAreLayerDependent(a);
    case "n"->r.nets.add(name,1,false).netNumber;case "b"->{r.nets.get(a).setClass(b<0?null:r.netClasses.get(b));yield "ok";}
    case "t"->{r.netClasses.get(a).setTraceClearanceClass(b);yield "ok";}case "w"->{r.netClasses.get(a).setTraceHalfWidth(b,c);yield "ok";}
    case "c"->{r.netClasses.get(a).defaultItemClearanceClasses.set(DefaultItemClearanceClasses.ItemClass.values()[b],c);yield "ok";}
    case "z"->{r.netClasses.get(a).defaultItemClearanceClasses=b==0?null:new DefaultItemClearanceClasses();yield "ok";}
    case "V"->{r.createDefaultViaRule(a<0?null:r.netClasses.get(a),name);yield "ok";}case "v"->s.id(r.getViaRule(name));case "d"->bits(r.getDefaultViaDiameter());
    case "u"->{r.viaInfos.get(a).setClearanceClassIndex(b);yield "ok";}case "p"->{r.viaInfos.get(a).setPadstack(null);yield "ok";}
    case "r"->{if(a==0)r.viaRules.clear();else if(a==1)r.viaRules.add(null);else r.viaRules.add(new ViaRule(name));yield "ok";}
    case "X"->{r.changeClearanceClassIndex(a,b,queryItems);yield "ok";}case "R"->r.removeClearanceClass(a,queryItems);
    case "h"->{r.setHoleClearance(a);yield "ok";}case "e"->{r.setPinEdgeToTurnDist(d);yield "ok";}case "i"->{r.setIgnoreConduction(a!=0);yield "ok";}case "s"->{r.setUseSlowAutorouteAlgorithm(a!=0);yield "ok";}case "a"->{r.setTraceAngleRestriction(a<0?null:AngleRestriction.values()[a]);yield "ok";}
    case "F"->{r.mode=a;yield "ok";}case "Q"->s.rule(r.getDefaultViaRule());case "I"->"ok";
    default->throw new AssertionError(op);
   }));
   if(op.equals("I")){if(a==0)items=null;else{items=new ArrayList<>();if(a==1)items.addAll(pool.subList(1,pool.size()));else{items.add(pool.get(0));if(pool.size()>1)items.add(pool.get(1));}}}
   out.add(r.events.isEmpty()?"-":r.events);out.add(s.dump(r,layers,items));
  }
  return String.join(" ",out);
 }
 public static void main(String[] args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String s;while((s=in.readLine())!=null)System.out.println(s+" | "+run(s));}
}
