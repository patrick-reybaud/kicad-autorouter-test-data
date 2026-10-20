package app.freerouting.autoroute.maze;

import app.freerouting.autoroute.maze.AutorouteControl.ExpansionCostFactor;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.util.*;

/** Executes the original DestinationDistance, including the cheap-cost save/restore path. */
public class DestinationDistanceOracle {
  static String[] tokens; static int index;
  static int integer() {return Integer.parseInt(tokens[index++]);}
  static double real() {return Double.longBitsToDouble(Long.parseUnsignedLong(tokens[index++],16));}
  static IntBox box() {return new IntBox(integer(),integer(),integer(),integer());}
  static void add(StringJoiner out, double v) {out.add(Long.toUnsignedString(Double.doubleToLongBits(v),16));}
  static void query(StringJoiner out, DestinationDistance d, int n, IntBox b, FloatPoint p) {
    for(int layer=0;layer<n;layer++) {
      add(out,d.calculate(b,layer));add(out,d.calculateCheapDistance(b,layer));
      add(out,d.calculate(b,layer));add(out,d.calculate(p,layer));
    }
  }
  public static void main(String[] args) throws Exception {
    var reader=new BufferedReader(new InputStreamReader(System.in));String line;
    while((line=reader.readLine())!=null) {
      tokens=line.split(" ");index=0;int n=integer();
      var costs=new ExpansionCostFactor[n];var active=new boolean[n];
      for(int i=0;i<n;i++){active[i]=integer()!=0;costs[i]=new ExpansionCostFactor(real(),real());}
      var distance=new DestinationDistance(costs,active,real(),real());
      int joinCount=integer();var boxes=new IntBox[joinCount];var layers=new int[joinCount];
      for(int i=0;i<joinCount;i++){layers[i]=integer();boxes[i]=box();}
      IntBox b=box();FloatPoint p=new FloatPoint(real(),real());var out=new StringJoiner(" ");
      query(out,distance,n,b,p);
      for(int i=0;i<joinCount;i++){distance.join(boxes[i],layers[i]);query(out,distance,n,b,p);}
      System.out.println(line+" | "+out);
    }
  }
}
