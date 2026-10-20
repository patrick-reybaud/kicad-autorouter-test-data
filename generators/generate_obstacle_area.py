#!/usr/bin/env python3
import argparse,hashlib,json,math,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2];java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');build=root/'build/obstacle-area';val=root/'validation/freerouting-port-76-obstacle-area';fixture=root/'upstream/kicad/qa/data/pcbnew/autorouter/obstacle_area_java.tsv'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def bits(x):return struct.pack('>d',x).hex()
def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');options=p.parse_args();build.mkdir(exist_ok=True);val.mkdir(exist_ok=True)
 flips=json.loads((root/'validation/freerouting-port-68-A/decision-flips.json').read_text());angles=[struct.unpack('>d',bytes.fromhex(r['input'].split()[2]))[0]*180/math.pi for r in flips if any(k!='raw' for k in r['changes'])];angles += [0.,-0.,90.,180.,360.,37.,1e-200]
 rng=random.Random(260919760);angles += [rng.uniform(-720,720) for _ in range(15)]
 inputs=[]
 for k in range(6):
  for c in range(12):
   for a in [0.,37.,90.]:
    unit=(k+c)%4;res=[1,1000,100,1][unit];host=(k+c)%4
    inputs.append(f'{k} {c} {5 if a==0 else 0} {bits(a)} {unit} {res} {host}')
 for i,a in enumerate(angles):
  for k in range(4):
   for face in range(4):
    c=3*face+i%3;inputs.append(f'{k} {c} {6 if i%2 else 0} {bits(a)} 0 1 3')
 for k,m in [(6,4),(7,4),(8,4),(9,4),(10,4),(11,4),(12,4),(0,1),(0,2),(1,3),(0,3),(3,3)]:
  for c in [0,3,6,9]:
   for a in [0.,37.,90.]:inputs.append(f'{k} {c} {m} {bits(a)} 0 1 0')
 for res in [0,-1,100,2147483647]:
  for unit in range(4):inputs.append(f'0 2 0 {bits(0.)} {unit} {res} 3')
 for c in [0,3,6,9]:inputs.append(f'0 {c} 4 {bits(0.)} -1 1 3')
 for f in range(-1,4):
  for t in range(-1,4):
   for x in [0.,-0.,1.,-1.,25.4,2147483647.,2147483648.,.5,5e-324,-5e-324,1e-300,-1e-300,1e300,-1e300,float('inf'),float('-inf'),float('nan')]:inputs.append(f'U {f} {t} {bits(x)}')
 for k,m in [(6,4),(7,4),(8,4),(9,4),(10,4),(11,4),(12,4),(0,1),(0,2),(1,3),(0,3),(3,3)]:
  for c in [1,2,4,5,7,8,10,11]:
   for angle in [0.,37.,90.]:inputs.append(f'{k} {c} {m} {bits(angle)} 0 1 0')
 for k in range(6):
  for c in range(12):inputs.append(f'{k} {c} 0 {bits(37.)} 0 1 3 1')
 inp=build/'input.txt'  ;inp.write_text('\n'.join(inputs)+'\n')
 x=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[];hashes={}
 for k,v in x['source_files'].items():
  f=root/'engine'/k;assert sha(f)==v['sha256'];sources.append(str(f));hashes[k]=v['sha256']
 jar=root/'engine/build/libs/freerouting-current-executable.jar'
 cmds=[[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,str(root/'tests/oracle/BoardConstructionOracle.java'),str(root/'tests/oracle/ComponentOutlineOracle.java'),str(root/'tests/oracle/ObstacleAreaOracle.java')],[str(java/'bin/java'),'-Xmx2g','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ObstacleAreaOracle',str(inp),str(build/'reference.tsv')]]
 with (val/'java.log').open('w') as log:
  for cmd in cmds:subprocess.run(cmd,check=True,stdout=log,stderr=subprocess.STDOUT)
 data=(build/'reference.tsv').read_bytes();assert len(data.splitlines())==len(inputs)
 parts=[];current=bytearray()
 for line in data.splitlines(keepends=True):
  if current and len(current)+len(line)>40*1024*1024:parts.append(bytes(current));current.clear()
  current.extend(line)
 if current:parts.append(bytes(current))
 assert len(parts)==4
 for i,part in enumerate(parts):
  path=Path(str(fixture)+f'.part{i}')
  if options.check:assert path.read_bytes()==part
  else:path.write_bytes(part)
 assert b''.join(parts)==data
 (val/'oracle.json').write_text(json.dumps({'reference_commit':x['reference_commit'],'source_sha256':hashes,'cases':len(inputs),'fields':sum(len(r.split(b' | ')[1].split()) for r in data.splitlines()),'fixture_sha256':hashlib.sha256(data).hexdigest(),'fixture_parts':[{'name':fixture.name+f'.part{i}','sha256':hashlib.sha256(part).hexdigest(),'bytes':len(part)} for i,part in enumerate(parts)],'input_sha256':sha(inp),'jar_sha256':sha(jar),'commands':cmds,'tolerances':[]},indent=2)+'\n')
if __name__=='__main__':main()
