#!/usr/bin/env python3
"""Qualify drill-page caches, holes, pin centers, arrays and source diagnostics."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/drill_page_java.tsv'
def cases():
 rng=random.Random(20261106)
 def op(o,a=0,b=0,c=0,d=0,v=0.):return list(map(str,[o,a,b,c,d]))+[struct.pack('>d',v).hex()]
 for k in range(240):
  out=['P',str(rng.randrange(1,4)),str(rng.choice([1,5]))]
  for i in range(1,5):out += list(map(str,[int(i>1),rng.choice([2,2,3,4]),rng.randrange(1,6),rng.randrange(2),rng.randrange(2)]))
  order=list(range(1,5));rng.shuffle(order);out+=list(map(str,order))
  ops=[op('P',1,1),op('S',30,30,0,-2)]
  for mode in [0,1,2,3,4,-1]:
   ops += [op('G',mode,mode,1,1),op('G',mode,0,1,0)]
  ops += [op('I'),op('G',1,0,1,0),op('A',1,rng.choice([30,60,200])),op('Q',1),op('q',0),op('G',1,0,1,1),op('M',0,37),op('Z'),op('V',2)]
  for j in range(60):
   o=rng.choice(['P','G','G','I','R','M','m','L','S','s','B','A','Q','q','V','Z','E','F']);a=rng.randrange(3);b=rng.randrange(3);c=rng.randrange(3);d=rng.randrange(3);v=rng.choice([-1.,0.,1.,float('nan'),float('inf')])
   if o=='P':a=rng.choice([0,1,2,3,5]);b=rng.randrange(3)
   if o=='G':a=rng.randrange(-1,4);b=rng.randrange(-1,5)
   if o=='M':a=rng.randrange(-1,5)
   if o=='m':a=rng.randrange(-1,6);b=rng.randrange(-1,4)
   if o=='S':a=rng.randrange(64);b=rng.choice([-1,rng.randrange(32)]);c=rng.randrange(-1,3);d=rng.choice([-2,-1,0,1,2,8])
   if o=='s':a=rng.randrange(1,5);b=rng.choice([0,1,2,3,4,5,6]);c=rng.randrange(6);d=rng.randrange(4)
   if o=='B':a=rng.randrange(-1,4)
   if o=='A':a=rng.randrange(2);b=rng.choice([-100,-1,30,60,200])
   if o in ['Q','V']:a=rng.randrange(8)
   if o=='q':a=rng.randrange(-1,8)
   if o=='F':a=rng.choice([-1,-1,100,301,302,303,304,401,402,403,404,502,503,504,602,603,604,700,710,720,800,810,900,950]);b=rng.randrange(1,4)
   ops.append(op(o,a,b,c,d,v))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/drill/DrillPage.java','autoroute/drill/DrillPageArray.java','autoroute/drill/ExpansionDrill.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-drill-page-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/DrillPageOracle.java'),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.DrillPageOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
