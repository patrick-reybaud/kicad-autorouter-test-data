#!/usr/bin/env python3
"""Qualify actual maze initialization and its pinned tie-pin preprocessing."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/maze_initialization_java.tsv'
def bits(v):return struct.pack('>d',v).hex()
def cases():
 rng=random.Random(20261120)
 def op(o,a=0,b=0,c=0,x=0.,y=0.):return list(map(str,[o,a,b,c,bits(x),bits(y)]))
 codes=[-1,-1,101,102,103,201,202,203,301,302,303,401,402,403,501,502,503,600,601,602,700,701,801,802,811,812,821,822,900]
 for k in range(480):
  ops=[op('I',[2,4,8,6,10,14][k%6],[0,2,4,8,14][k//6%5]),op('R',1073741825),op('C',1,3,k%2,x=0.,y=[3.,0.,-1.][k%3]),op('I',2,0),op('S',k%12),op('I',14,4),op('S',0)]
  for j in range(16):
   o=rng.choice(['I','I','I','Q','F','S','M','C','P','B','R']);a=b=c=0;x=y=0.
   if o=='I':a=rng.choice([-1,0,1,2,4,6,8,10,14,15]);b=rng.choice([-1,0,1,2,4,8,14]);c=rng.randrange(2)
   if o=='Q':a=rng.choice([-2147483648,-1,0,1000,2147483647]);x=rng.choice([0.,10.,100.]);y=rng.choice([0.,1.,80.])
   if o=='F':a=rng.choice(codes);b=rng.randrange(1,4)
   if o=='S':a=rng.randrange(16)
   if o=='M':a=rng.randrange(4)
   if o=='C':a=rng.randrange(2);b=rng.randrange(4);c=rng.randrange(-1,3);x=rng.choice([0.,.5,1.]);y=rng.choice([-1.,0.,.02,3.])
   if o=='P':a=rng.choice([-1,0,1,2,4,6,14]);b=rng.randrange(2);c=rng.randrange(6)
   if o=='B':a=rng.randrange(16);b=rng.randrange(2)
   if o=='R':a=rng.choice([0,1,2,99999,1073741825,2147483647])
   ops.append(op(o,a,b,c,x,y))
  yield ' '.join(['M',str(k%2),str(k//2%4),str(k-240),str(len(ops)),*[v for row in ops for v in row]])
 for k in range(120):
  failure=[101,102,103,201,202,203,301,302,303,401,402,403,501,502,503,600,601,602,700,701,801,802,811,812,821,822,900][k%27] if k<108 else -1
  ops=[op('Q',k-60,x=100.,y=80.),op('S',0 if k<108 else k-107),op('F',failure,k//27+1),op('I',14,4),op('F',-1,1),op('S',0),op('I',14,4,1),op('R',37)]
  yield ' '.join(['M',str(k%2),'1',str(k),str(len(ops)),*[v for row in ops for v in row]])
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting';names=['autoroute/maze/MazeSearchEngine.java','board/searchtree/ShapeSearchTree.java','autoroute/maze/DestinationDistance.java'];sources=[base/x for x in names];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for k,h in hashes.items():assert manifest['source_files'][k]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-maze-init-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/MazeInitializationOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx512m','-cp',tmp+os.pathsep+str(support),'app.freerouting.autoroute.maze.MazeInitializationOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
