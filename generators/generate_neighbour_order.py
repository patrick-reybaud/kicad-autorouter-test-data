#!/usr/bin/env python3
"""Qualify sorted neighbour corners, comparison caches, TreeSet order and door eligibility."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/neighbour_order_java.tsv'
def cases():
 rng=random.Random(20261110)
 def op(o,a=0,b=0,c=0,d=0,e=0,f=0,g=0,h=0):return list(map(str,[o,a,b,c,d,e,f,g,h]))
 for k in range(360):
  room_shape=rng.choice([1,9,10] if k<300 else [14,15]);out=list(map(str,['N',room_shape,*[rng.choice([-2147483648,-3,0,1,1,2,2147483647]) for _ in range(4)],rng.randrange(1,3),rng.randrange(1,3)]))
  ops=[]
  for i in range(1,13):ops += [op('N',i,1+i%4,2+i%4,0,0,i%3,i%4),op('A',i)]
  for t in range(5):ops += [op('T',t)]
  for shape in [6,7,8]:
   for a,b in [(1,2),(1,5),(5,1),(5,6),(3,4),(6,2)]:ops += [op('D',a,b,shape)]
  ops += [op('O',1,2),op('D',1,2,0),op('Z',1),op('D',1,2,0),op('d',7,0,5),op('d',8,1,6)]
  for j in range(70):
   o=rng.choice(['N','E','C','C','F','L','A','X','M','S','D','d','T','P','B']);a=rng.randrange(1,13);b=rng.randrange(5);c=rng.randrange(14 if k<300 else 16);d=rng.randrange(14 if k<300 else 16);e=rng.randrange(3);f=rng.randrange(3);g=rng.randrange(4);h=0
   if o in ['C']:b=rng.randrange(13)
   if o=='M':a=rng.randrange(1,5);b=rng.choice([-2147483648,-3,0,1,2,2147483647])
   if o=='S':a=rng.choice([0,1,9,10])
   if o=='D':a=rng.randrange(7);b=rng.randrange(7)
   if o=='d':a=rng.randrange(9);b=rng.randrange(3);c=rng.randrange(3,7)
   if o=='T':a=rng.randrange(5)
   if o=='P':a=rng.choice([-1,-1,101,102,103,104,201,202,303,304,401,402]);b=rng.randrange(1,4)
   if o=='B':a=rng.randrange(7);b=rng.randrange(3)
   ops.append(op(o,a,b,c,d,e,f,g,h))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/expansion/SortedRoomNeighbours.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-neighbour-order-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/NeighbourOrderOracle.java'),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.NeighbourOrderOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
