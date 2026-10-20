#!/usr/bin/env python3
"""Generate golden results by executing the actual personal-engine Java class.
Java is only required to regenerate this optional differential-test corpus.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'engine/src/main/java/app/freerouting/autoroute/ItemRouteResult.java'
DEST = ROOT / 'upstream/kicad/qa/data/pcbnew/autorouter/item_route_result_java.tsv'
HARNESS = r'''
import app.freerouting.autoroute.ItemRouteResult;
import java.io.*;
public class ItemResultOracle {
  static ItemRouteResult parse(String[] t, int i) {
    return new ItemRouteResult(Integer.parseInt(t[i]), Integer.parseInt(t[i+1]),
      Integer.parseInt(t[i+2]), Double.longBitsToDouble(Long.parseUnsignedLong(t[i+3],16)),
      Double.longBitsToDouble(Long.parseUnsignedLong(t[i+4],16)),
      Integer.parseInt(t[i+5]), Integer.parseInt(t[i+6]));
  }
  static String state(ItemRouteResult r) {
    return r.itemId()+" "+r.improved()+" "+Integer.toUnsignedString(Float.floatToIntBits(r.improvementPercentage()),16)
      +" "+r.viaCount()+" "+Long.toUnsignedString(Double.doubleToLongBits(r.traceLength()),16)
      +" "+r.incompleteCount()+" "+r.viaCountReduced()+" "+Long.toUnsignedString(Double.doubleToLongBits(r.lengthReduced()),16)
      +" "+r.incompleteCountBefore();
  }
  public static void main(String[] args) throws Exception {
    var input = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line=input.readLine()) != null) {
      String[] t=line.split(" ");
      String result;
      if(t[0].equals("D")) result=state(new ItemRouteResult(Integer.parseInt(t[1])));
      else if(t[0].equals("R")) {
        var r=parse(t,1); int override=Integer.parseInt(t[8]);
        if(override>=0) r.updateImproved(override!=0);
        result=state(r);
      } else {
        var a=parse(t,1); var b=parse(t,8);
        result=a.compareTo(b)+" "+a.improvedOver(b);
      }
      System.out.println(line+" | "+result);
    }
  }
}
'''

def bits(x):
    return format(struct.unpack('>Q', struct.pack('>d', x))[0], 'x')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true',help='Compare fresh Java output without rewriting the fixture')
    args=parser.parse_args()
    manifest=json.loads((ROOT / 'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    source_key=str(SOURCE.relative_to(ROOT/'engine'))
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest()==manifest['source_files'][source_key]['sha256'], 'Reference source changed; review the port manifest before regenerating the corpus'
    rng=random.Random(20260917)
    counts=[0,1,2,3,7,19,-1,-2,2147483647,-2147483648]
    lengths=[0.0,-0.0,1.0,1.5,60.0,80.0,1e-300,1e300,float('inf'),float('-inf'),float('nan'),-1.0]
    values=[]
    for i in range(256):
        values.append([i,*rng.choices(counts,k=2),*map(bits,rng.choices(lengths,k=2)),*rng.choices(counts,k=2)])
    values.extend([[901,-1,-2147483648,bits(80.0),bits(60.0),0,0],
                   [902,2,1,bits(80.0),bits(60.0),0,0],
                   [903,0,0,bits(80.0),bits(60.0),0,0],
                   [904,10,10,bits(80.0),bits(80.0),1,0]])
    cases=['D '+str(i) for i in [0,1,-1,2147483647,-2147483648]]
    for v in values:
        for override in [-1,0,1]: cases.append('R '+' '.join(map(str,[*v,override])))
    for a in values:
        for b in [a,values[0],rng.choice(values)]: cases.append('C '+' '.join(map(str,[*a,*b])))
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'))
    with tempfile.TemporaryDirectory(prefix='autorouter-java-oracle-') as temporary:
        tmp=Path(temporary); harness=tmp/'ItemResultOracle.java';harness.write_text(HARNESS)
        subprocess.run([str(home/'bin/javac'),'-d',str(tmp),str(SOURCE),str(harness)],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-cp',str(tmp),'ItemResultOracle'],input='\n'.join(cases)+'\n',text=True)
    if args.check:
        assert DEST.read_text()==generated, 'Java oracle changed; inspect before accepting a new fixture'
    else:
        DEST.write_text(generated)
    provenance={'source':'engine/'+str(SOURCE.relative_to(ROOT/'engine')),
      'source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
      'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),
      'cases':len(cases),'fixture':str(DEST.relative_to(ROOT)),
      'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),
      'java_version':subprocess.run([str(home/'bin/java'),'-version'],text=True,capture_output=True,check=True).stderr}
    print(json.dumps(provenance,indent=2))

if __name__=='__main__':main()
