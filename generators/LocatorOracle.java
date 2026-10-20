package app.freerouting.board.facade;

import app.freerouting.autoroute.drill.ExpansionDrill;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.maze.*;
import app.freerouting.autoroute.path.*;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;

/** The actual Java maze supplies the locator's inputs. No source is rewritten. */
public final class LocatorOracle extends BoardConstructionOracle {
  static String fp(FloatPoint p){return p==null?"null":bits(p.x)+","+bits(p.y);}
  static Method method(Class<?> c,String name,Class<?>... types)throws Exception {var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
  static String error(Throwable t){if(t instanceof InvocationTargetException i)t=i.getCause();if(t instanceof NullPointerException)return "null_error";if(t instanceof IndexOutOfBoundsException)return "index_error";if(t instanceof ClassCastException)return "cast_error";if(t instanceof IllegalArgumentException)return "argument_error";return t.getClass().getName()+":"+t.getMessage();}
  static final class Graph {
    final Pool doorIds=new Pool(),roomIds=new Pool(),mazeIds=new Pool();
    final NeutralBoardModel geometry=new NeutralBoardModel();
    int door(ExpandableObject d){return doorIds.id(d);}int room(ExpansionRoom r){return roomIds.id(r);}
    List<Object> maze(ExpandableObject d){var rows=new ArrayList<Object>();for(int i=0;i<d.mazeSearchElementCount();i++){var m=d.getMazeSearchElement(i);rows.add(list(mazeIds.id(m),m.isOccupied,door(m.backtrackDoor),m.sectionNoOfBacktrackDoor,m.roomRipped,m.adjustment==null?-1:m.adjustment.ordinal(),m.ripupCost));}return rows;}
    Object input(MazeSearchEngine.Result result)throws Exception {
      int destination=door(result.destinationDoor);var doors=new ArrayList<Object>();
      for(int i=0;i<doorIds.values.size();i++){
        var d=(ExpandableObject)doorIds.values.get(i);var row=obj("kind",d.getClass().getSimpleName());
        if(d instanceof TargetItemExpansionDoor t){row.put("room",room(t.room));row.put("item",t.item.getId());row.put("index",t.treeEntryNo);}
        else if(d instanceof ExpansionDoor e){row.put("first",room(e.firstRoom));row.put("second",room(e.secondRoom));row.put("dimension",e.dimension);}
        else if(d instanceof ExpansionDrill e){row.put("shape",geometry.area(e.getShape()));row.put("location",NeutralBoardModel.xy(e.location));row.put("firstLayer",e.firstLayer);row.put("lastLayer",e.lastLayer);var rooms=new ArrayList<Integer>();for(var r:e.roomArr)rooms.add(room(r));row.put("rooms",rooms);}
        else throw new IllegalArgumentException("Unknown source door "+d.getClass());
        row.put("maze",maze(d));doors.add(row);
      }
      var rooms=new ArrayList<Object>();
      for(Object raw:roomIds.values){var room=(ExpansionRoom)raw;var row=obj("kind",room.getClass().getSimpleName(),"shape",geometry.area(room.getShape()),"layer",room.getLayer());
        if(room instanceof CompleteFreeSpaceExpansionRoom r)row.put("id",r.getId());
        else if(room instanceof ObstacleExpansionRoom r){row.put("item",r.getItem().getId());row.put("index",r.getIndexInItem());}
        else if(room instanceof IncompleteFreeSpaceExpansionRoom r)row.put("contained",geometry.area(r.getContainedShape()));
        else throw new IllegalArgumentException("Unknown source room "+room.getClass());rooms.add(row);
      }
      return NeutralBoardItemsOracle.freeze(obj("destination",destination,"section",result.sectionNoOfDoor,"doors",doors,"rooms",rooms,"geometry",geometry.geometry));
    }
    Object state(){var a=new ArrayList<Object>();for(Object d:doorIds.values)a.add(maze((ExpandableObject)d));return a;}
  }
  static Object snapshot(FoundConnectionLocator l,Graph g)throws Exception {
    if(l==null)return null;
    var traces=new ArrayList<Object>();for(Object t:l.connectionItems){var corners=new ArrayList<Object>();for(var p:(IntPoint[])field(t,"corners"))corners.add(list(p.x,p.y));traces.add(list(field(t,"layer"),corners));}
    var backtrack=new ArrayList<Object>();for(Object e:(Object[])field(l,"backtrackArray"))backtrack.add(list(g.door((ExpandableObject)field(e,"door")),field(e,"sectionNoOfDoor"),g.room((ExpansionRoom)field(e,"nextRoom"))));
    var current=(FloatPoint)field(l,"currentFromPoint");var previous=(FloatPoint)field(l,"previousFromPoint");
    return obj("startItem",l.startItem==null?0:l.startItem.getId(),"targetItem",l.targetItem==null?0:l.targetItem.getId(),"startLayer",l.startLayer,"targetLayer",l.targetLayer,"traces",traces,"backtrack",backtrack,"currentFrom",fp(current),"previousFrom",fp(previous),"pointAlias",current==previous,"indices",list(field(l,"currentTraceLayer"),field(l,"currentFromDoorIndex"),field(l,"currentToDoorIndex"),field(l,"currentTargetDoorIndex")),"targetShape",shape((TileShape)field(l,"currentTargetShape")));
  }
  static Object directionDecisions(FoundConnectionLocator locator,Graph graph)throws Exception {
    var fromMethod=method(FoundConnectionLocator45Degree.class,"calcHorizontalFirstFromDoor",ExpandableObject.class,FloatPoint.class,FloatPoint.class);
    var toMethod=method(FoundConnectionLocator45Degree.class,"calcHorizontalFirstToDoor",ExpandableObject.class,FloatPoint.class,FloatPoint.class);
    var out=new ArrayList<Object>();
    for(Object raw:graph.doorIds.values){var door=(ExpandableObject)raw;var box=door.getShape().boundingBox();
      var a=new FloatPoint(box.ll.x,box.ll.y);var b=new FloatPoint(box.ur.x,box.ur.y);var c=a.middlePoint(b);
      FloatPoint[] points={a,b,c,new FloatPoint(c.x+0.5,c.y-0.5),new FloatPoint(c.x+Math.nextDown(0.5),c.y+Math.nextUp(0.5)),new FloatPoint(c.x-17,c.y+17),null};
      for(var from:points)for(var to:points){String first,last;
        try{first=String.valueOf(fromMethod.invoke(null,door,from,to));}catch(Throwable t){first=error(t);}
        try{last=String.valueOf(toMethod.invoke(locator,door,from,to));}catch(Throwable t){last=error(t);}
        out.add(obj("door",graph.door(door),"from",from==null?null:list(bits(from.x),bits(from.y)),"to",to==null?null:list(bits(to.x),bits(to.y)),"fromDecision",first,"toDecision",last));
      }
    }
    return out;
  }
  static RoutingBoard load(Path path)throws Exception {
    try(var in=Files.newInputStream(path)){var result=DsnReader.readBoard(in,new Observers(),new ItemIdGenerator(),path.getFileName().toString());if(!(result instanceof BoardReadResult.Success s))throw new IllegalStateException(result.toString());return (RoutingBoard)s.board();}
  }
  static Object publicCase(Path path,int net,AngleRestriction angle)throws Exception {
    var b=load(path);b.rules.setTraceAngleRestriction(angle);var settings=new RouterSettings(b);settings.setFanoutEnabled(false);settings.setRunOptimizer(false);
    var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());ctrl.removeUnconnectedVias=false;
    var start=b.getConnectableItems(net).iterator().next();var from=start.getConnectedSet(net);var to=start.getUnconnectedSet(net);if(to.isEmpty())return null;
    var engine=b.initAutoroute(net,ctrl.traceClearanceClassIndex,null,null,false);var search=MazeSearchEngine.getInstance(from,to,engine,ctrl);
    var result=search==null?null:search.findConnection();if(result==null)return obj("case","public-no-search-result","net",net,"angle",angle.ordinal(),"locator",null);
    var graph=new Graph();Object input=graph.input(result);var ripped=new TreeSet<Item>();var costs=new HashMap<Item,Integer>();FoundConnectionLocator locator=null;String failure=null;
    try{locator=FoundConnectionLocator.getInstance(result,ctrl,engine.autorouteSearchTree,angle,ripped,costs);}catch(Throwable t){failure=error(t);}
    var out=obj("locator",snapshot(locator,graph),"error",failure,"ripped",ids(ripped),"costs",List.of(),"graphState",graph.state());
    var costRows=new ArrayList<Object>();for(Item item:new TreeSet<>(costs.keySet()))costRows.add(list(item.getId(),costs.get(item)));out.put("costs",costRows);
    var diagnostics=new ArrayList<Object>();if(locator!=null)locator.emitDiagnostics(d->diagnostics.add(list(d.kind().ordinal(),shape(d.shape()),d.layer(),bits(d.intensity()))),1.25);out.put("diagnostics",diagnostics);
    var resolvedCosts=new ArrayList<Object>();for(var cost:settings.getTraceCosts())resolvedCosts.add(list(bits(cost.horizontal()),bits(cost.vertical())));
    var active=new ArrayList<Object>();var bends=new ArrayList<Object>();for(int i=0;i<b.getLayerCount();i++){active.add(settings.getLayerActive(i));bends.add(bits(settings.getBendCost(i)));}
    var resolved=obj("traceCosts",resolvedCosts,"active",active,"bend",bends,"viaCosts",settings.getViaCosts(),"viasAllowed",settings.getViasAllowed(),"neckdown",settings.getAutomaticNeckdown());
    return obj("case","public-maze-to-locator","fixture","fr-ecc83","net",net,"angle",angle.ordinal(),"sourceSeed",ctrl.ripupCosts,"resolvedSettings",resolved,"sourceStartSet",ids(from),"sourceDestinationSet",ids(to),"settings",NeutralBoardModel.JSON.toJsonTree(settings),"widths",ctrl.compensatedTraceHalfWidth,"clearance",ctrl.traceClearanceClassIndex,"input",input,"expected",out,"directionDecisions",locator==null?List.of():directionDecisions(locator,graph));
  }
  static Object helperCases()throws Exception {
    var additional=method(FoundConnectionLocator.class,"calculateAdditionalCorner",FloatPoint.class,FloatPoint.class,boolean.class,AngleRestriction.class);
    var result=new ArrayList<Object>();double[] values={-100,-1,-0.0,0.0,0.5,1,100,Double.MIN_VALUE,Double.MAX_VALUE,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NaN};
    for(int i=0;i<values.length;i++)for(int j=0;j<values.length;j++)for(int angle=-1;angle<3;angle++)for(boolean horizontal:new boolean[]{false,true})for(boolean alias:new boolean[]{false,true}){
      var from=new FloatPoint(values[i],values[j]);var to=alias?from:new FloatPoint(values[j],values[i]);var a=angle<0?null:AngleRestriction.values()[angle];
      var corner=(FloatPoint)additional.invoke(null,from,to,horizontal,a);
      result.add(obj("from",list(bits(from.x),bits(from.y)),"to",list(bits(to.x),bits(to.y)),"inputAlias",alias,"angle",angle,"horizontal",horizontal,"point",fp(corner),"aliasFrom",corner==from,"aliasTo",corner==to));
    }
    return result;
  }
  public static void main(String[] args)throws Exception {
    var cases=new ArrayList<Object>();for(var angle:new AngleRestriction[]{AngleRestriction.FORTYFIVE_DEGREE,AngleRestriction.NINETY_DEGREE})for(int net=1;net<=13;net++){var c=publicCase(Path.of(args[0]),net,angle);if(c!=null)cases.add(c);}
    Files.writeString(Path.of(args[1]),NeutralBoardModel.JSON.toJson(obj("publicCases",cases,"helpers",helperCases(),"nativeRoutingExecuted",false,"scope","Locator-only oracle; each public graph is produced by real Java maze search on a fresh public ECC83 board."))+"\n");
  }
}
