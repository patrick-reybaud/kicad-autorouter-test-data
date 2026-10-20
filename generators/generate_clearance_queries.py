#!/usr/bin/env python3
"""Qualify clearance overlap ordering, symmetric enlargement and compensation bypass."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/clearance_queries_java.tsv'
def cases():
 rng=random.Random(20261012)
 def box(x,y,w,h):return f'B {x} {y} {x+w} {y+h}'
 def octagon(x,y,r):return f'O {x-r} {y-r} {x+r} {y+r} {x-y-r} {x-y+r} {x+y-r} {x+y+r}'
 def obj(i,shapes,mode=0,cl=None):return f'{i%4+1} {i%3} {i%2} {i%3 if cl is None else cl} {mode} {len(shapes)} '+' '.join(shapes)
 scene=[obj(0,[box(0,0,8,6),octagon(5,4,7)]),obj(1,[box(-4,-2,7,8)]),obj(2,['T 0 0 13 1 4 12']),obj(3,[octagon(0,0,4),box(2,2,3,3)]),obj(4,[box(-7,-5,10,10)])]
 queries=['N',box(-2,-2,8,8),octagon(1,2,8),'T -3 -2 9 1 1 10','C 3 2 5','H 0 0 10 1',box(100,100,3,4)]
 def row(d,c,k,l,cl,no,b,seed,default,changes,nets,q,objects):return f'C {d} {c} {k} {l} {cl} {no} {b} {seed} {default} {len(changes)} '+' '.join(changes)+' '+('-1' if nets is None else str(len(nets))+' '+ ' '.join(map(str,nets)))+' '+q+' '+str(len(objects))+' '+' '.join(objects)
 for d in [4,8]:
  for c in [0,1]:
   for k in range(4):
    for q in queries:
     for cl in range(3):yield row(d,c,k,0,cl,0,0,0,12,['1 2 0 32','2 1 0 4'],[0,1,1],q,scene)
 for seed in [0,2147483645,2147483646,2147483647,-2147483648,-1]:
  for k in range(4):
   for default in [0,2,25,100,2147483630,2147483646]:yield row(8,0,k,0,1,0,0,seed,default,[],[],box(-20,-20,50,50),scene)
 for mode in range(6):
  for k in range(4):
   for c in [0,1]:yield row(8,c,k,0,1,0,0,0,12,[],None if mode==0 else [],box(-20,-20,50,50),[obj(0,[box(0,0,4,4)],mode),*scene[1:]])
 for b in [0,1,2]:
  for k in range(4):
   for no in [0,1]:
    for l in [-1,0,7]:yield row(8,0,k,l,7,no,b,0,12,[],[],queries[5],scene)
 for d in [4,8]:
  for c in [0,1]:
   for k in range(4):
    for mode in range(6):yield row(d,c,k,0,1,0,0,0,12,[],[],box(-20,-20,50,50),[obj(0,[box(0,0,4,4)],mode+10),*scene[1:]])
 for _ in range(850):
  objects=[]
  for i in range(rng.randrange(0,12)):
   shapes=[]
   for j in range(rng.randrange(0,5)):
    x=rng.randrange(-30,31);y=rng.randrange(-30,31);shapes.append(rng.choice([box(x,y,rng.randrange(1,16),rng.randrange(1,16)),octagon(x,y,rng.randrange(1,16)),f'T {x} {y} {x+13} {y+1} {x+4} {y+12}','N']))
   objects.append(obj(i,shapes,rng.randrange(6) if rng.random()<0.15 else 0,rng.randrange(5)))
  nets=None if rng.random()<0.1 else [rng.randrange(-1,4) for _ in range(rng.randrange(4))]
  changes=[f'{rng.randrange(3)} {rng.randrange(3)} {rng.randrange(2)} {rng.choice([0,2,10,25,100])}' for i in range(rng.randrange(8))]
  yield row(rng.choice([4,8]),rng.randrange(2),rng.randrange(4),rng.randrange(-1,4),rng.randrange(-1,5),1 if rng.random()<0.08 else 0,1 if rng.random()<0.06 else 0,rng.choice([0,2147483646]),rng.choice([0,2,10,25,100]),changes,nets,rng.choice(queries),objects)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','rules/ClearanceMatrix.java','board/searchtree/ShapeSearchTree.java','board/searchtree/SearchTreeObject.java','datastructures/ShapeTree.java','datastructures/MinAreaTree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-clearance-queries-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ClearanceQueriesOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.ClearanceQueriesOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
