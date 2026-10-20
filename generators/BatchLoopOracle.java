package app.freerouting.board.facade;

import app.freerouting.autoroute.BoardHistory;
import app.freerouting.autoroute.events.*;
import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.PipelineOracleBridge;
import app.freerouting.autoroute.pipeline.TaskState;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.geometry.planar.*;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;

/** Actual source BatchAutorouter.runBatchLoop and BoardHistory/getHash relations with application-resolved settings. */
public final class BatchLoopOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static final List<Object> timings=new ArrayList<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static final class Hashes {final Map<String,Integer> ids=new LinkedHashMap<>();int id(String h){return ids.computeIfAbsent(h,k->ids.size());}}
  static Object history(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var entry=new BoardEntryOracle.Entry(b,observer,exporter);int loaded=snapshot(entry.snapshot());
    var settings=PassRunnerOracle.prepare(b,path,in);var scoring=settings.scoring;
    if(in.has("maxPasses")&&!in.get("maxPasses").isJsonNull())settings.maxPasses=in.get("maxPasses").getAsInt();
    if(in.has("strictDrc")&&!in.get("strictDrc").isJsonNull())settings.strictDrc=in.get("strictDrc").getAsBoolean();
    int initial=snapshot(entry.snapshot());
    String mode=in.get("mode").getAsString();var hashes=new Hashes();
    if(mode.equals("history")){
      var rows=new ArrayList<Object>();var bh=new BoardHistory(scoring);
      String h0=b.getHash();rows.add(obj("op","hash","hash",hashes.id(h0),"again",hashes.id(b.getHash())));
      bh.add(b);rows.add(obj("op","add","size",bh.size(),"max",PassRunnerOracle.fbits(bh.getMaxScore()),"contains",bh.contains(b),"rank",bh.getRank(b)));
      var pin=b.getPins().iterator().next();double w=pin.minWidth();rows.add(obj("op","minWidth","value",bits(w),"hash",hashes.id(b.getHash()),"contains",bh.contains(b)));
      var trace=b.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(-30000,-20000),new IntPoint(-20000,-20000)}),0,500,new int[]{2},1,FixedState.UNFIXED);
      rows.add(obj("op","insert","hash",hashes.id(b.getHash()),"contains",bh.contains(b),"rank",bh.getRank(b)));
      bh.add(b);rows.add(obj("op","add2","size",bh.size(),"max",PassRunnerOracle.fbits(bh.getMaxScore()),"contains",bh.contains(b),"rank",bh.getRank(b)));
      b.removeItems(List.of(trace));rows.add(obj("op","remove","hash",hashes.id(b.getHash()),"contains",bh.contains(b),"rank",bh.getRank(b)));
      bh.add(b);rows.add(obj("op","add3","size",bh.size(),"max",PassRunnerOracle.fbits(bh.getMaxScore()),"rank",hashes.id(b.getHash())));
      var thread=new StoppableThread(){@Override protected void threadAction(){}};
      var router=new BatchAutorouter(thread,b,settings,!settings.isFanoutEnabled(),true,settings.getStartRipupCosts(),settings.tracePullTightAccuracy!=null?settings.tracePullTightAccuracy:500);
      var job=new RoutingJob();job.routerSettings=settings;job.board=b;PipelineOracleBridge.setJob(router,job);
      boolean more=PipelineOracleBridge.autoroutePass(router,1);
      rows.add(obj("op","pass","value",more,"hash",hashes.id(b.getHash()),"contains",bh.contains(b),"rank",bh.getRank(b),"size",bh.size()));
      bh.add(b);rows.add(obj("op","add4","size",bh.size(),"max",PassRunnerOracle.fbits(bh.getMaxScore()),"rank",bh.getRank(b),"stats",PassRunnerOracle.stats(new BoardStatistics(b),scoring)));
      bh.remove(b);rows.add(obj("op","removeHistory","size",bh.size(),"contains",bh.contains(b),"max",PassRunnerOracle.fbits(bh.getMaxScore())));
      bh.clear();rows.add(obj("op","clear","size",bh.size(),"max",PassRunnerOracle.fbits(bh.getMaxScore()),"board",snapshot(entry.snapshot())));
      return obj("input",in,"resolvedSettings",ConnectionRouterOracle.resolved(b,settings),"loaded",loaded,"initial",initial,"rows",rows,"hashCount",hashes.ids.size(),"source","Actual BoardHistory/getHash relations on a prepared public board; hash values are compared by first-occurrence index");
    }
    if(mode.equals("restore")){
      // Actual BoardHistory.restoreBoard/restoreBestBoard (BasicBoard.deserialize of serialize(false)), a further
      // autoroute pass on the restored board, restore-count limits, and the strict-DRC serialize/enforce/deserialize
      // round trip on a trace inserted in violation at fixed coordinates.
      var rows=new ArrayList<Object>();var bh=new BoardHistory(scoring);
      var thread=new StoppableThread(){@Override protected void threadAction(){}};
      var router=new BatchAutorouter(thread,b,settings,!settings.isFanoutEnabled(),true,settings.getStartRipupCosts(),settings.tracePullTightAccuracy!=null?settings.tracePullTightAccuracy:500);
      var job=new RoutingJob();job.routerSettings=settings;job.board=b;PipelineOracleBridge.setJob(router,job);
      bh.add(b);rows.add(obj("op","add","size",bh.size(),"max",PassRunnerOracle.fbits(bh.getMaxScore()),"hash",hashes.id(b.getHash())));
      boolean more=PipelineOracleBridge.autoroutePass(router,1);
      rows.add(obj("op","pass","value",more,"hash",hashes.id(b.getHash()),"stats",PassRunnerOracle.stats(new BoardStatistics(b),scoring),"board",snapshot(entry.snapshot())));
      bh.add(b);rows.add(obj("op","add2","size",bh.size(),"max",PassRunnerOracle.fbits(bh.getMaxScore()),"rank",bh.getRank(b)));
      RoutingBoard r1=bh.restoreBoard(3);entry.rebind(r1);
      rows.add(obj("op","restore","same",r1==b,"hash",hashes.id(r1.getHash()),"rank",bh.getRank(r1),"contains",bh.contains(r1),"maintaining",r1.isMaintainingAutorouteDatabase(),"maxId",r1.communication.idGenerator.maxGeneratedId(),"stats",PassRunnerOracle.stats(new BoardStatistics(r1),scoring),"board",snapshot(entry.snapshot())));
      PipelineOracleBridge.setBoard(router,r1);
      boolean more2=PipelineOracleBridge.autoroutePass(router,2);
      rows.add(obj("op","pass2","value",more2,"hash",hashes.id(r1.getHash()),"incomplete",PipelineOracleBridge.calculateIncomplete(router),"stats",PassRunnerOracle.stats(new BoardStatistics(r1),scoring),"board",snapshot(entry.snapshot())));
      var limit=new ArrayList<Object>();for(int i=0;i<5;i++){var r=bh.restoreBoard(3);limit.add(r==null?null:hashes.id(r.getHash()));}
      rows.add(obj("op","restoreLimit","hashes",limit,"size",bh.size()));
      var best=bh.restoreBestBoard();rows.add(obj("op","restoreBest","hash",best==null?null:hashes.id(best.getHash()),"rank",best==null?null:bh.getRank(best)));
      Pin p0=null;for(var pin:r1.getPins())if(pin.firstLayer()==0&&pin.netCount()>0){p0=pin;break;}
      int netA=p0.getNetNumber(0);int netB=netA%r1.rules.nets.maxNetNumber()+1;var box=p0.boundingBox();int y=(box.ll.y+box.ur.y)/2;
      int maxId=r1.communication.idGenerator.maxGeneratedId();byte[] snap=r1.serialize(false);
      var t=r1.insertTraceWithoutCleaning(new Polyline(new Point[]{new IntPoint(box.ll.x-2000,y),new IntPoint(box.ur.x+2000,y)}),0,500,new int[]{netB},1,FixedState.UNFIXED);
      rows.add(obj("op","violate","pin",items.id(p0),"netA",netA,"netB",netB,"trace",items.id(t),"violations",t.clearanceViolations().size(),"hash",hashes.id(r1.getHash())));
      var rejection=BatchAutorouter.enforceStrictDrc(r1,netB,maxId);
      rows.add(obj("op","enforce","state",rejection==null?null:rejection.state.name(),"details",rejection==null?null:rejection.details,"hash",hashes.id(r1.getHash()),"items",r1.getItems().size(),"board",snapshot(entry.snapshot())));
      RoutingBoard r2=(RoutingBoard)BasicBoard.deserialize(snap);entry.rebind(r2);
      rows.add(obj("op","deserialize","same",r2==r1,"hash",hashes.id(r2.getHash()),"maxId",r2.communication.idGenerator.maxGeneratedId(),"items",r2.getItems().size(),"board",snapshot(entry.snapshot())));
      bh.clear();
      return obj("input",in,"resolvedSettings",ConnectionRouterOracle.resolved(b,settings),"loaded",loaded,"initial",initial,"rows",rows,"hashCount",hashes.ids.size(),"source","Actual BoardHistory.restoreBoard/restoreBestBoard, BasicBoard.serialize(false)/deserialize and enforceStrictDrc on a prepared public board; restored boards are snapshotted with the original identity pools; hash values are compared by first-occurrence index");
    }
    var thread=new StoppableThread(){@Override protected void threadAction(){}};
    var router=new BatchAutorouter(thread,b,settings,!settings.isFanoutEnabled(),true,settings.getStartRipupCosts(),settings.tracePullTightAccuracy!=null?settings.tracePullTightAccuracy:500);
    var job=new RoutingJob();job.routerSettings=settings;job.board=b;PipelineOracleBridge.setJob(router,job);
    var events=new ArrayList<Object>();final int[] currentPass={0};
    router.addTaskStateChangedEventListener(new TaskStateChangedEventListener(){@Override public void onTaskStateChangedEvent(TaskStateChangedEvent e){if(e.getTaskState()==TaskState.RUNNING)currentPass[0]=e.getPassNumber();events.add(obj("kind","task","state",e.getTaskState().name(),"pass",e.getPassNumber(),"hash",hashes.id(e.getBoardHash())));}});
    router.addBoardUpdatedEventListener(new BoardUpdatedEventListener(){@Override public void onBoardUpdatedEvent(BoardUpdatedEvent e){events.add(obj("kind","board","pass",currentPass[0],"stats",PassRunnerOracle.stats(e.getBoardStatistics(),scoring),"counters",PassRunnerOracle.counters(e.getRouterCounters()),"sameBoard",e.getBoard()==b));}});
    long start=System.nanoTime();Object value=null;String error=null;
    try{value=router.runBatchLoop();}catch(Throwable e){error=ShapeEntryOracle.error(e);}
    long elapsed=System.nanoTime()-start;timings.add(list(in.get("case").getAsInt(),elapsed));
    // Keep task events and, per pass, only the unconditional first and last board events.
    var kept=new ArrayList<Object>();Object lastBoard=null;int lastPass=-1;boolean firstOfPass=true;
    for(var raw:events){var e=(Map<?,?>)raw;if(e.get("kind").equals("task")){if(lastBoard!=null){kept.add(lastBoard);lastBoard=null;}kept.add(e);firstOfPass=true;continue;}
      int p=(Integer)e.get("pass");if(p!=lastPass){if(lastBoard!=null)kept.add(lastBoard);lastBoard=null;lastPass=p;firstOfPass=true;}
      if(firstOfPass){kept.add(obj("kind","board","position","first","pass",p,"stats",e.get("stats"),"counters",e.get("counters"),"sameBoard",e.get("sameBoard")));firstOfPass=false;lastBoard=null;}
      else lastBoard=obj("kind","board","position","last","pass",p,"stats",e.get("stats"),"counters",e.get("counters"),"sameBoard",e.get("sameBoard"));}
    if(lastBoard!=null)kept.add(lastBoard);
    var finalBoard=PipelineOracleBridge.board(router);
    var failures=new ArrayList<Object>();var infoIds=new TreeSet<Integer>();var failMap=(Map<?,?>)field(finalBoard.failureLog,"failures");for(var k:failMap.keySet())infoIds.add((Integer)k);
    for(int id:infoIds){var info=failMap.get(id);failures.add(list(id,field(info,"netNumber"),field(info,"failureCount"),String.valueOf(field(info,"lastFailureState")),field(info,"lastFailureReason"),field(info,"lastAttemptPass")));}
    var result=obj("value",value,"error",error,"sameBoardObject",finalBoard==b,"stopRequested",thread.isStopRequested(),"stopAutoRouterRequested",thread.isStopAutoRouterRequested(),"totalItemsRouted",PipelineOracleBridge.totalItemsRouted(router),"initialUnroutedCount",router.getInitialUnroutedCount(),
      "events",kept,"failures",failures,"incompleteCount",PipelineOracleBridge.calculateIncomplete(router),"stats",PassRunnerOracle.stats(new BoardStatistics(finalBoard),scoring),"metrics",PassRunnerOracle.metrics(finalBoard),"finalHash",hashes.id(finalBoard.getHash()),"hashCount",hashes.ids.size(),"board",snapshot(entry.snapshot()));
    System.err.println("history="+in.get("case")+" board="+boardName+" maxPasses="+settings.maxPasses+" value="+value+" error="+error+" events="+kept.size()+" incomplete="+PipelineOracleBridge.calculateIncomplete(router)+" ms="+(elapsed/1e6));
    return obj("input",in,"resolvedSettings",ConnectionRouterOracle.resolved(b,settings),"loaded",loaded,"initial",initial,"result",result,"source","Public board prepared by the actual application post-load path, routed by the actual BatchAutorouter.runBatchLoop; progress events filtered to the unconditional first/last per pass; hashes compared by first-occurrence index");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var histories=new ArrayList<Object>();for(var c:input.getAsJsonArray("histories"))histories.add(history(Path.of(args[1]),c.getAsJsonObject()));
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"histories\":"+NeutralBoardModel.JSON.toJson(histories)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
    Files.writeString(Path.of(args[3]),NeutralBoardModel.JSON.toJson(obj("timings",timings))+"\n");
  }
}
