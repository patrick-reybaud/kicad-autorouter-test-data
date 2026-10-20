#!/usr/bin/env python3
"""Compare connection extraction, cache sharing, lengths and detour on acyclic contacts."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/connection_java.tsv'
def cases():
 rng=random.Random(20261105)
 def op(o,a=0,b=0,c=0,d=0,v=0.):return list(map(str,[o,a,b,c,d]))+[struct.pack('>d',v).hex()]
 for k in range(320):
  out=['C'];ids=list(range(101,109));rng.shuffle(ids)
  for i in range(1,9):
   kind=int(i not in [1,8]) if k<80 else rng.randrange(2);route=int(i not in [1,8]) if k<80 else rng.randrange(2)
   first=0 if k<80 else rng.randrange(3);last=2 if k<80 else rng.randrange(first,4)
   out += list(map(str,[kind,ids[i-1],route,first,last,0,-1]))+[struct.pack('>d',rng.choice([0.,10.,100.,100.5,1e20])).hex()]
  for i in range(2,9):out += [str(i-1 if k<80 else rng.randrange(1,i)),str(i-1 if k<80 else rng.randrange(8))]
  ops=[]
  for i in range(1,9):ops.append(op('G',(i-1)%4+1,i))
  for i in range(1,5):ops += [op('T',i),op('D',i),op('H',i,i)]
  for j in range(100):
   o=rng.choice(['G','G','T','D','C','H','N','U','A','R','X','S','L','P','F']);a=rng.randrange(1,5);b=rng.randrange(5);c=0;d=0;value=0.
   if o=='G':b=rng.randrange(9)
   if o=='H':b=rng.randrange(1,9)
   if o in ['C','A','R']:a=rng.randrange(9) if o!='C' else rng.randrange(1,9)
   if o=='S':a=rng.randrange(1,9);b=rng.randrange(2);c=rng.randrange(5)==0;d=rng.choice([-2,-1,-1,0,1,2]);c=int(c);value=rng.choice([-10.,0.,3.25,100.,1e20,float('nan'),float('inf')])
   if o=='L':a=rng.randrange(1,9);b=rng.randrange(3);c=rng.randrange(b,4)
   if o=='P':a=rng.randrange(1,9);b=rng.randrange(1,9);c=rng.randrange(8)
   if o=='F':a=rng.choice([-1,-1,*[base+i for base in [100,200,300,400,500,600,700,800] for i in range(1,9)]]);b=rng.randrange(1,4)
   ops.append(op(o,a,b,c,d,value))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/path/Connection.java','autoroute/ItemAutorouteInfo.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-connection-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ConnectionOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.ConnectionOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
