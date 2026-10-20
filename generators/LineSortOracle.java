package app.freerouting.geometry.planar;

import java.io.*;
import java.util.*;

/** Trace the actual JVM sort using the unchanged Freerouting Line comparator. */
public class LineSortOracle extends ExactCoordinatesOracle {
  static long count, hash;
  static StringBuilder trace;
  static long mergeLoFailures, mergeHiFailures;
  static void mix(int value) {
    for(int shift=0;shift<32;shift+=8) {hash^=(value>>>shift)&255;hash*=1099511628211L;}
  }
  static final class Entry implements Comparable<Entry> {
    final int id;final Line line;
    Entry(int id,Line line) {this.id=id;this.line=line;}
    @Override public int compareTo(Entry other) {
      ++count;mix(id);mix(other.id);
      try {
        int result=line.compareTo(other.line);mix(result);
        if(trace!=null)trace.append(id).append(',').append(other.id).append(',').append(result).append(';');
        return result;
      } catch(ClassCastException ex) {
        mix(-2);if(trace!=null)trace.append(id).append(',').append(other.id).append(",X;");throw ex;
      }
    }
  }
  static String status(RuntimeException ex) {
    if(ex instanceof ClassCastException)return "type_error";
    if(ex instanceof IndexOutOfBoundsException)return "index_error";
    if(ex instanceof IllegalArgumentException)return "invalid_argument";
    throw ex;
  }
  public static void main(String[] args) throws Exception {
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null) {
      tokens=row.split(" ");index=0;int paletteCount=integer();var palette=new Line[paletteCount];
      for(int i=0;i<paletteCount;i++)palette[i]=new Line(point(),point());
      int n=integer();var entries=new Entry[n];var lines=new Line[n];var identities=new IdentityHashMap<Line,Integer>();
      for(int i=0;i<n;i++) {Line p=palette[integer()];lines[i]=new Line(p.a,p.b);entries[i]=new Entry(i,lines[i]);identities.put(lines[i],i);}
      if(index!=tokens.length)throw new AssertionError("unconsumed input");
      count=0;hash=0xcbf29ce484222325L;trace=n<=128?new StringBuilder():null;String result="ok";
      try {Arrays.sort(entries);}catch(RuntimeException ex) {
        result=status(ex);
        if(ex instanceof IllegalArgumentException)for(var frame:ex.getStackTrace()) {
          if(frame.getMethodName().equals("mergeLo"))++mergeLoFailures;
          if(frame.getMethodName().equals("mergeHi"))++mergeHiFailures;
        }
      }
      var ids=new StringJoiner(",");ids.add(String.valueOf(n));for(var e:entries)ids.add(String.valueOf(e.id));
      String factory;
      try {
        Simplex s=Simplex.getInstance(lines);var j=new StringJoiner(",");j.add(String.valueOf(s.borderLineCount()));
        for(int i=0;i<s.borderLineCount();i++)j.add(String.valueOf(identities.get(s.borderLine(i))));
        factory=(s==Simplex.EMPTY?"E:":"S:")+j;
      }catch(RuntimeException ex) {factory=status(ex);}
      System.out.println(row+" | "+result+" "+count+" "+Long.toUnsignedString(hash,16)+" "+ids+" "+factory+" "+(trace==null||trace.length()==0?"-":trace));
    }
    System.err.println("{\"mergeLo_contract_failures\":"+mergeLoFailures+",\"mergeHi_contract_failures\":"+mergeHiFailures+"}");
  }
}
