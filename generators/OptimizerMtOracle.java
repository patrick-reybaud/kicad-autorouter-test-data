package app.freerouting.board.facade;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.autoroute.events.*;
import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.OptimizerMtOracleBridge;
import app.freerouting.autoroute.pipeline.PipelineOracleBridge;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;

/** Actual source BatchOptimizerMultiThreaded.runBatchLoop (J4, tranche 131) on a board first routed by the actual batch loop with
 *  application-resolved settings, both under the frozen test clock (D115-1): one worker thread, results processed in scheduling
 *  order once every task of the pass is constructed (OptimizerMtOracleBridge), the board-updated listener of the source's GUI
 *  worker (the optimizer's board replaces the job's board). Per-task results and winners, per-pass returns, board-updated events
 *  with board hashes by first-occurrence index, the routed and final decision hashes, statistics and item geometry. The throttled
 *  progress publications of the task optimizers (wall clock, no listeners) are not observable and not recorded. */
public final class OptimizerMtOracle extends BoardConstructionOracle {
  static final List<Object> timings=new ArrayList<>();
  static Object run(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var settings=PassRunnerOracle.prepare(b,path,in);
    if(in.has("routePasses")&&!in.get("routePasses").isJsonNull())settings.maxPasses=in.get("routePasses").getAsInt();
    var hashes=new OptimizerOracle.Hashes();int hash0=hashes.id(b.getHash());
    var thread=new StoppableThread(){@Override protected void threadAction(){}};
    var router=new BatchAutorouter(thread,b,settings,!settings.isFanoutEnabled(),true,settings.getStartRipupCosts(),settings.tracePullTightAccuracy!=null?settings.tracePullTightAccuracy:500);
    var job=new RoutingJob();job.routerSettings=settings;job.board=b;job.thread=thread;PipelineOracleBridge.setJob(router,job);
    long routeStart=System.nanoTime();boolean routeValue=router.runBatchLoop();long routeNanos=System.nanoTime()-routeStart;
    RoutingBoard routed=PipelineOracleBridge.board(router);
    int routedHash=hashes.id(routed.getHash());String routedDecision=DecisionHash.of(routed);
    var routedStats=PassRunnerOracle.stats(new BoardStatistics(routed),settings.scoring);
    // Optimizer settings: the application defaults, with the case's pass and item limits, one worker thread and the case's strategies.
    var o=settings.optimizer;
    if(in.has("optPasses")&&!in.get("optPasses").isJsonNull())o.maxPasses=in.get("optPasses").getAsInt();
    if(in.has("optMaxItems")&&!in.get("optMaxItems").isJsonNull())o.maxItems=in.get("optMaxItems").getAsInt();
    o.maxThreads=1;
    o.boardUpdateStrategy=BoardUpdateStrategy.valueOf(in.get("updateStrategy").getAsString());
    o.itemSelectionStrategy=ItemSelectionStrategy.valueOf(in.get("selectionStrategy").getAsString());
    o.hybridRatio=in.has("hybridRatio")&&!in.get("hybridRatio").isJsonNull()?in.get("hybridRatio").getAsString():null;
    settings.setRunOptimizer(true);
    job.board=routed;job.thread=thread;
    var optimizer=new OptimizerMtOracleBridge.Observing(job);
    var tasks=new ArrayList<Object>();var events=new ArrayList<Object>();
    optimizer.addTaskStateChangedEventListener(new TaskStateChangedEventListener(){@Override public void onTaskStateChangedEvent(TaskStateChangedEvent e){tasks.add(list(e.getTaskState().name(),e.getPassNumber(),hashes.id(e.getBoardHash())));}});
    optimizer.addBoardUpdatedEventListener(new BoardUpdatedEventListener(){@Override public void onBoardUpdatedEvent(BoardUpdatedEvent e){
      boolean replacement=e.getRouterCounters()==null;
      events.add(list(replacement,replacement?null:e.getRouterCounters().passCount,hashes.id(e.getBoard().getHash()),e.getBoard()==job.board));
      job.board=e.getBoard(); // GuiRoutingJobWorker: the multi-threaded optimizer's board replaces the job's board
    }});
    long optStart=System.nanoTime();String error=null;
    try{optimizer.runBatchLoop();}catch(Throwable e){error=ShapeEntryOracle.error(e);}
    long optNanos=System.nanoTime()-optStart;timings.add(list(in.get("case").getAsInt(),routeNanos,optNanos));
    var taskRecords=new ArrayList<Object>();for(var t:optimizer.tasks)taskRecords.add(obj("pass",t.passNo,"order",t.order,"item",t.result.itemId(),"result",OptimizerOracle.result(t.result),"won",t.won,"hash",hashes.id(t.boardHash)));
    var passes=new ArrayList<Object>();for(var p:optimizer.passes)passes.add(obj("pass",p.passNo,"preferred",p.withPreferredDirections,"routeImproved",PassRunnerOracle.fbits(p.routeImproved),"tasks",p.numTasks,"finished",p.numTasksFinished,"ripupIncreasedAfter",p.ripupIncreasedAfter,"totalAfter",p.totalAfter,"hash",hashes.id(p.hashAfter)));
    RoutingBoard finalBoard=job.board;
    return obj("input",in,"resolvedSettings",ConnectionRouterOracle.resolved(b,settings),"optimizerSettings",OptimizerOracle.optimizerSettings(o),
      "strategies",obj("updateStrategy",o.boardUpdateStrategy.name(),"selectionStrategy",o.itemSelectionStrategy.name(),"hybridRatio",o.hybridRatio),"initialHash",hash0,
      "routeValue",routeValue,"routedHash",routedHash,"routedDecisionHash",routedDecision,"routedStats",routedStats,"routedVias",routed.getVias().size(),"routedTraces",routed.getTraces().size(),
      "tasks",tasks,"events",events,"passes",passes,"taskResults",taskRecords,"totalItemsOptimized",optimizer.totalItemsOptimized(),"ripupIncreasedFinal",optimizer.useIncreasedRipupCosts(),"timedOut",optimizer.isTimedOut(),"error",error,
      "finalBoardIsOptimizerBoard",finalBoard==optimizer.board(),"hashCount",hashes.ids.size(),"finalHash",hashes.id(finalBoard.getHash()),"decisionHash",DecisionHash.of(finalBoard),
      "stats",PassRunnerOracle.stats(new BoardStatistics(finalBoard),settings.scoring),"vias",finalBoard.getVias().size(),"traces",finalBoard.getTraces().size(),"finalItems",OptimizerOracle.geometry(finalBoard),
      "source","Public board prepared by the actual application post-load path, routed by the actual BatchAutorouter.runBatchLoop, then optimized by the actual BatchOptimizerMultiThreaded.runBatchLoop (observing subclass, one worker thread, results processed after the construction loop of each pass, GUI board replacement listener), frozen test clock; per-task and per-pass decisions, board-updated and task events, hashes by first-occurrence index, decision hashes");
  }
  public static void main(String[] args)throws Exception {
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var runs=new ArrayList<Object>();
    for(var c:input.getAsJsonArray("cases")){System.err.println("optimizer-mt case "+c);runs.add(run(Path.of(args[1]),c.getAsJsonObject()));}
    Files.writeString(Path.of(args[2]),NeutralBoardModel.JSON.toJson(obj("cases",runs,"nativeRoutingExecuted",false))+"\n");
    Files.writeString(Path.of(args[3]),NeutralBoardModel.JSON.toJson(obj("timings",timings))+"\n");
  }
}
