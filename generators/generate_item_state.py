#!/usr/bin/env python3
"""Qualify item ID rollover and tree-cache reference/array behavior."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/item_state_java.tsv'
def cases():
 rng=random.Random(20261007);starts=[-2147483648,-1,0,1,1073741821,1073741822,1073741823,1073741824,2147483647];counts=[-9223372036854775808,-1,0,1,9223372036854775807]
 for start in starts:
  for wraps in counts:
   ops=['M','S','N','N','M','B','S','B','N',f'R 1073741823 {wraps}','N','S','B','M']
   yield f'I {start} {wraps} {len(ops)} '+' '.join(ops)
 for _ in range(120):
  ops=[rng.choice(['N','N','N','M','B','S',f'R {rng.choice(starts)} {rng.choice(counts)}']) for i in range(90)];yield f'I {rng.choice(starts)} {rng.choice(counts)} {len(ops)} '+' '.join(ops)
 def op(c,a=0,b=0,d=0):return f'{c} {a} {b} {d}'
 for tree in range(4):
  for arr in range(4):
   ops=[op('e',tree),op('s',tree),op('E',tree,arr),op('S',tree,arr),op('A',tree),op('B',tree),op('E',(tree+1)%4,arr),op('S',(tree+1)%4,arr),op('M',arr,0,3),op('m',arr,0,3),op('E',tree,1),op('S',tree,1),op('M',4,1,2),op('m',4,1,2),op('C'),op('e',tree),op('s',tree),op('S',tree,0),op('B',tree)]
   yield f'C {len(ops)} '+' '.join(ops)
 for _ in range(220):
  ops=[]
  for i in range(85):
   c=rng.choice(['E','S','e','s','C','M','m','A','B']);a=rng.randrange(4) if c not in ['M','m'] else rng.randrange(0,13);b=rng.randrange(-1,14) if c in ['E','S'] else rng.randrange(-1,5);d=rng.randrange(-1,5);ops.append(op(c,a,b,d))
  yield f'C {len(ops)} '+' '.join(ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/'board/actions'/f'{n}.java' for n in ['ItemIdGenerator','ItemSearchTreesInfo']]+[base/'datastructures'/f'{n}.java' for n in ['IdGenerator','ShapeTree','MinAreaTree']]+[base/'board/searchtree/SearchTreeObject.java'];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-item-state-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ItemStateOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.actions.ItemStateOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,check=True,timeout=120)
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for expected,actual in zip(inputs,rows):assert actual.startswith(expected+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
