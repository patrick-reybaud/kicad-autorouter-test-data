package app.freerouting.autoroute.maze;

import app.freerouting.geometry.planar.*;
import app.freerouting.autoroute.expansion.*;
import java.io.*;
import java.util.*;

/** Executes actual source geometry and queue ordering; no rewritten expected algorithm. */
public class TrigDecisionOracle {
  static String bits(double x) { return Long.toHexString(Double.doubleToLongBits(x)); }
  static double real(String s) { return Double.longBitsToDouble(Long.parseUnsignedLong(s,16)); }
  static String point(FloatPoint p) { return bits(p.x)+":"+bits(p.y); }
  static String corners(TileShape s) {
    StringJoiner j=new StringJoiner("/");
    for(int i=0;i<s.borderLineCount();i++)j.add(point(s.corner(i).toFloat()));
    return j.toString();
  }
  static String corners(Polyline s) {
    StringJoiner j=new StringJoiner("/");
    for(int i=0;i<s.cornerCount();i++)j.add(point(s.corner(i).toFloat()));
    return j.toString();
  }
  static String signs(FloatPoint p, int k) {
    return p.sideOf(new FloatPoint(k+0.5,-1000000),new FloatPoint(k+0.5,1000000)).toString();
  }
  static String geometry(String[] t) {
    double angle=real(t[2]); int radius=Integer.parseInt(t[3]),k=Integer.parseInt(t[4]);
    FloatPoint pole=new FloatPoint(real(t[5]),real(t[6]));
    FloatPoint original=new FloatPoint(pole.x+radius,pole.y);
    FloatPoint p=original.rotate(angle,pole);
    // Real copper shape classes: a pad's octagonal bound, board rectangle and fixed wire.
    TileShape pad=new Circle(new IntPoint(0,0),radius).boundingOctagon();
    TileShape outline=new IntBox(150000,-470000,500000,-150000);
    Polyline wire=new Polyline(new Point[]{new IntPoint(200000,-200000),new IntPoint(240000,-200000)});
    // Prime and observe actual reference caches and identities before/after rotation.
    FloatPoint[] wireCache=wire.cornerApproxArr(); IntBox wireBox=wire.boundingBox();
    TileShape rp=pad.rotateApprox(angle,pole), ro=outline.rotateApprox(angle,pole);
    Polyline rw=wire.rotateApprox(angle,pole);
    Direction dir=Direction.getInstanceApprox(angle);
    app.freerouting.geometry.planar.Vector v=p.round().differenceBy(pole.round());
    app.freerouting.geometry.planar.Vector w=rw.lastCorner().differenceBy(rw.firstCorner());
    double va=v.angleApprox(),pair=v.angleApprox(w),da=dir.angleApprox();
    Direction vd=Direction.getInstance(v),wd=Direction.getInstance(w);
    var room=new CompleteFreeSpaceExpansionRoom(rp,0,1);
    var next1=new CompleteFreeSpaceExpansionRoom(new IntBox(-1000000,-1000000,1000000,1000000),0,2);
    var next2=new CompleteFreeSpaceExpansionRoom(new IntBox(-1000000,-1000000,1000000,1000000),0,3);
    var door1=new ExpansionDoor(room,next1);var door2=new ExpansionDoor(room,next2);
    // Equal geometric candidates around the selected quantization boundary. Distances and
    // sorting are source operations; this is a local queue probe, not a full route.
    FloatPoint q=p.round().toFloat();
    double cost1=q.distance(new FloatPoint(k,Math.round(pole.y))),cost2=q.distance(new FloatPoint(k+1,Math.round(pole.y)));
    var e1=new MazeListElement(door1,0,null,0,cost1,cost1,next1,new FloatLine(q,q),false,MazeSearchElement.Adjustment.NONE,false);
    var e2=new MazeListElement(door2,0,null,0,cost2,cost2,next2,new FloatLine(q,q),false,MazeSearchElement.Adjustment.NONE,false);
    var queue=new TreeSet<MazeListElement>();queue.add(e1);queue.add(e2);
    StringJoiner out=new StringJoiner(" ");
    out.add("raw="+point(p));
    out.add("round="+point(q));
    out.add("side="+signs(p,k));
    out.add("point_identity="+(p==original));
    out.add("pad_corners="+corners(rp));
    out.add("outline_corners="+corners(ro));
    out.add("wire_corners="+corners(rw));
    out.add("corner_equality="+rp.corner(0).equals(p.round()));
    out.add("pad_corner_index="+rp.equalsCorner(p.round()));
    out.add("pad_dimension="+rp.dimension());
    out.add("cache_identity="+(wireCache==wire.cornerApproxArr())+":"+(wireBox==wire.boundingBox())+":"+(wireCache==rw.cornerApproxArr())+":"+(rw.cornerApproxArr()==rw.cornerApproxArr())+":"+(rw.boundingBox()==rw.boundingBox()));
    out.add("object_identity="+(rp==pad)+":"+(ro==outline)+":"+(rw==wire));
    out.add("direction="+point(dir.getVector().toFloat()));
    out.add("vector_angle="+bits(va));out.add("vector_pair_angle="+bits(pair));out.add("direction_angle="+bits(da));
    out.add("vector_side="+v.sideOf(w));
    out.add("direction_sort="+Integer.signum(dir.compareTo(vd))+":"+Integer.signum(vd.compareTo(wd)));
    out.add("angle_sort="+Integer.signum(Double.compare(va,da))+":"+Integer.signum(Double.compare(pair,0)));
    out.add("door_order="+Integer.signum(e1.compareTo(e2)));
    out.add("door_choice="+(queue.first()==e1?1:2));
    return out.toString();
  }
  static volatile double sink;
  public static void main(String[] args)throws Exception {
    // Reach warmed reference methods before collecting fields. Calls keep runtime input.
    for(int i=0;i<50000;i++){double a=(i-25000)*0.000123;sink=Math.sin(a)+Math.cos(a)+StrictMath.sin(a)+NativeTrig.sin(a);}
    var input=new BufferedReader(new InputStreamReader(System.in));String line;
    while((line=input.readLine())!=null){String[] t=line.split(" ");
      if(t[0].equals("T")){
        double a=real(t[2]);System.out.println(line+" | "+bits(Math.sin(a))+" "+bits(Math.cos(a))+" "+bits(StrictMath.sin(a))+" "+bits(StrictMath.cos(a))+" "+bits(NativeTrig.sin(a))+" "+bits(NativeTrig.cos(a)));
      } else if(t[0].equals("R")) {
        FloatPoint p=new FloatPoint(real(t[1]),real(t[2]));
        FloatPoint pole=new FloatPoint(real(t[3]),real(t[4]));
        FloatPoint result=p.rotate(real(t[5]),pole);
        System.out.println(line+" | d"+bits(result.x)+" d"+bits(result.y));
      } else System.out.println(line+" | "+geometry(t));
    }
  }
}
