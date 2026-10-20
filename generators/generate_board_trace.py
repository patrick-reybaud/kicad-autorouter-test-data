#!/usr/bin/env python3
"""Qualify partial Trace/PolylineTrace state, geometry and tree adapter against actual Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/board_trace_java.tsv'
def cases():
 rng=random.Random(20261014)
 def op(c,a=0,b=0,d=0):return f'{c} {a} {b} {d}'
 def row(board,layer,half,fixed,nets,points,ops):return f'T {board} {layer} {half} {fixed} '+('-1' if nets is None else str(len(nets))+' '+' '.join(map(str,nets)))+' '+('-1' if points is None else str(len(points))+' '+' '.join(f'{x} {y}' for x,y in points))+' '+str(len(ops))+' '+' '.join(ops)
 paths=[None,[],[(0,0)],[(0,0),(20,0)],[(0,0),(10,0),(10,15),(23,15)],[(0,0),(7,3),(2,11),(25,18)]]
 sequence=[op('N',5,0),op('A',1),op('W',1),op('W',2),op('W',3),op('I'),op('g',2,0),op('g',3,0),op('T',3,4),op('Q',1,2,3),op('M',5,6),op('C',0,0),op('C',1,-1),op('C',1,8),op('P',2),op('e',1),op('e'),op('f',1,1,1),op('R'),op('s',4),op('s',2,1)]
 for board in [0,1]:
  for layer in [-2,0,1,4]:
   for fixed in [-1,0,2,3]:
    for points in paths:yield row(board,layer,2,fixed,[1],points,sequence)
 for failure in range(4):
  for p in range(4):yield row(0,0,3,0,[1],paths[4],[op('I'),op('F',failure),op('P',p),op('F'),op('W'),op('N',0,0,1),op('B'),op('P',2),op('B',1),op('P',3)])
 for nets in [None,[],[0],[-1,2]]:
  yield row(0,0,0,0,nets,paths[3],sequence)
 for _ in range(180):
  ops=[]
  for j in range(30):
   c=rng.choice(['I','R','T','Q','M','L','B','F','P','C','W','N','A','S','g','e','s','f','z']);a=rng.randrange(5);b=rng.randrange(-1,6);d=rng.randrange(-1,5)
   if c in ['F','P']:a=rng.randrange(4)
   if c=='S':a=rng.randrange(-1,4)
   if c in ['B','e']:a=rng.randrange(2)
   ops.append(op(c,a,b,d))
  yield row(rng.randrange(2),rng.randrange(-3,5),rng.randrange(-3,10),rng.randrange(4),[1],rng.choice(paths[3:]),ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/items/Trace.java','board/trace/PolylineTrace.java','board/trace/PolylineTraceGeometry.java','board/trace/PolylineTraceSearchTreeAdapter.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree45Degree.java','board/searchtree/ShapeSearchTree90Degree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-board-trace-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/BoardTraceOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.trace.BoardTraceOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
