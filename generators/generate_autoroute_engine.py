#!/usr/bin/env python3
"""Compare the actual personal engine room lifecycle, TimeLimit and ArrayList iterators."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/autoroute_engine_java.tsv'
def bits(x):return struct.pack('>d',x).hex()
def cases():
 rng=random.Random(20261115)
 def op(o,a=0,b=0,c=0,d=0,v=1.0):return list(map(str,[o,a,b,c,d,bits(v)]))
 for k in range(200):
  out=['E',str(k%2),str(k%3==0 and 1 or 0),'1',str(rng.choice([1,2147483647,-2147483648])),'2','2',str(rng.choice([3,4,5,6])),'3']
  ops=[op('C',1,1),op('C',2,1),op('C',3,1),op('D',1,2,1,3),op('A',4,0,0,4),op('D',1,4,2,3),op('N',1,1,1),op('T',1,1),op('P',1),op('M',k%3+1),op('g',2147483647),op('G'),op('L',30)]
  for j in range(35):
   o=rng.choice(['A','D','R','X','Z','N','S','Q','G','H','K','B','M','F','V','U','T','L','J','P','E'])
   a=rng.randrange(1,4);b=0;c=0;d=0;v=rng.choice([0.,-1.,1.,float('nan')])
   if o=='A':a=rng.randrange(4,9);b=rng.randrange(7);c=rng.randrange(2);d=rng.randrange(7)
   if o=='D':a=rng.randrange(1,9);b=rng.randrange(1,9);c=rng.randrange(3);d=rng.randrange(1,4)
   if o in ['R','Z']:a=rng.randrange(9)
   if o=='N':a=rng.choice([-1,1,2,3]);b=rng.randrange(2);c=rng.randrange(2)
   if o=='S':a=rng.randrange(2);b=rng.randrange(2)
   if o=='F':a=rng.choice([-1,-1,101,102,201,202,301,302,401,402,501,502,701,702,801,802,851,852,900,901,902,950,951,952]);b=rng.randrange(1,3)
   if o=='T':b=rng.randrange(1,5)
   if o=='L':a=rng.choice([-1,0,2,6,14,30])
   if o in ['J','P','E']:a=rng.randrange(2)
   if o=='U':a=rng.randrange(7)
   ops.append(op(o,a,b,c,d,v))
  out+=[str(len(ops))];out+=[x for row in ops for x in row];yield ' '.join(out)
 # Explicit failures before/after recursive removal, net change and clear.
 for k in range(40):
  fail=[101,201,302,402,900,901,902,950,951,952][k%10]
  ops=[op('C',1,1),op('C',2,1),op('D',1,2,1,3),op('A',4,0,0,4),op('A',5,0,0,4),op('D',1,4,2,3),op('D',4,5,1,3),op('N',1,1,1),op('M',1),op('T',1,1),op('F',fail,k//10%2+1),op('X',1),op('N',2,1,1),op('E',1),op('K'),op('Q'),op('F',-1),op('B'),op('P',1),op('B')]
  out=['E','1',str(k%2),'1','1','2','2','3','3',str(len(ops))]+[x for row in ops for x in row];yield ' '.join(out)
 factors=[-float('inf'),-1.,-0.,0.,0.5,1.,1.5,2.,1e100,float('inf'),float('nan'),5e-324]
 for k in range(400):
  millis=rng.choice([-2147483648,-100,-1,0,1,100,2147483647]) if k<100 else rng.randrange(-2147483648,2147483648)
  fs=[rng.choice(factors) for _ in range(20)];yield ' '.join(['T',str(millis),str(len(fs)),*map(bits,fs)])
 for k in range(160):
  ops=[['A','1'],['A','2'],['I','0'],['N','0']]
  ops += [[rng.choice(['A','S','R','C','H','N','I']),str(rng.randrange(6))] for _ in range(30)]
  yield ' '.join(['I',str(len(ops)),*[x for row in ops for x in row]])
 # End-to-end repeated-removal histories preserve Java detached parent reachability.
 for k in range(40):
  ops=[op('C',1,1),op('C',2,1),op('C',3,1),op('D',1,2,1,3),op('X',1),op('X',1),op('V'),op('X',3),op('V'),op('F',202,k%2+1),op('V'),op('F',-1),op('K')]
  yield ' '.join(['E','1','0','1','1','2','2',str([3,4,5,6][k%4]),'3',str(len(ops)),*[x for row in ops for x in row]])
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/maze/AutorouteEngine.java','datastructures/TimeLimit.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-autoroute-engine-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/AutorouteEngineOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx512m','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.AutorouteEngineOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=180)
  if result.returncode:
   import sys
   sys.stderr.write(result.stderr);result.check_returncode()
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for line,row in zip(inputs,rows):assert row.startswith(line+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
