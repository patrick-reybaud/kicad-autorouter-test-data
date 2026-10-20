#!/usr/bin/env python3
"""Compare expansion drills, via cache histories and mutable TreeSet iteration."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/expansion_drill_java.tsv'
def cases():
 rng=random.Random(20261104)
 def op(o,a=0,b=0,c=0,d=0,v=0.):return [o,str(a),str(b),str(c),str(d),struct.pack('>d',float(v)).hex()]
 for k in range(180):
  out=['D'];order=list(range(8));rng.shuffle(order)
  for i in range(8):out += [str(rng.randrange(-2,5)),str(rng.choice([-2147483648,-1,0,1,5,2147483647]))]
  out += list(map(str,order));ops=[op('D',1,1,1,0,3),op('D',2,0,0,1,0),op('D',3,3,3,-1,4)]
  for mode in range(-1,6):
   for mask in [-1,0,256,511,rng.randrange(512)]:ops.append(op('Q',1,mask,mode,0))
  ops += [op('M',1,0,3,19),op('U',1),op('D',1,2,2,2147483647,-2147483648),op('Q',1,511,0),op('R',3),op('Q',2,0,0),op('D',2,1,1,3,1)]
  for j in range(60):
   o=rng.choice(['D','Q','M','R','A','U','V','H','F','E']);a=rng.randrange(1,4);b=rng.randrange(-1,5);c=rng.randrange(4);d=rng.randrange(-2,4);v=rng.choice([-1.,0.,1.,float('nan'),float('inf')])
   if o=='D':b=rng.randrange(4);c=rng.randrange(4);v=d+rng.randrange(-2,5)
   if o=='Q':b=rng.choice([-1,rng.randrange(512)]);c=rng.randrange(-1,6);d=rng.randrange(3)
   if o=='A':c=rng.randrange(-1,8)
   if o=='V':a=rng.randrange(-1,5);b=rng.randrange(-1,8)
   if o=='F':a=rng.choice([-1,-1,100,101,102,103,104,105,106,107,700,800,900]);b=rng.randrange(1,4)
   ops.append(op(o,a,b,c,d,v))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
 for k in range(200):
  out=['V',str(rng.randrange(4)),str(rng.randrange(-1,3)),str(rng.randrange(1,5)),str(rng.randrange(5)),str(rng.randrange(4))]
  def vop(o,a=0,b=0,c=0,d=0):return list(map(str,[o,a,b,c,d]))
  ops=[vop('G',1),vop('G',2),vop('U',1),vop('P',1,0,2,37),vop('C'),vop('G',3),vop('D'),vop('G',4)]
  for j in range(80):
   o=rng.choice(['G','G','C','D','S','s','P','R','U','F']);a=rng.randrange(1,5);b=rng.randrange(-1,5);c=rng.randrange(5);d=rng.randrange(-1,5)
   if o=='S':a=rng.randrange(4);b=rng.randrange(-1,4);c=rng.randrange(-1,5)
   if o=='s':a=rng.randrange(4)
   if o=='F':a=rng.choice([-1,-1,100,200,300,400,500,501,600]);b=rng.randrange(1,4)
   ops.append(vop(o,a,b,c,d))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
 for k in range(200):
  out=['I',*map(str,[rng.randrange(50) for _ in range(16)])];ops=[];order=list(range(16));rng.shuffle(order)
  for i in order:ops.append(['A',str(i),'0'])
  ops += [['I','0','0'],['R','0','0']]
  for j in range(90):
   o=rng.choice(['N','N','R','H','M','I']);ops.append([o,str(rng.randrange(16)),str(rng.randrange(50))])
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/drill/ExpansionDrill.java','board/model/items/Via.java','autoroute/ItemAutorouteInfo.java','autoroute/expansion/CompleteFreeSpaceExpansionRoom.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-expansion-drill-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ExpansionDrillOracle.java'),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.ExpansionDrillOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
