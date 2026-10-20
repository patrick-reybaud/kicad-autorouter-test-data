import java.io.*;
import java.util.*;
public class OrderedSetMutationOracle {
 static int mode,equalMode,fail,count;static String events;
 static class Key implements Comparable<Key>{final int id;int value;Key(int id,int value){this.id=id;this.value=value;}public int compareTo(Key k){return compare(this,k);}public boolean equals(Object o){Key k=(Key)o;event("E",this,k);return k!=null&&(equalMode==0?this==k:value==k.value);}public int hashCode(){return id;}}
 static int id(Key k){return k==null?0:k.id;}
 static void event(String op,Key a,Key b){events+=op+id(a)+":"+id(b)+",";if(++count==fail)throw new IllegalStateException();}
 static int compare(Key a,Key b){event("C",a,b);if(a==null||b==null)throw new NullPointerException();return mode==0?Integer.compare(a.value,b.value):b.value-a.value;}
 static String state(TreeSet<Key> s){String r=""+s.size();for(Key k:s)r+="/"+k.id+":"+k.value;return r;}
 interface Call {Object run();}
 static String call(Call c){try{return String.valueOf(c.run());}catch(NullPointerException e){return "null_error";}catch(IllegalStateException e){return "callback_error";}}
 static String run(String input){Scanner in=new Scanner(input);in.next();mode=in.nextInt();equalMode=in.nextInt();int n=in.nextInt();Key[] keys=new Key[n+1];for(int i=1;i<=n;i++)keys[i]=new Key(i,in.nextInt());Comparator<Key> cmp=mode==2?null:OrderedSetMutationOracle::compare;var a=new TreeSet<Key>(cmp);var b=new TreeSet<Key>(cmp);List<TreeSet<Key>> sets=new ArrayList<>(List.of(a,b));int steps=in.nextInt();ArrayList<String> out=new ArrayList<>();
  for(int i=0;i<steps;i++){int op=in.nextInt(),target=in.nextInt(),x=in.nextInt(),arg=in.nextInt();fail=in.nextInt();int size=in.nextInt();ArrayList<Key> list=new ArrayList<>();for(int j=0;j<size;j++)list.add(keys[in.nextInt()]);TreeSet<Key> s=sets.get(target);events="";count=0;out.add(call(()->switch(op){case 0->s.add(keys[x]);case 1->s.contains(keys[x]);case 2->s.remove(keys[x]);case 3->{s.clear();yield "ok";}case 4->s.addAll(sets.get(1-target));case 5->s.removeAll(sets.get(x));case 6->s.removeAll(list);case 7->{keys[x].value=arg;yield "ok";}case 8->{sets.set(target,new TreeSet<>(sets.get(1-target)));yield "ok";}default->throw new AssertionError();}));out.add(state(sets.get(0))+";"+state(sets.get(1)));out.add(events.isEmpty()?"-":events);}
  if(in.hasNext())throw new AssertionError();return String.join(" ",out);
 }
 public static void main(String[]args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
