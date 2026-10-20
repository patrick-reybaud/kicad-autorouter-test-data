package app.freerouting.board.facade;

import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.PipelineOracleBridge;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.*;
import app.freerouting.settings.sources.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;

/** Actual source BatchAutorouter connection routing (AutorouteConnectionRouter) with application-resolved settings. */
public final class ConnectionRouterOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static final List<Object> timings=new ArrayList<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static Object metrics(RoutingBoard b){
    double length=0;int remaining=0,violations=0;
    for(var t:b.getTraces())length+=t.getLength();
    for(int n=1;n<=b.rules.nets.maxNetNumber();n++)remaining+=Math.max(0,b.getConnectedSets(n).size()-1);
    for(var i:b.getItems())violations+=i.clearanceViolationCount();
    return obj("traceCount",b.getTraces().size(),"viaCount",b.getVias().size(),"lengthBits",bits(length),"remainingTopologicalConnections",remaining,"itemClearanceViolationCount",violations);
  }
  static Object floatPoint(FloatPoint p){return p==null?null:list(bits(p.x),bits(p.y));}
  /** The actual application post-load path: DefaultSettings + DSN settings, board optimizations, overrides. */
  static RouterSettings prepare(RoutingBoard b,Path dsn,JsonObject in)throws Exception {
    RouterSettings merged;try(var f=Files.newInputStream(dsn)){merged=new SettingsMerger(new DefaultSettings(),new DsnFileSettings(f,dsn.getFileName().toString())).merge();}
    var job=new RoutingJob();job.routerSettings=merged;job.board=b;
    var manager=new HeadlessBoardManager(job);
    var boardField=HeadlessBoardManager.class.getDeclaredField("board");boardField.setAccessible(true);boardField.set(manager,b);
    var jobField=HeadlessBoardManager.class.getDeclaredField("routingJob");jobField.setAccessible(true);jobField.set(manager,job);
    var apply=HeadlessBoardManager.class.getDeclaredMethod("applyRouterSettingsForLoadedBoard");apply.setAccessible(true);apply.invoke(manager);
    var post=HeadlessBoardManager.class.getDeclaredMethod("applyImmediatePostLoadProcessing");post.setAccessible(true);post.invoke(manager);
    merged.setFanoutEnabled(false);merged.setRunOptimizer(false);
    if(in.has("neckWidthUm")&&!in.get("neckWidthUm").isJsonNull())merged.neckWidthUm=in.get("neckWidthUm").getAsDouble();
    return merged;
  }
  static Object resolved(RoutingBoard b,RouterSettings s){
    var costs=new ArrayList<Object>();for(var c:s.getTraceCosts())costs.add(list(bits(c.horizontal()),bits(c.vertical())));
    var active=new ArrayList<Object>();var bend=new ArrayList<Object>();var pref=new ArrayList<Object>();var against=new ArrayList<Object>();
    for(int i=0;i<b.getLayerCount();i++){active.add(s.getLayerActive(i));bend.add(bits(s.getBendCost(i)));pref.add(bits(s.getPreferredDirectionTraceCosts(i)));against.add(bits(s.getAgainstPreferredDirectionTraceCosts(i)));}
    return obj("traceCosts",costs,"active",active,"bend",bend,"preferredDirectionTraceCosts",pref,"againstPreferredDirectionTraceCosts",against,
      "viaCosts",s.getViaCosts(),"planeViaCosts",s.getPlaneViaCosts(),"viasAllowed",s.getViasAllowed(),"neckdown",s.getAutomaticNeckdown(),"startRipupCosts",s.getStartRipupCosts(),
      "neckWidthUm",bits(s.getNeckWidthUm()),"strictDrc",s.isStrictDrc(),"layerCount",s.getLayerCount(),"maxPasses",s.maxPasses,"maxItems",s.maxItems,"runRouter",s.getRunRouter(),
      "fanoutEnabled",s.isFanoutEnabled(),"saveIntermediateStages",s.saveIntermediateStages,"tracePullTightAccuracy",s.tracePullTightAccuracy,
      "fanoutMinEscapeLengthMm",s.fanout==null||s.fanout.minEscapeLengthMm==null?null:bits(s.fanout.minEscapeLengthMm),"fanoutMaxEscapeLengthMm",s.fanout==null||s.fanout.maxEscapeLengthMm==null?null:bits(s.fanout.maxEscapeLengthMm),
      "copperToEdgeClearanceUm",s.copperToEdgeClearanceUm==null?null:bits(s.copperToEdgeClearanceUm),"holeClearanceUm",s.holeClearanceUm==null?null:bits(s.holeClearanceUm),
      "scoring",obj("unroutedNetPenalty",bits(s.scoring.unroutedNetPenalty),"clearanceViolationPenalty",bits(s.scoring.clearanceViolationPenalty),"bendPenalty",bits(s.scoring.bendPenalty),"defaultPreferredDirectionTraceCost",bits(s.scoring.defaultPreferredDirectionTraceCost),"viaCosts",s.scoring.viaCosts));
  }
  static Object history(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var entry=new BoardEntryOracle.Entry(b,observer,exporter);int loaded=snapshot(entry.snapshot());
    int outlineClassBefore=b.getOutline().clearanceClassIndex();int holeBefore=b.rules.getHoleClearance();int classesBefore=b.rules.clearanceMatrix.getClassCount();int itemsBefore=b.getItems().size();
    var settings=prepare(b,path,in);
    var preparation=obj("outlineClassBefore",outlineClassBefore,"outlineClassAfter",b.getOutline().clearanceClassIndex(),"boardEdgeClass",b.rules.clearanceMatrix.getNo("board_edge"),"classesBefore",classesBefore,"classesAfter",b.rules.clearanceMatrix.getClassCount(),"holeBefore",holeBefore,"holeAfter",b.rules.getHoleClearance(),"itemsBefore",itemsBefore,"itemsAfter",b.getItems().size());
    int initial=snapshot(entry.snapshot());
    var thread=new StoppableThread(){@Override protected void threadAction(){}};
    boolean preferred=in.get("preferred").getAsBoolean();int passes=in.get("passes").getAsInt(),limit=in.get("limit").getAsInt();
    var router=new BatchAutorouter(thread,b,settings,!settings.isFanoutEnabled(),preferred,settings.getStartRipupCosts(),settings.tracePullTightAccuracy!=null?settings.tracePullTightAccuracy:500);
    var rows=new ArrayList<Object>();long maxElapsed=0;
    for(int pass=1;pass<=passes;pass++){
      var list=PipelineOracleBridge.autorouteItems(router,b);rows.add(obj("op","items","pass",pass,"items",ids(list)));
      int calls=0;
      for(var item:list){
        for(int i=0;i<item.netCount();i++){
          if(calls>=limit)break;
          var ripped=new TreeSet<Item>();var costs=new LinkedHashMap<Item,Integer>();
          long start=System.nanoTime();var result=PipelineOracleBridge.autorouteItem(router,item,item.getNetNumber(i),ripped,costs,pass);long elapsed=System.nanoTime()-start;maxElapsed=Math.max(maxElapsed,elapsed);
          var costRows=new ArrayList<Object>();var keys=new ArrayList<>(costs.keySet());keys.sort(Comparator.comparingInt(Item::getId));for(var k:keys)costRows.add(list(k.getId(),costs.get(k)));
          var air=PipelineOracleBridge.airLine(router);
          rows.add(obj("op","route","pass",pass,"item",item.getId(),"net",item.getNetNumber(i),"state",result.state.name(),"details",result.details,"ripped",ids(ripped),"costs",costRows,"airLine",air==null?null:list(floatPoint(air.a),floatPoint(air.b)),"getAirLine",router.getAirLine()!=null,"totalItemsRouted",PipelineOracleBridge.totalItemsRouted(router),"metrics",metrics(b),"board",snapshot(entry.snapshot())));timings.add(list(in.get("case").getAsInt(),pass,item.getId(),item.getNetNumber(i),elapsed));
          calls++;
          System.err.println("history="+in.get("case")+" board="+boardName+" pass="+pass+" item="+item.getId()+" net="+item.getNetNumber(i)+" state="+result.state+" ms="+(elapsed/1e6));
        }
        if(calls>=limit)break;
      }
    }
    return obj("input",in,"resolvedSettings",resolved(b,settings),"preparation",preparation,"loaded",loaded,"initial",initial,"rows",rows,"source","Public board prepared by the actual application post-load path, routed by actual BatchAutorouter.autorouteItem calls");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
    Files.writeString(Path.of(args[3]),NeutralBoardModel.JSON.toJson(obj("timings",timings))+"\n");
  }
}
