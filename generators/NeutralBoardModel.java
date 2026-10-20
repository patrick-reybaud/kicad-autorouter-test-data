package app.freerouting.board.facade;

import app.freerouting.board.model.items.*;
import app.freerouting.board.model.structure.*;
import app.freerouting.core.library.*;
import app.freerouting.core.library.Package;
import app.freerouting.geometry.planar.*;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.*;
import com.google.gson.*;
import java.util.*;

/** Test-bench state exporter. No parser or Java serialization code enters the engine. */
public final class NeutralBoardModel extends BoardConstructionOracle {
  static final Gson JSON=new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
  static List<Object> list(Object... values){return new ArrayList<>(Arrays.asList(values));}
  static Map<String,Object> obj(Object... values){var r=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)r.put((String)values[i],values[i+1]);return r;}
  // UTF-16 code units also preserve unpaired surrogates, unlike a UTF-8 string conversion.
  static String str(String s){if(s==null)return null;StringBuilder r=new StringBuilder();for(char c:s.toCharArray())r.append(String.format(Locale.ROOT,"%04x",(int)c));return r.toString();}
  static Object xy(Object p)throws Exception {
    if(p==null)return null;
    if(p instanceof IntPoint q)return list("I",q.x,q.y);
    if(p instanceof IntVector q)return list("I",q.x,q.y);
    return list("R",field(p,"x").toString(),field(p,"y").toString(),field(p,"z").toString());
  }
  final IdentityHashMap<Object,Integer> identities=new IdentityHashMap<>();
  final List<Object> geometry=new ArrayList<>();
  int area(Area a)throws Exception {
    if(a==null)return 0;Integer old=identities.get(a);if(old!=null)return old;
    int id=geometry.size()+1;identities.put(a,id);geometry.add(null);Object value;
    if(a instanceof IntBox b)value=list("B",b.ll.x,b.ll.y,b.ur.x,b.ur.y);
    else if(a instanceof IntOctagon o)value=list("O",o.leftX,o.bottomY,o.rightX,o.topY,o.upperLeftDiagonalX,o.lowerRightDiagonalX,o.lowerLeftDiagonalX,o.upperRightDiagonalX);
    else if(a instanceof Simplex s){var lines=new ArrayList<Object>();for(int i=0;i<s.borderLineCount();i++){Line l=s.borderLine(i);lines.add(list(xy(l.a),xy(l.b)));}value=list("S",lines,s==Simplex.EMPTY);}
    else if(a instanceof Circle c)value=list("C",xy(c.center),c.radius);
    else if(a instanceof PolygonShape p){var points=new ArrayList<Object>();for(Point c:(Point[])field(p,"corners"))points.add(xy(c));value=list("P",points);}
    else if(a instanceof PolylineArea p){var holes=new ArrayList<Object>();for(var h:p.getHoles())holes.add(area(h));value=list("A",area(p.getBorder()),holes);}
    else throw new IllegalArgumentException("Unsupported neutral geometry "+a.getClass());
    geometry.set(id-1,value);return id;
  }
  Object keepouts(Package.Keepout[] input)throws Exception {if(input==null)return null;var r=new ArrayList<Object>();for(var k:input)r.add(k==null?null:list(str(k.name),area(k.area),k.layer));return r;}
  static int packageId(Object p){return p==null?0:((Package)p).id;}
  static int partId(LogicalPart p){return p==null?0:p.id;}
  static int identityIndex(Object value,List<?> values){if(value==null)return -1;for(int i=0;i<values.size();i++)if(values.get(i)==value)return i;throw new IllegalArgumentException("Unregistered rule reference");}
  Map<String,Object> metadata(RoutingBoard b)throws Exception {
    var r=new LinkedHashMap<String,Object>();r.put("version","NBF1");r.put("bounds",list(b.boundingBox.ll.x,b.boundingBox.ll.y,b.boundingBox.ur.x,b.boundingBox.ur.y));
    var layers=new ArrayList<Object>();for(var l:b.layerStructure.layers)layers.add(list(str(l.name),l.isSignal));r.put("layers",layers);
    var m=b.rules.clearanceMatrix;var names=new ArrayList<Object>();var values=new ArrayList<Object>();var maxima=new ArrayList<Object>();
    for(int i=0;i<m.getClassCount();i++){names.add(str(m.getName(i)));for(int j=0;j<m.getClassCount();j++)for(int l=0;l<layers.size();l++)values.add(m.getValue(i,j,l,false));for(int l=0;l<layers.size();l++)maxima.add(m.maxValue(i,l));}
    var overall=new ArrayList<Object>();for(int l=0;l<layers.size();l++)overall.add(m.maxValue(l));r.put("matrix",obj("names",names,"values",values,"rowMax",maxima,"max",overall));
    var pads=new ArrayList<Object>();if(b.library.padstacks!=null)for(int n=1;n<=b.library.padstacks.count();n++){var p=b.library.padstacks.get(n);var shapes=new ArrayList<Object>();for(ConvexShape s:(ConvexShape[])field(p,"shapes"))shapes.add(area(s));pads.add(list(str(p.name),p.attachAllowed,p.placedAbsolute,p.holeOnly,shapes));}r.put("pads",b.library.padstacks==null?null:pads);
    var packages=new ArrayList<Object>();if(b.library.packages!=null)for(int n=1;n<=b.library.packages.count();n++){var p=b.library.packages.get(n);var pins=new ArrayList<Object>();var raw=(Package.Pin[])field(p,"pins");if(raw!=null)for(var pin:raw)pins.add(pin==null?null:list(str(pin.name),pin.padstackId,xy(pin.relativeLocation),bits(pin.rotationInDegree)));
      var outline=new ArrayList<Object>();if(p.outline!=null)for(Shape s:p.outline)outline.add(area(s));var widths=new ArrayList<Object>();if(p.outlineWidths!=null)for(double w:p.outlineWidths)widths.add(bits(w));
      packages.add(list(str(p.name),p.isFront,raw==null?null:pins,p.outline==null?null:outline,p.outlineWidths==null?null:widths,p.outlineIsClosed,keepouts(p.keepouts),keepouts(p.viaKeepouts),keepouts(p.placeKeepoutArr)));}r.put("packages",b.library.packages==null?null:packages);
    var parts=new ArrayList<Object>();for(int n=1;n<=b.library.logicalParts.count();n++){var p=b.library.logicalParts.get(n);var pins=new ArrayList<Object>();for(int k=0;k<p.pinCount();k++){var q=p.getPin(k);pins.add(list(q.pinIndex,str(q.pinName),str(q.gateName),q.gateSwapCode,str(q.gatePinName),q.gatePinSwapCode));}parts.add(list(str(p.name),pins));}r.put("parts",parts);
    var viaPads=new ArrayList<Object>();Object rawVia=field(b.library,"viaPadstacks");if(rawVia!=null)for(var p:b.library.getViaPadstacks())viaPads.add(p==null?0:p.id);r.put("viaPads",rawVia==null?null:viaPads);
    var viaObjects=new ArrayList<ViaInfo>();var vias=new ArrayList<Object>();for(int i=0;i<b.rules.viaInfos.count();i++){var v=b.rules.viaInfos.get(i);viaObjects.add(v);vias.add(list(str(v.getName()),v.getPadstack()==null?0:v.getPadstack().id,v.getClearanceClassIndex(),v.attachSmdAllowed()));}r.put("vias",vias);
    var rules=new ArrayList<Object>();for(var rule:b.rules.viaRules){var ids=new ArrayList<Object>();for(int i=0;i<rule.viaCount();i++)ids.add(identityIndex(rule.getVia(i),viaObjects));rules.add(list(str(rule.name),ids));}r.put("viaRules",rules);
    var classes=new ArrayList<Object>();var classObjects=new ArrayList<NetClass>();var defaultObjects=new ArrayList<DefaultItemClearanceClasses>();var defaults=new ArrayList<Object>();
    for(int i=0;i<b.rules.netClasses.count();i++){var cl=b.rules.netClasses.get(i);classObjects.add(cl);if(cl.defaultItemClearanceClasses!=null&&!defaultObjects.contains(cl.defaultItemClearanceClasses))defaultObjects.add(cl.defaultItemClearanceClasses);}
    for(var d:defaultObjects){var row=new ArrayList<Object>();for(var k:DefaultItemClearanceClasses.ItemClass.values())row.add(d.get(k));defaults.add(row);}r.put("defaults",defaults);
    for(var cl:classObjects){var widths=new ArrayList<Object>();var active=new ArrayList<Object>();for(int l=0;l<layers.size();l++){widths.add(cl.getTraceHalfWidth(l));active.add(cl.isActiveRoutingLayer(l));}classes.add(list(str(cl.getName()),cl.isIgnoredByAutorouter,cl.getTraceClearanceClass(),identityIndex(cl.getViaRule(),b.rules.viaRules),cl.isShoveFixed(),cl.getPullTight(),cl.getIgnoreCyclesWithAreas(),bits(cl.getMinimumTraceLength()),bits(cl.getMaximumTraceLength()),widths,active,identityIndex(cl.defaultItemClearanceClasses,defaultObjects)));}r.put("classes",classes);
    var nets=new ArrayList<Object>();for(int n=1;n<=b.rules.nets.maxNetNumber();n++){var net=b.rules.nets.get(n);nets.add(list(str(net.name),net.subnetNumber,net.containsPlane(),identityIndex(net.getNetClass(),classObjects)));}r.put("nets",nets);
    var q=b.rules;r.put("rules",list(q.getTraceAngleRestriction()==null?-1:q.getTraceAngleRestriction().ordinal(),q.getIgnoreConduction(),q.getUseSlowAutorouteAlgorithm(),q.getHoleClearance(),bits(q.getPinEdgeToTurnDist()),q.getMinTraceHalfWidth(),q.getMaxTraceHalfWidth()));
    var components=new ArrayList<Object>();for(var p:b.components.getAll())components.add(list(str(p.name),xy(p.getLocation()),bits(p.getRotationInDegree()),p.placedOnFront(),packageId(field(p,"libPackageFront")),packageId(field(p,"libPackageBack")),p.positionFixed,str(p.getPartNumber()),partId(p.getLogicalPart())));r.put("components",components);r.put("flipRotateFirst",b.components.getFlipStyleRotateFirst());r.put("geometry",geometry);
    var aliasIds=new IdentityHashMap<Object,Integer>();var padAliases=new ArrayList<Object>();var packageAliases=new ArrayList<Object>();
    if(b.library.padstacks!=null)for(int n=1;n<=b.library.padstacks.count();n++)padAliases.add(arrayId(field(b.library.padstacks.get(n),"shapes"),aliasIds));
    if(b.library.packages!=null)for(int n=1;n<=b.library.packages.count();n++){var p=b.library.packages.get(n);packageAliases.add(list(arrayId(field(p,"pins"),aliasIds),arrayId(p.outline,aliasIds),arrayId(p.outlineWidths,aliasIds),arrayId(p.outlineIsClosed,aliasIds),arrayId(p.keepouts,aliasIds),arrayId(p.viaKeepouts,aliasIds),arrayId(p.placeKeepoutArr,aliasIds)));}
    r.put("arrayAliases",list(padAliases,packageAliases));
    return r;
  }
  static int arrayId(Object a,IdentityHashMap<Object,Integer> ids){return a==null?0:ids.computeIfAbsent(a,k->ids.size()+1);}
  static String exportMetadata(RoutingBoard b)throws Exception{return JSON.toJson(new NeutralBoardModel().metadata(b));}
}
