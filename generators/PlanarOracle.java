package app.freerouting.geometry.planar;

import app.freerouting.datastructures.BigIntAux;
import app.freerouting.datastructures.Signum;
import java.io.*;
import java.math.BigInteger;
import java.util.*;

/** Calls the actual pinned reference classes; no algorithm is reimplemented here. */
public class PlanarOracle {
  static String[] tokens;
  static int index;
  static String next() { return tokens[index++]; }
  static int integer() { return Integer.parseInt(next()); }
  static double real() { return Double.longBitsToDouble(Long.parseUnsignedLong(next(), 16)); }
  static FloatPoint point() { return new FloatPoint(real(), real()); }
  static IntBox box() { return new IntBox(integer(), integer(), integer(), integer()); }
  static String d(double value) { return "d" + Long.toUnsignedString(Double.doubleToLongBits(value),16); }
  static String p(FloatPoint v) { return v==null ? "null" : d(v.x)+" "+d(v.y); }
  static String p(IntPoint v) { return v.x+" "+v.y; }
  static String l(FloatLine v) { return v==null ? "null" : p(v.a)+" "+p(v.b); }
  static String b(IntBox v) { return p(v.ll)+" "+p(v.ur); }
  static String all(Object... values) {
    var result = new StringJoiner(" ");
    for (Object value : values) result.add(String.valueOf(value));
    return result.toString();
  }
  public static void main(String[] args) throws Exception {
    var input = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line=input.readLine())!=null) {
      tokens=line.split(" "); index=1;
      String output;
      switch(tokens[0]) {
        case "N" -> {
          double a=real(), b=real();
          output=all(Signum.of(a),Signum.asInt(a),Signum.of(a).negate(),Side.of(a),Side.of(a).negate(),
            d(Math.min(a,b)),d(Math.max(a,b)),(int)a,Math.round(a),(int)Math.round(a),d(Math.rint(a)));
        }
        case "G" -> output=String.valueOf(BigIntAux.binaryGcd(integer(),integer()));
        case "B" -> {
          var a=new BigInteger[]{new BigInteger(next()),new BigInteger(next()),new BigInteger(next())};
          var b=new BigInteger[]{new BigInteger(next()),new BigInteger(next()),new BigInteger(next())};
          var c=BigIntAux.addRationalCoordinates(a,b);
          output=all(c[0],c[1],c[2],BigIntAux.determinant(a[0],a[1],b[0],b[1]));
        }
        case "X" -> {
          IntBox a=box(), b=box(); double h=real(),v=real();
          output=all(b(a.union(b)),d(a.weightedDistance(b,h,v)),a.isEmpty(),b(IntBox.EMPTY),b(IntBox.EMPTY.union(a)));
        }
        case "P" -> {
          FloatPoint a=point(), b=point(), c=point(), e=point();
          double h=real(),v=real(); int n=integer(),g=integer(),g2=integer();
          output=all(d(a.sizeSquare()),d(a.size()),d(a.distanceSquare(b)),d(a.distance(b)),
            d(a.weightedDistance(b,h,v)),p(a.round()),p(a.roundToGrid(g,g2)),p(a.add(b)),p(a.subtract(b)),
            d(a.scalarProduct(b,c)),d(a.scalarProduct(null,c)),p(a.changeSize(h)),p(a.changeLength(b,h)),
            p(a.middlePoint(b)),p(a.middlePoint(a)),a.sideOf(b,c),p(a.turn90Degree(n)),p(a.turn90Degree(n,b)),
            a.isContainedInBox(b,c,v),b(a.boundingBox()),p(a.circleCenter(b,c)),a.insideCircle(b,c,e));
        }
        case "T" -> {
          FloatPoint a=point(), b=point(); double radius=real();
          var tangent=a.tangentialPoints(b,radius); var s=new StringJoiner(" ");
          s.add(String.valueOf(tangent.length));for(var p:tangent)s.add(p(p));
          output=all(s,p(a.leftTangentialPoint(b,radius)),p(a.rightTangentialPoint(b,radius)),
            p(a.leftTangentialPoint(null,radius)),p(a.rightTangentialPoint(null,radius)));
        }
        case "R" -> { FloatPoint a=point(),pole=point(); double angle=real(); output=p(a.rotate(angle,pole)); }
        case "L" -> {
          FloatLine a=new FloatLine(point(),point()), b=new FloatLine(point(),point());
          FloatPoint p=point(); double dist=real(); int count=integer();
          var parts=new StringJoiner(" ");
          try { var sections=a.divideSegmentIntoSections(count); parts.add(String.valueOf(sections.length));
            for(var segment:sections)parts.add(l(segment)); }
          catch(NegativeArraySizeException ex) {parts.add("negative_count");}
          output=all(l(a.opposite()),l(a.adjustDirection(b)),p(a.intersection(b)),l(a.translate(dist)),
            d(a.signedDistance(p)),p(a.perpendicularProjection(p)),d(a.segmentDistance(p)),
            l(a.segmentProjection(b)),l(a.segmentProjection2(b)),l(a.shrinkSegment(dist)),
            p(a.nearestSegmentPoint(p)),parts);
        }
        default -> throw new IllegalArgumentException(tokens[0]);
      }
      System.out.println(line+" | "+output);
    }
  }
}
