#!/usr/bin/env python3
"""Runtime experiment: original expectations stay immutable; candidates only change trig callsites."""
import collections,difflib,hashlib,json,os,subprocess
from pathlib import Path
from generate_trig_decision import ROOT, geometry, inputs, bits, value, nearby
from build_trig_runtime_candidate import build
BUILD=ROOT/'build/trig-runtime';VAL=ROOT/'validation/freerouting-port-71-A-runtime'
J=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home').resolve();JAR=ROOT/'engine/build/libs/freerouting-current-executable.jar'
NUMERIC={'raw','vector_angle','vector_pair_angle','direction_angle'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def setup():
 build()
 base=ROOT/'engine/src/main/java/app/freerouting'
 sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]+[base/'autoroute/maze/MazeListElement.java',base/'autoroute/expansion/ExpansionDoor.java']
 manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for s in sources:assert sha(s)==manifest['source_files'][str(s.relative_to(ROOT/'engine'))]['sha256']
 candidates=[];patch=[]
 for p in sources:
  if p.name in ['FloatPoint.java','Direction.java']:
   old=p.read_text();new=old
   for fn in ['sin','cos']:
    assert new.count('Math.'+fn+'(')==1;new=new.replace('Math.'+fn+'(','NativeTrig.'+fn+'(')
   d=BUILD/'candidate-java'/p.name;d.parent.mkdir(exist_ok=True);d.write_text(new);candidates.append(d)
   patch.extend(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='reference/'+p.name,tofile='test-only-candidate/'+p.name))
  else:candidates.append(p)
 (VAL/'candidate-callsite.patch').write_text(''.join(patch))
 for mode,src in [('reference',sources),('candidate',candidates)]:
  subprocess.run([str(J/'bin/javac'),'-cp',str(JAR),'-d',str(BUILD/mode),*map(str,src),str(ROOT/'tests/oracle/NativeTrig.java'),str(ROOT/'tests/oracle/TrigDecisionOracle.java')],check=True)
 old_report=json.loads((ROOT/'validation/freerouting-port-68-A/oracle.json').read_text())
 for name,rows,old_name in [('geometry',geometry(),'geometry.in'),('numeric',inputs(),'trig.in')]:
  target=BUILD/(name+'.in');target.write_text('\n'.join(rows)+'\n')
  assert sha(target)==old_report['artifacts_sha256'][old_name], 'Original input generation changed'
 (VAL/'source-hashes.json').write_text(json.dumps({str(p.relative_to(ROOT)):sha(p) for p in sources},indent=2)+'\n')
def execute(kind,name,flags=(),library='libtrig-fma.dylib',label=None):
 label=label or kind
 inp=BUILD/(name+'.in');out=BUILD/(label+'-'+name+'.out');err=BUILD/(label+'-'+name+'.stderr')
 cmd=[str(J/'bin/java'),*flags,'--enable-native-access=ALL-UNNAMED','-Xmx1g','-Dfreerouting.trig.library='+str(BUILD/library),'-cp',str(BUILD/kind)+os.pathsep+str(JAR),'app.freerouting.autoroute.maze.TrigDecisionOracle']
 with inp.open() as i,out.open('w') as o,err.open('w') as e:
  r=subprocess.run(cmd,stdin=i,stdout=o,stderr=e,timeout=300)
 record={'argv':cmd,'input_sha256':sha(inp),'output_sha256':sha(out),'stderr':err.read_text(),'exit_code':r.returncode}
 (VAL/(label+'-'+name+'-command.json')).write_text(json.dumps(record,indent=2)+'\n');r.check_returncode();return out

def compare(ref,cand,name):
 counts=collections.Counter();changes=[];decision=[]
 rr=ref.read_text().splitlines();cc=cand.read_text().splitlines()
 for a,b in zip(rr,cc,strict=True):
  il,ar=a.split(' | ');ir,br=b.split(' | ');assert il==ir
  af=dict(x.split('=',1) for x in ar.split());bf=dict(x.split('=',1) for x in br.split());assert af.keys()==bf.keys()
  delta={k:{'reference':af[k],'candidate':bf[k]} for k in af if af[k]!=bf[k]}
  if delta:
   change={'input':il,'changes':delta};changes.append(change)
   if set(delta)-NUMERIC:decision.append(change)
  for k in delta:counts[k]+=1
 for k in af:counts.setdefault(k,0)
 report={'cases':len(rr),'changed_cases':len(changes),'decision_cases':len(decision),'changed_fields':dict(counts),'first_decision':decision[0] if decision else None,'reference_sha256':sha(ref),'candidate_sha256':sha(cand),'no_tolerance':True}
 (VAL/(name+'-comparison.json')).write_text(json.dumps(report,indent=2)+'\n');(VAL/(name+'-decision-flips.json')).write_text(json.dumps(decision,indent=2)+'\n');print(name,json.dumps(report));return report
if __name__=='__main__':
 import argparse
 p=argparse.ArgumentParser();p.add_argument('stage',choices=['setup','baseline','numeric','modes']);args=p.parse_args()
 if args.stage=='setup':setup()
 if args.stage=='baseline':
  r=execute('reference','geometry');old=json.loads((ROOT/'validation/freerouting-port-68-A/oracle.json').read_text());assert sha(r)==old['artifacts_sha256']['reference.out'],'Unmodified Java baseline changed; investigate runtime, do not update expectation'
  c=execute('candidate','geometry');compare(r,c,'baseline')
 if args.stage=='numeric':execute('reference','numeric')
 if args.stage=='modes':
  r=BUILD/'reference-geometry.out'
  for label,flags in [('interpreted',['-Xint']),('c1',['-XX:TieredStopAtLevel=1']),('batch',['-Xbatch']),('no_intrinsics',['-XX:+UnlockDiagnosticVMOptions','-XX:DisableIntrinsic=_dsin,_dcos'])]:
   other=execute('reference','geometry',flags,label=label);compare(r,other,'mode-'+label)
