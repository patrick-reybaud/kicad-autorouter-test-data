#!/usr/bin/env python3
"""Qualify local shape replacement identities/partial states and trace offset dispatch."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/search_updates_java.tsv'
def cases():
 rng=random.Random(20261013)
 def op(c,a=0,b=0,d=0):return f'{c} {a} {b} {d}'
 def row(ops):return f'C {len(ops)} '+' '.join(ops)
 for tree in [0,1,2,3,4,5,6]:
  for plan in range(4):
   for index in [-1,0,1,2,3]:
    yield row([op('P',plan),op('I',tree),op('Y',tree,index,3),op('g',tree,index),op('V',tree),op('Y',tree,index,0),op('V',tree),op('Y',tree,index,1),op('R',tree),op('Y',tree,index,2),op('y',tree,0,1)])
 for tree in [1,2,4,5,6]:
  for mode in range(5):
   for failure in [0,5]:yield row([op('I',tree),op('P',3,mode),op('D'),op('F',failure),op('Y',tree,1,3),op('F'),op('P',2),op('B'),op('Y',tree,0,1),op('B',1),op('Y',tree,1,2),op('V',tree)])
 for _ in range(150):
  tree=rng.choice([1,2,4,5,6]);ops=[op('I',tree)]
  for step in range(35):
   c=rng.choice(['Y','Y','Y','g','n','D','V','R','I','S','E','P']);a=tree;b=rng.randrange(-1,4);d=rng.randrange(4)
   if c=='P':a=rng.randrange(4);b=rng.randrange(5)
   if c=='S':b=rng.randrange(4)
   if c=='E':b=rng.randrange(3)
   ops.append(op(c,a,b,d))
  yield row(ops)
 paths=[None,[],[(0,0)],[(0,0),(10,0)],[(0,0),(10,0),(10,15),(23,15)],[(0,0),(7,3),(2,11),(25,18)],[(0,0),(10,10),(20,10),(20,20)]]
 for kind in range(3):
  for points in paths:
   for half in [-3,0,1,7]:
    for start,end in [(-2,9),(0,1),(0,5),(1,3),(3,9),(7,-1)]:
     yield f'O {kind} {half} {start} {end} '+('-1' if points is None else str(len(points))+' '+' '.join(f'{x} {y}' for x,y in points))
 for _ in range(150):
  points=[(rng.randrange(-40,41),rng.randrange(-40,41)) for _ in range(rng.randrange(2,10))]
  yield f'O {rng.randrange(3)} {rng.randrange(-5,20)} {rng.randrange(-2,9)} {rng.randrange(-2,15)} {len(points)} '+' '.join(f'{x} {y}' for x,y in points)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/actions/ItemSearchTreesInfo.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree45Degree.java','board/searchtree/ShapeSearchTree90Degree.java','geometry/planar/Polyline.java','board/searchtree/SearchTreeManager.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-search-updates-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/SearchUpdatesOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.SearchUpdatesOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
