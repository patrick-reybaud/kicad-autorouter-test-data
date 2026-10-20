package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.ItemRouteResult;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/** Test-only observing subclass of the actual BatchOptimizerMultiThreaded (J4, tranche 131): records every task result in the
 *  order the source processes them, and every pass. Determinism of the reference: with one worker thread, a result is held until
 *  the source's own construction loop of the pass is over (its pool.shutdown(), private field read by reflection), so every task of
 *  the pass has copied the board the pass started with and the results are processed in scheduling order (FIFO of the single
 *  worker). No algorithm of its own; every call is forwarded to the source. */
public final class OptimizerMtOracleBridge {
  private OptimizerMtOracleBridge() {}
  private static final Field POOL;
  static {
    try { POOL = BatchOptimizerMultiThreaded.class.getDeclaredField("pool"); POOL.setAccessible(true); }
    catch (NoSuchFieldException e) { throw new IllegalStateException(e); }
  }
  public static final class TaskRecord {
    public final int passNo, order; public final ItemRouteResult result; public final boolean won; public final String boardHash;
    TaskRecord(int passNo, int order, ItemRouteResult result, boolean won, String boardHash) { this.passNo = passNo; this.order = order; this.result = result; this.won = won; this.boardHash = boardHash; }
  }
  public static final class PassRecord {
    public final int passNo, numTasks, numTasksFinished, totalAfter; public final boolean withPreferredDirections, ripupIncreasedAfter; public final float routeImproved; public final String hashAfter;
    PassRecord(int p, boolean w, float v, int tasks, int finished, boolean r, int total, String h) { passNo = p; withPreferredDirections = w; routeImproved = v; numTasks = tasks; numTasksFinished = finished; ripupIncreasedAfter = r; totalAfter = total; hashAfter = h; }
  }
  public static final class Observing extends BatchOptimizerMultiThreaded {
    public final List<TaskRecord> tasks = new ArrayList<>();
    public final List<PassRecord> passes = new ArrayList<>();
    private int currentPass, order;
    public Observing(RoutingJob job) { super(job); }
    @Override
    public synchronized boolean isWinningCandidate(OptimizeRouteTask task) {
      waitForConstructionLoop();
      boolean won = super.isWinningCandidate(task);
      tasks.add(new TaskRecord(currentPass, order++, task.getRouteResult(), won, task.board.getHash()));
      return won;
    }
    @Override
    protected float optRoutePass(int passNo, boolean withPreferredDirections) {
      currentPass = passNo; order = 0;
      float v = super.optRoutePass(passNo, withPreferredDirections);
      passes.add(new PassRecord(passNo, withPreferredDirections, v, getNumTasks(), getNumTasksFinished(), useIncreasedRipupCosts, totalItemsOptimized, board.getHash()));
      return v;
    }
    private void waitForConstructionLoop() {
      try {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) POOL.get(this);
        while (pool != null && !pool.isShutdown()) Thread.sleep(1);
      } catch (IllegalAccessException | InterruptedException e) { throw new IllegalStateException(e); }
    }
    public RoutingBoard board() { return board; }
    public int totalItemsOptimized() { return totalItemsOptimized; }
    public boolean useIncreasedRipupCosts() { return useIncreasedRipupCosts; }
  }
}
