#!/usr/bin/env python3
"""Qualify Trace netclass/cycle policies, unconnected sets and collection membership against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/trace_policy_java.tsv'
def cases():
 rng=random.Random(20261022)
 def spec(kind,ids):return ' '.join(map(str,[kind,len(ids),*ids]))
 def row(flags,provider,fail,codes,nodes,supplied):return 'P '+' '.join(map(str,[flags,provider,fail,len(nodes),*codes]))+' '+' '.join(nodes)+' '+supplied
 # A loop through a plane, a fork, equal IDs and net 49 diagnostics.
 for code in range(-2,4):
  for provider in range(5):
   for linked in range(2):
    nodes=[]
    for i in range(1,6):
     typ=2 if i==1 else 1 if i==3 else 0
     links={1:[2,4],2:[1,3],3:[2,4],4:[3,1],5:[]}[i]
     specs=[spec(linked,links),spec(linked,[2,5]),spec(linked,[4])]
     nodes.append(' '.join(map(str,[typ,7 if i in [2,5] else i,0,0,1,49]))+' '+' '.join(specs))
    yield row(0,provider,-1,[0,1,2,code],nodes,spec(0,list(range(1,6))))
 for k in range(500):
  n=rng.randrange(1,9);nodes=[]
  for i in range(1,n+1):
   specs=[spec(rng.choices([0,1,2],weights=[6,3,1])[0],[rng.randrange(n+1) for _ in range(rng.randrange(5))]) for _ in range(3)]
   nets=[rng.choice([-1,0,1,2,3,49,10000001]) for _ in range(rng.randrange(4))]
   nodes.append(' '.join(map(str,[2 if i==1 else rng.randrange(3),rng.randrange(1,14),rng.randrange(-1,4),rng.choice([0,0,0,1]),len(nets),*nets]))+' '+' '.join(specs))
  yield row(rng.choice([0,0,0,1,2,4]) if k<420 else 8|rng.choice([0,0,1,2,4]),rng.randrange(5),rng.choice([-1,-1,-1,rng.randrange(3,3*n+3)]),[rng.randrange(-2,4) for _ in range(4)],nodes,spec(rng.randrange(3),[rng.randrange(n+1) for _ in range(rng.randrange(12))]))
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['rules/Net.java','rules/Nets.java','board/facade/BoardConnectivityQueries.java','board/model/items/Item.java','board/model/items/Trace.java','board/model/items/ConductionArea.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-trace-policy-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/TracePolicyOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.TracePolicyOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
