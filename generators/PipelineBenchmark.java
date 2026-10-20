package app.freerouting.board.facade;
import app.freerouting.autoroute.events.*;
import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.PipelineOracleBridge;
import app.freerouting.autoroute.pipeline.TaskState;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;
/** Separate measurement driver: the actual complete batch loop with application-resolved settings. */
public final class PipelineBenchmark extends BoardConstructionOracle {
  /** Observation only: the separately compiled clock shadow supplies counters, never extra clock reads. */
  static long[] clockCounters() {
    try {
      var cls=Class.forName("app.freerouting.datastructures.TimeLimit");
      return new long[]{cls.getField("constructions").getLong(null),cls.getField("checks").getLong(null),cls.getField("expirations").getLong(null)};
    } catch(ReflectiveOperationException ex) { return null; }
  }
  static final class PassMeasurements {
    final List<Object> rows=new ArrayList<>();
    int activePass=0;
    long start;
    long[] counters;
    void finish(TaskState state) {
      long now=System.nanoTime();var end=clockCounters();
      if(activePass!=0) rows.add(obj("pass",activePass,"routeNanoseconds",now-start,
        "constructions",end==null?null:end[0]-counters[0],"checks",end==null?null:end[1]-counters[1],
        "clockReads",end==null?null:end[0]-counters[0]+end[1]-counters[1],
        "expirations",end==null?null:end[2]-counters[2],"endEvent",state.name()));
      activePass=0;
    }
    void begin(TaskState state,int pass) {
      if(state==TaskState.RUNNING){activePass=pass;counters=clockCounters();start=System.nanoTime();}
    }
  }
  static RoutingBoard read(Path path)throws Exception{try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,new app.freerouting.board.state.BoardObserverAdaptor(),new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success s))throw new IllegalStateException(read.toString());return (RoutingBoard)s.board();}}
  /** Bench overrides of the application-resolved settings (the same values are exported as resolvedSettings). */
  static void override(RouterSettings settings,JsonObject in){
    if(in.has("maxPasses")&&!in.get("maxPasses").isJsonNull())settings.maxPasses=in.get("maxPasses").getAsInt();
    if(in.has("strictDrc")&&!in.get("strictDrc").isJsonNull())settings.strictDrc=in.get("strictDrc").getAsBoolean();
    if(in.has("viasAllowed")&&!in.get("viasAllowed").isJsonNull())settings.setViasAllowed(in.get("viasAllowed").getAsBoolean());
    if(in.has("startRipupCosts")&&!in.get("startRipupCosts").isJsonNull())settings.setStartRipupCosts(in.get("startRipupCosts").getAsInt());
  }
  /** Failure log rows (item id, net, count, last state, last reason, last pass), ordered by item id. */
  static List<Object> failures(RoutingBoard b)throws Exception {
    var rows=new ArrayList<Object>();var ids=new TreeSet<Integer>();var map=(Map<?,?>)field(b.failureLog,"failures");for(var k:map.keySet())ids.add((Integer)k);
    for(int id:ids){var info=map.get(id);rows.add(list(id,field(info,"netNumber"),field(info,"failureCount"),String.valueOf(field(info,"lastFailureState")),field(info,"lastFailureReason"),field(info,"lastAttemptPass")));}
    return rows;
  }
  static Object run(Path path,JsonObject in)throws Exception {
    RoutingBoard b=read(path);var settings=PassRunnerOracle.prepare(b,path,in);var scoring=settings.scoring;
    override(settings,in);
    var thread=new StoppableThread(){@Override protected void threadAction(){}};
    var router=new BatchAutorouter(thread,b,settings,!settings.isFanoutEnabled(),true,settings.getStartRipupCosts(),settings.tracePullTightAccuracy!=null?settings.tracePullTightAccuracy:500);
    var job=new RoutingJob();job.routerSettings=settings;job.board=b;PipelineOracleBridge.setJob(router,job);
    var hashes=new BatchLoopOracle.Hashes();var tasks=new ArrayList<Object>();var passBoards=new ArrayList<Object>();
    var passMeasurements=new PassMeasurements();
    // Task events fire synchronously in the loop thread: router.board is then the board the pass starts on (a restored copy after
    // a restoration) or the final board. Its statistics are recorded as the observable state of every reference replacement.
    router.addTaskStateChangedEventListener(new TaskStateChangedEventListener(){@Override public void onTaskStateChangedEvent(TaskStateChangedEvent e){
      passMeasurements.finish(e.getTaskState());
      int id=hashes.id(e.getBoardHash());tasks.add(list(e.getTaskState().name(),e.getPassNumber(),id));
      // No statistics at STARTED on either side: the event hash is computed before the listener runs, but the statistics
      // fill the pre-calculated item caches that both hash profiles cover (D114-6); the loop's own first computation follows.
      // Decision hash (D115-3): canonical registry-resolved item state without caches; the event hash id is the cache hash.
      String decision;try{decision=DecisionHash.of(PipelineOracleBridge.board(router));}catch(Exception ex){throw new RuntimeException(ex);}
      passBoards.add(list(e.getTaskState().name(),e.getPassNumber(),id,e.getTaskState()==TaskState.STARTED?null:PassRunnerOracle.stats(new BoardStatistics(PipelineOracleBridge.board(router)),scoring),decision));
      passMeasurements.begin(e.getTaskState(),e.getPassNumber());}});
    // Distinct board objects seen by the progress events: every restoration replaces router.board by a fresh copy.
    var boards=new IdentityHashMap<RoutingBoard,Integer>();boards.put(b,0);
    router.addBoardUpdatedEventListener(new BoardUpdatedEventListener(){@Override public void onBoardUpdatedEvent(BoardUpdatedEvent e){boards.putIfAbsent(e.getBoard(),boards.size());}});
    // Statistics of the prepared board are taken on a separate instance: computing them on the routed board would fill
    // its pre-calculated item caches before the loop and change the hash of the STARTED event (D114-6).
    RoutingBoard before=read(path);PassRunnerOracle.prepare(before,path,in);override(settings,in);var statsBefore=PassRunnerOracle.stats(new BoardStatistics(before),scoring);before=null;
    long start=System.nanoTime();boolean value=router.runBatchLoop();long elapsed=System.nanoTime()-start;
    var finalBoard=PipelineOracleBridge.board(router);var exporter=new NeutralBoardModel();exporter.metadata(finalBoard);
    var geometry=new ArrayList<Object>();for(var item:finalBoard.getItems())geometry.add(NeutralBoardItemsOracle.describe(exporter,item));
    var deterministic=obj("value",value,"tasks",tasks,"passBoards",passBoards,"decisionHash",DecisionHash.of(finalBoard),"communication",NeutralBoardItemsOracle.communication(finalBoard),"stopRequested",thread.isStopRequested(),"stopAutoRouterRequested",thread.isStopAutoRouterRequested(),"totalItemsRouted",PipelineOracleBridge.totalItemsRouted(router),"incompleteCount",PipelineOracleBridge.calculateIncomplete(router),"stats",PassRunnerOracle.stats(new BoardStatistics(finalBoard),scoring),"metrics",PassRunnerOracle.metrics(finalBoard),"items",geometry);
    boards.putIfAbsent(finalBoard,boards.size());
    // The job's log entries (info and debug) carry the source's own restoration and stop messages.
    var log=new ArrayList<Object>();int restorations=0,finalRestorations=0;
    for(var e:FRLogger.getLogEntries().getEntries(null,(UUID)field(job,"id"))){String m=e.getMessage();log.add(e.getType().name()+" "+m);if(m.contains("Restoring an earlier board"))restorations++;if(m.contains("Restoring the best board as the final result"))finalRestorations++;}
    // Test clock of the shadowed TimeLimit (D114-5, D115-1): mode and read counters, present only with the shadow class.
    Object timeLimit=null;try{var cls=Class.forName("app.freerouting.datastructures.TimeLimit");var c=cls.getField("constructions");timeLimit=obj("clock",System.getProperty("freerouting.test.clock","frozen"),"constructions",c.get(null),"checks",cls.getField("checks").get(null),"expirations",cls.getField("expirations").get(null));}catch(NoSuchFieldException ex){timeLimit=obj("clock","wall");}
    return obj("deterministic",deterministic,"geometry",exporter.geometry,"failures",failures(finalBoard),"boardObjects",boards.size(),"finalBoardIsInitial",finalBoard==b,"statsBefore",statsBefore,"log",log,"restorations",restorations,"finalRestorations",finalRestorations,"timeLimit",timeLimit,"routeNanoseconds",elapsed,"passes",tasks.size()>0?((List<?>)tasks.get(tasks.size()-1)).get(1):null,"executedPasses",passMeasurements.rows.size(),"passMeasurements",passMeasurements.rows);
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(app.freerouting.geometry.planar.PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    Path path=Path.of(args[0]),out=Path.of(args[1]);var in=JsonParser.parseString(args[2]).getAsJsonObject();
    if(args.length>3&&args[3].equals("--prepare")){
      RoutingBoard b=read(path);var settings=PassRunnerOracle.prepare(b,path,in);override(settings,in);
      // Export the neutral model before the application overrides so the native replay applies them itself.
      RoutingBoard fresh=read(path);var exporter=new NeutralBoardModel();exporter.metadata(fresh);var initial=new ArrayList<Item>(fresh.getItems());initial.sort(Comparator.comparingInt(Item::getId));var descriptors=new ArrayList<Object>();for(var item:initial)descriptors.add(NeutralBoardItemsOracle.describe(exporter,item));
      var model=obj("version","NBF2","metadata",exporter.metadata(fresh),"communication",NeutralBoardItemsOracle.communication(fresh),"items",descriptors);
      Files.writeString(out,NeutralBoardModel.JSON.toJson(obj("model",model,"resolvedSettings",ConnectionRouterOracle.resolved(b,settings),"input",in,"sourceAccepted",true))+"\n");return;
    }
    int runs=args.length>3?Integer.parseInt(args[3]):1;var results=new ArrayList<Object>();
    for(int i=0;i<runs;i++){var r=run(path,in);results.add(r);System.err.println("run="+i+" ms="+(((Number)((Map<?,?>)r).get("routeNanoseconds")).longValue()/1e6)+" restorations="+((Map<?,?>)r).get("restorations")+"+"+((Map<?,?>)r).get("finalRestorations"));}
    Files.writeString(out,NeutralBoardModel.JSON.toJson(obj("runs",results,"runtime","Java reference","scope","Actual BatchAutorouter.runBatchLoop with application-resolved settings; fanout and optimizer disabled; monotonic elapsed time, engine clock reported separately; first run cold JIT, later runs warm in the same JVM"))+"\n");
  }
}
