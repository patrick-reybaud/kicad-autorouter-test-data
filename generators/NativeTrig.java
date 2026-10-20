package app.freerouting.geometry.planar;
/** Test-only JNI boundary to the original Sun fdlibm C++ adaptation. */
public final class NativeTrig {
  static { System.load(System.getProperty("freerouting.trig.library")); }
  public static native double sin(double x);
  public static native double cos(double x);
}
