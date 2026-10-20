package app.freerouting.board.facade;
import app.freerouting.autoroute.maze.*;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.board.state.BoardObserverAdaptor;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import java.nio.file.*;
import java.util.*;
import static app.freerouting.board.facade.NeutralBoardModel.obj;
import static app.freerouting.board.facade.NeutralBoardModel.list;
/** Separate measurement driver: one low-level source attempt per eligible net, capped by input. */
public final class RouteBenchmark extends BoardConstructionOracle {
  // D162-7: observe the existing test-clock shadow without reading its clock.
  static long[] clockCounters() throws Exception {
    var cls=Class.forName("app.freerouting.datastructures.TimeLimit");
    try { return new long[]{cls.getField("constructions").getLong(null),cls.getField("checks").getLong(null),cls.getField("expirations").getLong(null)}; }
    catch(NoSuchFieldException ex) {
      if(!System.getProperty("freerouting.test.clock","wall").equals("wall"))throw ex;
      return null;
    }
  }
  public static void main(String[] args)throws Exception {
    Path path=Path.of(args[0]),out=Path.of(args[1]);int limit=Integer.parseInt(args[2]);
    RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,new BoardObserverAdaptor(),new ItemIdGenerator(),path.getFileName().toString());if(!(read instanceof BoardReadResult.Success s))throw new IllegalStateException(read.toString());b=(RoutingBoard)s.board();}
    var settings=new RouterSettings(b);settings.setFanoutEnabled(false);settings.setRunOptimizer(false);
    var costs=new ArrayList<Object>();for(var c:settings.getTraceCosts())costs.add(list(bits(c.horizontal()),bits(c.vertical())));var active=new ArrayList<Object>();var bend=new ArrayList<Object>();for(int l=0;l<b.getLayerCount();l++){active.add(settings.getLayerActive(l));bend.add(bits(settings.getBendCost(l)));}
    var resolved=obj("traceCosts",costs,"active",active,"bend",bend,"viaCosts",settings.getViaCosts(),"viasAllowed",settings.getViasAllowed(),"neckdown",settings.getAutomaticNeckdown(),"startRipupCosts",settings.getStartRipupCosts());
    var exporter=new NeutralBoardModel();exporter.metadata(b);var initial=new ArrayList<Item>(b.getItems());initial.sort(Comparator.comparingInt(Item::getId));var descriptors=new ArrayList<Object>();for(var item:initial)descriptors.add(NeutralBoardItemsOracle.describe(exporter,item));
    if(args.length>3&&args[3].equals("--prepare")){
      if(initial.isEmpty()||!(initial.get(0) instanceof app.freerouting.board.model.structure.BoardOutline))throw new IllegalStateException("Benchmark neutral constructor requires a leading outline");
      var model=obj("version","NBF2","metadata",exporter.metadata(b),"communication",NeutralBoardItemsOracle.communication(b),"items",descriptors);
      Files.writeString(out,NeutralBoardModel.JSON.toJson(obj("model",model,"resolvedSettings",resolved,"maxAttempts",limit,"angle",b.rules.getTraceAngleRestriction().name(),"sourceAccepted",true))+"\n");return;
    }
    var results=new ArrayList<Object>();int attempts=0;var before=clockCounters();long start=System.nanoTime();
    for(int net=1;net<=b.rules.nets.maxNetNumber()&&attempts<limit;net++){
      var available=b.getConnectableItems(net);if(available.isEmpty())continue;var first=available.iterator().next();var from=first.getConnectedSet(net);var to=first.getUnconnectedSet(net);if(to.isEmpty())continue;
      var ctrl=new AutorouteControl(b,net,settings,settings.getViaCosts(),settings.getTraceCosts());ctrl.removeUnconnectedVias=false;
      var engine=b.initAutoroute(net,ctrl.traceClearanceClassIndex,null,null,false);var ripped=new TreeSet<Item>();
      var result=engine.autorouteConnection(from,to,ctrl,ripped,null);attempts++;
      results.add(obj("net",net,"state",result.state.name(),"details",result.details,"seed",ctrl.ripupCosts,"ripped",ids(ripped)));
    }
    long elapsed=System.nanoTime()-start;
    var after=clockCounters();
    var work=before==null?obj("clock","wall"):obj("clock",System.getProperty("freerouting.test.clock","frozen"),
      "constructions",after[0]-before[0],"checks",after[1]-before[1],
      "clockReads",after[0]-before[0]+after[1]-before[1],"expirations",after[2]-before[2]);
    double length=0;int remaining=0,violations=0;for(var trace:b.getTraces())length+=trace.getLength();for(int net=1;net<=b.rules.nets.maxNetNumber();net++)remaining+=Math.max(0,b.getConnectedSets(net).size()-1);for(var item:b.getItems())violations+=item.clearanceViolationCount();
    var geometry=new ArrayList<Object>();for(var item:b.getItems())geometry.add(NeutralBoardItemsOracle.describe(exporter,item));
    var deterministic=obj("attempts",results,"traces",b.getTraces().size(),"vias",b.getVias().size(),"lengthBits",bits(length),"remainingTopologicalConnections",remaining,"itemClearanceViolationCount",violations,"items",geometry);
    Files.writeString(out,NeutralBoardModel.JSON.toJson(obj("deterministic",deterministic,"routeNanoseconds",elapsed,"timeLimit",work,"runtime","Java reference","scope","Low-level source calls; no batch pipeline, fanout or global optimizer; empty observer adapter; setup and final measurement outside route timer"))+"\n");
  }
}
