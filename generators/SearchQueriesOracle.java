package app.freerouting.board.searchtree;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
public class SearchQueriesOracle {
  static String events;
  static boolean recording;
  static class Base implements ShapeTree.Storable {
    final int serial,id,net,layer,mode;final TileShape[] shapes;ShapeTree.Leaf[] entries;
    Base(int serial,int id,int net,int layer,int mode,TileShape[] shapes){this.serial=serial;this.id=id;this.net=net;this.layer=layer;this.mode=mode;this.shapes=shapes;}
    public int compareTo(Object other){if(recording){events+="c"+serial+":"+((Base)other).serial+",";if(mode==5)throw new IllegalStateException();}return ((Base)other).id-id;}
    public int treeShapeCount(ShapeTree t){return shapes.length;}
    public TileShape getTreeShape(ShapeTree t,int i){if(recording){events+="s"+serial+":"+i+",";if(mode==1)throw new IllegalStateException();if(mode==2)return null;}return shapes[i];}
    public void setSearchTreeEntries(ShapeTree.Leaf[] e,ShapeTree t){entries=e;}
  }
  static class Obj extends Base implements SearchTreeObject {
    Obj(int serial,int id,int net,int layer,int mode,TileShape[] shapes){super(serial,id,net,layer,mode,shapes);}
    public int getId(){return id;}
    public boolean isObstacle(int n){events+="n"+serial+":"+n+",";if(mode==4)throw new IllegalStateException();return n!=net;}
    public boolean isTraceObstacle(int n){return isObstacle(n);}
    public int shapeLayer(int i){events+="l"+serial+":"+i+",";if(mode==3)throw new IllegalStateException();return layer+i%2;}
  }
  static ConvexShape shape(Scanner s){String k=s.next();return switch(k){
    case "N" -> null;case "B" -> new IntBox(s.nextInt(),s.nextInt(),s.nextInt(),s.nextInt());
    case "O" -> new IntOctagon(s.nextInt(),s.nextInt(),s.nextInt(),s.nextInt(),s.nextInt(),s.nextInt(),s.nextInt(),s.nextInt());
    case "T" -> TileShape.getInstance(new Point[]{new IntPoint(s.nextInt(),s.nextInt()),new IntPoint(s.nextInt(),s.nextInt()),new IntPoint(s.nextInt(),s.nextInt())});
    case "H" -> Simplex.getInstance(new Line[]{new Line(new IntPoint(s.nextInt(),s.nextInt()),new IntPoint(s.nextInt(),s.nextInt()))});
    case "C" -> new Circle(new IntPoint(s.nextInt(),s.nextInt()),s.nextInt());default -> throw new AssertionError(k);
  };}
  static String err(Throwable e){if(e instanceof InvocationTargetException)return err(e.getCause());if(e instanceof NullPointerException)return "null_error";if(e instanceof IndexOutOfBoundsException)return "index_error";if(e instanceof ClassCastException)return "cast_error";if(e instanceof IllegalStateException)return "callback_error";throw new RuntimeException(e);}
  static String entries(Collection<ShapeTree.TreeEntry> list){String s=""+list.size();for(var e:list)s+=","+((Base)e.object).serial+":"+e.shapeIndexInObject;return s;}
  static String objects(Collection<SearchTreeObject> list){String s=""+list.size();for(var o:list)s+=","+((Base)o).serial;return s;}
  static String run(String input)throws Exception{
    Scanner in=new Scanner(input);if(!in.next().equals("Q"))throw new AssertionError();recording=false;events="";
    int directions=in.nextInt(),kind=in.nextInt(),layer=in.nextInt(),filterMode=in.nextInt(),nullOutput=in.nextInt(),seed=in.nextInt(),dead=in.nextInt(),count=in.nextInt();int[] ignored=count<0?null:new int[count];if(ignored!=null)for(int i=0;i<count;i++)ignored[i]=in.nextInt();ConvexShape query=shape(in);
    ShapeSearchTree tree=new ShapeSearchTree(directions==4?OrthogonalBoundingDirections.INSTANCE:FortyfiveDegreeBoundingDirections.INSTANCE,null,0);
    ArrayList<Base> all=new ArrayList<>();int n=in.nextInt();for(int i=0;i<n;i++){int id=in.nextInt(),net=in.nextInt(),l=in.nextInt(),generic=in.nextInt(),mode=in.nextInt(),len=in.nextInt();TileShape[] shapes=new TileShape[len];for(int j=0;j<len;j++)shapes[j]=(TileShape)shape(in);Base o=generic==0?new Obj(i,id,net,l,mode,shapes):new Base(i,id,net,l,mode,shapes);all.add(o);tree.insert(o);}
    if(in.hasNext())throw new AssertionError("unused input");if(dead!=0){var a=tree.toArray();if(a.length>0)a[0].object=null;}
    LinkedList<ShapeTree.TreeEntry> resultEntries=new LinkedList<>();Set<SearchTreeObject> resultObjects=new TreeSet<>();if(seed!=0&&!all.isEmpty()){resultEntries.add(new ShapeTree.TreeEntry(all.get(0),-7));if(all.get(0) instanceof SearchTreeObject o)resultObjects.add(o);}
    Predicate<SearchTreeObject> predicate=filterMode<0?null:o->{Base b=(Base)o;events+="p"+b.serial+",";return switch(filterMode){case 0->true;case 1->b.serial%2==0;case 2->false;case 3->{if(b.serial%2!=0)throw new IllegalStateException();yield true;}case 4->{tree.remove(b.entries);yield true;}default->throw new AssertionError();};};
    recording=true;String error="ok";
    try{switch(kind){
      case 0->tree.overlappingTreeEntries(query,layer,nullOutput==0?resultEntries:null);
      case 1->tree.overlappingTreeEntries(query,layer,ignored,nullOutput==0?resultEntries:null);
      case 2->resultObjects=tree.overlappingObjects(query,layer);
      case 3->resultObjects=tree.overlappingObjects(query,layer,predicate);
      case 4->tree.overlappingObjects(query,layer,ignored,nullOutput==0?resultObjects:null);
      case 5->{Method m=ShapeSearchTree.class.getDeclaredMethod("overlappingTreeEntries",ConvexShape.class,int.class,int[].class,Predicate.class,Collection.class);m.setAccessible(true);m.invoke(tree,query,layer,ignored,predicate,nullOutput==0?resultEntries:null);}
      default->throw new AssertionError(kind);
    }}catch(Throwable e){error=err(e);}recording=false;
    return error+" "+entries(resultEntries)+" "+objects(resultObjects)+" "+(events.isEmpty()?"-":events)+" "+tree.toArray().length;
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
