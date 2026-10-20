import java.io.*;
import java.lang.reflect.*;
import java.util.*;
/** Test-only observer of the real pinned JVM HashMap, not an algorithm reimplementation. */
public class IntegerGroupMapOracle {
  static Object field(Object value,String name)throws Exception {
    for(Class<?> c=value.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(NoSuchFieldException e){}
    throw new NoSuchFieldException(name);
  }
  static String state(HashMap<Integer,ArrayList<Integer>> map)throws Exception {
    Object[] table=(Object[])field(map,"table");StringBuilder out=new StringBuilder();
    out.append(table==null?0:table.length).append(':').append(field(map,"threshold")).append(':').append(map.size());
    if(table!=null)for(int i=0;i<table.length;i++)if(table[i]!=null){out.append('#').append(i).append(':').append(table[i].getClass().getSimpleName().equals("TreeNode")?'T':'L');for(Object node=table[i];node!=null;node=field(node,"next"))out.append('!').append(field(node,"key"));}
    out.append('/');for(var e:map.entrySet()){out.append('#').append(e.getKey());for(int v:e.getValue())out.append(',').append(v);}
    return out.toString();
  }
  public static void main(String[] args)throws Exception {
    try(var in=new BufferedReader(new FileReader(args[0]));var out=new PrintWriter(args[1])){
      for(String line;(line=in.readLine())!=null;){var tokens=new Scanner(line);int n=tokens.nextInt();var map=new HashMap<Integer,ArrayList<Integer>>();StringBuilder result=new StringBuilder(state(map));
        for(int i=0;i<n;i++){String op=tokens.next();int key=tokens.nextInt();if(op.equals("C"))map.clear();else map.computeIfAbsent(key,k->new ArrayList<>()).add(i);result.append(' ').append(state(map));}
        if(tokens.hasNext())throw new AssertionError();out.println(line+" | "+result);
      }
    }
  }
}
