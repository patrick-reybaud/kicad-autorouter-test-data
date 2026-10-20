#!/usr/bin/env python3
"""Qualify unique contact dispatch and Item graph traversal against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/item_contacts_java.tsv'
def cases():
 rng=random.Random(20261017)
 for t1 in range(4):
  for t2 in range(4):
   for p in range(6):
    for q in range(6):yield f'P {t1} 1 2 {p} {q} {t2} 1 3 {q} {p}'
 for _ in range(180):yield 'P '+' '.join(str(rng.randrange(4))+' '+str(rng.randrange(-1,4))+' '+str(rng.randrange(-1,4))+' '+str(rng.randrange(6))+' '+str(rng.randrange(6)) for i in range(2))
 for case in range(240):
  n=rng.randrange(1,12);values=[]
  for i in range(n):
   ids=[1,3,9,2147483647,2147483646];links=[rng.randrange(n+1) for j in range(rng.randrange(n+2))];count=-1 if rng.randrange(8)==0 else len(links)
   values += [rng.randrange(2),rng.choice(ids),rng.randrange(-1,4),rng.randrange(-2,3),count]+(links if count>=0 else [])
  ops=[]
  for i in range(30):
   op=rng.choice(['C','Y','I']);a=rng.randrange(n+1);b=rng.randrange(-1,4) if op=='C' else rng.randrange(n+1);c=rng.randrange(2) if op=='C' else rng.randrange(n+1);d=rng.randrange(1<<n)
   ops.append(f'{op} {a} {b} {c} {d}')
  yield 'G '+str(n)+' '+' '.join(map(str,values))+' '+str(rng.choice([-1,-1,-1,rng.randrange(n+1)]))+' '+str(len(ops))+' '+' '.join(ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/items/DrillItem.java','board/model/items/Trace.java','board/model/items/ConductionArea.java','board/model/items/ObstacleArea.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree45Degree.java','board/searchtree/ShapeSearchTree90Degree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-item-contacts-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ItemContactsOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.ItemContactsOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
