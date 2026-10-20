package app.freerouting.autoroute.maze;
import app.freerouting.geometry.planar.*;
import app.freerouting.autoroute.maze.AutorouteControl.ExpansionCostFactor;
import java.io.*;import java.util.*;
public class DestinationAliasOracle {
 interface Call {Object run();}
 static String call(Call f){try{return String.valueOf(f.run());}catch(NullPointerException e){return "null_error";}catch(IndexOutOfBoundsException e){return "index_error";}}
 static Object field(DestinationDistance d,String name){try{var f=DestinationDistance.class.getDeclaredField(name);f.setAccessible(true);return f.get(d);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
 static String bits(double x){return Long.toHexString(Double.doubleToLongBits(x));}
 static double dbl(Scanner in){return Double.longBitsToDouble(Long.parseUnsignedLong(in.next(),16));}
 static class State {
  ExpansionCostFactor[] costs,oldCosts;boolean[] layers,oldLayers;DestinationDistance[] distances=new DestinationDistance[2];
  IntBox[] boxes={new IntBox(0,0,0,0),new IntBox(-10,-20,100,200),new IntBox(500,300,800,900),IntBox.EMPTY,new IntBox(33554430,-33554430,33554431,-33554429)};
  String costs(ExpansionCostFactor[] a){if(a==null)return "N";String s="";for(var x:a)s+=x==null?"N,":bits(x.horizontal())+":"+bits(x.vertical())+",";return "["+s+"]";}
  String layers(boolean[] a){if(a==null)return "N";String s="";for(var x:a)s+=x?"1":"0";return "["+s+"]";}
  String distance(DestinationDistance d){if(d==null)return "N";String s=field(d,"layerCount")+":"+field(d,"activeLayerCount")+":";for(String f:new String[]{"minCheapViaCost","minComponentSideTraceCost","maxComponentSideTraceCost","minSolderSideTraceCost","maxSolderSideTraceCost","maxInnerSideTraceCost","minComponentInnerTraceCost","minSolderInnerTraceCost","minComponentSolderInnerTraceCost","minNormalViaCost"})s+=bits((double)field(d,f))+",";for(String f:new String[]{"componentSideBox","solderSideBox","innerSideBox"}){IntBox b=(IntBox)field(d,f);s+="B"+b.ll.x+","+b.ll.y+","+b.ur.x+","+b.ur.y;}for(String f:new String[]{"boxIsEmpty","componentSideBoxIsEmpty","solderSideBoxIsEmpty","innerSideBoxIsEmpty"})s+=":"+field(d,f);var c=(ExpansionCostFactor[])field(d,"traceCosts");var l=(boolean[])field(d,"layerActive");return s+"C"+costs(c)+":"+(c==costs)+":"+(c==oldCosts)+"L"+layers(l)+":"+(l==layers)+":"+(l==oldLayers);}
  String dump(){return "C"+costs(costs)+"O"+costs(oldCosts)+"L"+layers(layers)+"O"+layers(oldLayers)+"D"+distance(distances[0])+"D"+distance(distances[1]);}
 }
 static String run(String line){var in=new Scanner(line);in.next();State s=new State();int n=in.nextInt();var out=new ArrayList<String>();for(int k=0;k<n;k++){String op=in.next();int a=in.nextInt(),b=in.nextInt(),c=in.nextInt();double x=dbl(in),y=dbl(in);out.add(call(()->switch(op){
 case "A"->{s.oldCosts=s.costs;s.costs=a<0?null:new ExpansionCostFactor[a];yield "ok";}
 case "B"->{s.oldLayers=s.layers;s.layers=a<0?null:new boolean[a];if(s.layers!=null)for(int i=0;i<a;i++)s.layers[i]=(b&(1<<i))!=0;yield "ok";}
 case "M"->{s.costs[a]=b==0?null:new ExpansionCostFactor(x,y);yield "ok";}
 case "R"->{s.oldCosts[a]=b==0?null:new ExpansionCostFactor(x,y);yield "ok";}
 case "L"->{s.layers[a]=b!=0;yield "ok";}
 case "l"->{s.oldLayers[a]=b!=0;yield "ok";}
 case "C"->{s.distances[a]=new DestinationDistance(s.costs,s.layers,x,y);yield "ok";}
 case "J"->{s.distances[a].join(s.boxes[b],c);yield "ok";}
 case "Q"->bits(s.distances[a].calculate(s.boxes[b],c));
 case "P"->bits(s.distances[a].calculate(new FloatPoint(x,y),c));
 case "H"->bits(s.distances[a].calculateCheapDistance(s.boxes[b],c));
 default->throw new AssertionError(op);
 }));out.add(s.dump());}return String.join(" ",out);}
 public static void main(String[]args)throws Exception{var in=new BufferedReader(new InputStreamReader(System.in));String s;while((s=in.readLine())!=null)System.out.println(s+" | "+run(s));}
}
