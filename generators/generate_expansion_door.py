#!/usr/bin/env python3
"""Qualify expansion room lists, maze sections, door geometry and reference state transitions."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/expansion_door_java.tsv'
def cases():
 rng=random.Random(20261101)
 def bits(x):return struct.pack('>d',x).hex()
 def sh(kind,x,y,w,h):return [str(kind),*([str(v) for v in [x,y,w,h]] if kind else [])]
 def op(o,a=0,b=0,c=0,d=0.):return [o,str(a),str(b),str(c),bits(d)]
 offsets=[-4.,-3.,-2.,-1.,0.,1.,2.,8.,12.,100.,float('nan'),float('inf'),-float('inf')]
 for k in range(480):
  kinds=[k%2,(k//2)%2];shapes=[]
  if k<160:
   # Shared edge, overlap corner, containment and point contacts in every shape form.
   boxes=[(0,0,100,100),(100,0,80,100),(40,30,100,100),(20,20,10,10),(100,100,50,50),(150,150,20,20)]
   q=boxes[1+(k//16)%5];shapes=[sh(1+k%4,*boxes[0]),sh(1+(k//4)%4,*q)]
  else:
   for i in range(2):shapes.append(sh(rng.randrange(5),rng.randrange(-200,200),rng.randrange(-200,200),rng.randrange(0,180),rng.randrange(0,180)))
  out=['E']
  for i in range(2):out+=shapes[i]+[str(kinds[i]),str(rng.choice([-2147483648,-1,0,1,5,2147483647])),str(rng.choice([-2147483648,-1,0,1,2,2147483647]))]
  out.append(str([-9,1,2,0,3][k%5]))
  ops=[op('G',1,d=d) for d in offsets]+[op('L',1,4),op('T',1,0,2,33.),op('W',1),op('L',1,4),op('Q',1,0),op('L',1,2),op('X',0,0,1),op('A',1,1),op('A',1,2),op('A',2,1),op('U',1),op('C',1),op('V',1,3),op('P',2),op('B',2,0),op('Z',2)]
  for i in range(2):
   if kinds[i]==0:ops += [op('F',i+1),op('I',i+1,1),op('I',i+1,2)]
  for j in range(90):
   o=rng.choice(['A','R','C','E','P','G','L','N','Z','T','B','Q','H','U','V','W','X','S','D'])
   a=rng.randrange(1,5);b=rng.randrange(-1,8);c=rng.randrange(5)
   if o in ['A','R','C','E','P','U','S']:a=rng.randrange(1,3)
   if o in ['A','R','V']:b=rng.randrange(5)
   if o in ['E','S']:b=rng.randrange(3)
   if o=='V':a=rng.randrange(3)
   ops.append(op(o,a,b,c,rng.choice(offsets)))
  for failure in [-1,3,4,5,6,7,8,103,106,-1]:
   ops += [op('K',failure),op('G',1,d=1.),op('D',1),op('G',2,d=0.),op('D',2)]
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/maze/MazeSearchElement.java','autoroute/expansion/ExpandableObject.java','autoroute/expansion/ExpansionRoom.java','autoroute/expansion/FreeSpaceExpansionRoom.java','autoroute/expansion/IncompleteFreeSpaceExpansionRoom.java','autoroute/expansion/ExpansionDoor.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-expansion-door-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ExpansionDoorOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.autoroute.expansion.ExpansionDoorOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
