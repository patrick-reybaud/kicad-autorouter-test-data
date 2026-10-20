package app.freerouting.board.facade;

import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.nio.file.*;
import java.util.*;

/** Corpus admission and source-quality observations only; never fabricates or routes a path. */
public final class PublicBoardProbe extends BoardConstructionOracle {
  public static void main(String[] args) throws Exception {
    var report = new LinkedHashMap<String,Object>();
    report.put("purpose", "public-corpus-admission-and-initial-quality");
    report.put("routingExecuted", false);
    long started = System.nanoTime();
    var observers = new Observers();
    try (var input = Files.newInputStream(Path.of(args[0]))) {
      var result = DsnReader.readBoard(input, observers, new ItemIdGenerator(), Path.of(args[0]).getFileName().toString());
      report.put("loadNanoseconds", System.nanoTime() - started);
      if (!(result instanceof BoardReadResult.Success success)) {
        report.put("accepted", false);
        report.put("result", result.toString());
      } else {
        var board = (RoutingBoard)success.board();
        report.put("accepted", true);
        report.put("warnings", success.warnings());
        report.put("angleRestriction", board.rules.getTraceAngleRestriction().name());
        report.put("layers", Arrays.stream(board.layerStructure.layers).map(layer -> NeutralBoardModel.obj("name", layer.name, "signal", layer.isSignal)).toList());
        report.put("nets", board.rules.nets.maxNetNumber());
        report.put("pins", board.getPins().size());
        report.put("traces", board.getTraces().size());
        report.put("vias", board.getVias().size());
        report.put("items", board.getItems().size());
        report.put("revision", board.getRevision());
        report.put("metadata", new NeutralBoardModel().metadata(board));
        if (args.length > 2 && args[2].equals("--pad-semantics")) {
          var pinShapes = new ArrayList<Object>();
          for (var pin : board.getPins()) {
            var shapes = new ArrayList<Object>();
            for (int layer = pin.firstLayer(); layer <= pin.lastLayer(); ++layer) {
              var shape = pin.getShape(layer - pin.firstLayer());
              if (shape != null) {
                var box = shape.boundingBox();
                shapes.add(NeutralBoardModel.list(layer, box.ll.x, box.ll.y, box.ur.x, box.ur.y));
              }
            }
            pinShapes.add(NeutralBoardModel.obj("component", pin.componentName(),
                "center", NeutralBoardModel.xy(pin.getCenter()), "shapes", shapes,
                "nets", pin.netNumbers));
          }
          report.put("padSemantics", pinShapes);
        }
        // Write admission before the potentially expensive quality inspection.
        Files.writeString(Path.of(args[1]), NeutralBoardModel.JSON.toJson(report) + "\n");
        long statisticsStarted = System.nanoTime();
        report.put("statistics", new BoardStatistics(board, Unit.MM, true, true));
        report.put("statisticsNanoseconds", System.nanoTime() - statisticsStarted);
        report.put("drcScope", "Freerouting source clearance checks, not the full KiCad DRC");
      }
    } catch (Throwable error) {
      report.put("exception", error.toString());
      report.putIfAbsent("accepted", false);
      Files.writeString(Path.of(args[1]), NeutralBoardModel.JSON.toJson(report) + "\n");
      throw error;
    }
    Files.writeString(Path.of(args[1]), NeutralBoardModel.JSON.toJson(report) + "\n");
  }
}
