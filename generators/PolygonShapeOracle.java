package app.freerouting.geometry.planar;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
/** Executes the unchanged PolygonShape, inherited queries and shared Java Random behavior. */
public class PolygonShapeOracle extends CircleOracle {
  static String pg(PolygonShape a){return a==null?"null":ps(a.corners);}
  static long randomState(Random r)throws Exception{var field=Random.class.getDeclaredField("seed");field.setAccessible(true);return ((AtomicLong)field.get(r)).get();}
  static long polygonRandomState()throws Exception{var field=PolygonShape.class.getDeclaredField("randomGenerator");field.setAccessible(true);return randomState((Random)field.get(null));}
  static String cache(PolygonShape a)throws Exception{String s="";for(String n:new String[]{"precalculatedBoundingBox","precalculatedBoundingOctagon","precalculatedConvexPieces","precalculatedConvexPiecesBox"}){var f=PolygonShape.class.getDeclaredField(n);f.setAccessible(true);s+=(f.get(a)==null?"0":"1");}return s;}
  public static void main(String[] args)throws Exception {
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null){tokens=row.split(" ");index=1;var out=new SimplexOut();
    try { switch(tokens[0]){
    case "Q" -> {
      int mode=integer();var pts=readPoints();var p=point();var fp=fp();var v=vector();var pole=ip();int factor=integer(),idx=integer();double offset=real();var box=readBox();var line=new Line(point(),point());
      out.call(()->{var a=mode==0?new PolygonShape(pts):new PolygonShape(new Polygon(pts));
        out.call(()->pg(a));out.call(()->a.dimension());out.call(()->a.isBounded());out.call(()->a.isEmpty());out.call(()->a.borderLineCount());out.call(()->a.cornerIsBounded(idx));
        out.call(()->p(a.corner(idx)));out.call(()->q(a.cornerApprox(idx)));out.call(()->nl(a.borderLine(idx)));out.call(()->qs(a.cornerApproxArr()));out.call(()->ps(a.boundedCorners()));out.call(()->a.equalsCorner(p));
        out.call(()->f(a.circumference()));out.call(()->f(a.area()));out.call(()->q(a.centreOfGravity()));out.call(()->a.isContainedIn(box));out.call(()->tileText(a.boundingBox()));out.call(()->tileText(a.boundingOctagon()));
        out.call(()->a.isConvex());out.call(()->pg(a.convexHull()));out.call(()->a.convexHull()==a);out.call(()->tileText(a.boundingTile()));
        out.call(()->a.indexOfLeftMostCorner(fp));out.call(()->a.indexOfRightMostCorner(fp));out.call(()->a.polarLineSegment(fp)==null?"null":q(a.polarLineSegment(fp).a)+";"+q(a.polarLineSegment(fp).b));
        out.call(()->a.prevNo(idx));out.call(()->a.nextNo(idx));out.call(()->a.getBorder()==a);out.call(()->a.getHoles().length);out.call(()->a.intersects(line));out.call(()->p(a.leftMostCorner(p)));out.call(()->p(a.rightMostCorner(p)));
        out.call(()->pg(a.translateBy(v)));out.call(()->a.translateBy(v)==a);out.call(()->pg(a.turn90Degree(factor,pole)));out.call(()->pg(a.mirrorVertical(pole)));out.call(()->pg(a.mirrorHorizontal(pole)));
        out.call(()->pg(a.enlarge(offset)));out.call(()->a.enlarge(offset)==a);out.call(()->f(a.distance(fp)));out.call(()->f(a.borderDistance(fp)));out.call(()->f(a.smallestRadius()));out.call(()->a.containsOnBorder(p));out.call(()->pls(a.cutout(null)));
        out.call(()->tileText(a.boundingShape(OrthogonalBoundingDirections.INSTANCE)));out.call(()->tileText(a.boundingShape(FortyfiveDegreeBoundingDirections.INSTANCE)));
        return "ok";});
    }
    case "D" -> {
      var pts=readPoints();var p=point();var fp=fp();var t=tile();var c=circle();
      out.call(()->{var a=new PolygonShape(pts);var alias=a;
        try{out.add(cache(a));}catch(Exception e){throw new RuntimeException(e);}
        out.call(()->tiles(a.splitToConvex()));
        try{out.add(polygonRandomState());out.add(cache(a));}catch(Exception e){throw new RuntimeException(e);}
        out.call(()->a.splitToConvex()==alias.splitToConvex());out.call(()->tiles(alias.splitToConvex()));
        out.call(()->a.isOutside(p));out.call(()->a.contains(p));out.call(()->a.containsInside(p));out.call(()->a.contains(fp));out.call(()->q(a.nearestPointApprox(fp)));
        out.call(()->a.intersects((Shape)t));out.call(()->t.intersects((Shape)a));out.call(()->a.intersects(c));out.call(()->c.intersects((Shape)a));
        var other=new PolygonShape(pts);out.call(()->tiles(other.splitToConvex()));out.call(()->a.splitToConvex()==other.splitToConvex());
        out.call(()->a.boundingOctagon()==alias.boundingOctagon());out.call(()->a.boundingOctagon()==other.boundingOctagon());
        try{out.add(polygonRandomState());out.add(cache(a));out.add(cache(other));}catch(Exception e){throw new RuntimeException(e);}
        return "ok";});
    }
    case "R" -> {
      long seed=Long.parseLong(next());int n=integer();int[] bounds=new int[n];for(int i=0;i<n;i++)bounds[i]=integer();
      var r=new Random(seed);out.add(randomState(r));
      for(int bound:bounds){out.call(()->r.nextInt(bound));out.add(randomState(r));}
      r.setSeed(seed);out.add(randomState(r));for(int bound:bounds)out.call(()->r.nextInt(bound));out.add(randomState(r));
    }
    default -> throw new IllegalArgumentException(tokens[0]);
    }
    } catch(StackOverflowError error) { System.out.println(row+" | source_stack_overflow"); continue; }
    if(index!=tokens.length)throw new AssertionError("Unconsumed input: "+row);
    System.out.println(row+" | "+out.result);
    }
  }
}
