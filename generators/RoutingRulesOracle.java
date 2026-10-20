package app.freerouting.rules;
import app.freerouting.core.library.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.util.*;
public class RoutingRulesOracle extends ClearanceOracle {
  static String bits(double v){return "d"+Long.toUnsignedString(Double.doubleToLongBits(v),16);}
  static double real(){return Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));}
  static ArrayList<Padstack> pads(){var p=new ArrayList<Padstack>();p.add(null);Padstacks list=new Padstacks(new LayerStructure(new Layer[6]));p.add(list.add((ConvexShape[])null));p.add(list.add(new ConvexShape[0]));p.add(list.add(new ConvexShape[2]));p.add(list.add(new IntBox(0,0,20,20),0,5));p.add(list.add(new IntBox(0,0,20,20),1,3));p.add(list.add(new IntBox(0,0,20,20),0,5));return p;}
  static ViaRule[] rules(){return new ViaRule[]{null,new ViaRule("same"),new ViaRule("same"),ViaRule.EMPTY};}
  static String viaState(ArrayList<ViaInfo> vias,ArrayList<Padstack> pads,ViaInfos infos,ViaRule[] rules){
    String s="";for(ViaInfo v:vias)s+="#"+(v==null?"null":text(v.getName())+":"+pads.indexOf(v.getPadstack())+":"+v.getClearanceClassIndex()+":"+v.attachSmdAllowed());
    s+="/"+infos.count();for(int i=0;i<infos.count();i++)s+=","+vias.indexOf(infos.get(i));
    for(int r=1;r<rules.length;r++){s+="/"+rules[r].viaCount();for(int i=0;i<rules[r].viaCount();i++)s+=","+vias.indexOf(rules[r].getVia(i));}return s;
  }
  static String netState(ArrayList<NetClass> pool,NetClasses classes,ViaRule[] rules){
    String s=""+classes.count();for(int i=0;i<classes.count();i++)s+=","+pool.indexOf(classes.get(i));
    for(NetClass c:pool){if(c==null){s+="#null";continue;}s+="#"+text(c.getName())+":"+c.getTraceClearanceClass()+":"+Arrays.asList(rules).indexOf(c.getViaRule())+":"+c.isShoveFixed()+":"+c.getPullTight()+":"+c.getIgnoreCyclesWithAreas()+":"+c.isIgnoredByAutorouter+":"+bits(c.getMinimumTraceLength())+":"+bits(c.getMaximumTraceLength())+":"+c.layerCount();for(int l=0;l<c.layerCount();l++)s+=","+c.getTraceHalfWidth(l)+","+c.isActiveRoutingLayer(l);s+="/";if(c.defaultItemClearanceClasses==null)s+="null";else for(var item:DefaultItemClearanceClasses.ItemClass.values())s+=c.defaultItemClearanceClasses.get(item)+",";}return s;
  }
  static String run(String input)throws Exception{
    in=new Scanner(input);String kind=in.next();ArrayList<String> out=new ArrayList<>();ViaRule[] rules=rules();
    if(kind.equals("V")){
      var pads=pads();var vias=new ArrayList<ViaInfo>();vias.add(null);String[] names={"A","a","A",null,"","𐐀"};int[] ps={4,5,6,0,1,3};for(int i=0;i<6;i++)vias.add(new ViaInfo(names[i],pads.get(ps[i]),i+1,i%2==1,null));ViaInfos infos=new ViaInfos();int n=in.nextInt();out.add(viaState(vias,pads,infos,rules));
      for(int x=0;x<n;x++){
        String op=in.next();int i=in.nextInt(),j=in.nextInt(),k=in.nextInt(),v=in.nextInt();String name=name();
        out.add(call(()->switch(op){
          case "N" -> {vias.get(i).setName(name);yield "ok";}case "P" -> {vias.get(i).setPadstack(pads.get(j));yield "ok";}case "C" -> {vias.get(i).setClearanceClassIndex(v);yield "ok";}case "T" -> {vias.get(i).setAttachSmdAllowed(v!=0);yield "ok";}case "D" -> vias.get(i).compareTo(vias.get(j));
          case "A" -> infos.add(vias.get(i));case "R" -> infos.remove(vias.get(i));case "G" -> vias.indexOf(infos.get(i));case "H" -> vias.indexOf(infos.get(name));case "E" -> infos.nameExists(name);
          case "U" -> {rules[j].appendVia(vias.get(i));yield "ok";}case "X" -> rules[j].removeVia(vias.get(i));case "Y" -> rules[j].contains(vias.get(i));case "Z" -> rules[j].containsPadstack(pads.get(i));case "L" -> vias.indexOf(rules[j].getLayerRange(i,k));case "S" -> rules[k].swap(vias.get(i),vias.get(j));case "V" -> vias.indexOf(rules[j].getVia(i));default -> throw new IllegalArgumentException(op);
        }));out.add(viaState(vias,pads,infos,rules));
      }
    }else if(kind.equals("N")){
      LayerStructure ls=new LayerStructure(layers());NetClasses classes=new NetClasses();ArrayList<NetClass> pool=new ArrayList<>();pool.add(null);int n=in.nextInt();out.add(netState(pool,classes,rules));
      for(int x=0;x<n;x++){
        String op=in.next();int i=in.nextInt(),j=in.nextInt(),k=in.nextInt(),v=in.nextInt();String name=name();double a=real(),b=real();int len=in.nextInt();int[] widths=len<0?null:new int[len];if(widths!=null)for(int z=0;z<len;z++)widths[z]=in.nextInt();
        out.add(call(()->switch(op){
          case "A" -> {NetClass c=classes.append(name,ls,null,v!=0);pool.add(c);yield pool.indexOf(c);}case "B" -> {NetClass c=classes.append(ls,null);pool.add(c);yield pool.indexOf(c);}case "R" -> classes.remove(pool.get(i));case "G" -> pool.indexOf(classes.get(i));case "H" -> pool.indexOf(classes.get(name));case "F" -> pool.indexOf(classes.find(v,k,rules[j]));case "Z" -> pool.indexOf(classes.find(widths,k,rules[j]));
          case "M" -> {pool.get(i).setName(name);yield "ok";}case "W" -> {pool.get(i).setTraceHalfWidth(v);yield "ok";}case "w" -> {pool.get(i).setTraceHalfWidth(k,v);yield "ok";}case "I" -> {pool.get(i).setTraceHalfWidthOnInner(v);yield "ok";}case "C" -> {pool.get(i).setTraceClearanceClass(v);yield "ok";}case "V" -> {pool.get(i).setViaRule(rules[j]);yield "ok";}case "S" -> {pool.get(i).setShoveFixed(v!=0);yield "ok";}case "T" -> {pool.get(i).setPullTight(v!=0);yield "ok";}case "Y" -> {pool.get(i).setIgnoreCyclesWithAreas(v!=0);yield "ok";}case "X" -> {pool.get(i).isIgnoredByAutorouter=v!=0;yield "ok";}case "D" -> {pool.get(i).setMinimumTraceLength(a);pool.get(i).setMaximumTraceLength(b);yield "ok";}
          case "a" -> {pool.get(i).setActiveRoutingLayer(k,v!=0);yield "ok";}case "t" -> {pool.get(i).setAllLayersActive(v!=0);yield "ok";}case "u" -> {pool.get(i).setAllInnerLayersActive(v!=0);yield "ok";}case "q" -> pool.get(i).traceWidthIsLayerDependent();case "z" -> pool.get(i).traceWidthIsInnerLayerDependent();case "h" -> pool.get(i).getTraceHalfWidth(k);case "l" -> pool.get(i).isActiveRoutingLayer(k);
          case "d" -> {pool.get(i).defaultItemClearanceClasses.set(DefaultItemClearanceClasses.ItemClass.values()[j],v);yield "ok";}case "e" -> {pool.get(i).defaultItemClearanceClasses.setAll(v);yield "ok";}case "K" -> {pool.get(i).defaultItemClearanceClasses=pool.get(j).defaultItemClearanceClasses;yield "ok";}case "O" -> {pool.get(i).defaultItemClearanceClasses=null;yield "ok";}default -> throw new IllegalArgumentException(op);
        }));out.add(netState(pool,classes,rules));
      }
    }else throw new IllegalArgumentException(kind);
    if(in.hasNext())throw new AssertionError("unused input");return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
