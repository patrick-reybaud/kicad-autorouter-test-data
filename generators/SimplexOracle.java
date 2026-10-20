package app.freerouting.geometry.planar;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;

/** Executes the pinned, unmodified Simplex and its actual shape bindings. */
public class SimplexOracle extends RegularShapesOracle {
  static Line[] readLines() {
    int n=integer();var lines=new Line[n];
    for(int i=0;i<n;i++)lines[i]=new Line(point(),point());
    return lines;
  }
  static Simplex simplex() {
    String mode=next();var lines=readLines();
    return mode.equals("N")?Simplex.getInstance(lines):new Simplex(lines);
  }
  static TileShape tile() {
    return switch(next()) {case "B"->readBox();case "O"->readOct();default->simplex();};
  }
  static String tileText(TileShape v) {
    if(v==null)return "null";
    if(v instanceof Simplex s) {
      var j=new StringJoiner(";");j.add("S,"+s.borderLineCount());
      for(int i=0;i<s.borderLineCount();i++)j.add(line(s.borderLine(i)));
      return j.toString();
    }
    return s(v);
  }
  static String tiles(TileShape[] values) {
    if(values==null)return "null";
    var j=new StringJoiner("#");j.add(String.valueOf(values.length));for(var v:values)j.add(tileText(v));return j.toString();
  }
  static String floats(FloatPoint[] values) {
    var j=new StringJoiner(";");j.add(String.valueOf(values.length));for(var v:values)j.add(p(v));return j.toString();
  }
  static class SimplexOut extends ShapeOut {
    @Override void call(Supplier<Object> f) {
      try {super.call(f);}catch(NullPointerException ex){add("null_error");}
    }
  }
  public static void main(String[] args) throws Exception {
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null) {
      tokens=row.split(" ");index=1;var out=new SimplexOut();
      switch(tokens[0]) {
        case "S" -> {
          var a=simplex();var b=simplex();var query=point();var v=vector();int edge=integer();double offset=real();
          out.add(tileText(a));out.add(a.isEmpty());out.add(a.getId());out.add(a.borderLineCount());
          out.call(()->a.cornerIsBounded(edge));out.call(()->a.isBounded());
          out.call(()->p(a.corner(edge)));out.call(()->a.cornerApprox(edge)==null?"null":p(a.cornerApprox(edge)));
          out.call(()->floats(a.cornerApproxArr()));out.call(()->a.borderLine(edge)==null?"null":line(a.borderLine(edge)));
          out.call(()->a.dimension());out.call(()->p(a.centreOfGravity()));out.call(()->f(a.maxWidth()));out.call(()->f(a.minWidth()));
          out.call(()->a.isIntBox());out.call(()->a.isIntOctagon());out.call(()->tileText(a.toIntOctagon()));
          out.call(()->tileText(a.translateBy(v)));out.call(()->tileText(a.boundingBox()));out.call(()->tileText(a.boundingOctagon()));
          out.call(()->tileText(a.offset(offset)));out.call(()->tileText(a.enlarge(offset)));out.call(()->a.indexOfRightMostCorner(query));
          out.call(()->tileText(a.intersection(b)));out.call(()->tileText(((TileShape)a).intersection(b)));out.call(()->a.intersects(b));
          out.call(()->tileText(a.removeBorderLine(edge)));out.call(()->a.removeBorderLine(edge)==a);
          out.call(()->tileText(a.removeRedundantLines()));out.call(()->a.removeRedundantLines()==a);out.call(()->tileText(a.simplify()));
          Line[] lines=new Line[a.borderLineCount()];for(int i=0;i<lines.length;i++)lines[i]=a.borderLine(i);
          out.call(()->tileText(Simplex.getInstance(lines)));
          out.call(()->a.borderLineIndex(a.borderLine(edge)));
          out.call(()->{Line l=a.borderLine(edge);return a.borderLineIndex(new Line(l.a,l.b));});
          out.call(()->new Line(0,0,1,1).isOnTheLeft(a));out.call(()->new Line(0,0,1,1).isOnTheRight(a));
          out.add(a.boundingTile()==a);out.add(a.toSimplex()==a);
          out.call(()->tileText(a.boundingBox()));out.call(()->tileText(a.boundingOctagon()));
        }
        case "B" -> {
          var b=readBox();var o=readOct();
          out.call(()->tileText(b.toSimplex()));out.call(()->b.toSimplex()==b.toSimplex());
          out.call(()->tileText(o.toSimplex()));out.call(()->o.toSimplex()==o.toSimplex());
          var alias=o;out.call(()->alias.toSimplex()==o.toSimplex());
          out.call(()->o.toSimplex()==Simplex.EMPTY);out.call(()->b.toSimplex()==Simplex.EMPTY);
        }
        case "C" -> {
          var a=tile();var b=tile();
          out.call(()->tileText(a.intersection(b)));out.call(()->tileText(b.intersection(a)));out.call(()->a.intersects(b));out.call(()->b.intersects(a));
          out.call(()->tiles(a.cutout(b)));out.call(()->tiles(b.cutout(a)));
          out.call(()->tiles(a.toSimplex().cutoutFrom(b.toSimplex())));
          out.call(()->tileText(a.simplify()));out.call(()->tileText(b.simplify()));
        }
        case "D" -> {
          var a=direction();var b=direction();out.call(()->f(((IntDirection)a).determinant((IntDirection)b)));
        }
        default->throw new IllegalArgumentException(tokens[0]);
      }
      if(index!=tokens.length)throw new AssertionError("unconsumed input: "+row);
      System.out.println(row+" | "+out.result);
    }
  }
}
