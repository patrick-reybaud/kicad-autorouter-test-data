package app.freerouting.board.facade;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.nio.file.*;
import java.util.*;

/** Bench-only input preparation: calls the existing Java DSN reader, never routes. */
public final class PrepareFirstRouteInput extends BoardConstructionOracle {
  public static void main(String[] args)throws Exception {
    var observers=new Observers();
    var result=DsnReader.readBoard(Files.newInputStream(Path.of(args[0])),observers,new ItemIdGenerator(),"crossing-two-nets");
    if(!(result instanceof BoardReadResult.Success success))throw new IllegalStateException(result.toString());
    var board=(RoutingBoard)success.board();board.rules.setTraceAngleRestriction(AngleRestriction.NINETY_DEGREE);
    if(board.rules.nets.maxNetNumber()!=2 || board.getPins().size()!=4 || !board.getTraces().isEmpty() || !board.getVias().isEmpty())throw new IllegalStateException("Unexpected reduced input");
    var exporter=new NeutralBoardModel();exporter.metadata(board);var descriptors=new ArrayList<Object>();
    var created=new ArrayList<Item>(board.getItems());created.sort(Comparator.comparingInt(Item::getId));
    for(var item:created)descriptors.add(NeutralBoardItemsOracle.describe(exporter,item));
    Object input=NeutralBoardModel.obj("version","NBF2","metadata",exporter.metadata(board),"communication",NeutralBoardItemsOracle.communication(board),"items",descriptors);
    var pins=new ArrayList<Object>();for(var pin:board.getPins())pins.add(NeutralBoardModel.obj("id",pin.getId(),"component",pin.getComponentId(),"pinIndex",pin.getPinIndex(),"center",point(pin.getCenter()),"nets",pin.netNumbers,"firstLayer",pin.firstLayer(),"lastLayer",pin.lastLayer()));
    Object record=NeutralBoardModel.obj("status","input candidate; native replay and routing not executed","sourceReader","pinned support jar; io/specctra remains outside the native corpus","warnings",success.warnings(),"angleRestriction","NINETY_DEGREE","nets",board.rules.nets.maxNetNumber(),"pins",pins,"initialTracks",board.getTraces().size(),"initialVias",board.getVias().size(),"initialRevision",board.getRevision(),"input",input);
    Files.writeString(Path.of(args[1]),NeutralBoardModel.JSON.toJson(record)+"\n");
  }
}
