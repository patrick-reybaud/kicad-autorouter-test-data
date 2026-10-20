package app.freerouting.geometry.planar;
import app.freerouting.datastructures.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/** Runs unchanged personal trees/stacks. Reflection reads topology and storage, never mutates it. */
public class TreeOracle extends TileShapeOracle {
  static final Field rootField, parentField, boundsField, stackLevel, stackStorage;
  static {try {
    rootField=ShapeTree.class.getDeclaredField("root");
    Class<?> node=Class.forName("app.freerouting.datastructures.ShapeTree$TreeNode");
    parentField=node.getDeclaredField("parent");boundsField=node.getDeclaredField("boundingShape");
    stackLevel=ArrayStack.class.getDeclaredField("level");stackStorage=ArrayStack.class.getDeclaredField("nodeArr");
    for(Field f:new Field[]{rootField,parentField,boundsField,stackLevel,stackStorage})f.setAccessible(true);
  }catch(Exception e){throw new ExceptionInInitializerError(e);}}
  static Object get(Field f,Object o){try{return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
  static ArrayList<String> trace;
  static IdentityHashMap<ShapeTree.Leaf,Integer> serials;
  static MinAreaTree[] trees;
  static int serial(ShapeTree.Leaf leaf){if(leaf==null)return -1;return serials.computeIfAbsent(leaf,k->serials.size());}
  static String list(Collection<?> v){var j=new StringJoiner("#");j.add(""+v.size());for(var x:v)j.add(String.valueOf(x));return j.toString();}
  static int compare(int a,int b,int mode){
    if(mode==0)return Integer.compare(a,b);
    if(mode==1)return a-b;
    int x=Math.floorMod(a,3),y=Math.floorMod(b,3);
    if(x==y)return Integer.compare(a,b);
    return (x+1)%3==y?-1:1;
  }
  static class Obj implements ShapeTree.Storable {
    int id,key,count,failGet,failSet,mode;TileShape[] shapes;
    ShapeTree.Leaf[][] entries=new ShapeTree.Leaf[2][];
    int tid(ShapeTree t){return t==trees[0]?0:1;}
    public int treeShapeCount(ShapeTree t){trace.add("count,"+tid(t)+","+id);return count;}
    public TileShape getTreeShape(ShapeTree t,int i){trace.add("get,"+tid(t)+","+id+","+i);if(i==failGet)throw new ArithmeticException();return shapes[i];}
    public void setSearchTreeEntries(ShapeTree.Leaf[] leaves,ShapeTree t){
      trace.add("set,"+tid(t)+","+id+","+leaves.length);if(failSet!=0)throw new ArithmeticException();
      entries[tid(t)]=leaves;for(var l:leaves)serial(l);
    }
    public int compareTo(Object other){var o=(Obj)other;trace.add("cmp,"+id+","+o.id);return compare(key,o.key,mode);}
  }
  static TileShape maybeTile(){if(tokens[index].equals("N")){index++;return null;}return tile();}
  static RegularTileShape regular(){if(tokens[index].equals("N")){index++;return null;}return shape();}
  static String leaf(ShapeTree.Leaf l){
    if(l==null)return "null";String depth;
    try{depth=""+l.distanceToRoot();}catch(NullPointerException e){depth="null_error";}
    return serial(l)+","+(l.object==null?-1:((Obj)l.object).id)+","+l.shapeIndexInObject+","+depth+","+tileText(l.boundingShape);
  }
  static String leaves(ShapeTree.Leaf[] a){if(a==null)return "null";var r=new ArrayList<String>();for(var l:a)r.add(leaf(l));return list(r);}
  static String topology(MinAreaTree tree){
    record Node(Object value,int parent,Object expected){}
    var pending=new ArrayDeque<Node>();Object root=get(rootField,tree);if(root!=null)pending.push(new Node(root,-1,null));
    var result=new ArrayList<String>();
    while(!pending.isEmpty()){
      var n=pending.pop();Object v=n.value();int i=result.size();
      String text=n.parent()+","+(get(parentField,v)==n.expected())+","+tileText((TileShape)get(boundsField,v));
      if(v instanceof ShapeTree.Leaf l)result.add("L,"+text+","+leaf(l));
      else{var a=(ShapeTree.InnerNode)v;result.add("I,"+text);pending.push(new Node(a.secondChild,i,v));pending.push(new Node(a.firstChild,i,v));}
    }
    return list(result);
  }
  static String entries(Obj[] objects){var r=new ArrayList<String>();for(var o:objects)for(var a:o.entries)r.add(leaves(a));return list(r);}
  static void treeCase(SimplexOut out){
    serials=new IdentityHashMap<>();trace=new ArrayList<>();
    trees=new MinAreaTree[]{new MinAreaTree(OrthogonalBoundingDirections.INSTANCE),new MinAreaTree(FortyfiveDegreeBoundingDirections.INSTANCE)};
    int mode=integer(),count=integer();var objects=new Obj[count];
    for(int i=0;i<count;i++){var o=new Obj();o.id=i;o.mode=mode;o.key=integer();o.count=integer();o.failGet=integer();o.failSet=integer();int n=integer();o.shapes=new TileShape[n];for(int k=0;k<n;k++)o.shapes[k]=maybeTile();objects[i]=o;}
    int commands=integer();
    for(int k=0;k<commands;k++){
      String op=next();int ti=integer();var t=trees[ti];
      switch(op){
        case "I"->{var o=objects[integer()];out.call(()->{t.insert(o);return "ok";});}
        case "R"->{var o=objects[integer()];out.call(()->{t.remove(o.entries[ti]);return "ok";});}
        case "L"->{var o=objects[integer()];int n=integer();out.call(()->{t.removeLeaf(o.entries[ti][n]);return "ok";});}
        case "N"->out.call(()->{t.insert((ShapeTree.Storable)null);return "ok";});
        case "X"->out.call(()->{t.remove(null);return "ok";});
        case "Q"->{var q=regular();out.call(()->leaves(t.overlaps(q).toArray(new ShapeTree.Leaf[0])));}
        case "V"->{int action=integer(),limit=integer();var q=regular();var visits=new ArrayList<String>();
          out.call(()->{t.forEachOverlap(q,action==2?null:l->{visits.add(leaf(l));if(action==1&&visits.size()==limit)throw new ArithmeticException();if(action==3)t.removeLeaf(l);});return "ok";});out.add(list(visits));}
        default->throw new AssertionError(op);
      }
      for(var tree:trees){out.add(tree.size());out.call(()->topology(tree));out.call(()->leaves(tree.toArray()));}
      out.call(()->entries(objects));out.add(list(trace));trace.clear();
    }
  }
  static void setCase(SimplexOut out){
    int mode=integer(),throwAt=integer(),n=integer();int[] keys=new int[n];for(int i=0;i<n;i++)keys[i]=integer();
    var calls=new ArrayList<String>();var set=new TreeSet<Integer>((a,b)->{calls.add(a+","+b);if(calls.size()==throwAt)throw new ArithmeticException();return compare(keys[a],keys[b],mode);});
    for(int i=0;i<n;i++){final int id=i;out.call(()->set.add(id));}
    out.add(list(set));out.add(list(calls));
  }
  static void stackCase(SimplexOut out){
    int capacity=integer(),n=integer();String[] ops=new String[n];Integer[] args=new Integer[n];
    for(int i=0;i<n;i++){ops[i]=next();if(ops[i].equals("P")){String a=next();args[i]=a.equals("N")?null:Integer.valueOf(a);}}
    out.call(()->{var stack=new ArrayStack<Integer>(capacity);
      for(int i=0;i<n;i++){int id=i;out.call(()->{switch(ops[id]){case "P":stack.push(args[id]);return "ok";case "R":stack.reset();return "ok";default:return String.valueOf(stack.pop());}});
        out.add(get(stackLevel,stack));out.add(list(Arrays.asList((Object[])get(stackStorage,stack))));}
      return "ok";});
  }
  static void leafCase(SimplexOut out){
    int a=integer(),b=integer(),x=integer(),y=integer();trace=new ArrayList<>();var p=new Obj();p.id=0;p.key=a;p.mode=1;var q=new Obj();q.id=1;q.key=b;q.mode=1;
    var one=new ShapeTree.Leaf(p,x,null,null);var two=new ShapeTree.Leaf(q,y,null,null);
    out.call(()->one.compareTo(two));out.call(()->two.compareTo(one));out.call(()->one.distanceToRoot());out.add(list(trace));
  }
  public static void main(String[] args)throws Exception{
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null){tokens=row.split(" ");index=1;var out=new SimplexOut();
      switch(tokens[0]){case "T"->treeCase(out);case "S"->setCase(out);case "A"->stackCase(out);case "L"->leafCase(out);default->throw new AssertionError(tokens[0]);}
      if(index!=tokens.length)throw new AssertionError("unconsumed input");System.out.println(row+" | "+out.result);
    }
  }
}
