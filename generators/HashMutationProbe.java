package app.freerouting.board.facade;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.DesignRulesChecker;
import com.google.gson.JsonParser;
import java.nio.file.*;
/** Diagnostic: which board state the hash profile sees change when incompletes/statistics are computed (tranche 114). */
public final class HashMutationProbe extends BoardConstructionOracle {
  static int firstDiff(byte[] a,byte[] b){int n=Math.min(a.length,b.length);for(int i=0;i<n;i++)if(a[i]!=b[i])return i;return a.length==b.length?-1:n;}
  static String around(byte[] a,int i){StringBuilder sb=new StringBuilder();for(int k=Math.max(0,i-160);k<Math.min(a.length,i+60);k++){char c=(char)(a[k]&0xff);sb.append(c>=32&&c<127?c:'.');}return sb.toString();}
  public static void main(String[] args)throws Exception{
    Path path=Path.of(args[0]);RoutingBoard b=PipelineBenchmark.read(path);PassRunnerOracle.prepare(b,path,JsonParser.parseString("{\"maxPasses\":null}").getAsJsonObject());
    byte[] s0=b.serialize(true);String h0=b.getHash();
    new DesignRulesChecker(b,null).calculateAllIncompletes();byte[] s1=b.serialize(true);String h1=b.getHash();
    new DesignRulesChecker(b,null).calculateAllIncompletes();byte[] s2=b.serialize(true);String h2=b.getHash();
    new BoardStatistics(b);byte[] s3=b.serialize(true);String h3=b.getHash();
    System.out.println("prepared->drc "+h0.equals(h1)+"  drc->drc "+h1.equals(h2)+"  drc->stats "+h2.equals(h3)+"  lengths "+s0.length+" "+s1.length+" "+s2.length+" "+s3.length);
    int d=firstDiff(s0,s1);System.out.println("first diff prepared/drc at "+d);if(d>=0){System.out.println("before: "+around(s0,d));System.out.println("after : "+around(s1,d));}
    int d2=firstDiff(s1,s2);System.out.println("first diff drc/drc at "+d2);if(d2>=0){System.out.println("before: "+around(s1,d2));System.out.println("after : "+around(s2,d2));}
  }
}
