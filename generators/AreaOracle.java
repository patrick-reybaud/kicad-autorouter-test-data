package app.freerouting.geometry.planar;
import app.freerouting.datastructures.Stoppable;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;
/** Unchanged PolylineArea, with real boundary shapes and counted stop callbacks. */
public class AreaOracle extends PolygonShapeOracle {
  static PolylineShape boundary(){String mode=next();if(mode.equals("N"))return null;if(mode.equals("P"))return new PolygonShape(readPoints());--index;return tile();}
  static PolylineShape[] holes(){int n=integer();if(n<0)return null;var a=new PolylineShape[n];for(int i=0;i<n;i++)a[i]=boundary();return a;}
  static String sh(PolylineShape s){return s==null?"null":s instanceof PolygonShape p?"P,"+pg(p):tileText((TileShape)s);}
  static String hs(PolylineShape[] holes){if(holes==null)return "null";StringJoiner j=new StringJoiner("@");j.add(""+holes.length);for(var h:holes)j.add(sh(h));return j.toString();}
  static String area(PolylineArea a){return sh(a.getBorder())+"/"+hs(a.getHoles());}
  static boolean cached(PolylineArea a){try{var f=PolylineArea.class.getDeclaredField("precalculatedConvexPieces");f.setAccessible(true);return f.get(a)!=null;}catch(Exception e){throw new RuntimeException(e);}}
  static String childCache(PolylineArea a){try{String s=a.getBorder() instanceof PolygonShape p?cache(p):"-";if(a.getHoles()==null)return s+"/null";for(var h:a.getHoles())s+="/"+(h instanceof PolygonShape p?cache(p):"-");return s;}catch(Exception e){throw new RuntimeException(e);}}
  static class Stop implements Stoppable{
    int calls=0,requests=0,threshold,throwAt;boolean requested;
    Stop(int t,int e){threshold=t;throwAt=e;}
    public void requestStop(){requested=true;++requests;}
    public boolean isStopRequested(){++calls;if(calls==throwAt)throw new ArithmeticException("fixture");return requested||(threshold>=0&&calls>=threshold);}
  }
  public static void main(String[] args)throws Exception{
    System.out.println("ORACLE_READY");
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null){tokens=row.split(" ");index=1;var out=new SimplexOut();
      var border=boundary();var holes=holes();var a=new PolylineArea(border,holes);var alias=a;
      switch(tokens[0]){
      case "Q" -> {
        var p=point();var fp=fp();var v=vector();var pole=ip();int factor=integer();var box=readBox();
        out.call(()->area(a));out.call(()->a.getBorder()==border);out.call(()->a.getHoles()==holes);
        out.call(()->a.dimension());out.call(()->a.isBounded());out.call(()->a.isEmpty());out.call(()->a.isContainedIn(box));out.call(()->tileText(a.boundingBox()));out.call(()->tileText(a.boundingOctagon()));
        out.call(()->a.contains(p));out.call(()->a.contains(fp));out.call(()->qs(a.cornerApproxArr()));
        out.call(()->area(a.translateBy(v)));out.call(()->a.translateBy(v)==a);out.call(()->area(a.turn90Degree(factor,pole)));out.call(()->area(a.mirrorVertical(pole)));out.call(()->area(a.mirrorHorizontal(pole)));
        out.add(cached(a));out.call(()->q(a.nearestPointApprox(fp)));out.add(cached(a));out.call(()->tiles(a.splitToConvex()));out.call(()->a.splitToConvex()==alias.splitToConvex());out.add(childCache(a));
      }
      case "S" -> {
        int threshold=integer(),throwAt=integer(),requestFirst=integer();var stop=new Stop(threshold,throwAt);if(requestFirst!=0)stop.requestStop();
        out.add(cached(a));out.add(childCache(a));out.call(()->tiles(a.splitToConvex(stop)));out.add(stop.calls);out.add(stop.requests);out.add(cached(a));out.add(childCache(a));
        stop.requestStop();out.call(()->tiles(alias.splitToConvex(stop)));out.add(stop.calls);out.add(stop.requests);out.add(cached(a));
        out.call(()->tiles(a.splitToConvex(null)));out.add(cached(a));out.add(childCache(a));
        out.call(()->tiles(a.splitToConvex(stop)));out.add(stop.calls);out.add(cached(a));out.call(()->a.splitToConvex()==alias.splitToConvex());
      }
      default->throw new IllegalArgumentException(tokens[0]);
      }
      if(index!=tokens.length)throw new AssertionError("Unconsumed input: "+row);
      System.out.println(row+" | "+out.result);
    }
  }
}
