package app.freerouting.board.facade;

import app.freerouting.autoroute.ItemRouteResult;
import app.freerouting.autoroute.events.*;
import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.OptimizerOracleBridge;
import app.freerouting.autoroute.pipeline.PipelineOracleBridge;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;

/** Actual source BatchOptimizer.runBatchLoop (D120-1) on a board first routed by the actual batch loop with application-resolved
 *  settings, both under the frozen test clock (D115-1): per-item results of optRouteItem, per-pass returns of optRoutePass, task
 *  events with board hashes by first-occurrence index, the routed and final decision hashes, statistics and item geometry.
 *  The throttled progress publications (wall clock) are not recorded. */
public final class OptimizerOracle extends BoardConstructionOracle {
  static final List<Object> timings=new ArrayList<>();
  static final class Hashes {final Map<String,Integer> ids=new LinkedHashMap<>();int id(String h){return ids.computeIfAbsent(h,k->ids.size());}}
  static Object optimizerSettings(OptimizerSettings o){
    if(o==null)return null;
    return obj("enabled",o.enabled,"algorithm",o.algorithm,"maxPasses",o.maxPasses,"maxItems",o.maxItems,"maxThreads",o.maxThreads,
      "improvementThreshold",o.optimizationImprovementThreshold==null?null:PassRunnerOracle.fbits(o.optimizationImprovementThreshold),
      "maxConsecutiveFailures",o.maxConsecutiveFailures,"additionalRipupCostFactorAtStart",o.additionalRipupCostFactorAtStart,
      "traceRipupCostFactor",o.traceRipupCostFactor==null?null:PassRunnerOracle.fbits(o.traceRipupCostFactor),
      "maxAutoroutePasses",o.maxAutoroutePasses,"timeoutString",o.timeoutString);
  }
  static Object result(ItemRouteResult r){
    return list(r.itemId(),r.improved(),r.viaCount(),bits(r.traceLength()),r.incompleteCountBefore(),r.incompleteCount(),r.viaCountReduced(),bits(r.lengthReduced()),PassRunnerOracle.fbits(r.improvementPercentage()));
  }
  static List<Object> geometry(RoutingBoard b)throws Exception{
    var exporter=new NeutralBoardModel();exporter.metadata(b);
    var out=new ArrayList<Object>();for(var item:b.getItems())out.add(NeutralBoardItemsOracle.describe(exporter,item));return out;
  }
  static Object run(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var settings=PassRunnerOracle.prepare(b,path,in);
    if(in.has("routePasses")&&!in.get("routePasses").isJsonNull())settings.maxPasses=in.get("routePasses").getAsInt();
    var hashes=new Hashes();int hash0=hashes.id(b.getHash());
    var thread=new StoppableThread(){@Override protected void threadAction(){}};
    var router=new BatchAutorouter(thread,b,settings,!settings.isFanoutEnabled(),true,settings.getStartRipupCosts(),settings.tracePullTightAccuracy!=null?settings.tracePullTightAccuracy:500);
    var job=new RoutingJob();job.routerSettings=settings;job.board=b;job.thread=thread;PipelineOracleBridge.setJob(router,job);
    long routeStart=System.nanoTime();boolean routeValue=router.runBatchLoop();long routeNanos=System.nanoTime()-routeStart;
    RoutingBoard routed=PipelineOracleBridge.board(router);
    int routedHash=hashes.id(routed.getHash());String routedDecision=DecisionHash.of(routed);
    var routedStats=PassRunnerOracle.stats(new BoardStatistics(routed),settings.scoring);
    // Optimizer settings: the application defaults, with the case's pass and item limits.
    var o=settings.optimizer;
    if(in.has("optPasses")&&!in.get("optPasses").isJsonNull())o.maxPasses=in.get("optPasses").getAsInt();
    if(in.has("optMaxItems")&&!in.get("optMaxItems").isJsonNull())o.maxItems=in.get("optMaxItems").getAsInt();
    settings.setRunOptimizer(true);
    job.board=routed;job.thread=thread;
    var optimizer=new OptimizerOracleBridge.Observing(job);
    var tasks=new ArrayList<Object>();
    optimizer.addTaskStateChangedEventListener(new TaskStateChangedEventListener(){@Override public void onTaskStateChangedEvent(TaskStateChangedEvent e){tasks.add(list(e.getTaskState().name(),e.getPassNumber(),hashes.id(e.getBoardHash())));}});
    long optStart=System.nanoTime();String error=null;
    try{optimizer.runBatchLoop();}catch(Throwable e){error=ShapeEntryOracle.error(e);}
    long optNanos=System.nanoTime()-optStart;timings.add(list(in.get("case").getAsInt(),routeNanos,optNanos));
    var items=new ArrayList<Object>();for(var r:optimizer.items)items.add(obj("item",r.itemId,"preferred",r.withPreferredDirections,"ripupIncreased",r.ripupIncreasedBefore,"totalBefore",r.totalBefore,"result",result(r.result)));
    var passes=new ArrayList<Object>();for(var p:optimizer.passes)passes.add(obj("pass",p.passNo,"preferred",p.withPreferredDirections,"routeImproved",PassRunnerOracle.fbits(p.routeImproved),"ripupIncreasedAfter",p.ripupIncreasedAfter,"totalAfter",p.totalAfter,"hash",hashes.id(p.hashAfter)));
    RoutingBoard finalBoard=job.board;
    return obj("input",in,"resolvedSettings",ConnectionRouterOracle.resolved(b,settings),"optimizerSettings",optimizerSettings(o),"initialHash",hash0,
      "routeValue",routeValue,"routedHash",routedHash,"routedDecisionHash",routedDecision,"routedStats",routedStats,"routedVias",routed.getVias().size(),"routedTraces",routed.getTraces().size(),
      "tasks",tasks,"passes",passes,"items",items,"totalItemsOptimized",optimizer.totalItemsOptimized(),"ripupIncreasedFinal",optimizer.useIncreasedRipupCosts(),"timedOut",optimizer.isTimedOut(),"error",error,
      "hashCount",hashes.ids.size(),"finalHash",hashes.id(finalBoard.getHash()),"decisionHash",DecisionHash.of(finalBoard),
      "stats",PassRunnerOracle.stats(new BoardStatistics(finalBoard),settings.scoring),"vias",finalBoard.getVias().size(),"traces",finalBoard.getTraces().size(),"finalItems",geometry(finalBoard),
      "source","Public board prepared by the actual application post-load path, routed by the actual BatchAutorouter.runBatchLoop, then optimized by the actual BatchOptimizer.runBatchLoop (observing subclass), frozen test clock; per-item and per-pass decisions, task events, hashes by first-occurrence index, decision hashes");
  }
  public static void main(String[] args)throws Exception {
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var runs=new ArrayList<Object>();
    for(var c:input.getAsJsonArray("cases")){System.err.println("optimizer case "+c);runs.add(run(Path.of(args[1]),c.getAsJsonObject()));}
    Files.writeString(Path.of(args[2]),NeutralBoardModel.JSON.toJson(obj("cases",runs,"nativeRoutingExecuted",false))+"\n");
    Files.writeString(Path.of(args[3]),NeutralBoardModel.JSON.toJson(obj("timings",timings))+"\n");
  }
}
