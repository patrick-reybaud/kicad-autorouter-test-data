#!/usr/bin/env python3
"""Qualify DrillItem connected movement and ordered layer reconnection against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/drill_move_java.tsv'
def cases():
 rng=random.Random(20261019)
 contacts=[[2,1,0,2,1],[2,9,0,9,2],[2,3,2,4,1],[2,2,1,3,0],[1,6,0,0,0],[0,7,0,0,0]]
 def row(cfg,cs,vs):return 'M '+' '.join(map(str,cfg))+' '+str(len(cs))+' '+' '.join(' '.join(map(str,c)) for c in cs)+' '+' '.join(' '.join(map(str,v)) for v in vs)
 for angle in [-1,0,1,2]:
  for mode in range(4):
   for center in range(5):yield row([angle,0,mode,center,0,0,0],contacts,[(5,8,1),(-10,-2,1),(0,0,1)])
 for flags in [0,1,2,4,8,16,3,12,17]:
  for fail in range(9):yield row([2,flags,3,1,fail,2,3],contacts,[(9,4,1),(-5,8,3),(4,4,1)])
 for _ in range(240):
  cs=[[rng.randrange(3),rng.randrange(1,20),rng.randrange(-2,5),rng.randrange(-2,12),rng.randrange(-1,4)] for i in range(rng.randrange(10))]
  yield row([rng.randrange(-1,3),rng.choice([0,0,0,1,2,4,8,16]),rng.randrange(4),rng.randrange(5),rng.randrange(9),rng.randrange(1,5),rng.randrange(4)],cs,[(rng.randrange(-50,51),rng.randrange(-50,51),rng.choice([1,1,3])) for i in range(3)])
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/items/DrillItem.java','board/model/items/Trace.java','board/trace/PolylineTrace.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree45Degree.java','board/searchtree/ShapeSearchTree90Degree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-drill-move-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/DrillMoveOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.DrillMoveOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
