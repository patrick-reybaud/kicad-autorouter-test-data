package app.freerouting.autoroute.expansion;
import app.freerouting.autoroute.maze.*;
import app.freerouting.geometry.planar.*;
import java.io.*;
import java.util.*;
public class ExpansionDoorOracle {
 interface Call{Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException|NoSuchElementException e){return "index_error";}catch(NegativeArraySizeException e){return "size_error";}catch(IllegalStateException e){return "callback_error";}}
 static String bits(double x){return Long.toHexString(Double.doubleToLongBits(x));}
 static double dbl(Scanner in){return Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));}
 static TileShape shape(Scanner in){int kind=in.nextInt();if(kind==0)return null;int x=in.nextInt(),y=in.nextInt(),w=in.nextInt(),h=in.nextInt();IntBox b=new IntBox(x,y,x+w,y+h);return switch(kind){case 1->b;case 2->b.toIntOctagon();case 3->b.toSimplex();default->new IntOctagon(x,y,x+w,y+h,x-y-h+1,x+w-y-1,x+y+1,x+w+y+h-1).normalize();};}
 static String sh(TileShape s){if(s==null)return "N";String r=s.getId()+":"+s.dimension()+":"+s.borderLineCount();if(!s.isEmpty())for(int i=0;i<s.borderLineCount();i++){var p=s.corner(i).toFloat();r+=","+bits(p.x)+":"+bits(p.y);}return r;}
 static String lines(FloatLine[] a){String s=""+a.length;for(var l:a)s+=","+bits(l.a.x)+":"+bits(l.a.y)+":"+bits(l.b.x)+":"+bits(l.b.y);return s;}
 static class Inc extends IncompleteFreeSpaceExpansionRoom {
  State state;int serial;Inc(State s,int i,TileShape sh,int layer,TileShape contained){super(sh,layer,contained);state=s;serial=i;}
  public TileShape getShape(){return state.touch(serial,0)?null:super.getShape();}
  public int getLayer(){state.touch(serial,1);return super.getLayer();}
  public int getId(){state.touch(serial,2);return super.getId();}
 }
 static class Complete extends CompleteFreeSpaceExpansionRoom {
  State state;int serial;Complete(State s,int i,TileShape sh,int layer,int id){super(sh,layer,id);state=s;serial=i;}
  public TileShape getShape(){return state.touch(serial,0)?null:super.getShape();}
  public int getLayer(){state.touch(serial,1);return super.getLayer();}
  public int getId(){state.touch(serial,2);return super.getId();}
 }
 static class State{
  String events="";boolean active;int failure=-1;
  boolean touch(int serial,int kind){if(!active)return false;int code=serial*3+kind;events+=code+",";if(failure==code)throw new IllegalStateException();return failure==100+code;}
  String invoke(Call f){events="";active=true;String result=call(f);active=false;return result+"~"+(events.isEmpty()?"-":events);}

  IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();int next=1;
  ArrayList<FreeSpaceExpansionRoom> rooms=new ArrayList<>();ArrayList<ExpansionDoor> doors=new ArrayList<>();
  List<ExpansionDoor> oldDoors;MazeSearchElement[] oldSections;
  int id(Object o){return o==null?0:ids.computeIfAbsent(o,k->next++);}
  int rid(ExpansionRoom r){return r==null?0:rooms.indexOf(r);}
  int did(ExpandableObject d){return d==null?0:doors.indexOf(d);}
  String list(List<ExpansionDoor> l){if(l==null)return "N";String s=id(l)+":"+l.size();for(var d:l)s+=","+did(d);return s;}
  String sec(MazeSearchElement e){return e==null?"N":id(e)+":"+e.isOccupied+":"+did(e.backtrackDoor)+":"+e.sectionNoOfBacktrackDoor+":"+e.roomRipped+":"+(e.adjustment==null?-1:e.adjustment.ordinal())+":"+e.ripupCost;}
  String arr(MazeSearchElement[] a){if(a==null)return "N";String s=id(a)+":"+a.length;for(var e:a)s+="/"+sec(e);return s;}
  String dump(){String s="";for(int i=1;i<rooms.size();i++){var r=rooms.get(i);s+="R"+i+":"+list(r.getDoors())+":"+r.getLayer()+":"+sh(r.getShape());if(r instanceof IncompleteFreeSpaceExpansionRoom q)s+=":"+sh(q.getContainedShape());}for(int i=1;i<doors.size();i++)s+="D"+i+":"+(doors.get(i)==null?"N":arr(doors.get(i).sectionArr));return s+"O"+list(oldDoors)+"S"+arr(oldSections);}
 }
 static String run(String line){Scanner in=new Scanner(line);in.next();State s=new State();s.rooms.add(null);s.doors.add(null);TileShape[] shapes=new TileShape[3];shapes[0]=null;
  for(int i=1;i<=2;i++){shapes[i]=shape(in);int kind=in.nextInt(),layer=in.nextInt(),id=in.nextInt();s.rooms.add(kind==0?new Inc(s,i,shapes[i],layer,shapes[3-i]):new Complete(s,i,shapes[i],layer,id));}
  int dim=in.nextInt();ArrayList<String> out=new ArrayList<>();out.add(s.invoke(()->{s.doors.add(dim==-9?new ExpansionDoor(s.rooms.get(1),s.rooms.get(2)):new ExpansionDoor(s.rooms.get(1),s.rooms.get(2),dim));return "ok";}));if(s.doors.size()==1)s.doors.add(null);
  s.doors.add(new ExpansionDoor(s.rooms.get(2),s.rooms.get(1),dim==-9?1:dim));s.doors.add(new ExpansionDoor(s.rooms.get(1),s.rooms.get(1),2));s.doors.add(new ExpansionDoor(null,s.rooms.get(1),0));
  for(int i=1;i<=4;i++){var door=s.doors.get(i);out.add(s.invoke(()->door.getId()));out.add(s.invoke(()->sh(door.getShape())));out.add(s.invoke(()->door.getDimension()));for(var room:s.rooms){out.add(s.invoke(()->s.rid(door.otherRoom((ExpansionRoom)room))));if(room==null||room instanceof CompleteExpansionRoom)out.add(s.invoke(()->s.rid(door.otherRoom((CompleteExpansionRoom)room))));}}
  out.add(s.dump());int n=in.nextInt();for(int j=0;j<n;j++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();double offset=dbl(in);
   out.add(s.invoke(()->switch(op){
    case "A"->{s.rooms.get(a).addDoor(s.doors.get(b));yield "ok";}case "R"->s.rooms.get(a).removeDoor(s.doors.get(b));
    case "C"->{s.rooms.get(a).clearDoors();yield "ok";}case "E"->s.rooms.get(a).doorExists(s.rooms.get(b));case "P"->{s.rooms.get(a).resetDoors();yield "ok";}
    case "G"->lines(s.doors.get(a).getSectionSegments(offset));case "L"->{s.doors.get(a).allocateSections(b);yield "ok";}
    case "N"->{s.doors.get(a).sectionArr=null;yield "ok";}case "Z"->{s.doors.get(a).reset();yield "ok";}
    case "T"->{var e=s.doors.get(a).getMazeSearchElement(b);e.isOccupied=true;e.backtrackDoor=s.doors.get(c);e.sectionNoOfBacktrackDoor=b-8;e.roomRipped=true;e.adjustment=c==0?null:MazeSearchElement.Adjustment.values()[c%3];e.ripupCost=(int)offset;yield "ok";}
    case "B"->{s.doors.get(a).sectionArr[b]=null;yield "ok";}case "Q"->s.sec(s.doors.get(a).getMazeSearchElement(b));case "H"->s.doors.get(a).mazeSearchElementCount();
    case "U"->{s.oldDoors=s.rooms.get(a).getDoors();yield "ok";}case "V"->{if(a==0)s.oldDoors.clear();else if(a==1)s.oldDoors.add(s.doors.get(b));else s.oldDoors.remove(s.doors.get(b));yield "ok";}
    case "W"->{s.oldSections=s.doors.get(a).sectionArr;yield "ok";}case "X"->{s.oldSections[b]=c==0?null:new MazeSearchElement();yield "ok";}
    case "S"->{s.rooms.get(a).setShape(shapes[b]);yield "ok";}case "I"->{((IncompleteFreeSpaceExpansionRoom)s.rooms.get(a)).setContainedShape(shapes[b]);yield "ok";}
    case "F"->{var r=(IncompleteFreeSpaceExpansionRoom)s.rooms.get(a);var x=r.getTargetDoors();var y=r.getTargetDoors();yield x.size()+":"+y.size()+":"+(x==y);}
    case "K"->{s.failure=a;yield "ok";}case "D"->s.doors.get(a).getId();default->throw new AssertionError(op);
   }));out.add(s.dump());
  }return String.join(" ",out);
 }
 public static void main(String[] args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String s;while((s=in.readLine())!=null)System.out.println(s+" | "+run(s));}
}
