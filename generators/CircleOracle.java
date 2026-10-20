package app.freerouting.geometry.planar;
import java.io.*;
import java.util.*;
/** Executes the original circle and bounding-direction classes, with their actual dispatch. */
public class CircleOracle extends PolylineOracle {
  static Circle circle(){return new Circle(ip(),integer());}
  static String c(Circle a){return "C,"+a.center.x+","+a.center.y+","+a.radius;}
  static RegularTileShape typedBounds(ShapeBoundingDirections d,TileShape t){
    if(t instanceof IntBox b)return d.bounds(b);
    if(t instanceof IntOctagon o)return d.bounds(o);
    return d.bounds((Simplex)t);
  }
  static boolean typedIntersection(Circle a,TileShape t){
    if(t instanceof IntBox b)return a.intersects(b);
    if(t instanceof IntOctagon o)return a.intersects(o);
    return a.intersects((Simplex)t);
  }
  public static void main(String[] args)throws Exception{
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null){tokens=row.split(" ");index=1;var out=new SimplexOut();
    switch(tokens[0]){
case "Q" -> {
var a=circle();var p=point();var fp=fp();var v=vector();var pole=ip();int factor=integer();double offset=real();var box=readBox();
out.call(()->c(a));
out.call(()->a.isEmpty());
out.call(()->a.isBounded());
out.call(()->a.dimension());
out.call(()->f(a.circumference()));
out.call(()->f(a.area()));
out.call(()->q(a.centreOfGravity()));
out.call(()->a.isOutside(p));
out.call(()->a.contains(p));
out.call(()->a.contains(fp));
out.call(()->a.containsInside(p));
out.call(()->a.containsOnBorder(p));
out.call(()->f(a.distance(fp)));
out.call(()->f(a.smallestRadius()));
out.call(()->tileText(a.boundingBox()));
out.call(()->tileText(a.boundingOctagon()));
out.call(()->tileText(a.boundingTile()));
out.call(()->a.isContainedIn(box));
out.call(()->c(a.turn90Degree(factor,pole)));
out.call(()->c(a.mirrorVertical(pole)));
out.call(()->c(a.mirrorHorizontal(pole)));
out.call(()->f(a.maxWidth()));
out.call(()->f(a.minWidth()));
out.call(()->c(a.offset(offset)));
out.call(()->c(a.shrink(offset)));
out.call(()->c(a.translateBy(v)));
out.call(()->a.translateBy(v)==a);
out.call(()->q(a.nearestPointApprox(fp)));
out.call(()->f(a.borderDistance(fp)));
out.call(()->c(a.enlarge(offset)));
out.call(()->a.enlarge(offset)==a);
out.call(()->tiles(a.splitToConvex()));
out.call(()->a.getBorder()==a);
out.call(()->a.getHoles().length);
out.call(()->qs(a.cornerApproxArr()));
out.call(()->pls(a.cutout(null)));
out.call(()->a.offset(offset)==a);
out.call(()->a.shrink(offset)==a);
out.call(()->a.turn90Degree(factor,pole)==a);
}
case "I" -> {
var a=circle();var b=circle();var t=tile();
out.call(()->a.intersects(b));
out.call(()->b.intersects(a));
out.call(()->a.intersects((Shape)b));
out.call(()->a.intersects((Shape)t));
out.call(()->t.intersects(a));
out.call(()->typedIntersection(a,t));
out.call(()->typedIntersection(b,t));
}
case "B" -> {
var t=tile();var a=circle();ShapeBoundingDirections d=integer()==4?OrthogonalBoundingDirections.INSTANCE:FortyfiveDegreeBoundingDirections.INSTANCE;
out.call(()->d.count());
out.call(()->tileText(d.bounds((ConvexShape)t)));
out.call(()->tileText(t.boundingShape(d)));
out.call(()->tileText(typedBounds(d,t)));
out.call(()->tileText(d.bounds(a)));
out.call(()->tileText(d.bounds((ConvexShape)a)));
out.call(()->tileText(a.boundingShape(d)));
out.call(()->t instanceof IntOctagon o && d.bounds(o).toSimplex()==o.toSimplex());
out.call(()->tileText(d.bounds((ConvexShape)t)));
}
default->throw new IllegalArgumentException(tokens[0]);
    }
    if(index!=tokens.length)throw new AssertionError("unconsumed input: "+row);
    System.out.println(row+" | "+out.result);
    }
  }
}
