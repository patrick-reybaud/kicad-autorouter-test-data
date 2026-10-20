package app.freerouting.geometry.planar;
import java.io.*;
/** Actual reference angles and winding, plus the Math.acos runtime operation they use. */
public class AngleOracle extends ExactCoordinatesOracle {
  static Point[] points(){int n=integer();Point[] p=new Point[n];for(int i=0;i<n;i++)p[i]=point();return p;}
  static String ps(Point[] a){String s=""+a.length;for(Point p:a)s+="#"+p(p);return s;}
  public static void main(String[] args)throws Exception{
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null){tokens=row.split(" ");index=1;var out=new Out();
      switch(tokens[0]){
      case "A" -> {double x=real();out.add(f(Math.acos(x)));out.add(f(StrictMath.acos(x)));}
      case "V" -> {var a=vector();var b=vector();out.add(f(a.angleApprox(b)));out.add(f(b.angleApprox(a)));out.add(f(a.angleApprox(a)));out.add(f(a.angleApprox()));out.add(f(b.angleApprox()));}
      case "D" -> {var a=direction();out.add(f(a.angleApprox()));}
      case "P" -> {var a=new Polygon(points());out.add(ps(a.cornerArray()));out.add(a.windingNumberAfterClosing());var b=a.revertCorners();out.add(ps(b.cornerArray()));out.add(b.windingNumberAfterClosing());out.add(new Polygon(a.cornerArray()).windingNumberAfterClosing());}
      default -> throw new IllegalArgumentException(tokens[0]);
      }
      if(index!=tokens.length)throw new AssertionError("Unconsumed input: "+row);
      System.out.println(row+" | "+out.result);
    }
  }
}
