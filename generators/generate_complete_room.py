#!/usr/bin/env python3
"""Qualify complete rooms, target collections, tree entries and source validation order."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/complete_room_java.tsv'
def cases():
 rng=random.Random(20261103)
 def bits(x):return struct.pack('>d',x).hex()
 def op(o,a=0,b=0,c=0,d=0,v=0.):return [o,str(a),str(b),str(c),str(d),bits(v)]
 for k in range(360):
  out=['C']
  for i in range(2):out += [str(rng.randrange(7)),str(rng.randrange(-1,3)),str(rng.choice([-2147483648,-1,0,1,2,2147483647]))]
  for i in range(4):out += [str(rng.randrange(7)),str(rng.randrange(-1,7)),str(rng.randrange(-1,3)),str(rng.randrange(2)),str(rng.randrange(-1,4))]
  ops=[op('H',1,0,-1,-1),op('H',2,0,2147483647,2147483647)]
  for a in [1,2]:
   for b in range(9):ops.append(op('O',a,b))
   for b in range(-1,8):ops.append(op('T',a,b,0,1))
   for d in [-1,1,2,4,8,16,32,64,128,63,255]:ops.append(op('Q',a,1,0,d))
  ops += [op('D',1,2,0,1),op('D',2,3,0,0),op('A',1,1),op('A',1,2),op('A',1,0),op('U',1),op('P',2,0,1,20),op('B',1),op('C',1),op('V',0,2),op('R',2,-1),op('I',1),op('I',2),op('Q',1,1,0,-1),op('X',1,1),op('X',2,1),op('J',1)]
  for j in range(80):
   o=rng.choice(['T','D','L','A','R','C','B','N','U','V','P','S','s','Q','q','O','H','F','E'])
   a=rng.randrange(1,3);b=rng.randrange(9);c=rng.randrange(-1,3);d=rng.randrange(3)
   if o=='T':b=rng.randrange(-1,8);d=rng.randrange(-1,4)
   if o in ['D','L','P']:a=rng.randrange(1,9)
   if o=='D':b=rng.randrange(5)
   if o=='L':b=rng.randrange(1,3)
   if o=='R':b=rng.randrange(-1,9)
   if o=='V':b=rng.randrange(-1,9)
   if o=='P':c=rng.randrange(9)
   if o=='S':b=rng.randrange(7)
   if o=='s':a=rng.randrange(1,5);b=rng.randrange(7);c=rng.randrange(-1,7)
   if o=='Q':b=rng.randrange(-1,4);d=rng.choice([-1,rng.randrange(256)])
   if o=='F':a=rng.choice([-1,-1,101,102,201,202,301,302,303,304,401,402,403,404,501,502,503,504,602,603,604,700,800,900]);b=rng.choice([1,2,3])
   ops.append(op(o,a,b,c,d,rng.choice([-1.,0.,1.,float('nan'),float('inf')])))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/expansion/CompleteFreeSpaceExpansionRoom.java','autoroute/expansion/CompleteExpansionRoom.java','autoroute/expansion/FreeSpaceExpansionRoom.java','autoroute/expansion/TargetItemExpansionDoor.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-complete-room-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.CompleteRoomOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
