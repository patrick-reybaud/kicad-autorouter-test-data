#!/usr/bin/env python3
"""Qualify Item lazy shape caches, error retries, tree hooks and mutation callback order."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/item_tree_cache_java.tsv'
def cases():
 rng=random.Random(20261009)
 def op(c,a=0,b=0,d=0):return f'{c} {a} {b} {d}'
 def row(ops):return f'C {len(ops)} '+' '.join(ops)
 for tree in range(5):
  for plan in range(4):
   for mode in range(5):
    yield row([op('P',plan,mode),op('S',tree,2),op('n',tree),op('g',tree,0),op('g',tree,-1),op('g',tree,9),op('E',tree,1),op('S',tree,3),op('g',tree,1),op('m',3,1,1),op('g',tree,1),op('D'),op('n',tree),op('B'),op('g',tree,0),op('n',tree),op('S',tree,2),op('B',1),op('C'),op('e',tree),op('t',0)])
 for tree in [1,2,4]:
  for plan in range(4):
   yield row([op('P',plan),op('I',tree),op('e',tree),op('V',tree),op('D'),op('V',tree),op('g',tree,1),op('R',tree),op('e',tree),op('V',tree)])
 for fail in range(6):
  for compensation in [0,1]:
   for observer in [0,1]:
    yield row([op('n',1),op('p',compensation),op('o',observer),op('Q'),op('F',fail),op('x',4,2),op('A',2),op('A',-1),op('A',3),op('F'),op('x',-2,0),op('A',1),op('B'),op('A',1),op('x',1,1)])
 for tree in [0,1,2,3,4]:
  yield row([op('k',tree),op('u',tree)]+[op('v',tree,cl,layer) for cl in [-1,0,1,2,3] for layer in [-1,0,1,2]])
 for _ in range(200):
  ops=[]
  for step in range(75):
   c=rng.choice(['n','g','t','E','e','S','C','D','P','m','B','F','A','x','b','Q','p','o','v','u','k','z']);a=rng.randrange(5);b=rng.randrange(-1,6);d=rng.randrange(-1,5)
   if c=='P':a=rng.randrange(4);b=rng.randrange(5)
   if c=='F':a=rng.randrange(6)
   if c=='E':b=rng.randrange(3)
   if c=='S':b=rng.randrange(4)
   if c=='m':a=rng.randrange(4)
   if c in ['B','b','p','o']:a=rng.randrange(2)
   ops.append(op(c,a,b,d))
  yield row(ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/actions/ItemSearchTreesInfo.java','board/searchtree/ShapeSearchTree.java','board/searchtree/SearchTreeManager.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-item-tree-cache-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ItemTreeCacheOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.ItemTreeCacheOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
