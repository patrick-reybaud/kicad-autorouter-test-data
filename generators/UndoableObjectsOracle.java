package app.freerouting.datastructures;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class UndoableObjectsOracle {
  static ArrayList<Obj> all;
  static int cloneCalls;
  static class Obj implements UndoableObjects.Storable {
    final int key,serial;int value,cloneMode;boolean valueEquality;
    Obj(int key,int value,int mode,boolean equal){this.key=key;this.value=value;cloneMode=mode;valueEquality=equal;serial=all.size();all.add(this);}
    public int compareTo(Object other){return ((Obj)other).key-key;}
    public Object clone(){cloneCalls++;if(cloneMode==1)return this;if(cloneMode==2)return null;if(cloneMode==3)throw new IllegalStateException("clone");return new Obj(key,value,cloneMode,valueEquality);}
    public boolean equals(Object other){return valueEquality?other instanceof Obj o&&key==o.key&&value==o.value:this==other;}
    public int hashCode(){return key;}
  }
  static Object field(Object x,String name)throws Exception{Field f=x.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(x);}
  static int serial(Object x){return x==null?0:((Obj)x).serial;}
  static String objects(Collection<? extends Object> list){String s=""+list.size();for(Object o:list)s+=","+serial(o);return s;}
  static String visible(UndoableObjects db){ArrayList<UndoableObjects.Storable> list=new ArrayList<>();var it=db.startReadObject();while(true){var o=db.readObject(it);if(o==null)break;list.add(o);}return objects(list);}
  static class Graph {
    IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();ArrayList<Object> nodes=new ArrayList<>();
    int id(Object n){if(n==null)return 0;Integer i=ids.get(n);if(i!=null)return i;nodes.add(n);ids.put(n,nodes.size());return nodes.size();}
    String state(UndoableObjects db,List<UndoableObjects.Storable> cancelled,List<UndoableObjects.Storable> restored)throws Exception{
      Map<?,?> map=(Map<?,?>)field(db,"objects");String s=field(db,"stackLevel")+":"+field(db,"redoPossible")+"/"+map.size();for(var e:map.entrySet())s+=","+serial(e.getKey())+":"+id(e.getValue());
      var stack=(List<?>)field(db,"deletedObjectsStack");s+="/"+stack.size();for(Object row:stack){var list=(Collection<?>)row;s+="#"+list.size();for(Object n:list)s+=","+id(n);}
      String graph="";for(int i=0;i<nodes.size();i++){Object n=nodes.get(i);graph+="#"+serial(field(n,"object"))+":"+field(n,"level")+":"+id(field(n,"undoObject"))+":"+id(field(n,"redoObject"));}
      s+="/"+nodes.size()+graph+"/"+all.size();for(Obj o:all)s+=o==null?"#null":"#"+o.serial+":"+o.key+":"+o.value+":"+o.cloneMode+":"+o.valueEquality;
      return s+"/"+visible(db)+"/"+objects(cancelled)+"/"+objects(restored)+"/"+cloneCalls;
    }
  }
  interface Operation{Object run();}
  static String call(Operation f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(IllegalStateException e){return "clone_error";}}
  @SuppressWarnings("unchecked")
  static String run(String input)throws Exception{
    Scanner in=new Scanner(input);if(!in.next().equals("U"))throw new IllegalArgumentException();all=new ArrayList<>();all.add(null);cloneCalls=0;int n=in.nextInt();for(int i=0;i<n;i++)new Obj(in.nextInt(),in.nextInt(),in.nextInt(),in.nextInt()!=0);
    UndoableObjects db=new UndoableObjects();Graph graph=new Graph();LinkedList<UndoableObjects.Storable> cancelled=new LinkedList<>(),restored=new LinkedList<>();Iterator<UndoableObjects.UndoableObjectNode>[] cursors=new Iterator[4];ArrayList<String> out=new ArrayList<>();out.add(graph.state(db,cancelled,restored));int count=in.nextInt();
    for(int step=0;step<count;step++){
      String op=in.next();int i=in.nextInt(),j=in.nextInt(),k=in.nextInt(),v=in.nextInt();
      out.add(call(()->switch(op){
        case "I" -> {db.insert(all.get(i));yield "ok";}case "D" -> db.delete(all.get(i));case "S" -> {db.saveForUndo(all.get(i));yield "ok";}case "M" -> {all.get(i).value=j;yield "ok";}case "Q" -> {db.generateSnapshot();yield "ok";}case "U" -> db.undo((i&1)!=0?cancelled:null,(i&2)!=0?((i&4)!=0?cancelled:restored):null);case "R" -> db.redo((i&1)!=0?cancelled:null,(i&2)!=0?((i&4)!=0?cancelled:restored):null);case "P" -> db.popSnapshot();
        case "B" -> {cursors[i]=db.startReadObject();yield "ok";}case "T" -> serial(db.readObject(cursors[i]));case "A" -> visible(db);case "N" -> new Obj(i,j,k,v!=0).serial;case "K" -> {all.get(i).cloneMode=j;yield "ok";}case "E" -> {all.get(i).valueEquality=j!=0;yield "ok";}case "O" -> {(i==0?cancelled:restored).add(all.get(j));yield "ok";}case "Z" -> {(i==0?cancelled:restored).clear();yield "ok";}
        case "F" -> {Obj chosen=null;var it=db.startReadObject();while(true){var o=(Obj)db.readObject(it);if(o==null)break;if(o.key==i){chosen=o;break;}}if(chosen!=null){db.saveForUndo(chosen);chosen.value=j;}yield serial(chosen);}default -> throw new IllegalArgumentException(op);
      }));out.add(graph.state(db,cancelled,restored));
    }
    if(in.hasNext())throw new AssertionError("unused input");return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
