#!/usr/bin/env python3
"""Export a neutral board state and qualify actual RoutingBoard repository/undo operations."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/board_construction_java.tsv'
def cases():
 def op(c,a=0,b=0,d=0):return f'{c} {a} {b} {d}'
 def row(config,ops):return f'{config} {len(ops)} '+' '.join(ops)
 for c in range(18):
  yield row(c,[op('Q'),op('B'),op('M',2,100,-20),op('T'),op('D',3),op('T'),op('I',300,600,320),op('T'),op('U'),op('T'),op('R'),op('Q'),op('D',4),op('P'),op('U'),op('R'),op('X',2,500,500),op('W')])
  yield row(c,[op('G',1),op('G',2),op('O',1),op('Q'),op('H'),op('M',1,20,30),op('Y',1,3,7),op('F',0,50,60),op('O'),op('V',1,2,34),op('C',1),op('M',3,-10,20),op('U'),op('R'),op('C'),op('A'),op('Z')])
 rng=random.Random(2026091969)
 for c in range(48):
  ops=[op('B'),op('B',1),op('B',2)]
  for k in range(18):
   cmd=rng.choice(['I','D','M','Q','U','R','P','G','C','V','O','A','Z','T','B','X','E','J','K'])
   a=rng.randrange(1,9);b=rng.randrange(-40,41);d=rng.randrange(-40,41)
   if cmd=='I':a=rng.randrange(0,4000);b=rng.randrange(100,3000);d=rng.randrange(-400,401)
   if cmd in ['G','T','B']:a=rng.randrange(3)
   if cmd in ['C','O']:a=rng.randrange(2)
   if cmd=='V':a=rng.randrange(3);b=rng.randrange(3);d=rng.randrange(0,30)
   ops.append(op(cmd,a,b,d))
  yield row(c%18,ops)
 for config in range(3):
  for sorted_set in [0,1]:
   for mutation in [0,1]:
    yield row(config,[op('Q'),op('w',sorted_set,mutation),op('U'),op('R')])
 for capacity in [-1,0,1,2,7,17]:
  ops=[];revision=2147483640
  def jint(x):return (x+2**31)%2**32-2**31
  for step in range(70):
   revision=jint(revision+1);x=rng.randrange(-20,21);first=rng.randrange(-2,3);last=rng.randrange(first,4)
   ops.append(f'R {revision} {x} {first} {last} {rng.randrange(4)}')
   for since in [revision,jint(revision+1),jint(revision-1),jint(revision-capacity),jint(revision-capacity-1)]:
    ops.append(f'T {since} {revision} {rng.randrange(-20,21)} {rng.randrange(-2,4)} {rng.randrange(4)}')
  yield f'L1 {capacity} {len(ops)} '+' '.join(ops)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');a=p.parse_args()
 manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text()); sources=[];hashes={}
 for key,entry in manifest['source_files'].items():
  f=ROOT/'engine'/key;h=hashlib.sha256(f.read_bytes()).hexdigest();assert entry['sha256']==h;hashes[key]=h;sources.append(f)
 jar=ROOT/'engine/build/libs/freerouting-current-executable.jar';home=Path(os.environ['JAVA_HOME']);inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-board-construction-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(jar),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/BoardConstructionOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx2g','-cp',tmp+os.pathsep+str(jar),'app.freerouting.board.facade.BoardConstructionOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=180)
  if result.returncode:
   import sys;sys.stderr.write(result.stderr);result.check_returncode()
  rows=[s for s in result.stdout.splitlines() if s.startswith(('NB1 ','L1 ')) and ' | ' in s];assert len(rows)==len(inputs),(len(rows),len(inputs));text='\n'.join(rows)+'\n'
  if a.check:assert DEST.read_text()==text,'Reference output changed'
  else:DEST.write_text(text)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'fixture_sha256':hashlib.sha256(text.encode()).hexdigest(),'bytes':len(text),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
