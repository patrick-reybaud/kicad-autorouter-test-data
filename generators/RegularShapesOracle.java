package app.freerouting.geometry.planar;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;

/** Actual pinned shape classes, including the RegularTileShape double dispatch. */
public class RegularShapesOracle extends ExactCoordinatesOracle {
  static IntBox readBox() {return new IntBox(integer(),integer(),integer(),integer());}
  static IntOctagon readOct() {
    String mode=next();if(mode.equals("E"))return IntOctagon.EMPTY;
    var o=new IntOctagon(integer(),integer(),integer(),integer(),integer(),integer(),integer(),integer());
    return mode.equals("N")?o.normalize():o;
  }
  static RegularTileShape shape() {return next().equals("B")?readBox():readOct();}
  static String s(TileShape v) {
    if(v==null)return "null";
    if(v instanceof IntBox b)return "B,"+box(b);
    var o=(IntOctagon)v;return "O,"+o.isEmpty()+","+oct(o);
  }
  static String list(TileShape[] values) {
    var j=new StringJoiner(";");j.add(String.valueOf(values.length));for(var v:values)j.add(s(v));return j.toString();
  }
  static String points(IntPoint[] values) {
    var j=new StringJoiner(";");j.add(String.valueOf(values.length));for(var v:values)j.add(p(v));return j.toString();
  }
  static String line(Line value) {return p(value.a)+";"+p(value.b);}
  static class ShapeOut extends Out {
    @Override void call(Supplier<Object> operation) {
      try {add(operation.get());}
      catch(ClassCastException ex) {add("type_error");}
      catch(NegativeArraySizeException ex) {add("negative_size");}
      catch(IndexOutOfBoundsException ex) {add("index_error");}
      catch(ArithmeticException ex) {add("arithmetic_error");}
      catch(IllegalArgumentException ex) {add("invalid_argument");}
    }
  }
  public static void main(String[] args) throws Exception {
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null) {
      tokens=row.split(" ");index=1;var out=new ShapeOut();
      switch(tokens[0]) {
        case "B" -> {
          IntBox a=readBox(),b=readBox();IntPoint p=ip(),pole=ip();FloatPoint f=fp();Vector v=vector();
          int edge=integer(),count=integer(),factor=integer(),width=integer();double distance=real();
          out.add(a.isEmpty());out.add(a.width());out.add(a.height());out.add(f(a.maxWidth()));out.add(f(a.minWidth()));out.add(f(a.area()));out.add(f(a.circumference()));
          out.call(()->p(a.corner(edge)));out.add(a.dimension());out.add(a.containsInside(p));out.add(p(a.nearestPoint(f)));
          out.add(points(a.nearestBorderProjections(p,count)));out.add(f(a.distance(f)));out.add(a.getId());out.add(s(a.boundingBox()));
          out.add(s(a.boundingOctagon()));out.add(s(a.boundingTile()));out.add(a.isBounded());out.add(a.cornerIsBounded(edge));out.add(s(a.simplify()));
          out.add(s(a.union(b)));out.add(s(a.intersection(b)));out.add(a.intersects(b));out.add(a.overlaps(b));out.add(a.isContainedIn(b));out.add(a.isContainedIn(a));out.add(a.containsInInterior(b));
          out.call(()->s(a.translateBy(v)));out.call(()->s(a.turn90Degree(factor,pole)));out.call(()->line(a.borderLine(edge)));out.add(a.borderLineIndex(new Line(0,0,1,1)));
          out.add(s(a.offset(distance)));out.add(s(a.horizontalOffset(distance)));out.add(s(a.verticalOffset(distance)));out.add(s(a.shrink(width)));
          out.call(()->a.compare(b,edge));out.add(s(a.nearestPart(b)));out.add(list(a.cutoutFrom(b)));out.add(list(a.cutout(b)));out.add(s(a.enlarge(distance)));
        }
        case "O" -> {
          IntOctagon a=readOct(),b=readOct();IntBox box=readBox();IntPoint p=ip();FloatPoint f=fp();Vector v=vector();
          int edge=integer(),count=integer(),dir=integer();double distance=real(),tolerance=real();
          out.add(s(a));out.add(a.isEmpty());out.add(a.isIntBox());out.add(a.dimension());out.add(a.getId());
          out.call(()->p(a.corner(edge)));out.call(()->a.cornerX(edge));out.call(()->a.cornerY(edge));out.add(f(a.area()));
          out.call(()->line(a.borderLine(edge)));out.add(a.borderLineIndex(new Line(0,0,1,1)));out.call(()->s(a.translateBy(v)));
          out.add(f(a.maxWidth()));out.add(f(a.minWidth()));out.add(s(a.offset(distance)));out.add(s(a.enlarge(distance)));
          out.add(a.contains(f));out.add(a.contains(p));out.add(s(a.union(b)));out.add(s(a.union(box)));out.add(s(a.intersection(b)));out.add(s(a.intersection(box)));
          out.add(s(a.normalize()));out.add(a.isNormalized());out.add(a.sideOfBorderLine(p.x,p.y,edge));
          out.add(a.isContainedIn(box));out.add(a.isContainedIn(b));out.add(a.intersects(box));out.add(a.intersects(b));out.add(a.overlaps(b));
          out.add(a.leftXValue(p.y));out.add(a.rightXValue(p.y));out.add(a.lowerYValue(p.x));out.add(a.upperYValue(p.x));
          out.call(()->a.compare(b,edge));out.call(()->a.compare(box,edge));out.add(p(a.borderPoint(p,FortyfiveDegreeDirection.values()[dir])));
          out.add(points(a.nearestBorderProjections(p,count)));out.add(a.borderLineSideOf(f,edge,tolerance));
          out.add(s(a.simplify()));out.add(s(a.boundingBox()));out.add(s(a.boundingOctagon()));out.add(s(a.boundingTile()));
          out.add(a.isBounded());out.add(a.cornerIsBounded(edge));out.add(a.borderLineCount());out.add(a.isIntOctagon());
        }
        case "C" -> {
          IntOctagon hole=readOct(),outer=readOct();IntBox box=readBox();
          out.add(list(hole.cutoutFrom(box)));out.add(list(hole.cutoutFrom(outer)));out.add(list(box.cutoutFrom(outer)));
          out.add(list(box.cutout(hole)));out.add(list(outer.cutout(hole)));out.add(list(hole.cutout(box)));
          out.add(list(hole.cutout(hole)));out.add(list(hole.cutoutFrom(hole)));
        }
        case "G" -> {
          RegularTileShape a=shape(),b=shape();int edge=integer();
          out.add(s(a.union(b)));out.add(s(a.intersection(b)));out.add(s(a.intersectionWithSimplify(b)));out.add(a.contains(b));out.add(a.intersects(b));out.call(()->a.compare(b,edge));out.add(s(a.simplify()));
          var line=new Line(0,0,1,1);out.add(line.isOnTheLeft(a));out.add(line.isOnTheRight(a));
        }
        case "S" -> {var a=readBox();double width=real();out.call(()->list(a.divideIntoSections(width)));}
        case "F" -> {
          Point p=point();int n=integer();FloatPoint[] values=new FloatPoint[n];for(int i=0;i<n;i++)values[i]=fp();
          out.add(s(p.surroundingOctagon()));out.add(s(FloatPoint.boundingOctagon(values)));out.add(FloatPoint.boundingOctagon(values).contains(p));
        }
        default -> throw new IllegalArgumentException(tokens[0]);
      }
      if(index!=tokens.length)throw new AssertionError("unconsumed input "+row);
      System.out.println(row+" | "+out.result);
    }
  }
}
