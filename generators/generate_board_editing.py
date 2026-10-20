#!/usr/bin/env python3
"""Qualify actual RoutingBoard trace insertion/split/combine/normalize, with no algorithm stubs."""
import argparse,hashlib,json,os,subprocess,tempfile,random
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/board_editing_java.tsv'
def cases():
 def op(k,a=0,b=0,c=0):return f'{k} {a} {b} {c}'
 def row(config,ops):return f'{1000+config} {len(ops)} '+' '.join(ops)
 for angle in range(3):
  for shape in range(12):
   for fixed in range(4):
    yield row(angle,[op('Q'),op('h'),op('j',500,600,0),op('N',shape,1,fixed),op('g',-1),op('U'),op('R')])
 for config in range(9):
  for shape in [0,1,2,3,4,5,6,10]:
   yield row(config,[op('G',1),op('G',2),op('C',1),op('Q'),op('N',shape,1),op('s',2,650,600),op('c',2),op('n',2),op('g',1),op('U'),op('R'),op('t',800,600,1),op('r',2)])
 for config in range(6):
  yield row(config,[op('h'),op('j',1001,2001,0),op('h'),op('j',-3,5,2),op('a'),op('e',1),op('Q'),op('N',4,2),op('n',4,1),op('s',4,1200,600),op('U'),op('R')])
 rng=random.Random(2026091970)
 for config in range(30):
  ops=[op('Q')]
  for _ in range(16):
   k=rng.choice(['N','s','c','n','g','Q','U','R','h','j','e','t'])
   a=rng.randrange(2,12);b=rng.choice([0,1,500,600,650,800,1000]);c=rng.randrange(3)
   if k=='N':a=rng.randrange(12);b=rng.randrange(1,3);c=rng.randrange(4)
   if k=='g':a=rng.choice([-1,1,2])
   if k=='e':a=rng.randrange(3)
   if k=='s':b=rng.choice([500,650,800,1200]);c=rng.choice([600,1000])
   if k=='t':a=rng.choice([500,650,800]);b=rng.choice([600,1000]);c=rng.choice([1,2])
   ops.append(op(k,a,b,c))
  yield row(config%9,ops)
 for config in range(3):
  for net in [49,94]:
   yield row(config,[op('Q'),op('N',0,net),op('N',4,net),op('N',6,net),op('g',net),op('U'),op('R'),op('h'),op('a')])
 for config in range(3):
  for failure in range(1,6):
   yield row(config,[op('Q'),op('f',failure,1),op('N',0,1),op('U'),op('R'),op('Z'),op('f',failure,1),op('N',4,1),op('g',1)])
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');args=p.parse_args();manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[];hashes={}
 for key,entry in manifest['source_files'].items():
  f=ROOT/'engine'/key;h=hashlib.sha256(f.read_bytes()).hexdigest();assert h==entry['sha256'];hashes[key]=h;sources.append(f)
 home=Path(os.environ['JAVA_HOME']);jar=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-board-editing-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(jar),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/BoardConstructionOracle.java')],check=True)
  try:r=subprocess.run([str(home/'bin/java'),'-Xmx2g','-cp',tmp+os.pathsep+str(jar),'app.freerouting.board.facade.BoardConstructionOracle'],input='\n'.join(inputs)+'\n',text=True,capture_output=True,timeout=180)
  except subprocess.TimeoutExpired as e:
   output=e.stdout or b'';output=output.decode() if isinstance(output,bytes) else output;rows=[s for s in output.splitlines() if s.startswith('NB2 ')];(ROOT/'validation/freerouting-port-70-B2/java-timeout.json').write_text(json.dumps({'completed':len(rows),'first_uncompleted_input':inputs[len(rows)],'timeout_seconds':180},indent=2)+'\n');raise
  if r.returncode:
   import sys;sys.stderr.write(r.stderr);r.check_returncode()
  rows=[s for s in r.stdout.splitlines() if s.startswith('NB2 ')];assert len(rows)==len(inputs),(len(rows),len(inputs));text='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==text,'Java expectation changed'
  else:DEST.write_text(text)
 print(json.dumps({'java_reference_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'cases':len(rows),'fields':sum(len(s.split(' | ')[1].split()) for s in rows),'fixture_sha256':hashlib.sha256(text.encode()).hexdigest(),'bytes':len(text)},indent=2))
if __name__=='__main__':main()
