package app.freerouting.geometry.planar;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Actual pinned shapes; output is never derived from the native candidate. */
public class RotationModelOracle {
  static volatile double sink;
  static String bits(double x) {return Long.toHexString(Double.doubleToLongBits(x));}
  static String point(FloatPoint p) {return bits(p.x)+":"+bits(p.y);}
  static String shape(PolylineShape s) {
    StringJoiner j=new StringJoiner("/");
    for(int i=0;i<s.borderLineCount();i++)j.add(point(s.corner(i).toFloat()));
    return s.getClass().getSimpleName()+":"+s.dimension()+":"+j;
  }
  static boolean cache(PolylineArea a)throws Exception {
    var f=PolylineArea.class.getDeclaredField("precalculatedConvexPieces");f.setAccessible(true);return f.get(a)!=null;
  }
  static String run(String input)throws Exception {
    var t=input.split(" "); int kind=Integer.parseInt(t[0]),p=Integer.parseInt(t[2]);
    double a=Double.longBitsToDouble(Long.parseUnsignedLong(t[1],16));
    FloatPoint pole=p==2?null:p==0?new FloatPoint(0,0):new FloatPoint(.5,-.5);
    if(kind==0){Circle s=new Circle(new IntPoint(5000,37),127),r=s.rotateApprox(a,pole);return "circle="+point(r.center.toFloat())+":"+r.radius+":"+(s==r);}
    if(kind==1){PolylineShape s=new PolygonShape(new Point[]{new IntPoint(0,0),new IntPoint(100,0),new IntPoint(100,40),new IntPoint(20,40),new IntPoint(20,100),new IntPoint(0,100)}),r=s.rotateApprox(a,pole);return "shape="+shape(r)+":"+(s==r);}
    if(kind>=2&&kind<=5){PolylineShape border=kind==3?null:new IntBox(-100,-100,100,100);PolylineShape hole=kind==5?null:new IntBox(-10,-10,10,10);PolylineArea s=new PolylineArea(border,kind==4?null:new PolylineShape[]{hole});if(kind==2)s.splitToConvex();var r=s.rotateApprox(a,pole);return "area="+shape(r.getBorder())+";"+shape(r.getHoles()[0])+":"+(s==r)+":"+(s.getBorder()==r.getBorder())+":"+(s.getHoles()==r.getHoles())+":"+(s.getHoles()[0]==r.getHoles()[0])+":"+cache(s)+":"+cache(r);}
    if(kind>=6&&kind<=8){TileShape s=kind==6?new IntBox(0,0,100,0):kind==7?TileShape.getInstance(new IntPoint(37,-17)):Simplex.EMPTY;TileShape r=s.rotateApprox(a,pole);return "shape="+shape(r)+":"+(s==r);}
    if(kind==9||kind==10){Polyline s=new Polyline(kind==9?new Point[]{}:new Point[]{new IntPoint(37,-17)});var r=s.rotateApprox(a,pole);StringJoiner j=new StringJoiner("/");for(int i=0;i<r.cornerCount();i++)j.add(point(r.corner(i).toFloat()));return "wire="+r.lines.length+":"+j+":"+(s==r);}
    FloatPoint s=new FloatPoint(5000,37),r=s.rotate(a,pole);return "point="+point(r)+":"+(s==r);
  }
  public static void main(String[] args)throws Exception {
    for(int i=0;i<50000;i++)sink=Math.sin(i*.000123)+Math.cos(i*.000123);
    try(var out=Files.newBufferedWriter(Path.of(args[1]))) {for(String line:Files.readAllLines(Path.of(args[0]))) {String result;try{result=run(line);}catch(Exception e){result="exception="+e.getClass().getSimpleName();}out.write(line+" | "+result+"\n");}}
  }
}
