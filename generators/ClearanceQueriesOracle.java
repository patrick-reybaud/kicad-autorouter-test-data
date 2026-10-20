package app.freerouting.board.searchtree;
import app.freerouting.board.actions.*;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.datastructures.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.rules.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
public class ClearanceQueriesOracle {
  static String events;static boolean recording;
  static class TestItem extends Item {
    final int serial,layer,mode;final TileShape[] shapes;
    TestItem(int serial,int id,int net,int layer,int cl,int mode,TileShape[] shapes,BasicBoard b){super(new int[]{net},cl,id,0,FixedState.UNFIXED,b);this.serial=serial;this.layer=layer;this.mode=mode;this.shapes=shapes;}
    public int compareTo(Object o){if(recording){events+="c"+serial+":"+serial(o)+",";if(mode==5)throw new IllegalStateException();}return super.compareTo(o);}
    public int treeShapeCount(ShapeTree t){return shapes.length;}
    public TileShape getTreeShape(ShapeTree t,int i){if(recording){events+="s"+serial+":"+i+",";if(mode==1)throw new IllegalStateException();if(mode==2)return null;}return shapes[i];}
    public boolean isObstacle(int n){events+="n"+serial+":"+n+",";if(mode==4)throw new IllegalStateException();return super.isObstacle(n);}
    public int shapeLayer(int i){events+="l"+serial+":"+i+",";if(mode==3)throw new IllegalStateException();return layer+i%2;}
    public Item copy(int id){throw new UnsupportedOperationException();}public boolean isObstacle(Item i){return !sharesNet(i);}public int tileShapeCount(){return shapes.length;}
    protected TileShape[] calculateTreeShapes(ShapeSearchTree t){return shapes;}
    public boolean isOnLayer(int l){return l==layer;}public int firstLayer(){return layer;}public int lastLayer(){return layer;}
    public boolean write(ObjectOutputStream s){throw new UnsupportedOperationException();}public void translateBy(app.freerouting.geometry.planar.Vector v){throw new UnsupportedOperationException();}
    public void turn90Degree(int f,IntPoint p){throw new UnsupportedOperationException();}public void rotateApprox(double a,FloatPoint p){throw new UnsupportedOperationException();}public void changePlacementSide(IntPoint p){throw new UnsupportedOperationException();}
    public IntBox boundingBox(){throw new UnsupportedOperationException();}public boolean isSelectedByFilter(ItemSelectionFilter f){return false;}public void printInfo(ItemInfoPrinter p,Locale l){throw new UnsupportedOperationException();}
  }
  static int serial(Object o){return o instanceof TestItem i?i.serial:((Generic)o).serial;}
  static class Generic implements SearchTreeObject {
    final int serial,id,net,layer,mode;final TileShape[] shapes;
    Generic(int s,int i,int n,int l,int m,TileShape[] a){serial=s;id=i;net=n;layer=l;mode=m;shapes=a;}
    public int getId(){return id;}
    public int compareTo(Object o){if(recording){events+="c"+serial+":"+serial(o)+",";if(mode==5)throw new IllegalStateException();}return o instanceof Item?-1:((Generic)o).id-id;}
    public int treeShapeCount(ShapeTree t){return shapes.length;}
    public TileShape getTreeShape(ShapeTree t,int i){if(recording){events+="s"+serial+":"+i+",";if(mode==1)throw new IllegalStateException();if(mode==2)return null;}return shapes[i];}
    public void setSearchTreeEntries(ShapeTree.Leaf[] e,ShapeTree t){}
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
  static String err(Throwable e){if(e instanceof NullPointerException)return "null_error";if(e instanceof IndexOutOfBoundsException)return "index_error";if(e instanceof ClassCastException)return "cast_error";if(e instanceof IllegalStateException)return "callback_error";throw new RuntimeException(e);}
  static String entries(Collection<ShapeTree.TreeEntry> list){String s=""+list.size();for(var e:list)s+=","+serial(e.object)+":"+e.shapeIndexInObject;return s;}
  static String objects(Collection<? extends SearchTreeObject> list){String s=""+list.size();for(var o:list)s+=","+serial(o);return s;}
  static String run(String input)throws Exception{
    Scanner in=new Scanner(input);if(!in.next().equals("C"))throw new AssertionError();recording=false;events="";
    int dirs=in.nextInt(),comp=in.nextInt(),kind=in.nextInt(),layer=in.nextInt(),cl=in.nextInt(),nullOut=in.nextInt(),boardMode=in.nextInt(),seed=in.nextInt(),def=in.nextInt();
    var layers=new LayerStructure(new Layer[]{new Layer("F",true),new Layer("B",true)});var matrix=new ClearanceMatrix(3,layers,new String[]{"none","default","extra"});matrix.setDefaultValue(def);
    int changes=in.nextInt();for(int i=0;i<changes;i++)matrix.setValue(in.nextInt(),in.nextInt(),in.nextInt(),in.nextInt());
    BasicBoard board=new BasicBoard(new IntBox(0,0,100,100),layers,new PolylineShape[0],0,new BoardRules(layers,matrix),new Communication());
    if(boardMode==2){Field f=BasicBoard.class.getDeclaredField("boundingBox");f.setAccessible(true);f.set(board,null);}
    ShapeSearchTree tree=new ShapeSearchTree(dirs==4?OrthogonalBoundingDirections.INSTANCE:FortyfiveDegreeBoundingDirections.INSTANCE,boardMode==1?null:board,comp);
    int count=in.nextInt();int[] ignored=count<0?null:new int[count];if(ignored!=null)for(int i=0;i<count;i++)ignored[i]=in.nextInt();ConvexShape query=shape(in);
    ArrayList<ShapeTree.Storable> all=new ArrayList<>();int n=in.nextInt();for(int i=0;i<n;i++){int id=in.nextInt(),net=in.nextInt(),l=in.nextInt(),c=in.nextInt(),mode=in.nextInt(),len=in.nextInt();TileShape[] shapes=new TileShape[len];for(int j=0;j<len;j++)shapes[j]=(TileShape)shape(in);ShapeTree.Storable o=mode>=10?new Generic(i,id,net,l,mode-10,shapes):new TestItem(i,id,net,l,c,mode,shapes,board);all.add(o);tree.insert(o);}
    if(in.hasNext())throw new AssertionError("unused input");Field counter=ShapeSearchTree.class.getDeclaredField("lastGeneratedEntryId");counter.setAccessible(true);counter.setInt(null,seed);
    Collection<ShapeTree.TreeEntry> resultEntries=new LinkedList<>();Set<SearchTreeObject> resultObjects=new TreeSet<>();Set<Item> resultItems=new TreeSet<>();
    recording=true;String error="ok";try{switch(kind){
      case 0->tree.overlappingTreeEntriesWithClearance(query,layer,ignored,cl,nullOut==0?resultEntries:null);
      case 1->resultEntries=tree.overlappingTreeEntriesWithClearance(query,layer,ignored,cl);
      case 2->tree.overlappingObjectsWithClearance(query,layer,ignored,cl,nullOut==0?resultObjects:null);
      case 3->resultItems=tree.overlappingItemsWithClearance(query,layer,ignored,cl);
      default->throw new AssertionError(kind);
    }}catch(Throwable e){error=err(e);}recording=false;
    return error+" "+entries(resultEntries)+" "+objects(resultObjects)+" "+objects(resultItems)+" "+(events.isEmpty()?"-":events)+" "+counter.getInt(null);
  }
  public static void main(String[] args)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(System.in));for(String s;(s=r.readLine())!=null;)System.out.println(s+" | "+run(s));}
}
