#!/usr/bin/env python3
"""Qualify autoroute item caches, obstacle rooms, target doors and diagnostics against actual Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/autoroute_item_java.tsv'
def cases():
 rng=random.Random(20261102)
 def bits(x):return struct.pack('>d',x).hex()
 def op(o,a=0,b=0,c=0,d=0,v=0.):return [o,str(a),str(b),str(c),str(d),bits(v)]
 values=[-2147483648,-1,0,1,2,2147483647]
 for k in range(400):
  out=['A']
  for i in range(4):out += [str((k>>i)%2),str(rng.choice(values)),str(rng.randrange(1,7)),str(rng.choice(values)),str(rng.randrange(2)),*[str(rng.randrange(6)) for _ in range(6)],str(rng.randrange(-1,4))]
  ops=[]
  for i in range(1,5):
   ops += [op('I',i,i),op('G',i,i,0),op('G',i+4,i,1),op('F',i,1),op('T',i,1),op('G',i,i,0),op('T',i,5),op('G',i+4,i,1),op('N',i,i,0,i),op('N',i+4,i,0,0),op('Q',i+4),op('S',i,1),op('Q',i+4),op('C',i),op('Q',i+4),op('c',i,1),op('D',i,1,0,0,2.)]
  # All pairs, both same/different trace sections, duplicate and reverse insertion.
  for a in range(1,9):
   for b in range(1,9):ops.append(op('O',a,b))
  ops += [op('J',1,1,0),op('U',1),op('L',1),op('V'),op('A',1,0),op('P',1),op('B',1)]
  for j in range(100):
   o=rng.choice(['I','i','p','C','d','S','c','G','M','T','s','r','n','O','J','E','R','L','F','U','V','N','Q','H','Y','l','e','Z','P','B','t','K','D','b','v','f'])
   a=rng.randrange(1,5);b=rng.randrange(5);c=rng.randrange(-1,8);d=rng.randrange(9)
   if o in ['G','M','N','O','J','E','R','L','F','U','Q','H','Y','l','e','Z','B','t','b','f']:a=rng.randrange(1,9)
   if o in ['I','i','p','M','N']:b=rng.randrange(5)
   if o=='c':b=rng.randrange(3)
   if o=='G':b=rng.randrange(5)
   if o in ['O','E','J']:b=rng.randrange(9)
   if o in ['R','t']:b=rng.randrange(-1,4) if o=='t' else rng.randrange(9)
   if o=='t':c=rng.randrange(9)
   if o=='T':b=rng.randrange(-1,9)
   if o=='s':b=rng.randrange(-1,7);c=rng.randrange(6)
   if o=='n':b=rng.randrange(-1,4)
   if o=='K':a=rng.choice([-1,-1,-1,*[i*10+j for i in range(1,5) for j in range(5)]])
   if o=='v':a=rng.randrange(-1,3);b=rng.randrange(6)
   ops.append(op(o,a,b,c,d,rng.choice([-1.,-0.,0.,1.,2.,float('nan'),float('inf'),-float('inf')])))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/ItemAutorouteInfo.java','autoroute/expansion/ObstacleExpansionRoom.java','autoroute/expansion/TargetItemExpansionDoor.java','autoroute/AutorouteDiagnostic.java','board/model/items/Item.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-autoroute-item-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/AutorouteItemOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.AutorouteItemOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
