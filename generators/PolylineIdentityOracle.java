package app.freerouting.geometry.planar;

import com.google.gson.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Bench only: observe source point identities; never emulate forced insertion. */
public final class PolylineIdentityOracle {
  static final Gson JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
  static List<Object> list(Object... a) { return new ArrayList<>(Arrays.asList(a)); }
  static Map<String,Object> obj(Object... a) { var r=new LinkedHashMap<String,Object>();for(int i=0;i<a.length;i+=2)r.put((String)a[i],a[i+1]);return r; }
  static String error(Throwable t) {
    if(t instanceof ClassCastException)return "cast_error";
    if(t instanceof NullPointerException)return "null_error";
    if(t instanceof IndexOutOfBoundsException)return "index_error";
    if(t instanceof IllegalArgumentException)return "argument_error";
    if(t instanceof NegativeArraySizeException)return "length_error";
    return t.getClass().getName();
  }
  static Object xy(Point p) {
    if(p==null)return null;
    if(p instanceof IntPoint q)return list("I",q.x,q.y);
    var q=(RationalPoint)p;return list("R",q.x.toString(),q.y.toString(),q.z.toString());
  }
  static Point copy(Point p) {
    if(p==null)return null;
    if(p instanceof IntPoint q)return new IntPoint(q.x,q.y);
    var q=(RationalPoint)p;return new RationalPoint(q.x,q.y,q.z);
  }
  static Point point(JsonElement j) {
    var a=j.getAsJsonArray();if(a.get(0).getAsString().equals("I"))return new IntPoint(a.get(1).getAsInt(),a.get(2).getAsInt());
    return new RationalPoint(new BigInteger(a.get(1).getAsString()),new BigInteger(a.get(2).getAsString()),new BigInteger(a.get(3).getAsString()));
  }
  static final class Pool {
    final ArrayList<Object> values=new ArrayList<>();
    int id(Object p){if(p==null)return 0;for(int i=0;i<values.size();i++)if(values.get(i)==p)return i+1;values.add(p);return values.size();}
  }
  static Object cache(Polyline p,Pool refs)throws Exception {
    Field field=Polyline.class.getDeclaredField("precalculatedCorners");field.setAccessible(true);
    Point[] corners=(Point[])field.get(p);if(corners==null)return null;
    var out=new ArrayList<Object>();for(Point corner:corners)out.add(list(refs.id(corner),xy(corner)));return out;
  }
  static Object run(JsonObject input)throws Exception {
    var refs=new Pool();var polyIds=new Pool();var points=new ArrayList<Point>();
    for(var p:input.getAsJsonArray("points")){var q=point(p);points.add(q);refs.id(q);}
    Point[] selected=new Point[input.getAsJsonArray("indices").size()];int index=0;
    for(var i:input.getAsJsonArray("indices"))selected[index++]=points.get(i.getAsInt());
    Polyline p;
    try {
      p=switch(input.get("mode").getAsString()) {
        case "segment" -> new Polyline(selected[0],selected[1]);
        case "polygon" -> new Polyline(new Polygon(selected));
        case "lines" -> {Line[] lines=new Line[selected.length/2];for(int i=0;i<lines.length;i++)lines[i]=new Line(selected[2*i],selected[2*i+1]);yield new Polyline(lines);}
        default -> new Polyline(selected);
      };
    }catch(Throwable t){return obj("error",error(t));}
    polyIds.id(p);var result=new ArrayList<Object>();result.add(list("initial",p.lines.length,p.cornerCount(),cache(p,refs)));
    for(int i:new int[]{-2,0,1,999,-1}){
      try {Point q=p.corner(i);result.add(list("corner",i,refs.id(q),xy(q),cache(p,refs)));}
      catch(Throwable t){result.add(list("corner",i,error(t),cache(p,refs)));}
    }
    Point[] saved=p.corners();var savedIds=new ArrayList<Object>();for(Point q:saved)savedIds.add(list(refs.id(q),xy(q)));
    result.add(list("all",savedIds,cache(p,refs)));
    var first=p.firstCorner();var last=p.lastCorner();var duplicate=copy(last);
    result.add(list("endpoints",refs.id(first),refs.id(last),refs.id(duplicate),first==last,last==duplicate,last==null?duplicate==null:last.equals(duplicate)));
    for(String operation:new String[]{"alias","translateZero","rotateZero","turnZero","reverse","skipInvalid","translate","reconstruct"}){
      try {
        Polyline q=switch(operation){
          case "alias" -> p;
          case "translateZero" -> p.translateBy(Vector.ZERO);
          case "rotateZero" -> p.rotateApprox(0,null);
          case "turnZero" -> p.turn90Degree(0,new IntPoint(0,0));
          case "reverse" -> p.reverse();
          case "skipInvalid" -> p.skipLines(-1,0);
          case "translate" -> p.translateBy(new IntVector(3,-5));
          default -> new Polyline(p.lines.clone());
        };
        var before=cache(q,refs);Point a=q.firstCorner(),b=q.lastCorner();
        var row=obj("op",operation,"polyline",polyIds.id(q),"samePolyline",q==p,"cacheBefore",before,"first",list(refs.id(a),xy(a)),"last",list(refs.id(b),xy(b)),"sameFirst",a==first,"sameLast",b==last,"sameReverseFirst",a==last,"cacheAfter",cache(q,refs));
        result.add(row);
      }catch(Throwable t){result.add(obj("op",operation,"error",error(t)));}
    }
    var retained=new ArrayList<Object>();for(Point q:saved)retained.add(list(refs.id(q),xy(q)));
    result.add(list("retained",retained,cache(p,refs)));
    return obj("error",null,"states",result);
  }
  public static void main(String[] args)throws Exception {
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();var rows=new ArrayList<Object>();
    for(var c:input){Object expected;try{expected=run(c.getAsJsonObject());}catch(Throwable t){expected=obj("error",error(t));}rows.add(obj("input",c,"expected",expected));}
    Files.writeString(Path.of(args[1]),JSON.toJson(rows)+"\n");
  }
}
