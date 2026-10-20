#!/usr/bin/env python3
"""Qualify actual SearchTreeManager tree lifecycle, selection, invalidation and reinsertion."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/search_tree_manager_java.tsv'
def cases():
 rng=random.Random(20261010)
 def op(c,a=0,b=0,d=0):return f'{c} {a} {b} {d}'
 def row(ops,null=0):return f'M {null} {len(ops)} '+' '.join(ops)
 for angle in [-1,0,1,2]:
  for comp in [0,1]:
   yield row([op('I',1),op('I',2),op('I',3),op('A',angle),op('G',1),op('G',2),op('G',1),op('V',1),op('C',comp),op('M',1,1,42),op('v'),op('G',2),op('r',comp),op('r',2),op('G',2),op('Z'),op('E'),op('R',2),op('R',2),op('I',2),op('V',2)])
 for mode in range(4):
  for target in [1,2,3]:
   for operation in ['G','C','E','v']:
    yield row([op('I',1),op('I',2),op('I',3),op('C',1),op('P',target,2,mode),op('D',target),op(operation,2 if operation=='G' else 0),op('P',target,1,0),op('E'),op('V',target),op('Q'),op('x',target,3,0),op('F',target,2)])
 yield row([op('G'),op('G',1),op('C',1),op('G',1),op('G',2),op('v'),op('E'),op('C'),op('I',1),op('R',1),op('V',1)],1)
 for _ in range(200):
  ops=[]
  for step in range(60):
   c=rng.choice(['I','R','V','G','C','v','r','Z','E','A','P','B','D','b','M','F','x','Q','z']);a=rng.randrange(1,4);b=rng.randrange(3);d=rng.randrange(4)
   if c in ['G','r']:a=rng.randrange(3)
   if c=='C':a=rng.randrange(2)
   if c=='A':a=rng.randrange(-1,3)
   if c in ['B','b']:b=rng.randrange(2)
   if c=='M':a=rng.randrange(3);b=rng.randrange(3);d=rng.choice([0,2,10,25,100])
   ops.append(op(c,a,b,d))
  yield row(ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/searchtree/SearchTreeManager.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree45Degree.java','board/searchtree/ShapeSearchTree90Degree.java','board/model/structure/AngleRestriction.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-search-tree-manager-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/SearchTreeManagerOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.SearchTreeManagerOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
