package app.freerouting.geometry.planar;
import app.freerouting.core.library.*;
import app.freerouting.board.model.structure.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class PadstackOracle extends CircleOracle {
  static String name(){String s=next();if(s.equals("-"))return null;if(s.equals("."))return "";StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i+=4)b.append((char)Integer.parseInt(s.substring(i,i+4),16));return b.toString();}
  static String nameText(String s){if(s==null)return "-";if(s.isEmpty())return ".";StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i++)b.append(String.format(Locale.ROOT,"%04x",(int)s.charAt(i)));return b.toString();}
  static ConvexShape convex(){if(tokens[index].equals("N")){index++;return null;}if(tokens[index].equals("C")){index++;return circle();}return tile();}
  static ConvexShape[] shapes(){int n=integer();if(n<0)return null;ConvexShape[] a=new ConvexShape[n];for(int i=0;i<n;i++)a[i]=convex();return a;}
  static String convexText(ConvexShape a){return a==null?"null":a instanceof Circle c?c(c):tileText((TileShape)a);}
  static String desc(Padstack p){if(p==null)return "null";return p.id+":"+nameText(p.name)+":"+p.attachAllowed+":"+p.placedAbsolute+":"+p.holeOnly;}
  static boolean cache(Padstack p){try{Field f=Padstack.class.getDeclaredField("cachedDrillRadius");f.setAccessible(true);return f.get(p)!=null;}catch(Exception e){throw new RuntimeException(e);}}
  static String directions(Collection<Direction> values){String s=""+values.size();for(Direction d:values)s+="#"+v(d.getVector());return s;}
  public static void main(String[] args)throws Exception {
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null){tokens=row.split(" ");index=1;var out=new SimplexOut();
      if(tokens[0].equals("F")){String a=name(),b=name();out.call(()->a.compareToIgnoreCase(b));}
      else if(tokens[0].equals("Q")){
        String name=name();ConvexShape[] shapes=shapes();boolean drill=integer()!=0,absolute=integer()!=0;int layer=integer();double factor=real();
        Padstacks list=new Padstacks(null);Padstack p=list.add(name,shapes,drill,absolute);
        out.add(desc(p));out.call(()->p.boardLayerCount());out.call(()->p.fromLayer());out.call(()->p.toLayer());out.call(()->convexText(p.getShape(layer)));out.call(()->p.getShape(layer)==(layer>=0&&layer<shapes.length?shapes[layer]:null));out.call(()->directions(p.getTraceExitDirections(layer,factor)));
        out.add(cache(p));out.call(()->f(p.getDrillRadius()));out.add(cache(p));out.call(()->f(p.getDrillRadius()));p.holeOnly=true;out.add(desc(p));out.call(()->f(p.getDrillRadius()));out.call(()->p.compareTo(p));
      }else if(tokens[0].equals("L")){
        int layers=integer();LayerStructure structure=layers<0?null:new LayerStructure(new Layer[layers]);Padstacks list=new Padstacks(structure);int n=integer();
        for(int i=0;i<n;i++){
          String op=next();String name=name();ConvexShape[] array=shapes();ConvexShape shape=convex();int from=integer(),to=integer(),lookup=integer();boolean drill=integer()!=0,absolute=integer()!=0;
          out.call(()->desc(op.equals("A")?list.add(name,array,drill,absolute):op.equals("B")?list.add(array):list.add(shape,from,to)));
          out.add(list.count());out.call(()->desc(list.get(name)));out.call(()->desc(list.get(lookup)));
          Padstack p=list.get(list.count());out.call(()->p.boardLayerCount());out.call(()->p.fromLayer());out.call(()->p.toLayer());out.call(()->convexText(p.getShape(from)));out.call(()->p.getShape(from)==shape);out.call(()->f(p.getDrillRadius()));
        }
      }else throw new IllegalArgumentException(tokens[0]);
      if(index!=tokens.length)throw new AssertionError("unconsumed input");System.out.println(row+" | "+out.result);
    }
  }
}
