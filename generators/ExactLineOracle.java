package app.freerouting.geometry.planar;

import java.io.*;
import java.util.function.Supplier;

/** Calls the actual Line class; cached direction and factory construction are intentionally distinct. */
public class ExactLineOracle extends ExactCoordinatesOracle {
  static Line lineValue() {
    return switch(integer()) {
      case 0 -> new Line(point(),point());
      case 1 -> new Line(integer(),integer(),integer(),integer());
      case 2 -> new Line(point(),direction());
      case 3 -> Line.getInstance(point(),direction());
      default -> throw new IllegalArgumentException("line kind");
    };
  }
  static String l(Line value) {return p(value.a)+";"+p(value.b);}
  static String f32(float value) {return "f"+Integer.toUnsignedString(Float.floatToIntBits(value),16);}
  static class LineOut extends Out {
    @Override void call(Supplier<Object> operation) {
      try {add(operation.get());}
      catch(ClassCastException ex) {add("type_error");}
      catch(ArithmeticException ex) {add("arithmetic_error");}
      catch(IllegalArgumentException ex) {add("invalid_argument");}
    }
  }
  public static void main(String[] args) throws Exception {
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null) {
      tokens=row.split(" ");index=0;var out=new LineOut();
      Line a=lineValue(),b=lineValue(),c=lineValue();Point point=point();FloatPoint floating=fp();Vector v=vector();
      double tolerance=real(),distance=real(),x=real(),y=real();int factor=integer();IntPoint pole=ip();
      IntBox tile=new IntBox(integer(),integer(),integer(),integer());
      Line alias=a,fresh=new Line(a.a,a.b);
      out.add(l(a));out.add(a.getId());out.call(()->d(a.direction()));out.call(()->a.direction().equals(a.direction()));
      out.add(a.equals(a));out.add(a.equals(alias));out.call(()->a.equals(fresh));out.call(()->a.equals(b));
      out.call(()->a.fastEquals(a));out.call(()->a.fastEquals(alias));out.call(()->a.fastEquals(fresh));out.call(()->a.fastEquals(b));
      out.call(()->a.sideOf(point));out.call(()->a.sideOf(floating,tolerance));out.call(()->a.sideOf(floating));
      out.call(()->a.sideOfIntersection(b,c));out.call(()->a.isOnTheLeft(tile));out.call(()->a.isOnTheRight(tile));
      out.call(()->f(a.signedDistance(floating)));out.call(()->a.overlaps(b));out.call(()->l(a.opposite()));
      out.call(()->p(a.intersection(b)));out.call(()->p(a.intersectionApprox(b)));out.call(()->p(a.perpendicularProjection(point)));
      out.call(()->l(a.translate(distance)));out.call(()->d(a.translate(distance).direction()));
      out.call(()->l(a.translateBy(v)));out.call(()->a.equals(a.translateBy(v)));
      out.call(()->a.isOrthogonal());out.call(()->a.isDiagonal());out.call(()->a.isMultipleOf45Degree());
      out.call(()->a.isParallel(b));out.call(()->a.isPerpendicular(b));out.call(()->a.isEqualOrOpposite(b));
      out.call(()->f(a.cosAngle(b)));out.call(()->a.compareTo(b));out.call(()->f(a.functionValueApprox(x)));out.call(()->f(a.functionInYValueApprox(y)));
      out.call(()->d(a.perpendicularDirection(point)));out.call(()->l(a.turn90Degree(factor,pole)));
      out.call(()->l(a.mirrorVertical(pole)));out.call(()->l(a.mirrorHorizontal(pole)));out.call(()->f32(a.length()));
      out.call(()->p(floating.projectionApprox(a)));out.call(()->point.sideOf(a));
      out.call(()->p(point.perpendicularProjection(a)));out.call(()->d(point.perpendicularDirection(a)));
      if(index!=tokens.length)throw new AssertionError("Unconsumed input");
      System.out.println(row+" | "+out.result);
    }
  }
}
