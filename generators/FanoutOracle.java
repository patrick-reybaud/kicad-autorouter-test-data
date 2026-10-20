package app.freerouting.board.facade;

import app.freerouting.autoroute.pipeline.BatchFanout;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
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

/** Actual source BatchFanout.fanoutBoard with application-resolved settings and the fanout enabled (D119-1): per completed
 *  pass the source's own progress publication (counters, escape statistics), the board hash and a neutral snapshot, then
 *  the run summary and the decision hash. Wall-clock limits are frozen by the test clock (D115-1). */
public final class FanoutOracle extends BoardConstructionOracle {
  static final List<String> snapshots=new ArrayList<>();
  static final Map<String,Integer> snapshotIds=new HashMap<>();
  static final List<Object> timings=new ArrayList<>();
  static int snapshot(Object value){String s=value.toString();Integer id=snapshotIds.get(s);if(id!=null)return id;int n=snapshots.size();snapshots.add(s);snapshotIds.put(s,n);return n;}
  static final class Hashes {final Map<String,Integer> ids=new LinkedHashMap<>();int id(String h){return ids.computeIfAbsent(h,k->ids.size());}}
  static Object fanoutSettings(FanoutSettings f){
    if(f==null)return null;
    return obj("enabled",f.enabled,"maxPasses",f.maxPasses,"maxItems",f.maxItems,"maxMillisecondsPerPin",f.maxMillisecondsPerPin,"ripupAllowed",f.ripupAllowed,
      "minEscapeLengthMm",f.minEscapeLengthMm==null?null:bits(f.minEscapeLengthMm),"maxEscapeLengthMm",f.maxEscapeLengthMm==null?null:bits(f.maxEscapeLengthMm),
      "startViaDiameterMm",f.startViaDiameterMm==null?null:bits(f.startViaDiameterMm),"endViaDiameterMm",f.endViaDiameterMm==null?null:bits(f.endViaDiameterMm),
      "pinSortingOrder",f.pinSortingOrder,"fallbackToBoardVias",f.fallbackToBoardVias,"timeoutString",f.timeoutString);
  }
  static Object escape(BatchFanout.EscapeStatistics e){return e==null?null:list(e.totalSmdPins(),e.escapedCount(),bits(e.escapedPercentage()));}
  static Object run(Path root,JsonObject in)throws Exception {
    String boardName=in.get("board").getAsString();Path path=root.resolve(boardName).resolve("prepared").resolve(boardName+".dsn");
    var observer=new Observers();observer.constructionMetadata=true;RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,observer,new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success success))throw new IllegalStateException(read.toString());b=(RoutingBoard)success.board();}
    var exporter=new NeutralBoardModel();exporter.metadata(b);var created=new ArrayList<>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));for(var item:created)NeutralBoardItemsOracle.describe(exporter,item);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();var entry=new BoardEntryOracle.Entry(b,observer,exporter);int loaded=snapshot(entry.snapshot());
    var settings=PassRunnerOracle.prepare(b,path,in);
    settings.setFanoutEnabled(true);
    if(settings.fanout==null)settings.fanout=new FanoutSettings();
    if(in.has("maxPasses")&&!in.get("maxPasses").isJsonNull())settings.fanout.maxPasses=in.get("maxPasses").getAsInt();
    if(in.has("pinSortingOrder")&&!in.get("pinSortingOrder").isJsonNull())settings.fanout.pinSortingOrder=in.get("pinSortingOrder").getAsString();
    if(in.has("ripupAllowed")&&!in.get("ripupAllowed").isJsonNull())settings.fanout.ripupAllowed=in.get("ripupAllowed").getAsBoolean();
    int smdPins=0;for(var pin:b.getPins())if(pin.firstLayer()==pin.lastLayer())++smdPins;
    int initial=snapshot(entry.snapshot());
    var hashes=new Hashes();int hash0=hashes.id(b.getHash());
    var thread=new StoppableThread(){@Override protected void threadAction(){}};
    var passes=new ArrayList<Object>();final Exception[] failure={null};
    // Only the completed-pass publications are recorded: the interim ones are throttled by the wall clock.
    BatchFanout.FanoutProgressListener listener=status->{
      if(!status.passCompleted())return;
      try{
        passes.add(obj("pass",status.passNo(),"ripupCosts",status.ripupCosts(),"totalPins",status.totalPins(),"pinsToGo",status.pinsToGo(),"routed",status.routedCount(),"notRouted",status.notRoutedCount(),
          "insertErrors",status.insertErrorCount(),"extraViasThisPass",status.extraViasThisPass(),"extraViasTotal",status.extraViasTotal(),"escape",escape(status.escapeStatistics()),
          "vias",b.getVias().size(),"traces",b.getTraces().size(),"hash",hashes.id(b.getHash()),"board",snapshot(entry.snapshot())));
      }catch(Exception e){if(failure[0]==null)failure[0]=e;}
    };
    long start=System.nanoTime();Object summary=null;String error=null;
    try{
      var s=BatchFanout.fanoutBoard(b,settings,thread,listener);
      summary=obj("completedPasses",s.completedPassCount(),"escape",escape(s.escapeStatistics()),"timedOut",s.isTimedOut());
    }catch(Throwable e){error=ShapeEntryOracle.error(e);}
    long elapsed=System.nanoTime()-start;timings.add(list(in.get("case").getAsInt(),elapsed));
    if(failure[0]!=null)throw failure[0];
    String decision=DecisionHash.of(b);
    return obj("input",in,"resolvedSettings",ConnectionRouterOracle.resolved(b,settings),"fanoutSettings",fanoutSettings(settings.fanout),"smdPins",smdPins,"loaded",loaded,"initial",initial,"initialHash",hash0,
      "passes",passes,"summary",summary,"error",error,"hashCount",hashes.ids.size(),"finalHash",hashes.id(b.getHash()),"decisionHash",decision,
      "vias",b.getVias().size(),"traces",b.getTraces().size(),"stats",PassRunnerOracle.stats(new BoardStatistics(b),settings.scoring),"board",snapshot(entry.snapshot()),
      "source","Public board prepared by the actual application post-load path, fanout enabled, routed by the actual BatchFanout.fanoutBoard with a frozen test clock; completed-pass progress publications, board hashes by first-occurrence index, neutral snapshots interned");
  }
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{var p=(PolygonShape)s;String value="P:"+p.corners.length;for(var c:p.corners)value+="~"+point(c);return value;};
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var runs=new ArrayList<Object>();
    for(var c:input.getAsJsonArray("cases")){System.err.println("fanout case "+c);runs.add(run(Path.of(args[1]),c.getAsJsonObject()));}
    try(var out=Files.newBufferedWriter(Path.of(args[2]))){out.write("{\"cases\":"+NeutralBoardModel.JSON.toJson(runs)+",\"snapshots\":[");for(int i=0;i<snapshots.size();i++){if(i>0)out.write(",");out.write(snapshots.get(i));}out.write("],\"nativeRoutingExecuted\":false}\n");}
    Files.writeString(Path.of(args[3]),NeutralBoardModel.JSON.toJson(obj("timings",timings))+"\n");
  }
}
