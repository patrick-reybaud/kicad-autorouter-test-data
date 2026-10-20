package app.freerouting.board.facade;
import app.freerouting.autoroute.*;
import app.freerouting.autoroute.maze.*;
import app.freerouting.autoroute.expansion.*;
import app.freerouting.autoroute.drill.*;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.board.state.*;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.*;
import java.nio.file.*;
import java.util.*;

/** Actual RoutingBoard lifecycle; reflection only observes private state or injects documented faults. */
public final class BoardEntryOracle extends BoardConstructionOracle {
  static final class Entry {
    RoutingBoard b; final Observers o; final NeutralBoardModel export;
    /** Snapshots of a board restored from a serialized snapshot share the identity pools of the original. */
    void rebind(RoutingBoard board){b=board;}
    final Pool engines=new Pool(),rooms=new Pool(),other=new Pool();final Set<Integer> changed=new TreeSet<>();
    Entry(RoutingBoard b,Observers o,NeutralBoardModel e){this.b=b;this.o=o;export=e;extraTreeObjectId=x->"R"+rooms.id(x);}
    AutorouteEngine current()throws Exception{return (AutorouteEngine)field(b,"autorouteEngine");}
    String sequence(Collection<?> values,Pool pool){if(values==null)return "null";String s=""+other.id(values)+":"+values.size();for(var x:values)s+=","+pool.id(x);return s;}
    String array(Object[] values,Pool pool){if(values==null)return "null";String s=""+other.id(values)+":"+values.length;for(var x:values)s+=","+pool.id(x);return s;}
    String maze(ExpandableObject x){if(x instanceof ExpansionDoor d && d.sectionArr==null)return "null";String s=""+x.mazeSearchElementCount();for(int i=0;i<x.mazeSearchElementCount();i++){var m=x.getMazeSearchElement(i);s+="/"+other.id(m)+":"+m.isOccupied+":"+other.id(m.backtrackDoor)+":"+m.sectionNoOfBacktrackDoor+":"+m.roomRipped+":"+m.adjustment.ordinal()+":"+m.ripupCost;}return s;}
    String room(ExpansionRoom r)throws Exception {
      String s=shape(r.getShape())+":"+r.getLayer();
      if(r instanceof IncompleteFreeSpaceExpansionRoom x)s+="/I:"+shape(x.getContainedShape());
      if(r instanceof CompleteFreeSpaceExpansionRoom x){s+="/C:"+x.getId()+":"+x.isNetDependent();var target=x.getTargetDoors();s+="/T:"+sequence(target,other);for(var d:target)s+="!"+items.id(d.item)+":"+d.treeEntryNo+":"+rooms.id(d.room)+":"+shape(d.getShape())+":"+maze(d);}
      if(r instanceof ObstacleExpansionRoom x)s+="/O:"+items.id(x.getItem())+":"+x.getIndexInItem()+":"+x.allDoorsCalculated();
      var doors=r.getDoors();s+="/D:"+sequence(doors,other);for(var d:doors)s+="!"+rooms.id(d.firstRoom)+":"+rooms.id(d.secondRoom)+":"+d.dimension+":"+shape(d.getShape())+":"+other.id(d.sectionArr)+":"+maze(d);
      return s;
    }
    Object snapshot()throws Exception {
      Object state=NeutralBoardItemsOracle.snapshot(export,b,o,changed,List.of());
      int currentId=engines.id(current());var allEngines=new ArrayList<Object>();
      for(int ei=0;ei<engines.values.size();ei++){
        var e=(AutorouteEngine)engines.values.get(ei);var pages=(DrillPageArray)field(e,"drillPageArray");
        String head=e.getNetNumber()+":"+e.maintainDatabase+":"+trees.id(e.autorouteSearchTree)+":"+e.autorouteSearchTree.key+":"+field(e,"expansionRoomInstanceCount")+":"+(e.board==b)+":"+(e.stoppableThread==null)+":"+(field(e,"timeLimit")==null);
        String incomplete=sequence((Collection<?>)field(e,"incompleteExpansionRooms"),rooms),complete=sequence((Collection<?>)field(e,"completeExpansionRooms"),rooms);
        var pageState=new ArrayList<Object>();for(var page:pages.overlappingPages(b.boundingBox)){
          String p=other.id(page)+":"+shape(page.shape)+":"+field(page,"netNumber")+":"+maze(page);
          var drills=(Collection<?>)field(page,"drills");p+="/"+sequence(drills,other);
          if(drills!=null)for(Object value:drills){var d=(ExpansionDrill)value;p+="!"+point(d.location)+":"+d.firstLayer+":"+d.lastLayer+":"+shape(d.getShape())+":"+array(d.roomArr,rooms)+":"+maze(d);}
          pageState.add(p);
        }
        allEngines.add(NeutralBoardModel.obj("head",head,"incomplete",incomplete,"complete",complete,"pages",pageState));
      }
      var infos=new ArrayList<String>();for(Object value:items.values){var i=(Item)value;var info=i.getAutorouteInfoPur();String s=""+other.id(info);if(info!=null)s+=":"+info.isStartInfo()+":"+other.id(info.getPrecalculatedConnection())+":"+array((Object[])field(info,"expansionRoomArr"),rooms);infos.add(s);}
      var allRooms=new ArrayList<String>();for(int ri=0;ri<rooms.values.size();ri++)allRooms.add(room((ExpansionRoom)rooms.values.get(ri)));
      return NeutralBoardItemsOracle.freeze(NeutralBoardModel.obj("board",state,"current",currentId,"maintaining",b.isMaintainingAutorouteDatabase(),"engines",allEngines,"infos",infos,"rooms",allRooms));
    }
    String op(String op)throws Exception{
      if(op.startsWith("init:")){var v=op.split(":");return call(()->engines.id(b.initAutoroute(Integer.parseInt(v[1]),Integer.parseInt(v[2]),null,null,Boolean.parseBoolean(v[3]))));}
      switch(op){
        case "unboundInsert": {var trace=new app.freerouting.board.trace.PolylineTrace(new Polyline(new Point[]{new IntPoint(-5000,1000),new IntPoint(-3000,1000)}),0,20,new int[]{2},1,200,0,FixedState.UNFIXED,null);b.insertItem(trace);break;}
        case "moveUnbound": b.getItem(200).moveBy(new IntVector(20,30));break;
        case "complete": {var e=current();return call(()->e.completeExpansionRoom(e.addIncompleteExpansionRoom(null,0,new IntBox(-6000,-6000,-6000,-6000))).size());}
        case "finishFault": {var e=current(); @SuppressWarnings("unchecked") var list=(List<CompleteFreeSpaceExpansionRoom>)field(e,"completeExpansionRooms");if(list==null){list=new ArrayList<>();var f=AutorouteEngine.class.getDeclaredField("completeExpansionRooms");f.setAccessible(true);f.set(e,list);}list.add(null);return call(()->{b.finishAutoroute();return "ok";});}
        case "repairFault": {@SuppressWarnings("unchecked") var list=(List<CompleteFreeSpaceExpansionRoom>)field(current(),"completeExpansionRooms");list.remove(null);break;}
        case "finish": return call(()->{b.finishAutoroute();return "ok";});
        case "drop": b.setMaintainingAutorouteDatabase(false);break;
        case "retain": b.setMaintainingAutorouteDatabase(true);break;
        case "info": for(var i:b.getItems())i.getAutorouteInfo().setStartInfo(true);break;
        case "clearInfo": b.clearAllItemTemporaryAutorouteData();break;
        case "detach": b.searchTreeManager.remove(b.getItem(4));break;
        case "reattach": b.searchTreeManager.insert(b.getItem(4));break;
        case "snapshot": b.generateSnapshot();break;
        case "move": b.getItem(5).moveBy(new IntVector(17,-29));break;
        case "delete": b.removeItem(b.getItem(4));break;
        case "undo": return call(()->b.undo(changed));
        case "redo": return call(()->b.redo(changed));
        case "drills":case "smdDrills": {var e=current();var pages=(DrillPageArray)field(e,"drillPageArray");return call(()->{int n=0;for(var p:pages.overlappingPages(new IntBox(-8000,-8000,-7000,-7000)))n+=p.getDrills(e,op.equals("smdDrills")).size();return n;});}
        case "constructorFailure": {var via=b.rules.getDefaultViaRule().getVia(0);var pad=via.getPadstack();via.setPadstack(null);String result;try{result=call(()->engines.id(b.initAutoroute(1,3,null,null,false)));}finally{via.setPadstack(pad);}return result;}
        case "initFailure": {var item=b.getItem(5);var nets=item.netNumbers;item.netNumbers=null;String result;try{result=call(()->engines.id(b.initAutoroute(2,2,null,null,true)));}finally{item.netNumbers=nets;}return result;}
        default:throw new AssertionError(op);
      }return "ok";
    }
  }
  static String run(int scenario)throws Exception {
    int config=scenario%60;
    var com=new Communication(Unit.values()[config%4],1000+config,null,null,new ItemIdGenerator(),null);
    var seed=NeutralBoardModelOracle.seed(config,new PolylineShape[]{new IntBox(-9000,-9000,9000,9000)},com);var b=seed.board();
    var pins=(Package.Pin[])field(seed.front(),"pins");var p=pins[0];pins[0]=new Package.Pin(p.name,p.padstackId,new IntVector(-500,0),p.rotationInDegree);
    var created=new ArrayList<Item>();created.add(b.getOutline());created.add(b.insertPin(1,0,new int[]{1},1,FixedState.UNFIXED));created.add(b.insertPin(1,1,new int[]{2},2,FixedState.USER_FIXED));
    created.add(b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(1000,-4000),new IntPoint(3000,-4000)}),0,23,new int[]{1},1,FixedState.UNFIXED));
    created.add(b.insertVia(seed.via(),new IntPoint(1000,-2000),new int[]{1},1,FixedState.UNFIXED,true));
    var export=new NeutralBoardModel();var records=new ArrayList<Object>();export.metadata(b);for(var i:created)records.add(NeutralBoardItemsOracle.describe(export,i));
    Object initial=NeutralBoardItemsOracle.freeze(NeutralBoardModel.obj("version","NBF2","metadata",export.metadata(b),"communication",NeutralBoardItemsOracle.communication(b),"items",records));
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();
    var r=new Entry(b,seed.observers(),export);var observations=new ArrayList<Object>();observations.add(NeutralBoardModel.obj("result","initial","observation",r.snapshot()));
    var ops=List.of("retain","init:1:1:true","info","drills","drills","init:1:1:true","init:2:1:true","smdDrills","snapshot","move","delete","undo","redo","undo","info","detach","clearInfo","reattach","init:1:1:false","init:2:1:true","info","constructorFailure","init:1:1:false","initFailure","init:1:2:true","finish","finish","init:2:1:true","drills","drop","retain","init:1:1:true","finish");
    if(scenario>=60)ops=List.of("init:1:1:true","complete","info","drills","drills","snapshot","move","undo","init:2:1:true","smdDrills","finishFault","repairFault","finish","init:1:1:true","complete","drop","init:1:1:true","finish");
    if(scenario>=72)ops=List.of("unboundInsert","info","init:2:1:true","snapshot","moveUnbound","undo","redo","finish");
    for(String op:ops){System.err.println("config="+config+" op="+op);String result=r.op(op);observations.add(NeutralBoardModel.obj("result",result,"observation",r.snapshot()));}
    return NeutralBoardModel.JSON.toJson(NeutralBoardModel.obj("initial",initial,"operations",ops))+" | "+NeutralBoardModel.JSON.toJson(NeutralBoardModel.obj("config",scenario,"observations",observations));
  }
  public static void main(String[] args)throws Exception{try(var out=Files.newBufferedWriter(Path.of(args[1]))){for(String line:Files.readAllLines(Path.of(args[0])))out.write(run(Integer.parseInt(line))+"\n");}}
}
