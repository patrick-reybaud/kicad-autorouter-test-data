package app.freerouting.board.facade;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import java.nio.file.*;
import java.util.*;

/** Real reference-engine execution only. Not a native routing comparison. */
public final class PublicRouteJavaBaseline extends BoardConstructionOracle {
  public static void main(String[] args)throws Exception {
    var traceMessages=new ArrayList<String>();String tracePath=System.getProperty("oracle.trace.file");
    if(tracePath!=null){
      var logger=(org.apache.logging.log4j.core.Logger)app.freerouting.logger.FRLogger.getLogger();
      var sink=new org.apache.logging.log4j.core.appender.AbstractAppender("oracle-trace",null,org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout(),true,org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY){
        @Override public void append(org.apache.logging.log4j.core.LogEvent event){traceMessages.add(event.getMessage().getFormattedMessage());}
      };sink.start();logger.addAppender(sink);logger.setAdditive(false);logger.setLevel(org.apache.logging.log4j.Level.TRACE);
    }
    extraOutlineShape=s->{if(!(s instanceof app.freerouting.geometry.planar.PolygonShape p))throw new IllegalArgumentException("Unexpected outline shape");String value="P:"+p.corners.length;for(var point:p.corners)value+="~"+point(point);return value;};
    var o=new Observers();var loaded=DsnReader.readBoard(Files.newInputStream(Path.of(args[0])),o,new ItemIdGenerator(),Path.of(args[0]).getFileName().toString());
    if(!(loaded instanceof BoardReadResult.Success success))throw new IllegalStateException(loaded.toString());
    var b=(RoutingBoard)success.board();
    if(b.rules.nets.maxNetNumber()!=13||b.getPins().size()!=33||!b.getTraces().isEmpty()||!b.getVias().isEmpty())throw new IllegalStateException("Unexpected input");
    var settings=new RouterSettings(b);settings.setFanoutEnabled(false);settings.setRunOptimizer(false);
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();
    var exporter=new NeutralBoardModel();var changed=new TreeSet<Integer>();var observations=new ArrayList<Object>();
    observations.add(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()));var results=new ArrayList<Object>();
    for(int net=1;net<=b.rules.nets.maxNetNumber();net++){
      Item start=b.getConnectableItems(net).iterator().next();var from=start.getConnectedSet(net);var to=start.getUnconnectedSet(net);
      if(to.isEmpty()){ results.add(NeutralBoardModel.obj("net",net,"skipped",true,"reason","source destination set empty","startItem",start.getId())); continue; }
      var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());ctrl.removeUnconnectedVias=false;
      if(ctrl.isFanout)throw new IllegalStateException("Fanout enabled");
      var engine=b.initAutoroute(net,ctrl.traceClearanceClassIndex,null,null,false);
      var ripped=new TreeSet<Item>();var result=engine.autorouteConnection(from,to,ctrl,ripped,null);
      double length=0;for(var trace:b.getTraces())length+=trace.getLength();
      results.add(NeutralBoardModel.obj("net",net,"startItem",start.getId(),"startSet",ids(from),"destinationSet",ids(to),"state",result.state.name(),"details",result.details,"rippedItems",ids(ripped),"traceCount",b.getTraces().size(),"viaCount",b.getVias().size(),"totalLengthBits",bits(length),"connectedSetCount",b.getConnectedSets(net).size(),"control",NeutralBoardModel.obj("clearance",ctrl.traceClearanceClassIndex,"widths",ctrl.traceHalfWidth,"active",ctrl.layerActive,"viasAllowed",ctrl.viasAllowed,"ripupCosts",ctrl.ripupCosts,"isFanout",ctrl.isFanout,"removeUnconnectedVias",ctrl.removeUnconnectedVias)));
      observations.add(NeutralBoardItemsOracle.snapshot(exporter,b,o,changed,List.of()));
    }
    if(tracePath!=null)Files.write(Path.of(tracePath),traceMessages);
    Files.writeString(Path.of(args[1]),NeutralBoardModel.JSON.toJson(NeutralBoardModel.obj("status","Java-only actual execution; no native route or parity claim","entry","AutorouteEngine.autorouteConnection","driverPolicy","One real AutorouteEngine.autorouteConnection call per nonempty net destination, ascending net numbers; first source Connectable item in Java natural order. No batch pipeline; native must compute and compare the same sets. No prepared path.","seedPolicy","MazeSearchEngine source constructor seeds Random with ctrl.ripupCosts (1000)","settings",NeutralBoardModel.JSON.toJsonTree(settings),"results",results,"observations",observations))+"\n");
  }
}
