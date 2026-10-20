#!/usr/bin/env python3
"""Qualify Item clearance violations, escape-via filtering and severity aggregation against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/clearance_violations_java.tsv'
def cases():
 rng=random.Random(20261027)
 def arr(values):return [str(len(values)),*map(str,values)]
 def nets():return ['-1'] if rng.randrange(16)==0 else arr([rng.choice([0,1,2,3]) for _ in range(rng.randrange(4))])
 for k in range(600):
  n=rng.randrange(1,8);subject=rng.randrange(1,n+1);flags=rng.choice([0,0,0,0,8,8,1,2,4,16]);out=['C',str(flags),str(rng.randrange(4)),str(rng.choice([0,1,2,6,12,24,64])),str(subject),str(rng.choice([-1,-1,-1,rng.randrange(1,n+1)*8+rng.randrange(7)])),str(n)]
  for i in range(n):
   shapes=['-1'] if rng.randrange(30)==0 else arr([rng.randrange(8) for _ in range(rng.randrange(4))])
   out += [str(rng.randrange(4)),str(rng.choice([-1,0,1,1,2,3,4])),str(rng.choice([-1,0,0,0,1,1,2])),str(rng.randrange(3)),str(rng.randrange(2)),str(rng.randrange(-1,3)),*nets(),*shapes,rng.choice(['bff0000000000000','bff0000000000000','0','8000000000000000','7ff8000000000000','7ff0000000000000','4000000000000000','4059000000000000'])]
   for j in range(2):out += [str(rng.choices([0,1,2],[5,4,1])[0]),*arr([rng.randrange(n+1) for _ in range(rng.randrange(5))])]
  count=rng.randrange(10);out.append(str(count))
  for i in range(count):out += [str(rng.randrange(-1,n+1)),str(rng.randrange(-1,4))]
  yield ' '.join(out)
 # Directed successful geometry, tie-pin and escape-via paths on both layers.
 for first in range(1,7):
  for second in range(1,7):
   for typ in range(4):
    for compensated in [False,True]:
     for policy in range(3):
      def node(t,cl,nets,shapes,escape,layer,begin,end):
       return [str(t),str(cl),'0','1' if t!=3 else '0',str(escape),str(layer),*arr(nets),*arr(shapes),'bff0000000000000','1',*arr(begin),'1',*arr(end)]
      out=['D','8' if compensated else '0','1','12','1','-1','3']
      other_nets=[1] if typ==2 else [2]
      start=[2] if policy==0 else [2,3] if policy==1 else []
      end=[2,3] if policy==2 else []
      out+=node(typ,1,[1],[first,first],int(policy!=0),policy-1,start,end)
      out+=node(1 if typ==1 else 0,2,other_nets,[second,second],0,-1,[],[])
      out+=node(3,1,[1,2],[1],0,-1,[],[])
      out += ['6','1','0','-1','0','0','0','2','0','2','1','3','0']
      yield ' '.join(out)
 values=['0','8000000000000000','3ff0000000000000','bff0000000000000','7fefffffffffffff','ffefffffffffffff','7ff0000000000000','fff0000000000000','7ff8000000000000','7ff0000000000001','0000000000000001','8000000000000001']
 for k in range(240):
  count=rng.randrange(1,24);out=['A',str(count)]
  for i in range(count):out += [str(rng.randrange(-1,3)),rng.choice(values),rng.choice(values)]
  n=rng.randrange(1,8);out.append(str(n))
  for i in range(n):
   out += [rng.choice(values),rng.choice(values),str(int(rng.randrange(20)==0))]
   if rng.randrange(20)==0:out.append('-1')
   else:out+=arr([rng.randrange(count+1) if k%3==0 else rng.randrange(1,count+1) for _ in range(rng.randrange(60))])
  if rng.randrange(20)==0:out.append('-1')
  else:out+=arr([rng.randrange(n+1) if k%3==0 else rng.randrange(1,n+1) for _ in range(rng.randrange(15))])
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/items/Via.java','drc/ClearanceViolation.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-clearance-violations-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ClearanceViolationsOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.ClearanceViolationsOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
  if result.returncode:
   import sys
   sys.stderr.write(result.stderr);result.check_returncode()
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for expected,actual in zip(inputs,rows):assert actual.startswith(expected+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
