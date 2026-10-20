package app.freerouting.board.facade;

import app.freerouting.board.actions.*;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;
import static app.freerouting.board.facade.CollisionChecksOracle.json;
import static app.freerouting.board.facade.CollisionChecksOracle.xy;
import static app.freerouting.board.facade.CollisionChecksOracle.snapshot;

/** Invoke actual source board-search methods. Queries are unit inputs, never routing output. */
public final class RoutingSearchOracle extends BoardConstructionOracle {
  static Object facade(RoutingBoard board,Pool ids)throws Exception {
    var facade=field(board,"searchFacade");return obj("id",ids.id(facade),"ownerMatches",facade==null?null:field(facade,"board")==board);
  }
  static Object history(Path boardPath,int variant,JsonObject inputs)throws Exception {
    var o=new Observers();o.constructionMetadata=true;
    RoutingBoard b;try(var in=Files.newInputStream(boardPath)){var result=DsnReader.readBoard(in,o,new ItemIdGenerator(),boardPath.getFileName().toString());if(!(result instanceof BoardReadResult.Success success))throw new IllegalStateException(result.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);
    var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var changed=new TreeSet<Integer>();var facadeIds=new Pool();
    int initial=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()));Object initialFacade=facade(b,facadeIds);
    b.searchTreeManager.setClearanceCompensationUsed(variant%2!=0);
    var pin=(Pin)b.getItem(3);var center=(IntPoint)pin.getCenter();int own=pin.getNetNumber(0),foreign=own==1?2:1;
    Item added=null;
    if(variant/2>0){
      pin.netNumbers=new int[]{own,foreign};
      added=b.insertTraceWithoutCleaning(new Polyline(center,new IntPoint(center.x+50000,center.y)),0,100,new int[]{foreign},pin.clearanceClassIndex(),variant/2==1?FixedState.UNFIXED:FixedState.SHOVE_FIXED);
    }
    int prepared=snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()));Object preparedFacade=facade(b,facadeIds);
    var queries=new ArrayList<JsonObject>();var pins=new ArrayList<Pin>();for(var item:created)if(item instanceof Pin p)pins.add(p);
    for(var p:pins){
      var c=(IntPoint)p.getCenter();int net=p.netCount()>0?p.getNetNumber(0):1;
      for(int delta:new int[]{0,1,10000,-10000})for(boolean only:new boolean[]{false,true}){
        queries.add(json(obj("op","segment","a",xy(c),"b",list(c.x+delta,c.y),"layer",p.firstLayer(),"nets",new int[]{net},"width",100,"clearance",p.clearanceClassIndex(),"onlyFixed",only)));
      }
      for(int offset:new int[]{0,100,10000})for(Integer from:new Integer[]{null,p.getId(),3}){
        queries.add(json(obj("op","pick","point",list(c.x+offset,c.y),"layer",offset==100?-1:p.firstLayer(),"from",from)));
      }
    }
    for(var c:inputs.getAsJsonArray("polylines")){
      var p=c.getAsJsonObject();for(int index:new int[]{0,1,2,99})for(boolean only:new boolean[]{false,true})
        queries.add(json(obj("op","lineSegment","points",p.get("corners"),"index",index,"layer",p.get("layer"),"nets",new int[]{p.get("net").getAsInt()},"width",10,"clearance",1,"onlyFixed",only)));
    }
    var candidates=new ArrayList<Item>();candidates.addAll(created.subList(0,12));if(added!=null)candidates.add(added);
    for(var item:candidates){
      for(int net:new int[]{0,1,2,13})queries.add(json(obj("op","net","item",item.getId(),"net",net)));
      for(String mode:List.of("null","list","set"))for(int delta:new int[]{0,1,10000})
        queries.add(json(obj("op","move","item",item.getId(),"vector",list(delta,-delta),"collection",mode,"ignored",new int[]{3,4,3})));
    }
    queries.add(json(obj("op","segment","a",null,"b",xy(center),"layer",0,"nets",new int[0],"width",0,"clearance",0,"onlyFixed",false)));
    queries.add(json(obj("op","segment","a",xy(center),"b",null,"layer",0,"nets",new int[0],"width",0,"clearance",0,"onlyFixed",false)));
    queries.add(json(obj("op","pick","point",null,"layer",-1,"from",null)));
    var rows=new ArrayList<Object>();
    for(var q:queries){
      Object value=null;String failure=null;Collection<Item> ignored=null;
      try {
        String op=q.get("op").getAsString();
        switch(op){
          case "segment", "lineSegment" -> {
            int[] nets=NeutralBoardModel.JSON.fromJson(q.get("nets"),int[].class);int layer=q.get("layer").getAsInt(),width=q.get("width").getAsInt(),cl=q.get("clearance").getAsInt();boolean only=q.get("onlyFixed").getAsBoolean();
            if(op.equals("segment"))value=bits(b.checkTraceSegment(xy(q.get("a")),xy(q.get("b")),layer,nets,width,cl,only));
            else{var points=new ArrayList<Point>();for(var p:q.getAsJsonArray("points"))points.add(xy(p));var polyline=new Polyline(points.toArray(Point[]::new));var segment=new LineSegment(polyline,q.get("index").getAsInt());value=bits(b.checkTraceSegment(segment,layer,nets,width,cl,only));}
          }
          case "pick" -> {var from=q.get("from");Item item=b.pickNearestRoutingItem(xy(q.get("point")),q.get("layer").getAsInt(),from.isJsonNull()?null:b.getItem(from.getAsInt()));value=item==null?null:item.getId();}
          case "net" -> value=b.checkChangeNet(b.getItem(q.get("item").getAsInt()),q.get("net").getAsInt());
          case "move" -> {
            String mode=q.get("collection").getAsString();if(!mode.equals("null")){ignored=mode.equals("set")?new TreeSet<>():new LinkedList<>();for(var id:q.getAsJsonArray("ignored"))ignored.add(b.getItem(id.getAsInt()));}
            var v=q.getAsJsonArray("vector");value=b.checkMoveItem(b.getItem(q.get("item").getAsInt()),new IntVector(v.get(0).getAsInt(),v.get(1).getAsInt()),ignored);
          }
          default -> throw new AssertionError(op);
        }
      }catch(Throwable t){failure=ShapeEntryOracle.error(t);}
      rows.add(obj("input",q,"expected",obj("error",failure,"value",value,"ignored",ignored==null?null:ignored.stream().map(Item::getId).toList()),"facade",facade(b,facadeIds),"after",snapshot(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()))));
    }
    return obj("variant",variant,"initial",initial,"initialFacade",initialFacade,"prepared",prepared,"preparedFacade",preparedFacade,"rows",rows,"source","fr-ecc83: explicit synthetic search queries; optional trace obstacle is not routed output");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(int i=0;i<6;i++)histories.add(history(Path.of(args[1]),i,input));
    Files.writeString(Path.of(args[2]),NeutralBoardModel.JSON.toJson(obj("histories",histories,"snapshots",CollisionChecksOracle.snapshots,"nativeRoutingExecuted",false))+"\n");
  }
}
