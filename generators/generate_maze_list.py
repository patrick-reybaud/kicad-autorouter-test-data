#!/usr/bin/env python3
"""Compare maze candidate records, tie breaks and Java TreeSet traversal/removal."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/maze_list_java.tsv'
def cases():
 rng=random.Random(20261031)
 def bits(x):return struct.pack('>d',x).hex()
 costs=[float('nan'),float('inf'),-float('inf'),0.,-0.,1.,2.,10.,-1.,1e300,5e-324]
 ints=[-2147483648,-1,0,1,2,2147483647]
 for k in range(300):
  doors=rng.randrange(1,10);n=rng.randrange(1,28);out=['M',str(doors),*[str(rng.choice(ints)) for _ in range(doors)],str(n)]
  for i in range(n):out += [str(rng.randrange(doors+1)),str(rng.choice(ints)),str(rng.randrange(doors+1)),str(rng.choice(ints)),bits(rng.choice(costs)),bits(rng.choice(costs)),str(rng.randrange(3)),str(rng.randrange(3)),str(rng.randrange(2)),str(rng.randrange(-1,3)),str(rng.randrange(2))]
  ops=[]
  # Direct comparison cross-product before mutations; includes null argument/receiver.
  for a in range(n+1):
   for b in range(n+1):ops.append(['Q',str(a),str(b),'0'])
  for j in range(90):
   op=rng.choice(['A','A','A','R','C','P','F','S','X','M','Q','T','K']);a=rng.randrange(n+1);b=rng.randrange(n+1);c=0
   if op in ['S','M']:a=rng.randrange(1,doors+1)
   if op=='S':b=rng.choice(ints)
   if op=='M':b=rng.randrange(doors+1);c=rng.choice(ints)
   if op=='X':a=rng.choice([-1,-1,-1,rng.randrange(1,doors+1)])
   if op=='T':b=rng.choice(ints)
   ops.append([op,str(a),str(b),str(c)])
  out.append(str(len(ops)))
  for op in ops:out+=op
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/maze/MazeListElement.java','autoroute/maze/MazeSearchElement.java','autoroute/expansion/ExpandableObject.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-maze-list-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/MazeListOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.autoroute.maze.MazeListOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
