package app.freerouting.util;
import java.io.*;
/** Actual TextManager timespan parsing: the ISO-8601 text it builds and the seconds it returns. */
public class TimespanOracle {
  /** Round-trips a case through a line: "\0" is null, "\e" empty, "\s" space, "\t" tab, "\\" backslash. */
  static String decode(String s){
    if(s.equals("\\0"))return null;
    var out=new StringBuilder();
    for(int i=0;i<s.length();i++){
      char c=s.charAt(i);
      if(c!='\\'){out.append(c);continue;}
      char n=s.charAt(++i);
      switch(n){case 'e'->{}case 's'->out.append(' ');case 't'->out.append('\t');case 'n'->out.append('\n');
        case 'r'->out.append('\r');case 'f'->out.append('\f');case 'v'->out.append('\u000B');
        case '\\'->out.append('\\');default->throw new IllegalArgumentException(s);}
    }
    return out.toString();
  }
  static String encode(String s){
    if(s==null)return "\\0";
    if(s.isEmpty())return "\\e";
    var out=new StringBuilder();
    for(char c:s.toCharArray())switch(c){case ' '->out.append("\\s");case '\t'->out.append("\\t");
      case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\f'->out.append("\\f");
      case '\u000B'->out.append("\\v");case '\\'->out.append("\\\\");default->out.append(c);}
    return out.toString();
  }
  public static void main(String[] args)throws Exception{
    var input=new BufferedReader(new InputStreamReader(System.in));String row;
    while((row=input.readLine())!=null){
      String value=decode(row);
      String duration=value==null?null:TextManager.convertFromTimespanToDurationFormat(value);
      Long seconds=TextManager.parseTimespanString(value);
      System.out.println(row+" | "+encode(duration)+" "+(seconds==null?"null":seconds.toString()));
    }
  }
}
