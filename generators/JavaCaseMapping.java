// Oracle data extracted through the public API; no JDK implementation source copied.
public class JavaCaseMapping {
  public static void main(String[] args) {
    for (int c=0;c<=Character.MAX_CODE_POINT;c++) {
      int fold=Character.toLowerCase(Character.toUpperCase(c));
      if(c!=fold) System.out.println(c+" "+fold);
    }
  }
}
