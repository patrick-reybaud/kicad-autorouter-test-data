package app.freerouting.datastructures;

/**
 * Test-only shadow of the source TimeLimit for the deterministic Java references (tranche 114, D114-5; tranche 115,
 * D115-1). Compiled into a separate directory prepended to the class path of the reference drivers only; the pinned
 * engine source is untouched. The clock is chosen by the system property {@code freerouting.test.clock}:
 * "frozen" (default): no limit ever expires, as TIME_LIMIT::SetDefaultClock freezes the native engine;
 * "counting:<step>": virtual time advances by <step> milliseconds at every clock read, i.e. at construction and at
 * every limitExceeded(), exactly the reads of the source class, so a limit expires at the same call on both engines.
 * Static counters expose the reads for the drivers.
 */
public class TimeLimit {
  static final String MODE = System.getProperty("freerouting.test.clock", "frozen");
  static final long STEP = MODE.startsWith("counting:") ? Long.parseLong(MODE.substring("counting:".length())) : 0L;
  static long now = 0L;
  public static long constructions = 0L;
  public static long checks = 0L;
  public static long expirations = 0L;

  private final long timeStamp;
  private int timeLimit;

  static long read() {
    now += STEP;
    return now;
  }

  public TimeLimit(int milliSeconds) {
    this.timeLimit = milliSeconds;
    this.timeStamp = read();
    constructions++;
  }

  public boolean limitExceeded() {
    checks++;
    long currentTime = read();
    boolean exceeded = currentTime - this.timeStamp > this.timeLimit;
    if (exceeded) {
      expirations++;
    }
    return exceeded;
  }

  public void multiply(double factor) {
    if (factor <= 0) {
      return;
    }
    double newLimit = factor * this.timeLimit;
    newLimit = Math.min(newLimit, Integer.MAX_VALUE);
    this.timeLimit = (int) newLimit;
  }
}
