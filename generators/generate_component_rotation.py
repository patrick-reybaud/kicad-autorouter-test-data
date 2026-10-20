#!/usr/bin/env python3
"""Actual Component/Components rotations, including partial failures and undo callbacks."""
import argparse,hashlib,json,math,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2];java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');build=root/'build/component-rotation';val=root/'validation/freerouting-port-74-model-rotations';fixture=root/'upstream/kicad/qa/data/pcbnew/autorouter/component_rotation_java.tsv'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def bits(x):return struct.pack('>d',x).hex()
def cases():
 rng=random.Random(26091974)
 flips=json.loads((root/'validation/freerouting-port-68-A/decision-flips.json').read_text())
 angles=[struct.unpack('>d',bytes.fromhex(r['input'].split()[2]))[0]*180/math.pi for r in flips if any(k!='raw' for k in r['changes'])]
 angles += [0.,-0.,90.,180.,270.,360.,-37.,720.125,-1080.5]
 for kind in ['C','H']:
  for case in range(120):
   initial=bits(rng.choice([0.,-0.,37.,-90.,720.25,float('nan')]))
   point=f'{case%3} {rng.randrange(-40000,40001)} {rng.randrange(-40000,40001)} 3'
   row=f'C 00550031 {point} {initial} {case%2} 1 2 1 0 N' if kind=='C' else 'H'
   ops=[]
   if kind=='H':
    ops += [f'G {point} {initial} {case%2} 1',f'G 1 5000 0 1 {bits(0)} {1-case%2} 2','S','H 1 0']
   for step in range(16):
    angle=angles[(case+step)%len(angles)] if step%2==0 else rng.uniform(-720,720)
    pole=f'{0 if step%5==0 else 1} {rng.randrange(-100,101)} {rng.randrange(-100,101)}'
    ops += [f'F {step%2}',f'O {3 if step==15 and kind=="H" else 1} {bits(angle)} {pole} {case%2}']
    if step%4==0:ops+=['H 1 1']
    if kind=='H' and step%4==2:ops+=['U 1 -1 0','R 1 -1 0','S']
   row+=' '+str(len(ops))+' '+' '.join(ops)
   yield row

def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args();build.mkdir(exist_ok=True);val.mkdir(exist_ok=True)
 x=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[];hashes={}
 for k,v in x['source_files'].items():
  f=root/'engine'/k;assert sha(f)==v['sha256'];sources.append(str(f));hashes[k]=v['sha256']
 jar=root/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases());inp=build/'input.txt';inp.write_text('\n'.join(inputs)+'\n')
 compile=[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,str(root/'tests/oracle/ComponentsOracle.java')]
 run=[str(java/'bin/java'),'-Xmx2g','-cp',str(build)+':'+str(jar),'app.freerouting.board.model.structure.ComponentsOracle']
 with (val/'component-java.log').open('w') as log:
  subprocess.run(compile,check=True,stdout=log,stderr=subprocess.STDOUT)
  with inp.open() as source,(build/'reference.out').open('w') as dest:subprocess.run(run,stdin=source,stdout=dest,stderr=log,check=True,timeout=180)
 rows=[s for s in (build/'reference.out').read_text().splitlines() if s.startswith(('C ','H ')) and ' | ' in s];assert len(rows)==240
 for a,b in zip(inputs,rows):assert b.startswith(a+' | ')
 result='\n'.join(rows)+'\n'
 if args.check:assert fixture.read_text()==result
 else:fixture.write_text(result)
 (val/'component-oracle.json').write_text(json.dumps({'source_sha256':hashes,'reference_commit':x['reference_commit'],'cases':len(rows),'fields':sum(len(r.split(' | ')[1].split()) for r in rows),'fixture_sha256':sha(fixture),'input_sha256':sha(inp),'jar_sha256':sha(jar),'commands':[compile,run],'tolerances':[]},indent=2)+'\n')
if __name__=='__main__':main()
