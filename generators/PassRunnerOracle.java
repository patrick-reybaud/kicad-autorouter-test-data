package app.freerouting.board.facade;

import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.PipelineOracleBridge;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.DesignRulesChecker;
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

/** Actual source single-threaded autoroute passes (AutoroutePassRunner) with application-resolved settings. */
public final class PassRunnerOracle extends BoardConstructionOracle {
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
  static String fbits(Float v){return v==null?null:Integer.toUnsignedString(Float.floatToIntBits(v),16);}
  static Object stats(BoardStatistics s,ScoringSettings scoring){
    var bb=s.board.boundingBox;var sz=s.board.size;
    return obj("boundingBox",list(fbits(bb.x),fbits(bb.y),fbits(bb.width),fbits(bb.height)),"size",list(fbits(sz.x),fbits(sz.y),fbits(sz.width),fbits(sz.height)),
      "layers",list(s.layers.totalCount,s.layers.signalCount),
      "items",list(s.items.totalCount,s.items.traceCount,s.items.viaCount,s.items.conductionAreaCount,s.items.drillItemCount,s.items.pinCount,s.items.componentOutlineCount,s.items.otherCount),
      "components",s.components.totalCount,"pads",s.pads.totalCount,"nets",list(s.nets.totalCount,s.nets.classCount),
      "connections",list(s.connections.maximumCount,s.connections.incompleteCount),
      "traces",list(s.traces.totalCount,s.traces.totalSegmentCount,fbits(s.traces.totalLength),fbits(s.traces.totalLengthMm),fbits(s.traces.averageLength),fbits(s.traces.totalHorizontalLength),fbits(s.traces.totalVerticalLength),fbits(s.traces.totalAngledLength),fbits(s.traces.totalWeightedLength)),
      "bends",list(s.bends.totalCount,s.bends.ninetyDegreeCount,s.bends.fortyFiveDegreeCount,s.bends.otherAngleCount),
      "vias",list(s.vias.totalCount,s.vias.throughHoleCount,s.vias.blindCount,s.vias.buriedCount),
      "clearanceViolations",list(s.clearanceViolations.totalCount,bits(s.clearanceViolations.minViolationUm),bits(s.clearanceViolations.maxViolationUm),bits(s.clearanceViolations.avgViolationUm)),
      "fanout",list(s.fanout.totalSmdPins,s.fanout.pinsToEscape,s.fanout.escapedCount),"unit",s.unit,
      "maximumScore",fbits(s.getMaximumScore(scoring)),"score",fbits(s.calculateScore(scoring)),"normalizedScore",fbits(s.getNormalizedScore(scoring)));
  }
  static Object counters(RouterCounters c){return obj("phase",c.phase,"passCount",c.passCount,"queued",c.queuedToBeRoutedCount,"routed",c.routedCount,"skipped",c.skippedCount,"ripped",c.rippedCount,"failed",c.failedToBeRoutedCount,"incomplete",c.incompleteCount,"fanoutExtraVias",c.fanoutExtraViasCount);}
  static RouterSettings prepare(RoutingBoard b,Path dsn,JsonObject in)throws Exception {
    RouterSettings merged;try(var f=Files.newInputStream(dsn)){merged=new SettingsMerger(new DefaultSettings(),new DsnFileSettings(f,dsn.getFileName().toString())).merge();}
    var job=new RoutingJob();job.routerSettings=merged;job.board=b;
    var manager=new HeadlessBoardManager(job);
    var boardField=HeadlessBoardManager.class.getDeclaredField("board");boardField.setAccessible(true);boardField.set(manager,b);
    var jobField=HeadlessBoardManager.class.getDeclaredField("routingJob");jobField.setAccessible(true);jobField.set(manager,job);
    var apply=HeadlessBoardManager.class.getDeclaredMethod("applyRouterSettingsForLoadedBoard");apply.setAccessible(true);apply.invoke(manager);
    var post=HeadlessBoardManager.class.getDeclaredMethod("applyImmediatePostLoadProcessing");post.setAccessible(true);post.invoke(manager);
    merged.setFanoutEnabled(false);merged.setRunOptimizer(false);
    if(in.has("maxItems")&&!in.get("maxItems").isJsonNull())merged.maxItems=in.get("maxItems").getAsInt();
    return merged;
  }
  static Object history(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var entry=new BoardEntryOracle.Entry(b,observer,exporter);int loaded=snapshot(entry.snapshot());
    var settings=prepare(b,path,in);var scoring=settings.scoring;
    int initial=snapshot(entry.snapshot());
    var thread=new StoppableThread(){@Override protected void threadAction(){}};
    var router=new BatchAutorouter(thread,b,settings,!settings.isFanoutEnabled(),true,settings.getStartRipupCosts(),settings.tracePullTightAccuracy!=null?settings.tracePullTightAccuracy:500);
    var job=new RoutingJob();job.routerSettings=settings;job.board=b;PipelineOracleBridge.setJob(router,job);
    var events=new ArrayList<Object>();
    router.addBoardUpdatedEventListener(new BoardUpdatedEventListener(){@Override public void onBoardUpdatedEvent(BoardUpdatedEvent e){events.add(obj("stats",stats(e.getBoardStatistics(),scoring),"counters",counters(e.getRouterCounters()),"sameBoard",e.getBoard()==b));}});
    var initialStats=stats(new BoardStatistics(b),scoring);
    var rows=new ArrayList<Object>();int passes=in.get("passes").getAsInt();
    for(int pass=1;pass<=passes;pass++){
      events.clear();var itemsBefore=ids(PipelineOracleBridge.autorouteItems(router,b));
      long start=System.nanoTime();boolean more=PipelineOracleBridge.autoroutePass(router,pass);long elapsed=System.nanoTime()-start;
      timings.add(list(in.get("case").getAsInt(),pass,elapsed));
      var failures=new ArrayList<Object>();var infoIds=new TreeSet<Integer>();var failMap=(Map<?,?>)field(b.failureLog,"failures");for(var k:failMap.keySet())infoIds.add((Integer)k);
      for(int id:infoIds){var info=failMap.get(id);failures.add(list(id,field(info,"netNumber"),field(info,"failureCount"),String.valueOf(field(info,"lastFailureState")),field(info,"lastFailureReason"),field(info,"lastAttemptPass")));}
      var after=new BoardStatistics(b);
      rows.add(obj("pass",pass,"itemsBefore",itemsBefore,"value",more,"eventCount",events.size(),"firstEvent",events.isEmpty()?null:events.get(0),"lastEvent",events.isEmpty()?null:events.get(events.size()-1),
        "totalItemsRouted",PipelineOracleBridge.totalItemsRouted(router),"stopRequested",thread.isStopRequested(),"stopAutoRouterRequested",thread.isStopAutoRouterRequested(),"failures",failures,
        "incompleteCount",PipelineOracleBridge.calculateIncomplete(router),"stats",stats(after,scoring),"metrics",metrics(b),"airLine",router.getAirLine()!=null,"board",snapshot(entry.snapshot())));
      System.err.println("history="+in.get("case")+" board="+boardName+" pass="+pass+" more="+more+" events="+events.size()+" incomplete="+after.connections.incompleteCount+" score="+after.getNormalizedScore(scoring)+" ms="+(elapsed/1e6));
      if(!more)break;
    }
    return obj("input",in,"resolvedSettings",ConnectionRouterOracle.resolved(b,settings),"loaded",loaded,"initial",initial,"initialStats",initialStats,"rows",rows,"source","Public board prepared by the actual application post-load path, routed by actual BatchAutorouter.autoroutePass calls");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
    Files.writeString(Path.of(args[3]),NeutralBoardModel.JSON.toJson(obj("timings",timings))+"\n");
  }
}
