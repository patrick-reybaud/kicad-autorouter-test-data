package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.ItemRouteResult;
import app.freerouting.board.model.items.Item;
import app.freerouting.core.RoutingJob;
import java.util.ArrayList;
import java.util.List;

/** Test-only observing subclass of the actual BatchOptimizer (D120-1): records what each protected step decided.
 *  No algorithm of its own; every call is forwarded to the source. */
public final class OptimizerOracleBridge {
  private OptimizerOracleBridge() {}

  public static final class ItemRecord {
    public final int itemId; public final boolean withPreferredDirections, ripupIncreasedBefore; public final int totalBefore;
    public final ItemRouteResult result;
    ItemRecord(int itemId, boolean w, boolean r, int t, ItemRouteResult result) { this.itemId = itemId; withPreferredDirections = w; ripupIncreasedBefore = r; totalBefore = t; this.result = result; }
  }

  public static final class PassRecord {
    public final int passNo; public final boolean withPreferredDirections, ripupIncreasedAfter; public final float routeImproved; public final int totalAfter; public final String hashAfter;
    PassRecord(int p, boolean w, float v, boolean r, int t, String h) { passNo = p; withPreferredDirections = w; routeImproved = v; ripupIncreasedAfter = r; totalAfter = t; hashAfter = h; }
  }

  public static final class Observing extends BatchOptimizer {
    public final List<ItemRecord> items = new ArrayList<>();
    public final List<PassRecord> passes = new ArrayList<>();
    public Observing(RoutingJob job) { super(job); }

    @Override
    protected ItemRouteResult optRouteItem(Item item, boolean withPreferredDirections, boolean disableSnapshots) {
      boolean before = useIncreasedRipupCosts; int total = totalItemsOptimized;
      ItemRouteResult r = super.optRouteItem(item, withPreferredDirections, disableSnapshots);
      items.add(new ItemRecord(item.getId(), withPreferredDirections, before, total, r));
      return r;
    }

    @Override
    protected float optRoutePass(int passNo, boolean withPreferredDirections) {
      float v = super.optRoutePass(passNo, withPreferredDirections);
      passes.add(new PassRecord(passNo, withPreferredDirections, v, useIncreasedRipupCosts, totalItemsOptimized, board.getHash()));
      return v;
    }

    public int totalItemsOptimized() { return totalItemsOptimized; }
    public boolean useIncreasedRipupCosts() { return useIncreasedRipupCosts; }
  }
}
