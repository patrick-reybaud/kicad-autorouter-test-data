#!/usr/bin/env python3
"""Qualify trace-entry reuse/change/merge/cutout and manager/adapter delegation."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/trace_updates_java.tsv'
def cases():
 rng=random.Random(20261015)
 path=[(0,0),(10,0),(10,10),(20,10),(20,20),(30,20)]
 start=path[:3];end=path[3:];other=[(0,0),(-10,0),(-10,-10)];joined=[(-10,-10),(-10,0),(0,0),(10,0),(10,10),(20,10),(20,20),(30,20)]
 def row(scope,angle,op,a,b,flags,fail,polys):return f'U {scope} {angle} {op} {a} {b} {flags} {fail} '+' '.join(str(len(p))+' '+' '.join(f'{x} {y}' for x,y in p) for p in polys)
 for scope in range(4):
  for angle in range(3):
   for op in ['C','F','E','R']:
    for reverse in [0,1]:
     polys=[path[::-1] if reverse else path,start if op=='R' else other,end,joined]
     for a,b in [(-1,0),(0,0),(0,2),(1,1),(1,3),(2,4),(7,0)]:yield row(scope,angle,op,a,b,0,0,polys)
 for flags in [1,2,4,8,16,32,64,128,256]:
  for scope in range(4):
   for op in ['C','F','E','R']:
    for fail in ([0,1,2,4,5] if flags==256 else [0]):yield row(scope,2,op,1,3,flags,fail,[path,start if op=='R' else other,end,joined])
 for _ in range(320):
  op=rng.choice(['C','F','E','R']);polys=[]
  for i in range(4):
   count=rng.randrange(0,8);p=[];x=y=0
   for j in range(count):
    p.append((x,y))
    if j%2:x+=rng.randrange(1,10)
    else:y+=rng.randrange(1,10)
   polys.append(p)
  yield row(rng.randrange(4),rng.randrange(3),op,rng.randrange(-1,5),rng.randrange(-1,6),rng.choice([0,0,0,8,64,256]),rng.randrange(7),polys)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/items/Trace.java','board/trace/PolylineTrace.java','board/trace/PolylineTraceGeometry.java','board/trace/PolylineTraceSearchTreeAdapter.java','board/searchtree/SearchTreeManager.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree45Degree.java','board/searchtree/ShapeSearchTree90Degree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-trace-updates-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/TraceUpdatesOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.trace.TraceUpdatesOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
