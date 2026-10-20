package app.freerouting.board.facade;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.*;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.*;
import app.freerouting.io.specctra.DsnReader;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
/** Diagnostic: per-net incomplete counts, connected groups and ratsnest corners on a prepared board. */
public final class IncompletesProbe extends BoardConstructionOracle {
  public static void main(String[] args)throws Exception {
    Path path=Path.of(args[0]);RoutingBoard b;try(var f=Files.newInputStream(path)){var read=DsnReader.readBoard(f,new app.freerouting.board.state.BoardObserverAdaptor(),new ItemIdGenerator(),path.getFileName().toString());b=(RoutingBoard)((BoardReadResult.Success)read).board();}
    PassRunnerOracle.prepare(b,path,new JsonObject());
    var drc=new DesignRulesChecker(b,null);drc.calculateAllIncompletes();
    var inc=(Object[])field(drc,"netIncompletes");
    for(int n=1;n<=b.rules.nets.maxNetNumber();n++){
      var ni=(app.freerouting.drc.NetIncompletes)inc[n-1];
      var items=new ArrayList<String>();for(var item:b.getConnectableItems(n)){var corners=new ArrayList<String>();for(var c:item.getRatsnestCorners())corners.add(point(c));items.add(item.getId()+":"+item.getClass().getSimpleName()+":tail="+item.isTail()+":contacts="+item.getNormalContacts().size()+":corners="+corners+":set="+ids(item.getConnectedSet(n)));}
      System.out.println("net "+n+" count="+ni.count()+" groups="+ni.getConnectedGroupCount()+" items="+items);
    }
    System.out.println("total "+drc.getIncompleteCount()+" max "+drc.maxConnections);
  }
}
