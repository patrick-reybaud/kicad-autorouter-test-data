package app.freerouting.geometry.planar;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;
/** Executes unchanged personal Polyline/LineSegment/Polygon and TileShape bindings. */
public class PolylineOracle extends TileShapeOracle {
  static Point[] readPoints() {int n=integer();var a=new Point[n];for(int i=0;i<n;i++)a[i]=point();return a;}
  static Supplier<Polyline> poly() {
    String mode=next();
    if(mode.equals("L")){var lines=readLines();return ()->new Polyline(lines);}
    if(mode.equals("D")){var a=point();var b=point();return ()->new Polyline(a,b);}
    var points=readPoints();return ()->mode.equals("G")?new Polyline(new Polygon(points)):new Polyline(points);
  }
  static Supplier<LineSegment> seg() {
    String mode=next();
    if(mode.equals("P")){var p=poly();int i=integer();return ()->new LineSegment(p.get(),i);}
    if(mode.equals("T")){var t=tile();int i=integer();return ()->new LineSegment(t,i);}
    var a=new Line(point(),point());var b=new Line(point(),point());var c=new Line(point(),point());return ()->new LineSegment(a,b,c);
  }
  static String ls(Line[] a){var j=new StringJoiner("#");j.add(""+a.length);for(var l:a)j.add(nl(l));return j.toString();}
  static String nl(Line a){return a==null?"null":line(a);}
  static String pl(Polyline a){return a==null?"null":ls(a.lines);}
  static String pls(Polyline[] a){if(a==null)return "null";var j=new StringJoiner("#");j.add(""+a.length);for(var p:a)j.add(pl(p));return j.toString();}
  static String sg(LineSegment a){return a==null?"null":nl(a.getStartClosingLine())+"/"+nl(a.getLine())+"/"+nl(a.getEndClosingLine());}
  static String pairs(int[][] a){var j=new StringJoiner("#");j.add(""+a.length);for(var p:a)j.add(p[0]+","+p[1]);return j.toString();}
  public static void main(String[] args) throws Exception {
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null){tokens=row.split(" ");index=1;var out=new SimplexOut();
    switch(tokens[0]) {
case "G" -> {
var pts=readPoints();
out.call(()->{var a=new Polygon(pts);
out.call(()->ps(a.cornerArray()));
out.call(()->ps(a.revertCorners().cornerArray()));
out.call(()->pl(new Polyline(a)));
return "ok";});}
case "A" -> {
var pa=poly();var sa=seg();int idx=integer();
out.call(()->{var a=pa.get();var alias=a;
out.call(()->alias==a);out.call(()->q(a.cornerApprox(idx)));out.call(()->p(alias.corner(idx)));
out.call(()->q(a.cornerApprox(idx)));out.call(()->ps(alias.corners()));out.call(()->qs(a.cornerApproxArr()));return "ok";});
out.call(()->{var a=sa.get();var alias=a;
out.call(()->alias==a);out.call(()->q(a.startPointApprox()));out.call(()->q(a.endPointApprox()));
out.call(()->p(alias.startPoint()));out.call(()->q(a.startPointApprox()));out.call(()->q(a.endPointApprox()));
out.call(()->sg(alias.sortEndpointsInXY()));out.call(()->q(a.startPointApprox()));out.call(()->q(a.endPointApprox()));return "ok";});
}
case "Q" -> {
var pa=poly();var p=point();var fp=fp();var v=vector();var pole=ip();int factor=integer(),idx=integer(),from=integer(),to=integer(),width=integer();double len=real();
out.call(()->{var a=pa.get();
out.call(()->pl(a));
out.call(()->a.cornerCount());
out.call(()->a.isEmpty());
out.call(()->a.isPoint());
out.call(()->a.isOrthogonal());
out.call(()->a.isMultipleOf45Degree());
out.call(()->q(a.cornerApprox(idx)));
out.call(()->p(a.corner(idx)));
out.call(()->q(a.cornerApprox(idx)));
out.call(()->p(a.firstCorner()));
out.call(()->p(a.lastCorner()));
out.call(()->ps(a.corners()));
out.call(()->qs(a.cornerApproxArr()));
out.call(()->f(a.lengthApprox()));
out.call(()->f(a.lengthApprox(from,to)));
out.call(()->tileText(a.boundingBox()));
out.call(()->tileText(a.boundingBox(from,to)));
out.call(()->tileText(a.boundingOctagon(from,to)));
out.call(()->q(a.nearestPointApprox(fp)));
out.call(()->f(a.distance(fp)));
out.call(()->a.contains(p));
out.call(()->sg(a.projectionLine(p)));
out.call(()->sg(a.projectionLine(null)));
out.call(()->pl(a.reverse()));
out.call(()->pl(a.translateBy(v)));
out.call(()->a.translateBy(v)==a);
out.call(()->pl(a.turn90Degree(factor,pole)));
out.call(()->pl(a.mirrorVertical(pole)));
out.call(()->pl(a.mirrorHorizontal(pole)));
out.call(()->tiles(a.offsetShapes(width)));
out.call(()->tiles(a.offsetShapes(width,from,to)));
out.call(()->tileText(a.offsetShape(width,idx)));
out.call(()->tileText(a.offsetBox(width,idx)));
out.call(()->pl(a.skipLines(from,to)));
out.call(()->a.skipLines(from,to)==a);
out.call(()->pl(a.shorten(from,len)));
out.call(()->a.combine(null)==a);
out.call(()->tileText(a.boundingBox()));
return "ok";});}
case "B" -> {
var pa=poly();var pb=poly();int idx=integer();var line=new Line(point(),point());
out.call(()->{var a=pa.get();var b=pb.get();
out.call(()->pl(a.combine(b)));
out.call(()->a.combine(b)==a);
out.call(()->pl(b.combine(a)));
out.call(()->pls(a.split(idx,line)));
out.call(()->qs(a.cornerApproxArr()));
out.call(()->ps(a.corners()));
return "ok";});}
case "S" -> {
var sa=seg();var sb=seg();var p=point();var t=tile();double len=real();
out.call(()->{var a=sa.get();var b=sb.get();
out.call(()->sg(a));
out.call(()->q(a.startPointApprox()));
out.call(()->q(a.endPointApprox()));
out.call(()->tileText(a.boundingBox()));
out.call(()->tileText(a.boundingOctagon()));
out.call(()->sg(a.changeLengthApprox(len)));
out.call(()->p(a.startPoint()));
out.call(()->p(a.endPoint()));
out.call(()->q(a.startPointApprox()));
out.call(()->q(a.endPointApprox()));
out.call(()->sg(a.changeLengthApprox(len)));
out.call(()->sg(a.opposite()));
out.call(()->pl(a.toPolyline()));
out.call(()->tileText(a.toSimplex()));
out.call(()->a.contains(p));
out.call(()->ls(a.intersection(b)));
out.call(()->ls(b.intersection(a)));
out.call(()->a.intersects(b));
out.call(()->a.overlaps(b));
out.call(()->ints(a.borderIntersections(t)));
out.call(()->t.isIntersectedInteriorBy(a));
out.call(()->sg(a.sortEndpointsInXY()));
out.call(()->a.sortEndpointsInXY()==a);
out.call(()->q(a.sortEndpointsInXY().startPointApprox()));
out.call(()->q(a.sortEndpointsInXY().endPointApprox()));
return "ok";});}
case "W" -> {
var sa=seg();double width=real();boolean right=integer()!=0;
out.call(()->{var a=sa.get();
out.call(()->ps(a.stairApproximation(width,right)));
out.call(()->ps(a.stairApproximation45(width,right)));
out.call(()->p(a.startPoint()));
out.call(()->p(a.endPoint()));
out.call(()->ps(a.stairApproximation(width,right)));
out.call(()->ps(a.stairApproximation45(width,right)));
return "ok";});}
case "C" -> {
var t=tile();var pa=poly();
out.call(()->{var a=pa.get();
out.call(()->pairs(t.entrancePoints(a)));
out.call(()->pls(t.cutout(a)));
out.call(()->t.cutout(a).length==1&&t.cutout(a)[0]==a);
out.call(()->qs(a.cornerApproxArr()));
return "ok";});}
default->throw new IllegalArgumentException(tokens[0]);
    }
    if(index!=tokens.length)throw new AssertionError("unconsumed input: "+row);
    System.out.println(row+" | "+out.result);
    }
  }
}
