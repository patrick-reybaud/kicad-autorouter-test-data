#!/usr/bin/env python3
"""Qualify ShapeSearchTree overlap traversal, early filtering, exact tests and retained results."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/search_queries_java.tsv'
def cases():
 rng=random.Random(20261011)
 def box(x,y,w,h):return f'B {x} {y} {x+w} {y+h}'
 def octagon(x,y,r):return f'O {x-r} {y-r} {x+r} {y+r} {x-y-r} {x-y+r} {x+y-r} {x+y+r}'
 def obj(i,shapes,mode=0,generic=0):return f'{i%4+1} {i%3} {i%2} {generic} {mode} {len(shapes)} '+' '.join(shapes)
 scene=[obj(0,[box(0,0,8,6),octagon(5,4,7)]),obj(1,[box(-4,-2,7,8)]),obj(2,['T 0 0 13 1 4 12']),obj(3,[octagon(0,0,4),box(2,2,3,3)]),obj(4,[box(-7,-5,10,10)])]
 queries=['N',box(-2,-2,8,8),octagon(1,2,8),'T -3 -2 9 1 1 10','C 3 2 5','H 0 0 10 1',box(100,100,3,4)]
 def row(d,k,l,f,no,seed,dead,nets,q,objects):return f'Q {d} {k} {l} {f} {no} {seed} {dead} '+('-1' if nets is None else str(len(nets))+' '+ ' '.join(map(str,nets)))+' '+q+' '+str(len(objects))+' '+' '.join(objects)
 for d in [4,8]:
  for k in range(6):
   for q in queries:
    for f in [-1,0,1,2,3,4]:yield row(d,k,-1,f,0,1,0,[0,1,1],q,scene)
 for mode in range(6):
  for k in range(6):
   for dead in [0,1]:yield row(8,k,-1,0,0,0,dead,None,box(-20,-20,50,50),[obj(0,[box(0,0,4,4)],mode),*scene[1:]])
 for k in range(6):
  for no in [0,1]:
   for l in [-1,0,7]:yield row(8,k,l,2,no,0,0,None,box(-20,-20,50,50),[obj(0,[box(0,0,4,4)],0,1),*scene[1:]])
 for _ in range(650):
  objects=[]
  for i in range(rng.randrange(0,12)):
   shapes=[]
   for j in range(rng.randrange(0,5)):
    x=rng.randrange(-30,31);y=rng.randrange(-30,31);shapes.append(rng.choice([box(x,y,rng.randrange(1,16),rng.randrange(1,16)),octagon(x,y,rng.randrange(1,16)),f'T {x} {y} {x+13} {y+1} {x+4} {y+12}','N']))
   objects.append(obj(i,shapes,rng.randrange(6) if rng.random()<0.15 else 0,1 if rng.random()<0.06 else 0))
  nets=None if rng.random()<0.15 else [rng.randrange(-1,4) for _ in range(rng.randrange(4))]
  yield row(rng.choice([4,8]),rng.randrange(6),rng.randrange(-1,4),rng.randrange(-1,5),1 if rng.random()<0.08 else 0,0,1 if rng.random()<0.08 else 0,nets,rng.choice(queries[1:]),objects)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/searchtree/ShapeSearchTree.java','board/searchtree/SearchTreeObject.java','datastructures/ShapeTree.java','datastructures/MinAreaTree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-search-queries-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/SearchQueriesOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.SearchQueriesOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
