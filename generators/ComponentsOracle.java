package app.freerouting.board.model.structure;
import app.freerouting.core.library.*;
import app.freerouting.core.library.Package;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.state.BoardObservers;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
public class ComponentsOracle {
 interface Call { Object run(); }
 static class Out { ArrayList<String> values=new ArrayList<>(); void add(Object x){values.add(String.valueOf(x));} void call(Call f){try{add(f.run());}catch(NullPointerException e){add("null_error");}catch(IndexOutOfBoundsException e){add("index_error");}catch(IllegalStateException e){add("callback_error");}} public String toString(){return String.join(" ",values);} }
 static String dec(String s){if(s.equals("N"))return null;if(s.equals("-"))return "";StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i+=4)b.append((char)Integer.parseInt(s.substring(i,i+4),16));return b.toString();}
 static String hex(String s){if(s==null)return "N";if(s.isEmpty())return "-";StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(String.format(Locale.ROOT,"%04x",(int)c));return b.toString();}
 static String bits(double x){return Long.toHexString(Double.doubleToLongBits(x));}
 static Point point(Scanner in){int k=in.nextInt(),x=in.nextInt(),y=in.nextInt(),z=in.nextInt();return k==0?null:k==1?new IntPoint(x,y):Point.getInstance(BigInteger.valueOf(x),BigInteger.valueOf(y),BigInteger.valueOf(z));}
 static app.freerouting.geometry.planar.Vector vector(Scanner in){Point p=point(in);return p==null?null:p.differenceBy(new IntPoint(0,0));}
 static IntPoint pole(Scanner in){int k=in.nextInt(),x=in.nextInt(),y=in.nextInt();return k==0?null:new IntPoint(x,y);}
 static class State {
  IdentityHashMap<Component,Integer> ids=new IdentityHashMap<>();int next=1;
  Package[] packages;LogicalPart[] parts;
  State(){Packages p=new Packages(null);packages=new Package[]{null,p.add(new Package.Pin[0]),p.add(new Package.Pin[0])};LogicalParts l=new LogicalParts();parts=new LogicalPart[]{null,l.add("one",new LogicalPart.PartPin[0]),l.add("two",new LogicalPart.PartPin[0])};}
  String component(Component c){if(c==null)return "N";int serial=ids.computeIfAbsent(c,x->next++);Point p=c.getLocation();String loc=p==null?"N":(p instanceof IntPoint?"I":"R")+":"+bits(p.toFloat().x)+":"+bits(p.toFloat().y);return serial+":"+c.id+":"+hex(c.name)+":"+hex(c.toString())+":"+hex(c.getPartNumber())+":"+c.positionFixed+":"+c.isPlaced()+":"+c.placedOnFront()+":"+bits(c.getRotationInDegree())+":"+loc+":"+(c.getPackage()==null?0:c.getPackage().id)+":"+(c.getLogicalPart()==null?0:c.getLogicalPart().id);}
  String all(Iterable<Component> a){StringBuilder b=new StringBuilder();int n=0;for(Component c:a){b.append(',').append(component(c));n++;}return n+b.toString();}
 }
 static class Watch implements BoardObservers {
  State s;Components c;int fail,mode;String events="";ArrayList<Component> retained=new ArrayList<>();
  Watch(State s,Components c){this.s=s;this.c=c;}
  public void notifyMoved(Component p){events+=s.component(p)+"="+(c.get(p.id)==p)+",";retained.add(p);if(mode==1)p.translateBy(new IntVector(2,-3));if(mode==2)p.setLogicalPart(s.parts[2]);if(p.id==fail)throw new IllegalStateException();}
  public void notifyChanged(Item i){} public void notifyNew(Item i){}public void notifyDeleted(Item i){}public void activate(){}public void deactivate(){}public boolean isActive(){return true;}
 }
 static String run(String line){Scanner in=new Scanner(line);String kind=in.next();Out out=new Out();State s=new State();Components list=new Components();Iterable<Component> live=list.getAll();Watch watch=new Watch(s,list);ArrayList<Component> held=new ArrayList<>();Component direct=null;
  if(kind.equals("C")){String name=dec(in.next());Point p=point(in);double rotation=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));boolean front=in.nextInt()!=0;int f=in.nextInt(),b=in.nextInt(),id=in.nextInt();boolean fixed=in.nextInt()!=0;String part=dec(in.next());direct=new Component(name,p,rotation,front,s.packages[f],s.packages[b],id,fixed,part);out.add(s.component(direct));}
  int n=in.nextInt();
  for(int i=0;i<n;i++){
   String op=in.next();final Component subject=direct;
   if(op.equals("A")){String name=dec(in.next());Point p=point(in);double rotation=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));boolean front=in.nextInt()!=0;int f=in.nextInt(),b=in.nextInt();boolean fixed=in.nextInt()!=0;String part=dec(in.next());out.call(()->s.component(list.add(name,p,rotation,front,s.packages[f],s.packages[b],fixed,part)));}
   else if(op.equals("G")){Point p=point(in);double rotation=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));boolean front=in.nextInt()!=0;int pack=in.nextInt();out.call(()->s.component(list.add(p,rotation,front,s.packages[pack])));}
   else if(op.equals("M")){int id=in.nextInt();var v=vector(in);out.call(()->{if(subject==null)list.move(id,v);else subject.translateBy(v);return "ok";});}
   else if(op.equals("T")){int id=in.nextInt(),factor=in.nextInt();var p=pole(in);out.call(()->{if(subject==null)list.turn90Degree(id,factor,p);else subject.turn90Degree(factor,p);return "ok";});}
   else if(op.equals("O")){int id=in.nextInt();double angle=Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));var p=pole(in);boolean flip=in.nextInt()!=0;out.call(()->{if(subject==null)list.rotate(id,angle,p);else subject.rotate(angle,p,flip);return "ok";});}
   else if(op.equals("B")){int id=in.nextInt();var p=pole(in);out.call(()->{if(subject==null)list.changeSide(id,p);else subject.changeSide(p);return "ok";});}
   else if(op.equals("L")){int id=in.nextInt(),part=in.nextInt();out.call(()->{(subject==null?list.get(id):subject).setLogicalPart(s.parts[part]);return "ok";});}
   else if(op.equals("I")){int id=in.nextInt();out.call(()->s.component(list.get(id)));}
   else if(op.equals("N")){String name=dec(in.next());out.call(()->s.component(list.get(name)));}
   else if(op.equals("S")){list.generateSnapshot();out.add("ok");}
   else if(op.equals("U")||op.equals("R")){int enabled=in.nextInt();watch.fail=in.nextInt();watch.mode=in.nextInt();watch.events="";out.call(()->op.equals("U")?list.undo(enabled==0?null:watch):list.redo(enabled==0?null:watch));out.add(watch.events.isEmpty()?"-":watch.events);}
   else if(op.equals("F")){list.setFlipStyleRotateFirst(in.nextInt()!=0);out.add(list.getFlipStyleRotateFirst());}
   else if(op.equals("H")){int id=in.nextInt(),clone=in.nextInt();out.call(()->{Component c=subject==null?list.get(id):subject;held.add(clone==0?c:c.clone());return s.component(held.get(held.size()-1));});}
   else if(op.equals("Q")){String name=dec(in.next());Component other=new Component(name,null,0,true,null,null,99,false,null);out.call(()->subject.compareTo(other));out.call(()->subject.compareTo(null));out.call(()->subject.compareTo(new Object()));}
   else throw new AssertionError(op);
   out.add(subject==null?s.all(live):s.component(subject));out.add(s.all(held));out.add(s.all(watch.retained));out.add(list.count());out.add(list.getFlipStyleRotateFirst());
  }
  return out.toString();
 }
 public static void main(String[] args)throws Exception {var in=new BufferedReader(new InputStreamReader(System.in));String s;while((s=in.readLine())!=null)System.out.println(s+" | "+run(s));}
}
