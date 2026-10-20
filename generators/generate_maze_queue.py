#!/usr/bin/env python3
"""Execute the actual MazeSearchEngine anonymous TreeSet fanout filter."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/maze_queue_java.tsv'
def bits(v):return struct.pack('>d',v).hex()
def cases():
 rng=random.Random(20261118)
 def op(o,a=0,b=0,c=0,d=0,x=0.,y=0.,z=0.,w=0.):return list(map(str,[o,a,b,c,d,bits(x),bits(y),bits(z),bits(w)]))
 values=[-0.,0.,-1.,.5,1.,499.,500.,501.,2999.,3000.,3001.,1e308,float('nan'),float('inf'),-float('inf')]
 for k in range(600):
  ops=[op('F',1,1,0),op('Q',k%5,x=.5,y=3.),op('R',x=[1.,.5,0.1,1000.][k%4],y=1.)]
  for i,v in enumerate([499.,500.,501.,2999.,3000.,3001.]):ops +=[op('E',i+1,1+i%2,1,i%3,x=v),op('A',i+1),op('A',i+1)]
  ops +=[op('P'),op('P'),op('X',[0,1,2,3,4,11,12,13,14,15][k%10],[-1,101,102,200,300,301,302][k//10%7],k%2+1),op('F',1,1,k%3,x=k%3),op('E',7,2,1,k%4,x=1000.),op('A',7)]
  for j in range(28):
   o=rng.choice(['E','E','A','A','A','P','Q','R','F','X','C']);a=b=c=d=0;x=rng.choice(values);y=rng.choice(values);z=rng.choice(values);w=rng.choice(values)
   if o=='F':a=rng.randrange(2);b=rng.randrange(2);c=rng.randrange(-1,3)
   if o=='E':a=rng.randrange(1,16);b=rng.randrange(3);c=rng.randrange(4);d=rng.randrange(16)
   if o=='A':a=rng.randrange(16)
   if o=='Q':a=rng.randrange(5)
   if o=='X':a=rng.choice([0,1,2,3,4,11,12,13,14,15]);b=rng.choice([-1,101,102,103,200,300,301,302]);c=rng.randrange(1,3)
   ops.append(op(o,a,b,c,d,x,y,z,w))
  yield ' '.join(['Q',str(int(k%31==0)),str(len(ops)),*[v for row in ops for v in row]])
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting';names=['autoroute/maze/MazeSearchEngine.java','autoroute/maze/MazeListElement.java'];sources=[base/x for x in names];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for k,h in hashes.items():assert manifest['source_files'][k]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-maze-queue-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/MazeQueueOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx512m','-cp',tmp+os.pathsep+str(support),'app.freerouting.autoroute.maze.MazeQueueOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
