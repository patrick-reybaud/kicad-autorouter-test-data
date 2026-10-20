package app.freerouting.board.actions;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class ItemStateOracle {
  static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
  static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
  static class ObjectShape implements ShapeTree.Storable {
    final int id;ObjectShape(int id){this.id=id;}public int compareTo(Object o){return ((ObjectShape)o).id-id;}
    public int treeShapeCount(ShapeTree t){return 1;}public TileShape getTreeShape(ShapeTree t,int i){return new IntBox(id,0,id+1,1);}public void setSearchTreeEntries(ShapeTree.Leaf[] a,ShapeTree t){}
  }
  interface Call{Object run()throws Exception;}
  static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(Exception e){throw new RuntimeException(e);}}
  static int identity(List<?> pool,Object value){for(int i=0;i<pool.size();i++)if(pool.get(i)==value)return i;return -1;}
  static String state(ItemSearchTreesInfo info,List<ShapeTree> trees,List<ShapeTree.Leaf[]> arrays,List<TileShape[]> shapes,List<ShapeTree.Leaf> leaves,List<TileShape> tiles)throws Exception{
    var records=(Collection<?>)field(info,"treeList");String s=""+records.size();for(Object r:records)s+="#"+identity(trees,field(r,"tree"))+":"+identity(arrays,field(r,"entryArr"))+":"+identity(shapes,field(r,"precalculatedTreeShapes"));
    s+="/"+arrays.size();for(var a:arrays){if(a==null)s+="#null";else{s+="#"+a.length;for(var v:a)s+=","+identity(leaves,v);}}
    s+="/"+shapes.size();for(var a:shapes){if(a==null)s+="#null";else{s+="#"+a.length;for(var v:a)s+=","+identity(tiles,v);}}
    return s;
  }
  static String run(String input)throws Exception{
    Scanner in=new Scanner(input);String kind=in.next();ArrayList<String> out=new ArrayList<>();
    if(kind.equals("I")){
      int initial=in.nextInt();long wraps=in.nextLong();ItemIdGenerator a=new ItemIdGenerator();set(a,"lastGeneratedId",initial);set(a,"wrapAroundCount",wraps);ItemIdGenerator b=new ItemIdGenerator();int count=in.nextInt();
      for(int i=0;i<count;i++){String op=in.next();if(op.equals("N"))out.add(""+a.newId());else if(op.equals("B"))out.add(""+b.newId());else if(op.equals("S")){set(b,"lastGeneratedId",field(a,"lastGeneratedId"));set(b,"wrapAroundCount",field(a,"wrapAroundCount"));out.add("ok");}else if(op.equals("R")){set(a,"lastGeneratedId",in.nextInt());set(a,"wrapAroundCount",in.nextLong());out.add("ok");}else if(op.equals("M"))out.add(""+a.maxGeneratedId());else throw new IllegalArgumentException(op);
        out.add(a.maxGeneratedId()+":"+field(a,"wrapAroundCount")+":"+b.maxGeneratedId()+":"+field(b,"wrapAroundCount"));}
    }else if(kind.equals("C")){
      ArrayList<ShapeTree> trees=new ArrayList<>();trees.add(null);ArrayList<ShapeTree.Leaf> leaves=new ArrayList<>();leaves.add(null);for(int i=0;i<3;i++){MinAreaTree t=new MinAreaTree(i==2?OrthogonalBoundingDirections.INSTANCE:FortyfiveDegreeBoundingDirections.INSTANCE);t.insert(new ObjectShape(i));trees.add(t);leaves.add(t.toArray()[0]);}
      ArrayList<TileShape> tiles=new ArrayList<>();tiles.add(null);tiles.add(new IntBox(0,0,1,1));tiles.add(new IntBox(0,0,1,1));tiles.add(new IntOctagon(0,0,20,20,-20,20,0,40));
      ArrayList<ShapeTree.Leaf[]> arrays=new ArrayList<>();arrays.add(null);arrays.add(new ShapeTree.Leaf[0]);arrays.add(new ShapeTree.Leaf[]{null,leaves.get(1),leaves.get(2)});arrays.add(new ShapeTree.Leaf[]{leaves.get(1),leaves.get(1)});
      ArrayList<TileShape[]> shapes=new ArrayList<>();shapes.add(null);shapes.add(new TileShape[0]);shapes.add(new TileShape[]{null,tiles.get(1),tiles.get(2)});shapes.add(new TileShape[]{tiles.get(1),tiles.get(1)});
      ItemSearchTreesInfo info=new ItemSearchTreesInfo();int count=in.nextInt();out.add(state(info,trees,arrays,shapes,leaves,tiles));
      for(int i=0;i<count;i++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();out.add(call(()->switch(op){
        case "E" -> {info.setTreeEntries(arrays.get(b),trees.get(a));yield "ok";}case "S" -> {info.setPrecalculatedTreeShapes(shapes.get(b),trees.get(a));yield "ok";}case "e" -> identity(arrays,info.getTreeEntries(trees.get(a)));case "s" -> identity(shapes,info.getPrecalculatedTreeShapes(trees.get(a)));case "C" -> {info.clearPrecalculatedTreeShapes();yield "ok";}case "M" -> {arrays.get(a)[b]=leaves.get(c);yield "ok";}case "m" -> {shapes.get(a)[b]=tiles.get(c);yield "ok";}case "A" -> {arrays.add(info.getTreeEntries(trees.get(a)));yield arrays.size()-1;}case "B" -> {shapes.add(info.getPrecalculatedTreeShapes(trees.get(a)));yield shapes.size()-1;}default -> throw new IllegalArgumentException(op);
      }));out.add(state(info,trees,arrays,shapes,leaves,tiles));}
    }else throw new IllegalArgumentException(kind);
    if(in.hasNext())throw new AssertionError("unused input");return String.join(" ",out);
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
