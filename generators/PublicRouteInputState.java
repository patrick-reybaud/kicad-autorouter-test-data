package app.freerouting.board.facade;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.nio.file.*;
import java.util.*;

/** One exporter/identity dictionary for both actual input and observation. No routing. */
public final class PublicRouteInputState extends BoardConstructionOracle {
  public static void main(String[] args)throws Exception {
    extraOutlineShape=s->{if(!(s instanceof PolygonShape p))throw new IllegalArgumentException("Unexpected outline");String value="P:"+p.corners.length;for(var corner:p.corners)value+="~"+point(corner);return value;};
    var o=new Observers();o.constructionMetadata=true;
    var loaded=DsnReader.readBoard(Files.newInputStream(Path.of(args[0])),o,new ItemIdGenerator(),Path.of(args[0]).getFileName().toString());
    if(!(loaded instanceof BoardReadResult.Success success))throw new IllegalStateException(loaded.toString());
    var b=(RoutingBoard)success.board();int constructionAngle=b.rules.getTraceAngleRestriction().ordinal();
    var exporter=new NeutralBoardModel();exporter.metadata(b);var records=new ArrayList<Object>();var created=new ArrayList<Item>(b.getItems());created.sort(Comparator.comparingInt(Item::getId));
    for(var item:created)records.add(NeutralBoardItemsOracle.describe(exporter,item));
    var input=NeutralBoardItemsOracle.freeze(NeutralBoardModel.obj("version","NBF2","metadata",exporter.metadata(b),"communication",NeutralBoardItemsOracle.communication(b),"items",records));
    items=new Pool();trees=new Pool();shapes=new Pool();arrays=new Pool();componentHistory=new Pool();
    var expected=NeutralBoardItemsOracle.snapshot(exporter,b,o,new TreeSet<Integer>(),List.of());
    Files.writeString(Path.of(args[1]),NeutralBoardModel.JSON.toJson(NeutralBoardModel.obj("constructionTraceAngleRestriction",constructionAngle,"observerConstructionMetadata",true,"routeExecuted",false,"input",input,"expected",expected))+"\n");
  }
}
