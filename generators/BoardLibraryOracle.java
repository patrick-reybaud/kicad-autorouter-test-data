package app.freerouting.core.library;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.rules.*;
import app.freerouting.datastructures.UndoableObjects;
import java.io.*;
import java.util.*;
public class BoardLibraryOracle {
 interface Call { Object run(); }
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}catch(IllegalArgumentException e){return "sort_error";}catch(IllegalStateException e){return "callback_error";}}
 static String dec(String s){if(s.equals("N"))return null;if(s.equals("-"))return "";StringBuilder b=new StringBuilder();for(int i=0;i<s.length();i+=4)b.append((char)Integer.parseInt(s.substring(i,i+4),16));return b.toString();}
 static String hex(String s){if(s==null)return "N";if(s.isEmpty())return "-";StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(String.format(Locale.ROOT,"%04x",(int)c));return b.toString();}
 static class Out {ArrayList<String> values=new ArrayList<>();void add(Object o){values.add(String.valueOf(o));}void run(Call f){add(call(f));}public String toString(){return String.join(" ",values);}}
 static int pid(Package p){return p==null?0:p.id;}static int lid(LogicalPart p){return p==null?0:p.id;}static int did(Padstack p){return p==null?0:p.id;}
 static String pin(Package.Pin p){return p==null?"N":hex(p.name)+","+p.padstackId+","+(p.relativeLocation==null?"N":p.relativeLocation.toFloat().x+":"+p.relativeLocation.toFloat().y)+","+Long.toHexString(Double.doubleToRawLongBits(p.rotationInDegree));}
 static Package.Pin readPin(Scanner in){int kind=in.nextInt();if(kind==0)return null;String name=dec(in.next());int id=in.nextInt(),x=in.nextInt(),y=in.nextInt();long bits=Long.parseUnsignedLong(in.next(),16);return new Package.Pin(name,id,kind==1?null:new IntVector(x,y),Double.longBitsToDouble(bits));}
 static Package.Pin[] pins(Scanner in){int n=in.nextInt();if(n<0)return null;Package.Pin[] p=new Package.Pin[n];for(int i=0;i<n;i++)p[i]=readPin(in);return p;}
 static String events;
 static class PartPin extends LogicalPart.PartPin {
  int serial;PartPin(int id,int key,String name){super(key,name,"gate",id,"gatepin",-id);serial=id;}
  public int compareTo(LogicalPart.PartPin other){events+=serial+":"+(other==null?0:((PartPin)other).serial)+",";return super.compareTo(other);}
 }
 static String order(LogicalPart.PartPin[] p){if(p==null)return "N";String s=""+p.length;for(var pin:p)s+=","+(pin==null?0:((PartPin)pin).serial);return s;}
 static String partpin(LogicalPart.PartPin p){return p==null?"N":p.pinIndex+","+hex(p.pinName)+","+hex(p.gateName)+","+p.gateSwapCode+","+hex(p.gatePinName)+","+p.gatePinSwapCode;}
 static String pads(Padstack[] ps){String s=""+ps.length;for(var p:ps)s+=","+did(p);return s;}
 static int failure;
 static class ProbeVia extends Via {
  final int serial; final Padstack supplied;
  ProbeVia(int serial,int id,Padstack pad){super(pad,new IntPoint(0,0),new int[0],0,id,0,FixedState.UNFIXED,false,null);this.serial=serial;this.supplied=pad;}
  public Padstack getPadstack(){events+=serial+",";if(failure==serial)throw new IllegalStateException();return supplied;}
 }
 static class NotItem implements UndoableObjects.Storable { public Object clone(){return this;}public int compareTo(Object other){return other==this?0:1;} }
 static String run(String line)throws Exception {
  Scanner in=new Scanner(line);String kind=in.next();Out out=new Out();
  if(kind.equals("P")){
   Packages lib=new Packages(null);int n=in.nextInt();ArrayList<Package.Pin[]> arrays=new ArrayList<>();
   for(int i=0;i<n;i++){
    String name=dec(in.next());boolean front=in.nextInt()!=0;int mode=in.nextInt();var ps=pins(in);arrays.add(ps);
    Shape[] outline=new Shape[]{new IntBox(1,2,3,4),null};double[] widths={-0.0,Double.NaN};boolean[] closed={true,false};var ko=new Package.Keepout[]{new Package.Keepout("K",new Circle(new IntPoint(0,0),2),3),null};
    Package p=mode==0?lib.add(name,ps,outline,widths,closed,ko,ko,ko,front):lib.add(ps);
    out.add(pid(p));out.add(hex(p.toString()));out.add(p.isFront);out.add(mode==0?p.outline==outline:p.outline==null);out.add(mode==0?p.outlineWidths==widths:p.outlineWidths==null);out.add(mode==0?p.outlineIsClosed==closed:p.outlineIsClosed==null);out.add(mode==0?p.keepouts==ko:p.keepouts.length==0);out.add(mode==0?p.viaKeepouts==ko:p.viaKeepouts.length==0);out.add(mode==0?p.placeKeepoutArr==ko:p.placeKeepoutArr.length==0);
    if(mode==0){outline[0]=outline[1];widths[0]=17;closed[0]=false;ko[0]=null;out.add(p.outline[0]==null);out.add(p.outlineWidths[0]==17);out.add(!p.outlineIsClosed[0]);out.add(p.keepouts[0]==null);}
   }
   int q=in.nextInt();ArrayList<String> names=new ArrayList<>();for(int i=0;i<q;i++){String name=dec(in.next());names.add(name);out.run(()->pid(lib.get(name,false)));out.run(()->pid(lib.get(name,true)));}
   for(int i=-1;i<=n+1;i++){int id=i;out.run(()->pid(lib.get(id)));}
   for(int i=0;i<n;i++){Package p=lib.get(i+1);out.run(p::pinCount);for(int j:new int[]{-1,0,1,2,20})out.run(()->pin(p.getPin(j)));for(String name:names)out.run(()->p.getPinIndex(name));for(int j=0;j<n;j++){Package other=lib.get(j+1);out.run(()->p.compareTo(other));}var ps=arrays.get(i);if(ps!=null&&ps.length>0){ps[0]=new Package.Pin("replacement",777,null,-0.0);out.run(()->pin(p.getPin(0)));}}
   out.add(lib.count());
  }else if(kind.equals("L")){
   String name=dec(in.next());int n=in.nextInt();LogicalPart.PartPin[] ps=n<0?null:new LogicalPart.PartPin[n];for(int i=0;i<n;i++){int present=in.nextInt();if(present!=0)ps[i]=new PartPin(i+1,in.nextInt(),dec(in.next()));}
   LogicalParts lib=new LogicalParts();events="";out.run(()->lid(lib.add(name,ps)));out.add(events.isEmpty()?"-":events);out.add(order(ps));out.add(lib.count());
   for(int id:new int[]{Integer.MIN_VALUE,-1,0,1,2,Integer.MAX_VALUE})out.run(()->lid(lib.get(id)));
   for(String query:new String[]{null,"","a","A","Ω",name})out.run(()->lid(lib.get(query)));
   if(lib.count()>0){LogicalPart p=lib.get(1);out.add(hex(p.name));out.run(p::pinCount);for(int j:new int[]{-1,0,1,n-1,n})out.run(()->partpin(p.getPin(j)));if(n>0){ps[0]=new PartPin(999,42,"changed");out.run(()->partpin(p.getPin(0)));}}
   // Constructor itself does not sort its borrowed array.
   LogicalPart direct=new LogicalPart(name,70,ps);out.run(direct::pinCount);out.run(()->partpin(direct.getPin(-1)));out.run(()->partpin(direct.getPin(0)));
  }else if(kind.equals("B")){
   int layers=in.nextInt(),flags=in.nextInt(),n=in.nextInt();Layer[] ls=layers<0?null:new Layer[layers];if(ls!=null)for(int i=0;i<layers;i++)ls[i]=new Layer("l"+i,true);Padstacks registry=new Padstacks((flags&2)!=0?null:new LayerStructure(ls));BoardLibrary lib=new BoardLibrary((flags&1)!=0?null:registry,new Packages(registry));ArrayList<Padstack> ps=new ArrayList<>();ps.add(null);
   for(int i=1;i<=n;i++){String name=dec(in.next());int count=in.nextInt();ConvexShape[] sh=count<0?null:new ConvexShape[count];for(int j=0;j<count;j++)if(in.nextInt()!=0)sh[j]=new IntBox(0,0,i,i);ps.add(registry.add(name,sh,false,false));}
   int count=in.nextInt();for(int i=0;i<count;i++){String op=in.next();if(op.equals("S")){int size=in.nextInt();Padstack[] a=size<0?null:new Padstack[size];for(int j=0;j<size;j++)a[j]=ps.get(in.nextInt());out.run(()->{lib.setViaPadstacks(a);return "ok";});if(size>0)a[0]=null;}
    else if(op.equals("A")){Padstack p=ps.get(in.nextInt());out.run(()->lib.addViaPadstack(p));}
    else if(op.equals("R")){Padstack p=ps.get(in.nextInt());out.run(()->lib.removeViaPadstack(p,null));}
    else if(op.equals("M")){Padstack p=ps.get(in.nextInt());out.run(()->did(lib.getMirroredViaPadstack(p)));}
    else if(op.equals("G")){String name=dec(in.next());out.run(()->did(lib.getViaPadstack(name)));}
    else if(op.equals("I")){int id=in.nextInt();out.run(()->did(lib.getViaPadstack(id)));}
    else if(op.equals("C")){Padstack[] copy=lib.getViaPadstacks();if(copy.length>0)copy[0]=null;out.add("ok");}
    else throw new AssertionError(op);
    out.add(lib.viaPadstackCount());out.add(pads(lib.getViaPadstacks()));
   }
   out.add(lib.logicalParts!=null);out.add(lib.logicalParts.count());
  }else if(kind.equals("U")){
   int flags=in.nextInt();failure=in.nextInt();int n=in.nextInt();
   LayerStructure layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});Padstacks registry=new Padstacks(layers);ArrayList<Padstack> pads=new ArrayList<>();pads.add(null);for(int i=1;i<=5;i++)pads.add(registry.add("p"+i,new ConvexShape[]{new IntBox(0,0,10,10),null},false,false));pads.add(new Padstack("alias",1,new ConvexShape[]{null,null},false,false,registry));
   Packages packs=new Packages(registry);for(int i=0;i<n;i++)packs.add(pins(in));BoardLibrary lib=new BoardLibrary(registry,(flags&1)!=0?null:packs);
   BasicBoard board=new BasicBoard(new IntBox(-100,-100,100,100),layers,new PolylineShape[0],0,new BoardRules(layers,new ClearanceMatrix(2,layers,new String[]{"none","default"})),new app.freerouting.board.state.Communication());var outline=board.getOutline();if(outline!=null)board.itemList.delete(outline);
   int count=in.nextInt();for(int i=1;i<=count;i++){int id=in.nextInt(),pad=in.nextInt();board.itemList.insert(new ProbeVia(i,id,pads.get(pad)));}
   if((flags&4)!=0 || (flags&8)!=0){var field=BasicBoard.class.getDeclaredField("itemList");field.setAccessible(true);field.set(board,(flags&4)!=0?null:new UndoableObjects());if((flags&4)==0)board.itemList.insert(new NotItem());}
   for(Padstack pad:pads){events="";out.run(()->lib.isUsed(pad,(flags&2)!=0?null:board));out.add(events.isEmpty()?"-":events);}
  }else throw new AssertionError(kind);
  if(in.hasNext())throw new AssertionError();return out.toString();
 }
 public static void main(String[]args)throws Exception{var r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
