#!/usr/bin/env python3
"""Qualify general room completion, recursive restraint, division and deterministic obstacle order."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/room_completion_java.tsv'
def cases():
 rng=random.Random(20261107)
 def op(o,a=0,b=0,c=0,d=0):return list(map(str,[o,a,b,c,d]))
 for k in range(480):
  shapes=[(0,0,0,0,0),(1,0,0,200,200),(1,10,10,190,190),(1,30,30,30,30),(1,10,80,190,80),(1,10,10,190,190),(1,80,70,120,140),(2,80,70,120,140),(3,80,70,120,140),(1,40,20,40,150),(1,70,80,120,80),(1,210,210,240,240)]
  for i in range(8):
   x=rng.randrange(-20,200);y=rng.randrange(-20,200);shapes.append((rng.randrange(1,4),x,y,x+rng.randrange(1,80),y+rng.randrange(1,80)))
  if k>=320:
   shapes[6]=(4,80,70,120,140);shapes[7]=(5,80,70,120,140);shapes[8]=(6,80,70,120,140)
   for i in range(12,20):shapes[i]=(rng.randrange(4,7),*shapes[i][1:])
  out=['C',str(k%2)]
  for shape in shapes:out+=list(map(str,shape))
  out+=list(map(str,[rng.choice([0,1,2]),k%3,rng.choice([0,3,4,5]),2,(k+1)%3,rng.choice([3,4,5])]))
  n=[0,1,4,12,40][k%5];out.append(str(n))
  for i in range(n):out+=list(map(str,[rng.randrange(2),rng.randrange(1,60),rng.randrange(6,20),rng.randrange(3),int(rng.randrange(5)!=0)]))
  ops=[op('C',1,1,0,0),op('C',2,1,n,1)]
  for contained in [0,3,4,5]:
   ops += [op('S',1,1,contained),op('R',1,6),op('R',1,7),op('R',1,8),op('C',1,2,0,5),op('D',1,1),op('D',1,0),op('D',1,1,2)]
  ops += [op('D',-1),op('D',0),op('R',0,6),op('R',1,0),op('C',0),op('B'),op('C',1),op('B',-10,-10,200,200)]
  for j in range(40):
   o=rng.choice(['C','C','R','D','S','F']);a=rng.randrange(3);b=rng.randrange(20);c=rng.randrange(n+1);d=rng.choice([0,1,2,5,6])
   if o=='D':a=rng.randrange(-1,3);b=rng.randrange(2);c=rng.randrange(3)
   if o=='S':a=rng.randrange(1,3);b=rng.choice([0,1,2,9,10,11]);c=rng.randrange(6)
   if o=='F':a=rng.choice([-1,-1,701,702,711,712,721,722,900,101,102,201,202,301,302,401,402]);b=rng.randrange(1,4)
   ops.append(op(o,a,b,c,d))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/searchtree/ShapeSearchTree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-room-completion-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/RoomCompletionOracle.java'),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.RoomCompletionOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
