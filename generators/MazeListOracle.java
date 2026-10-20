package app.freerouting.autoroute.maze;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.AutorouteDiagnostic;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.util.*;
public class MazeListOracle {
 interface Call{Object run();}
 static class Out{ArrayList<String> fields=new ArrayList<>();void add(Object x){fields.add(String.valueOf(x));}void call(Call f){try{add(f.run());}catch(NullPointerException e){add("null_error");}catch(IndexOutOfBoundsException|NoSuchElementException e){add("index_error");}catch(IllegalStateException e){add("callback_error");}}public String toString(){return String.join(" ",fields);}}
 static String bits(double d){return Long.toHexString(Double.doubleToLongBits(d));}
 static double dbl(Scanner s){return Double.longBitsToDouble(Long.parseUnsignedLong(s.next(),16));}
 static class State{ArrayList<Door> doors=new ArrayList<>();ArrayList<MazeListElement> elements=new ArrayList<>();String events="";int failure=-1;int id(MazeListElement e){return e==null?0:elements.indexOf(e);}String order(Collection<MazeListElement> a){String s=""+a.size();for(var e:a)s+=","+id(e);return s;}String doorState(){String s=""+(doors.size()-1);for(int i=1;i<doors.size();i++)s+=","+doors.get(i).id;return s;} }
 static class Door implements ExpandableObject {
  State s;int serial,id,target,delta;Door(State s,int serial,int id){this.s=s;this.serial=serial;this.id=id;}
  public int getId(){s.events+=serial+":"+id+",";if(s.failure==serial)throw new IllegalStateException();if(target>0)s.doors.get(target).id+=delta;return id;}
  public TileShape getShape(){return null;}public int getDimension(){return 0;}public CompleteExpansionRoom otherRoom(CompleteExpansionRoom r){return null;}public int mazeSearchElementCount(){return 0;}public MazeSearchElement getMazeSearchElement(int i){return null;}public void reset(){}
 }
 static class Room implements CompleteExpansionRoom {
  int serial;Room(int i){serial=i;}public void addDoor(ExpansionDoor d){}public List<ExpansionDoor> getDoors(){return null;}public void clearDoors(){}public void resetDoors(){}public boolean doorExists(ExpansionRoom r){return false;}public boolean removeDoor(ExpandableObject d){return false;}public TileShape getShape(){return null;}public int getLayer(){return 0;}public int getId(){return serial;}public Collection<TargetItemExpansionDoor> getTargetDoors(){return null;}public SearchTreeObject getObject(){return null;}public void emitDiagnostic(AutorouteDiagnostic.Sink s,double v){}
 }
 static String element(MazeListElement e){if(e==null)return "N";String entry=e.shapeEntry==null?"N":bits(e.shapeEntry.a.x)+":"+bits(e.shapeEntry.a.y)+":"+bits(e.shapeEntry.b.x)+":"+bits(e.shapeEntry.b.y);return (e.door==null?0:((Door)e.door).serial)+":"+e.sectionNoOfDoor+":"+(e.backtrackDoor==null?0:((Door)e.backtrackDoor).serial)+":"+e.sectionNoOfBacktrackDoor+":"+bits(e.expansionValue)+":"+bits(e.sortingValue)+":"+(e.nextRoom==null?0:((Room)e.nextRoom).serial)+":"+entry+":"+e.roomRipped+":"+(e.adjustment==null?-1:e.adjustment.ordinal())+":"+e.alreadyChecked+":"+e.ripupCost;}
 static String run(String line){Scanner in=new Scanner(line);in.next();State s=new State();s.doors.add(null);s.elements.add(null);int n=in.nextInt();for(int i=1;i<=n;i++)s.doors.add(new Door(s,i,in.nextInt()));Room[] rooms={null,new Room(1),new Room(2)};FloatLine[] entries={null,new FloatLine(new FloatPoint(0,0),new FloatPoint(1,2)),new FloatLine(new FloatPoint(Double.NaN,Double.POSITIVE_INFINITY),new FloatPoint(-0.,Double.MIN_VALUE))};n=in.nextInt();Out out=new Out();for(int i=1;i<=n;i++){int door=in.nextInt(),section=in.nextInt(),back=in.nextInt(),backSection=in.nextInt();double expansion=dbl(in),sorting=dbl(in);int room=in.nextInt(),entry=in.nextInt();boolean ripped=in.nextInt()!=0;int adjustment=in.nextInt();boolean checked=in.nextInt()!=0;var e=new MazeListElement(s.doors.get(door),section,s.doors.get(back),backSection,expansion,sorting,rooms[room],entries[entry],ripped,adjustment<0?null:MazeSearchElement.Adjustment.values()[adjustment],checked);s.elements.add(e);out.add(element(e));}
  TreeSet<MazeListElement> set=new TreeSet<>();int steps=in.nextInt();for(int step=0;step<steps;step++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();s.events="";out.call(()->switch(op){case "A"->set.add(s.elements.get(a));case "R"->set.remove(s.elements.get(a));case "C"->set.contains(s.elements.get(a));case "P"->{var it=set.iterator();var result=it.next();it.remove();yield s.id(result);}case "F"->s.id(set.first());case "S"->{s.doors.get(a).id=b;yield "ok";}case "X"->{s.failure=a;yield "ok";}case "M"->{s.doors.get(a).target=b;s.doors.get(a).delta=c;yield "ok";}case "Q"->s.elements.get(a).compareTo(s.elements.get(b));case "T"->{s.elements.get(a).ripupCost=b;yield "ok";}case "K"->{set.clear();yield "ok";}default->throw new AssertionError(op);});out.add(s.events.isEmpty()?"-":s.events);out.add(s.order(set));out.add(s.doorState());String state=""+n;for(int i=1;i<=n;i++)state+=","+s.elements.get(i).ripupCost;out.add(state);}
  return out.toString();
 }
 public static void main(String[] args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String s;while((s=in.readLine())!=null)System.out.println(s+" | "+run(s));}
}
