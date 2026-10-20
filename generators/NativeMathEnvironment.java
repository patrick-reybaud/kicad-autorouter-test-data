/** Test-only runtime observation; no Freerouting or JDK source changes. */
public class NativeMathEnvironment {
  static native String environment();
  public static void main(String[] args) {
    System.load(args[0]);
    System.out.println(environment());
  }
}
