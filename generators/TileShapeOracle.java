package app.freerouting.geometry.planar;

import java.io.*;
import java.util.*;

/** Executes TileShape/PolylineShape methods with the real concrete overrides. */
public class TileShapeOracle extends SimplexOracle {
  static String q(FloatPoint value) {return value==null?"null":p(value);}
  static String fl(FloatLine value) {return value==null?"null":q(value.a)+";"+q(value.b);}
  static String qs(FloatPoint[] values) {
    var j=new StringJoiner("#");j.add(String.valueOf(values.length));for(var value:values)j.add(q(value));return j.toString();
  }
  static String ps(Point[] values) {
    var j=new StringJoiner("#");j.add(String.valueOf(values.length));for(var value:values)j.add(p(value));return j.toString();
  }
  static String ints(int[] values) {
    var j=new StringJoiner("#");j.add(String.valueOf(values.length));for(int value:values)j.add(String.valueOf(value));return j.toString();
  }
  public static void main(String[] args) throws Exception {
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null) {
      tokens=row.split(" ");index=1;var out=new SimplexOut();
      switch(tokens[0]) {
        case "Q" -> {
          TileShape a=tile(),b=tile();Point query=point(),end=point();FloatPoint fp=fp();
          Line line=new Line(point(),point());Direction dir=direction();int edge=integer(),count=integer();double tolerance=real();
          out.call(()->a.isEmpty());out.call(()->a.isBounded());out.call(()->a.dimension());out.add(a.borderLineCount());
          out.call(()->a.cornerIsBounded(edge));out.call(()->p(a.corner(edge)));out.call(()->q(a.cornerApprox(edge)));
          out.call(()->qs(a.cornerApproxArr()));out.call(()->a.borderLine(edge)==null?"null":line(a.borderLine(edge)));
          out.call(()->ps(a.boundedCorners()));out.call(()->a.equalsCorner(query));out.call(()->f(a.circumference()));
          out.call(()->q(a.centreOfGravity()));out.call(()->a.isContainedIn(new IntBox(-50,-50,50,50)));
          out.call(()->a.indexOfLeftMostCorner(fp));out.call(()->a.indexOfRightMostCorner(fp));out.call(()->fl(a.polarLineSegment(fp)));
          out.call(()->a.prevNo(edge));out.call(()->a.nextNo(edge));out.call(()->tileText((TileShape)a.getBorder()));out.call(()->a.getHoles().length);
          out.call(()->a.intersects(line));out.call(()->p(a.leftMostCorner(query)));out.call(()->p(a.rightMostCorner(query)));
          out.call(()->f(a.area()));out.call(()->a.isOutside(query));out.call(()->a.contains(query));out.call(()->a.contains(fp));out.call(()->a.contains(fp,tolerance));
          out.call(()->a.contains(b));out.call(()->a.containsInside(query));out.call(()->a.sideOfBorder(fp,tolerance));
          out.call(()->a.containsOnBorderLineNo(query));out.call(()->a.containsOnBorder(query));out.call(()->a.containsApprox(b));
          out.call(()->f(a.distance(fp)));out.call(()->f(a.borderDistance(fp)));out.call(()->f(a.smallestRadius()));
          out.call(()->p(a.nearestPoint(query)));out.call(()->q(a.nearestPointApprox(fp)));out.call(()->p(a.nearestBorderPoint(query)));
          out.call(()->q(a.nearestBorderPointApprox(fp)));out.call(()->qs(a.nearestBorderPointsApprox(fp,count)));
          out.call(()->a.indexOfNearestCorner(query));out.call(()->fl(a.diagonalCornerSegment()));
          out.call(()->qs(a.nearestRelativeOutsideLocations(b,count)));out.call(()->f(a.length()));out.call(()->ints(a.touchingSides(b)));
          out.call(()->f(a.distanceToTheLeft(line)));out.call(()->a.sideOf(line));out.call(()->a.intersectingBorderLineNo(query,dir));
          out.call(()->tiles(a.splitToConvex()));out.call(()->a.isIntersectedInteriorBy(query,end,line));
        }
        case "T" -> {
          TileShape a=tile(),b=tile();Vector v=vector();IntPoint pole=ip();int factor=integer();double offset=real();
          out.call(()->tileText(a.intersectionWithSimplify(b)));out.call(()->tileText((TileShape)a.offset(offset)));out.call(()->tileText((TileShape)a.shrink(offset)));
          out.call(()->tileText((TileShape)a.translateBy(v)));out.call(()->tileText(a.turn90Degree(factor,pole)));out.call(()->tileText(a.mirrorVertical(pole)));out.call(()->tileText(a.mirrorHorizontal(pole)));
        }
        case "D" -> {TileShape a=tile();double width=real();out.call(()->tiles(a.divideIntoSections(width)));}
        case "F" -> {
          Line[] lines=readLines();int n=integer();Point[] points=new Point[n];for(int i=0;i<n;i++)points[i]=point();Point p=point();IntBox box=readBox();
          int lx=integer(),ly=integer(),rx=integer(),uy=integer(),ulx=integer(),lrx=integer(),llx=integer(),urx=integer();
          out.call(()->tileText(TileShape.getInstance(lines)));out.call(()->tileText(TileShape.getInstance(points)));
          out.call(()->tileText(TileShape.getInstance(p)));out.call(()->tileText(TileShape.getInstance(box.ll.x,box.ll.y,box.ur.x,box.ur.y)));
          out.call(()->tileText(TileShape.getInstance(lx,ly,rx,uy,ulx,lrx,llx,urx)));
          out.call(()->tileText(TileShape.getInstance(new Line(p,p))));
        }
        default->throw new IllegalArgumentException(tokens[0]);
      }
      if(index!=tokens.length)throw new AssertionError("unconsumed input: "+row);
      System.out.println(row+" | "+out.result);
    }
  }
}
