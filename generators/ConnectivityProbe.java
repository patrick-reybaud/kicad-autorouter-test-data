package app.freerouting.board.facade;

import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.PipelineOracleBridge;
import app.freerouting.board.model.items.*;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.RouterSettings;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;

/**
 * Per-net incomplete connections (actual DesignRulesChecker airlines) of a prepared public board before and
 * after the actual batch loop, for the correspondence with KiCad's ratsnest. Diagnostic, not a decision oracle.
 */
public final class ConnectivityProbe extends BoardConstructionOracle {
  static Object incompletes(RoutingBoard b,ScoringOrNull unused)throws Exception{
    var drc=new DesignRulesChecker(b,null);drc.calculateAllIncompletes();
    var nets=new ArrayList<Object>();int total=0;
    for(int n=1;n<=b.rules.nets.maxNetNumber();n++){
      var net=b.rules.nets.get(n);var inc=drc.getNetIncompletes(n);
      var lines=new ArrayList<Object>();
      if(inc!=null)for(AirLine a:inc.getIncompletes())lines.add(list(a.fromItem.getClass().getSimpleName(),a.fromItem.getId(),a.fromCorner.x,a.fromCorner.y,a.toItem.getClass().getSimpleName(),a.toItem.getId(),a.toCorner.x,a.toCorner.y));
      int count=drc.getIncompleteCount(n);total+=count;
      int pins=0,traces=0,vias=0,areas=0;for(Item i:b.getConnectableItems(n)){if(i instanceof Pin)pins++;else if(i instanceof Trace)traces++;else if(i instanceof Via)vias++;else if(i instanceof ConductionArea)areas++;}
      if(count>0||pins>0)nets.add(obj("net",net.name,"number",n,"containsPlane",net.containsPlane(),"incomplete",count,"connectedGroups",inc==null?null:inc.getConnectedGroupCount(),"pins",pins,"traces",traces,"vias",vias,"conductionAreas",areas,"airlines",lines));
    }
    return obj("total",total,"nets",nets);
  }
  interface ScoringOrNull{}
  public static void main(String[] a)throws Exception{
    Path path=Path.of(a[0]);var in=JsonParser.parseString(a[2]).getAsJsonObject();
    RoutingBoard b=PipelineBenchmark.read(path);var settings=PassRunnerOracle.prepare(b,path,in);PipelineBenchmark.override(settings,in);
    var before=incompletes(b,null);
    var thread=new StoppableThread(){@Override protected void threadAction(){}};
    var router=new BatchAutorouter(thread,b,settings,!settings.isFanoutEnabled(),true,settings.getStartRipupCosts(),settings.tracePullTightAccuracy!=null?settings.tracePullTightAccuracy:500);
    var job=new RoutingJob();job.routerSettings=settings;job.board=b;PipelineOracleBridge.setJob(router,job);
    boolean value=router.runBatchLoop();var fb=PipelineOracleBridge.board(router);
    var after=incompletes(fb,null);
    Files.writeString(Path.of(a[1]),NeutralBoardModel.JSON.toJson(obj("input",in,"value",value,"sameBoard",fb==b,"before",before,"after",after))+"\n");
    System.err.println("connectivity probe: before "+((Map<?,?>)before).get("total")+" after "+((Map<?,?>)after).get("total"));
  }
}
