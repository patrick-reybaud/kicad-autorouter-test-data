#!/usr/bin/env python3
"""Qualify Net/Nets registry, model queries and statistics against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/nets_java.tsv'
def cases():
 rng=random.Random(20261020)
 base=[(0,0,1,0),(0,1,2,1),(0,2,1,0),(6,0,2,0),(8,1,2,0),(1,0,0,0),(1,1,0,0),(1,2,0,0),(1,3,0,0),(4,1,1,0),(5,2,0,0),(9,3,-1,-20),(2,2,0,0),(0,2,4,0),(2,1,0,0),(3,1,-2,0),(0,0,1,0),(3,1,-1,0),(0,4,1,0),(6,0,1,0),(10,0,0,0),(7,1,0,0),(10,1,29,3),(7,2,0,0)]
 yield 'R '+str(len(base))+' '+' '.join(' '.join(map(str,x)) for x in base)
 for k in range(180):
  ops=[]
  for j in range(28):
   op=rng.randrange(11)
   if op==0:args=(rng.randrange(16),rng.randrange(-2,4),rng.randrange(2))
   elif op==1:args=(rng.randrange(4),0,0)
   elif op==2:args=(rng.randrange(3),0,0)
   elif op==3:args=(rng.randrange(2),rng.randrange(-2,2),0)
   elif op==4:args=(rng.randrange(-1,10),rng.randrange(-1,2),0)
   elif op==5:args=(rng.randrange(-1,10),rng.randrange(2),0)
   elif op==6:args=(rng.randrange(16),rng.randrange(-2,4),0)
   elif op==7:args=(rng.choice([-2147483648,-1,0,1,2,3,4,5,2147483647]),0,0)
   elif op==8:args=(rng.randrange(0,10),rng.randrange(0,10),0)
   elif op==9:args=(rng.randrange(16),rng.randrange(-2,4),rng.choice([-1,0,8,99]))
   else:args=(rng.randrange(-1,10),rng.choice([0,-3,7]),rng.randrange(16))
   ops.append((op,*args))
  yield 'R '+str(len(ops))+' '+' '.join(' '.join(map(str,x)) for x in ops)
 bits=['0','8000000000000000','3ff0000000000000','4340000000000000','c340000000000000','3fd5555555555555','7ff0000000000000','fff0000000000000','7ff8000000000000','7ff0000000000001']
 for k in range(320):
  flags=rng.choice([0,0,0,1,4,8,5]);provider=rng.choice([0,0,0,1,2,3]);net=rng.choice([-1,0,1,2,3,10000001]);items=[]
  for i in range(rng.randrange(20)):
   ns=[rng.choice([0,1,2,3,10000001]) for _ in range(rng.randrange(4))]
   items.append(' '.join(map(str,[rng.randrange(5),rng.randrange(1,40),rng.randrange(-1,4),rng.randrange(2),len(ns),*ns]))+' '+rng.choice(bits))
  yield 'Q '+' '.join(map(str,[flags,provider,net,len(items)]))+' '+' '.join(items)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['rules/Net.java','rules/Nets.java','board/facade/BoardConnectivityQueries.java','board/model/items/Item.java','board/model/items/Trace.java','board/model/items/Pin.java','board/model/items/Via.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-nets-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/NetsOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.NetsOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
