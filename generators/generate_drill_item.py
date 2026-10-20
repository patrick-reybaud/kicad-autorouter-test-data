#!/usr/bin/env python3
"""Qualify DrillItem state, geometry and compensated hole obstacles against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/drill_item_java.tsv'
def cases():
 rng=random.Random(20261016)
 def op(c,a=0,b=0,d=0):return f'{c} {a} {b} {d}'
 base=[op('F'),op('L'),op('N'),op('B'),op('R'),op('W'),op('C'),op('r')]
 base += [op(c,i) for c in ['S','A','G','t'] for i in [-1,0,1,2,3,4,2147483647]]
 base += [op('K',i) for i in range(4)]+[op('H',i) for i in range(4)]
 base += [op('U',i,j,l) for i,j,l in [(0,0,0),(0,1,0),(1,2,2),(2,5,-1),(3,4,3),(0,7,3),(1,6,1)]]
 base += [op('g',i,j) for i,j in [(0,0),(1,1),(2,3),(-1,-1),(-1,1),(3,2)]]
 base += [op('f'),op('F'),op('L'),op('s',0,5),op('D'),op('F'),op('L'),op('W'),op('B'),op('T',3,7),op('Q',1,-2,4),op('M',2,3),op('R'),op('C'),op('c',3),op('H'),op('K'),op('p'),op('F'),op('D'),op('F'),op('p',1),op('W')]
 def row(cfg,ops):return 'D '+' '.join(map(str,cfg))+' '+str(len(ops))+' '+' '.join(ops)
 for angle in [-1,0,1,2]:
  for comp in [0,1,2]:
   for hole in [0,1,20]:
    for kind in range(8):
     yield row([angle,1,comp,hole,5,13,kind,0,1,0,1,1,0],base)
 for flags in [0,1,2,4,8,16,3,12]:
  for mask in [0,1,6,8,15]:
   for ctr in range(4):yield row([1,2,2,30,10,mask,3,1,0,1,4,ctr,flags],base)
 for _ in range(260):
  cfg=[rng.randrange(-1,3),rng.randrange(-1,5),rng.randrange(-1,5),rng.choice([-1,0,5,80,2147483647]),rng.randrange(16),rng.randrange(16),rng.randrange(8),rng.randrange(2),rng.randrange(2),rng.randrange(2),rng.randrange(7),rng.randrange(5),rng.choice([0,0,0,1,2,4,8,16])]
  ops=base[:]+[op('s',rng.randrange(4),rng.randrange(8)),op('D'),op('W'),op('R')]+[op('U',rng.randrange(4),rng.randrange(8),rng.randrange(-2,6)) for i in range(6)]
  yield row(cfg,ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/items/DrillItem.java','core/library/Padstack.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree45Degree.java','board/searchtree/ShapeSearchTree90Degree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-drill-item-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/DrillItemOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.DrillItemOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
