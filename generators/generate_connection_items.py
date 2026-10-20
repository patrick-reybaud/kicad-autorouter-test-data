#!/usr/bin/env python3
"""Qualify Connection-chain collection, fanout-via detection and trace ratsnest corners against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/connection_items_java.tsv'
def cases():
 rng=random.Random(20261023)
 def sp(kind,ids):return ' '.join(map(str,[kind,len(ids),*ids]))
 def node(vals,bits,specs):return ' '.join(map(str,vals))+' '+bits+' '+' '.join(sp(*s) for s in specs)
 def row(budget,nodes,points,ignore):return 'C '+str(budget)+' '+str(len(nodes))+' '+' '.join(nodes)+' '+' '.join(str(p) for r in points for p in r)+' '+sp(*ignore)
 bits=['0000000000000000','3ff0000000000000','4078ffffffffffff','4079000000000000','4079000000000001','7ff0000000000000','fff0000000000000','7ff8000000000000']
 for length in bits:
  for poly in range(2):
   for pinLayers in range(2):
    nodes=[node([2,1,0,1,0,1,0,0,0,1,1],'0',[(0,[2]),(0,[]),(0,[])]),node([4,2,0,0,0,1,0,poly,0,1,1],length,[(0,[1,3,4]),(0,[1]),(0,[3])]),node([1,3,0,pinLayers,0,1,0,0,0,1,1],'0',[(0,[2]),(0,[]),(0,[])]),node([4,4,0,0,1,1,0,poly,0,1,1],'3ff0000000000000',[(0,[2]),(0,[]),(0,[])])]
    yield row(80,nodes,[[-1,-1,0,-1,-1],[-1,0,-1,1,1],[-1,-1,1,-1,-1],[-1,-1,1,-1,-1]],(0,[2]))
 # An all-routable ring deliberately reaches the fixture's callback budget.
 for kind in range(2):
  for budget in [8,20,80]:
   nodes=[node([0,i,0,0,0,1,1,0,0,1,1],'0',[(kind,[i%4+1,(i+2)%4+1]),(0,[]),(0,[])]) for i in range(1,5)]
   pts=[[-1]*5 for _ in range(4)]
   for i in range(1,5):pts[i-1][i%4+1]=i;pts[i%4][i]=i
   yield row(budget,nodes,pts,(0,[]))
 for k in range(420):
  n=rng.randrange(1,9);nodes=[]
  for i in range(1,n+1):
   specs=[(rng.choices([0,1,2],weights=[7,2,1])[0],[rng.randrange(n+1) for _ in range(rng.randrange(5))]) for _ in range(3)]
   nodes.append(node([rng.randrange(5),rng.randrange(1,15),rng.randrange(3),rng.randrange(3),rng.randrange(-1,4),rng.choice([-1,0,1,2,5]),rng.randrange(2),rng.randrange(2),rng.randrange(-1,4),rng.randrange(-1,4),rng.choice([-1,0,1,1,1])],rng.choice(bits),specs))
  yield row(rng.choice([8,20,80]),nodes,[[rng.randrange(-1,4) for j in range(n+1)] for i in range(n)],(rng.randrange(3),[rng.randrange(n+1) for _ in range(rng.randrange(9))]))
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/items/Trace.java','board/model/items/Pin.java','board/model/items/Via.java','board/trace/PolylineTrace.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-connection-items-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ConnectionItemsOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.ConnectionItemsOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
