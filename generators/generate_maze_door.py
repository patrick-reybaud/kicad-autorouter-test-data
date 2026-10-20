#!/usr/bin/env python3
"""Qualify actual actual door segmentation, acute-corner gates and projection expansion."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/maze_door_java.tsv'
def bits(v):return struct.pack('>d',v).hex()
def cases():
 rng=random.Random(20261123)
 def op(o,*a,d=()):return [o,*map(str,[*a,*([0]*(8-len(a)))]),*(bits(v) for v in [*d,*([0.]*(4-len(d)))])]
 codes=[-1,*range(11,17),*range(21,27),101,102,111,112,400]
 for k in range(600):
  ops=[op('W',0,k%4,7,8),op('S',1,0,k%2,0,0,0),op('H',1,7,2),op('S',1,0,k//2%2,0,10,k%3),op('O',1,0,k%2),op('S',1,0,1,0,0,0)]
  for j in range(16):
   o=rng.choice(['S','S','S','P','C','F','M','H','L','O','V','J','R','W'])
   if o=='S':v=op(o,rng.randrange(3),0,rng.randrange(2),int(rng.random()<.06),rng.choice([-2147483648,-1,0,1,100,2147483647]),rng.randrange(-1,3))
   if o=='P':v=op(o,rng.randrange(5),rng.randrange(-2,4),rng.randrange(5),rng.randrange(-2,4),rng.randrange(3),rng.randrange(8),rng.randrange(4),rng.randrange(-1,3),d=(rng.choice([-1.,0.,100.,float('nan'),float('inf')]),0.))
   if o=='C':v=op(o,rng.randrange(2),int(rng.random()<.1),d=(rng.choice([-1.,0.,1.,13.,float('nan'),float('inf')]),rng.choice([0.,1.,3.,float('nan')]),rng.choice([0.,2.,5.,float('inf')])))
   if o in ['F','M']:v=op(o,rng.choice(codes),rng.randrange(1,5))
   if o=='H':v=op(o,rng.randrange(1,3),rng.randrange(11),rng.randrange(3))
   if o=='L':v=op(o,rng.randrange(1,3),rng.randrange(-1,3))
   if o=='O':v=op(o,rng.randrange(1,3),rng.randrange(3),rng.randrange(-1,2))
   if o=='V':v=op(o,rng.randrange(2),rng.randrange(2),rng.randrange(2),d=(rng.choice([0.,.005,.5]),rng.choice([0.,.005,3.])))
   if o=='J':v=op(o,rng.randrange(2),-10,-10,10,10)
   if o=='R':v=op(o,rng.randrange(2))
   if o=='W':v=op(o,rng.randrange(2),rng.choice([0,1,3,10,25]),rng.randrange(11),rng.randrange(11))
   ops.append(v)
  yield ' '.join(['D',str(k%3),str(k//3%4),str(len(ops)),*[x for row in ops for x in row]])
 for width in [0,1,3,10,25]:
  for shape in range(11):
   for thick in range(2):
    ops=[op('W',0,width,shape,8),op('H',1,3,2),op('S',1,0,thick,0,0,0),op('H',1,7,2),op('S',1,0,thick,0,10,1)]
    yield ' '.join(['D','1','3',str(len(ops)),*[x for row in ops for x in row]])
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting';names=['autoroute/maze/MazeSearchEngine.java','autoroute/expansion/ExpansionDoor.java','autoroute/maze/MazeListElement.java'];sources=[base/x for x in names];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for k,h in hashes.items():assert manifest['source_files'][k]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-maze-init-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/MazeDoorOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx512m','-cp',tmp+os.pathsep+str(support),'app.freerouting.autoroute.maze.MazeDoorOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
  if result.returncode:
   import sys
   sys.stderr.write(result.stderr);result.check_returncode()
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for a,b in zip(inputs,rows):assert b.startswith(a+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
