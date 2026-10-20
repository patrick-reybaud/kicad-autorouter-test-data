package app.freerouting.rules;
import app.freerouting.board.model.structure.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class ClearanceOracle {
  static Scanner in;
  static IdentityHashMap<Object,Integer> ids;
  static Object field(Object x,String name)throws Exception {Field f=x.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(x);}
  static String name(){String s=in.next();if(s.equals("-"))return null;if(s.equals("."))return "";StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i+=4)b.append((char)Integer.parseInt(s.substring(i,i+4),16));return b.toString();}
  static String text(String s){if(s==null)return "-";if(s.isEmpty())return ".";StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++)b.append(String.format(Locale.ROOT,"%04x",(int)s.charAt(i)));return b.toString();}
  static Layer[] layers(){int n=in.nextInt();if(n<0)return null;Layer[] a=new Layer[n];for(int i=0;i<n;i++){String mode=in.next();if(mode.equals("V"))a[i]=new Layer(name(),in.nextInt()!=0);else if(mode.equals("R"))a[i]=a[in.nextInt()];}return a;}
  interface Call {Object run()throws Exception;}
  static String call(Call f){try{Object v=f.run();return v==null?"null":v.toString();}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(NegativeArraySizeException e){return "negative_size";}catch(Exception e){throw new RuntimeException(e);}}
  static int id(Object x){if(x==null)return 0;return ids.computeIfAbsent(x,k->ids.size()+1);}
  static String ints(int[] a){StringBuilder s=new StringBuilder();s.append(a.length);for(int v:a)s.append(',').append(v);return s.toString();}
  static String row(Object r)throws Exception {
    if(r==null)return "null";
    String s=id(r)+":"+text((String)field(r,"name"));
    int[] max=(int[])field(r,"maxValue");s+="/"+id(max)+":"+ints(max);
    for(Object c:(Object[])field(r,"column"))s+="/"+id(c)+":"+ints((int[])field(c,"layer"));return s;
  }
  static String state(ClearanceMatrix m,List<Object> held)throws Exception {
    String s=m.getClassCount()+";"+ints((int[])field(m,"maxValueOnLayer"));
    Object[] rows=(Object[])field(m,"row");s+=";"+rows.length;for(Object r:rows)s+="#"+row(r);
    s+=";"+held.size();for(Object r:held)s+="#"+row(r);return s;
  }
  static String evaluate(String input)throws Exception {
    in=new Scanner(input);ids=new IdentityHashMap<>();String kind=in.next();ArrayList<String> out=new ArrayList<>();
    if(kind.equals("F")){String a=name(),b=name();out.add(call(()->a.equalsIgnoreCase(b)));}
    else if(kind.equals("L")){
      Layer[] a=layers();LayerStructure l=new LayerStructure(a);String q=name();int no=in.nextInt(),which=in.nextInt();Layer chosen=which==-2?new Layer(q,true):which==-1?null:a[which];
      out.add(call(()->l.getNo(q)));out.add(call(()->l.getNo(chosen)));out.add(call(()->l.signalLayerCount()));out.add(call(()->{Layer v=l.getSignalLayer(no);return (v==null?"null":text(v.name))+":"+l.getNo(v);}));out.add(call(()->l.getSignalLayerNo(chosen)));out.add(call(()->l.getLayerNo(no)));out.add(Boolean.toString(l.layers==a));
    }else if(kind.equals("D")){
      DefaultItemClearanceClasses a=new DefaultItemClearanceClasses(),b=new DefaultItemClearanceClasses(a);int n=in.nextInt();
      for(int j=0;j<n;j++){String op=in.next();int index=in.nextInt(),v=in.nextInt();final var x=a;final var y=b;
        switch(op){case "S":out.add(call(()->{x.set(DefaultItemClearanceClasses.ItemClass.values()[index],v);return "ok";}));break;case "A":a.setAll(v);out.add("ok");break;case "U":out.add(call(()->{y.set(DefaultItemClearanceClasses.ItemClass.values()[index],v);return "ok";}));break;case "K":b=new DefaultItemClearanceClasses(a);out.add("ok");break;}
        String s="";for(var c:DefaultItemClearanceClasses.ItemClass.values())s+=a.get(c)+",";s+="/";for(var c:DefaultItemClearanceClasses.ItemClass.values())s+=b.get(c)+",";out.add(s);
      }
    }else if(kind.equals("M")){
      int count=in.nextInt();LayerStructure layers=new LayerStructure(layers());int nn=in.nextInt();String[] names=new String[nn];for(int i=0;i<nn;i++)names[i]=name();String mode=in.next();int value=in.nextInt();
      ClearanceMatrix m;try{m=mode.equals("D")?ClearanceMatrix.getDefaultInstance(layers,value):new ClearanceMatrix(count,layers,names);}catch(RuntimeException e){out.add(call(()->{throw e;}));return String.join(" ",out);}
      List<Object> held=new ArrayList<>();out.add(state(m,held));int n=in.nextInt();
      for(int k=0;k<n;k++){
        String op=in.next();int i=in.nextInt(),j=in.nextInt(),l=in.nextInt(),v=in.nextInt();String name=name();
        out.add(call(()->switch(op){
          case "V" -> {m.setValue(i,j,l,v);yield "ok";}case "A" -> {m.setValue(i,j,v);yield "ok";}
          case "T" -> {m.setDefaultValue(v);yield "ok";}case "U" -> {m.setDefaultValue(l,v);yield "ok";}
          case "I" -> {m.setInnerValue(i,j,v);yield "ok";}case "P" -> m.appendClass(name);
          case "R" -> {m.removeClass(i);yield "ok";}case "Q" -> m.getValue(i,j,l,v!=0);
          case "X" -> m.maxValue(i,l);case "Y" -> m.maxValue(l);case "B" -> m.isLayerDependent(i,j);
          case "E" -> m.isInnerLayerDependent(i,j);case "H" -> text(m.getName(i));case "N" -> m.getNo(name);
          case "C" -> m.clearanceCompensationValue(i,l);case "Z" -> m.isEqual(i,j);
          case "K" -> {held.add(m.getRow(i));yield "ok";}default -> throw new IllegalArgumentException(op);
        }));out.add(state(m,held));
      }
    }else throw new IllegalArgumentException(kind);
    if(in.hasNext())throw new IllegalStateException("unused input");return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+evaluate(s));}
}
