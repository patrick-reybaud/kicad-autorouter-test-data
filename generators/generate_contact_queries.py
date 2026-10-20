#!/usr/bin/env python3
"""Qualify geometric contact discovery using actual trees and personal early filters."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/contact_queries_java.tsv'
def cases():
 rng=random.Random(20261018)
 def row(flags,objects):
  ops=[]
  for i,obj in enumerate(objects,1):
   for op in ['A','N','C']:ops.append(f'{op} {i} 0 0')
   for l in [-1,0,1,2,3]:
    for op in ['L','c']:ops.append(f'{op} {i} {l} 0')
   for net in [-1,1,2]:ops.append(f'G {i} {net} {i%2}')
   if obj[0]==2:
    for op in ['S','E','T']:ops.append(f'{op} {i} 0 0')
    for p in range(8):
     for ign in [0,1]:ops.append(f'P {i} {p} {ign}')
   if obj[0]==4:
    for no in [-1,0,1,2]:
     for null in [0,1]:ops.append(f'Q {i} {no} {null}')
  return f'Q {flags} {len(objects)} '+' '.join(' '.join(map(str,o)) for o in objects)+' '+str(len(ops))+' '+' '.join(ops)
 base=[[2,1,1,0,0,1,2,1,-1],[2,2,1,0,0,2,3,2,-1],[3,3,1,0,2,1,0,3,3],[4,4,1,0,0,4,0,4,-1],[4,5,2,1,1,5,0,5,-1],[5,6,1,0,0,0,0,3,-1],[1,7,1,0,0,0,0,3,-1]]
 for flags in [0,1]:
  for area in range(6):
   for net in [0,1,2]:
    objs=[x[:] for x in base];objs[3][5]=area;objs[3][2]=net;yield row(flags,objs)
 for _ in range(220):
  objs=[]
  for i in range(rng.randrange(2,10)):
   ty=rng.randrange(1,6);first=rng.randrange(3);last=rng.randrange(first,3);a=rng.randrange(6) if ty==4 else rng.randrange(8)
   objs.append([ty,rng.choice([i+1,i+1,1,2147483647]),rng.randrange(-1,4),first,last,a,rng.randrange(8),rng.randrange(8),rng.randrange(-1,8)])
  yield row(rng.choice([0,0,0,1]),objs)
 for count in [0,1,2,3,4,7,8,15,16,31,32,63]:
  for seed_count in [0,1,4]:
   for mode in [0,1,2,3]:
    arrays=[]
    for length in [seed_count,count,count//2+1,6]:
     values=[rng.randrange(-16,17) if mode<2 else rng.choice([-2147483648,-2147483647,-1,0,1,2147483646,2147483647]) for i in range(length)]
     if mode==1:values.sort()
     if mode==3:values.sort(reverse=True)
     arrays.append(str(length)+' '+' '.join(map(str,values)))
    yield 'S '+' '.join(arrays)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/items/DrillItem.java','board/model/items/Trace.java','board/model/items/ConductionArea.java','board/model/items/ObstacleArea.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree45Degree.java','board/searchtree/ShapeSearchTree90Degree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-contact-queries-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ContactQueriesOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.ContactQueriesOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
