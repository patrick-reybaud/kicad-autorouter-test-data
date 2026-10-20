package app.freerouting.board.facade;
import app.freerouting.board.model.items.Item;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Decision hash (D115-3): MD5 of the canonical, registry-resolved neutral description of the board items (ids,
 * geometry, nets, clearance classes, fixed states) and of the id-generator state, without any pre-calculated cache.
 * Canonical form shared with the native benchmark driver and tests/corpus/bench/pipeline.py: objects with keys in
 * ascending code-unit order, no whitespace, integers in decimal, strings with only '"', '\\' and control characters
 * escaped (\\u00xx). Diagnostic only: the engine's own hash (caches included) keeps governing BoardHistory.
 */
public final class DecisionHash extends BoardConstructionOracle {
  static Object resolve(Object index,List<Object> geometry,int depth){
    int i=((Number)index).intValue();if(i==0)return null;if(depth>8)throw new IllegalStateException("Neutral geometry nesting too deep");
    Object value=geometry.get(i-1);
    if(value instanceof List<?> l&&!l.isEmpty()&&"A".equals(l.get(0))){var holes=new ArrayList<Object>();for(Object h:(List<?>)l.get(2))holes.add(resolve(h,geometry,depth+1));var r=new ArrayList<Object>();r.add("A");r.add(resolve(l.get(1),geometry,depth+1));r.add(holes);return r;}
    return value;
  }
  static Object normalize(Object value,List<Object> geometry){
    if(value instanceof Map<?,?> m){var r=new LinkedHashMap<String,Object>();for(var e:m.entrySet()){String k=(String)e.getKey();if(k.equals("area"))r.put(k,resolve(e.getValue(),geometry,0));else if(k.equals("shapes")){var s=new ArrayList<Object>();for(Object i:(List<?>)e.getValue())s.add(resolve(i,geometry,0));r.put(k,s);}else r.put(k,normalize(e.getValue(),geometry));}return r;}
    if(value instanceof List<?> l){var r=new ArrayList<Object>();for(Object e:l)r.add(normalize(e,geometry));return r;}
    return value;
  }
  static void quote(String s,StringBuilder out){
    out.append('"');
    for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"')out.append("\\\"");else if(c=='\\')out.append("\\\\");else if(c<0x20)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}
    out.append('"');
  }
  static void canonical(Object v,StringBuilder out){
    if(v==null){out.append("null");return;}
    if(v instanceof Boolean b){out.append(b?"true":"false");return;}
    if(v instanceof Integer||v instanceof Long||v instanceof Short||v instanceof Byte){out.append(v.toString());return;}
    if(v instanceof Number)throw new IllegalArgumentException("Non-integral number in decision hash: "+v);
    if(v instanceof String s){quote(s,out);return;}
    if(v instanceof int[] a){out.append('[');for(int i=0;i<a.length;i++){if(i>0)out.append(',');out.append(a[i]);}out.append(']');return;}
    if(v instanceof List<?> l){out.append('[');boolean first=true;for(Object e:l){if(!first)out.append(',');first=false;canonical(e,out);}out.append(']');return;}
    if(v instanceof Map<?,?> m){var sorted=new TreeMap<String,Object>();for(var e:m.entrySet())sorted.put((String)e.getKey(),e.getValue());out.append('{');boolean first=true;for(var e:sorted.entrySet()){if(!first)out.append(',');first=false;quote(e.getKey(),out);out.append(':');canonical(e.getValue(),out);}out.append('}');return;}
    throw new IllegalArgumentException("Unsupported value in decision hash: "+v.getClass());
  }
  static String md5(String text)throws Exception{
    var digest=MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));var r=new StringBuilder();for(byte b:digest)r.append(String.format(Locale.ROOT,"%02x",b&0xff));return r.toString();
  }
  /** Canonical text of the decision state; hashed by {@link #of}. */
  static String text(RoutingBoard b)throws Exception{
    var exporter=new NeutralBoardModel();var items=new ArrayList<Item>(b.getItems());items.sort(Comparator.comparingInt(Item::getId));
    var descriptors=new ArrayList<Object>();for(var item:items)descriptors.add(NeutralBoardItemsOracle.describe(exporter,item));
    var root=NeutralBoardModel.obj("items",normalize(descriptors,exporter.geometry),"communication",NeutralBoardItemsOracle.communication(b));
    var out=new StringBuilder();canonical(root,out);return out.toString();
  }
  public static String of(RoutingBoard b)throws Exception{return md5(text(b));}
}
