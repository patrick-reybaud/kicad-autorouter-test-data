package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.geometry.planar.FloatLine;
import java.util.*;

/** Test-only access to package-private BatchAutorouter members; no algorithm of its own. */
public final class PipelineOracleBridge {
  private PipelineOracleBridge() {}
  public static List<Item> autorouteItems(BatchAutorouter r, RoutingBoard b) { return new ArrayList<>(r.getAutorouteItems(b)); }
  public static AutorouteAttemptResult autorouteItem(BatchAutorouter r, Item item, int net, SortedSet<Item> ripped, Map<Item, Integer> costs, int pass) { return r.autorouteItem(item, net, ripped, costs, pass); }
  public static FloatLine airLine(BatchAutorouter r) { return r.airLine; }
  public static boolean autoroutePass(BatchAutorouter r, int pass) { return r.autoroutePass(pass); }
  public static void removeTails(BatchAutorouter r, Item.StopConnectionOption option) { r.removeTails(option); }
  public static int calculateIncompleteCount(BatchAutorouter r, RoutingBoard b) { return r.calculateIncompleteCount(b); }
  public static int totalItemsRouted(BatchAutorouter r) { return r.totalItemsRouted; }
  public static RoutingBoard board(BatchAutorouter r) { return r.board; }
  public static void setBoard(BatchAutorouter r, RoutingBoard b) { r.board = b; }
  public static void setJob(BatchAutorouter r, app.freerouting.core.RoutingJob job) { r.job = job; }
  public static int calculateIncomplete(BatchAutorouter r) { return r.calculateIncompleteCount(r.board); }
}
