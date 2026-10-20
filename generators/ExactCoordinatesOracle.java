package app.freerouting.geometry.planar;

import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;

/** Exercises the unchanged reference classes, including package-private typed overloads. */
public class ExactCoordinatesOracle {
  static String[] tokens;
  static int index;
  static String next() { return tokens[index++]; }
  static int integer() { return Integer.parseInt(next()); }
  static BigInteger big() { return new BigInteger(next()); }
  static double real() { return Double.longBitsToDouble(Long.parseUnsignedLong(next(), 16)); }
  static IntPoint ip() { return new IntPoint(integer(), integer()); }
  static FloatPoint fp() { return new FloatPoint(real(), real()); }
  static Point point() {
    String kind=next();
    if(kind.equals("I"))return ip();
    return new RationalPoint(big(),big(),big());
  }
  static Vector vector() {
    String kind=next();
    if(kind.equals("I"))return new IntVector(integer(),integer());
    return new RationalVector(big(),big(),big());
  }
  static Direction direction() {
    String kind=next();
    if(kind.equals("I"))return new IntDirection(integer(),integer());
    return new BigIntDirection(big(),big());
  }
  static String f(double value) { return "d"+Long.toUnsignedString(Double.doubleToLongBits(value),16); }
  static String p(FloatPoint v) { return f(v.x)+","+f(v.y); }
  static String p(Point v) {
    if(v==null)return "null";
    if(v instanceof IntPoint i)return "I,"+i.x+","+i.y;
    var r=(RationalPoint)v;return "R,"+r.x+","+r.y+","+r.z;
  }
  static String v(Vector v) {
    if(v instanceof IntVector i)return "I,"+i.x+","+i.y;
    var r=(RationalVector)v;return "R,"+r.x+","+r.y+","+r.z;
  }
  static String d(Direction v) {
    if(v==null)return "null";
    if(v instanceof IntDirection i)return "I,"+i.x+","+i.y;
    var r=(BigIntDirection)v;return "B,"+r.x+","+r.y;
  }
  static String box(IntBox v) { return v.ll.x+","+v.ll.y+","+v.ur.x+","+v.ur.y; }
  static String oct(IntOctagon v) {
    return v.leftX+","+v.bottomY+","+v.rightX+","+v.topY+","+v.upperLeftDiagonalX+","+v.lowerRightDiagonalX+","+v.lowerLeftDiagonalX+","+v.upperRightDiagonalX;
  }
  static String run(Supplier<Object> operation) {
    try {return String.valueOf(operation.get());}
    catch(ArithmeticException ex) {return "arithmetic_error";}
    catch(IllegalArgumentException ex) {return "invalid_argument";}
  }
  static class Out {
    StringJoiner result=new StringJoiner(" ");
    void add(Object value) {result.add(String.valueOf(value));}
    void call(Supplier<Object> operation) {add(run(operation));}
  }
  static int typed(Direction a,Direction b) {
    return b instanceof IntDirection i ? a.compareTo(i) : a.compareTo((BigIntDirection)b);
  }
  public static void main(String[] args) throws Exception {
    var input=new BufferedReader(new InputStreamReader(System.in));String line;
    while((line=input.readLine())!=null) {
      tokens=line.split(" ");index=1;var out=new Out();
      switch(tokens[0]) {
        case "B" -> {
          var a=big();var b=big();
          out.add(a.intValue());out.add(a.hashCode());out.add(f(a.doubleValue()));out.add(a.gcd(b));
          out.call(()->a.mod(b));
        }
        case "C" -> {
          int x=integer(),y=integer();var a=big();var b=big();var z=big();
          out.call(()->p(Point.getInstance(x,y)));out.call(()->v(Vector.getInstance(x,y)));
          out.call(()->p(Point.getInstance(a,b,z)));out.call(()->v(Vector.getInstance(a,b,z)));
          out.call(()->p(new RationalPoint(a,b,z)));out.call(()->v(new RationalVector(a,b,z)));
        }
        case "P" -> {
          Point a=point(),b=point(),c=point();Vector v=vector();
          IntPoint l1=ip(),l2=ip();Line lineValue=new Line(l1,l2);
          IntBox bounds=new IntBox(integer(),integer(),integer(),integer());int factor=integer();
          out.add(p(a));out.add(a.getId());out.add(a.hashCode());out.add(p(a.toFloat()));out.add(a.isInfinite());
          out.add(a.equals(a));out.add(a.equals(b));out.add(a.compareX(b));out.add(a.compareY(b));out.add(a.compareXY(b));
          out.add(box(a.surroundingBox()));out.add(oct(a.surroundingOctagon()));out.add(a.isContainedIn(bounds));
          out.call(()->p(a.translateBy(v)));out.call(()->v(a.differenceBy(b)));out.call(()->a.sideOf(b,c));
          out.call(()->p(a.perpendicularProjection(lineValue)));out.call(()->d(a.perpendicularDirection(lineValue)));
          out.call(()->p(a.turn90Degree(factor,b)));out.call(()->p(a.mirrorVertical(b)));out.call(()->p(a.mirrorHorizontal(b)));
          out.call(()->d(Direction.getInstance(a,b)));out.call(()->d(Direction.getInstance(a,a)));
        }
        case "V" -> {
          Vector a=vector(),b=vector();Point pt=point();int factor=integer();double length=real();
          out.add(v(a));out.add(a.equals(a));out.add(a.equals(b));out.add(a.isZero());out.add(a.isOrthogonal());
          out.add(a.isDiagonal());out.add(a.isMultipleOf45Degree());out.add(p(a.toFloat()));
          out.add(f(a.lengthApprox()));out.add(f(a.scalarProduct(b)));out.add(f(a.cosAngle(b)));
          out.add(a.sideOf(b));out.add(a.projection(b));out.add(v(a.negate()));out.add(v(a.add(b)));
          out.add(v(a.turn90Degree(factor)));out.add(v(a.mirrorAtXAxis()));out.add(v(a.mirrorAtYAxis()));
          out.call(()->v(a.changeLengthApprox(length)));out.call(()->d(a.toNormalizedDirection()));
          out.call(()->p(pt instanceof IntPoint i ? a.addTo(i) : a.addTo((RationalPoint)pt)));
        }
        case "D" -> {
          Direction a=direction(),b=direction(),c=direction();int factor=integer();FloatPoint point=fp();
          Direction alias=a;
          Direction fresh=a instanceof IntDirection i?new IntDirection(i.x,i.y):new BigIntDirection(((BigIntDirection)a).x,((BigIntDirection)a).y);
          out.add(d(a));out.add(v(a.getVector()));out.add(a.isOrthogonal());out.add(a.isDiagonal());out.add(a.isMultipleOf45Degree());
          out.add(a.equals(a));out.add(a.equals(alias));out.add(a.equals(fresh));out.add(a.equals(b));
          out.add(a.sideOf(b));out.add(a.projection(b));out.add(a.compareTo(b));out.add(typed(a,b));out.add(a.compareFrom(b,c));
          out.add(d(a.opposite()));out.add(d(a.turn45Degree(factor)));out.add(a.equals(a.turn45Degree(factor)));
          out.add(d(a.middleApprox(b)));out.add(a.toString());out.add(p(point.roundToTheRight(a)));out.add(p(point.roundToTheLeft(a)));
          var constant=FortyfiveDegreeDirection.values()[Math.floorMod(factor,8)];
          out.add(d((Direction)constant.getClass().getMethod("getDirection").invoke(constant)));
        }
        case "I" -> {
          IntPoint a=ip(),b=ip(),c=ip();boolean left=integer()!=0;
          out.add(a.determinant(b));out.add(f(a.signedArea(b,c)));out.add(f(a.distanceSquare(b)));out.add(f(a.distance(b)));
          out.add(p(a.orthogonalProjection(b)));out.add(p(a.fortyfiveDegreeProjection(b)));
          out.add(p(a.fortyfiveDegreeCorner(b,left)));out.add(p(a.ninetyDegreeCorner(b,left)));
        }
        default -> throw new IllegalArgumentException(tokens[0]);
      }
      if(index!=tokens.length)throw new AssertionError("Unconsumed input "+line);
      System.out.println(line+" | "+out.result);
    }
  }
}
