#!/usr/bin/env python3
"""Actual Java Pin on RoutingBoard; no expected algorithms rewritten in the harness."""
import argparse,hashlib,json,math,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2];java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');build=root/'build/pin-model';val=root/'validation/freerouting-port-73-pin';fixture=root/'upstream/kicad/qa/data/pcbnew/autorouter/pin_model_java.tsv'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def bits(x):return struct.pack('>d',x).hex()
def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args();build.mkdir(exist_ok=True);val.mkdir(exist_ok=True)
 rng=random.Random(26091973);inputs=[]
 for config in range(64):
  for component,pin in [(0,0),(90,45),(37,23),(rng.uniform(-720,720),rng.uniform(-360,360))]:inputs.append(f'{config} {bits(component)} {bits(pin)}')
 flips=json.loads((root/'validation/freerouting-port-68-A/decision-flips.json').read_text())
 for i,row in enumerate(x for x in flips if any(k!='raw' for k in x['changes'])):
  angle=struct.unpack('>d',bytes.fromhex(row['input'].split()[2]))[0]*180/math.pi
  for config in [0,1,8,12,17,33,63]:inputs.append(f'{config} {bits(angle)} {bits(0.)}')
 inputs += [f'{100+i} {bits(0.)} {bits(0.)}' for i in range(12)]
 inp=build/'input.txt';inp.write_text('\n'.join(inputs)+'\n')
 x=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[];hashes={}
 for k,v in x['source_files'].items():
  f=root/'engine'/k;assert sha(f)==v['sha256'];sources.append(str(f));hashes[k]=v['sha256']
 jar=root/'engine/build/libs/freerouting-current-executable.jar'
 cmds=[[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,str(root/'tests/oracle/BoardConstructionOracle.java'),str(root/'tests/oracle/PinModelOracle.java')],[str(java/'bin/java'),'-Xmx2g','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.PinModelOracle',str(inp),str(build/'reference.tsv')]]
 with (val/'java.log').open('w') as log:
  for cmd in cmds:subprocess.run(cmd,check=True,stdout=log,stderr=subprocess.STDOUT)
 result=build/'reference.tsv';assert len(result.read_text().splitlines())==len(inputs)
 if a.check:assert fixture.read_bytes()==result.read_bytes()
 else:fixture.write_bytes(result.read_bytes())
 (val/'oracle.json').write_text(json.dumps({'reference_commit':x['reference_commit'],'source_sha256':hashes,'jar_sha256':sha(jar),'input_sha256':sha(inp),'fixture_sha256':sha(fixture),'cases':len(inputs),'fields':sum(len(r.split(' | ')[1].split()) for r in result.read_text().splitlines()),'commands':cmds,'tolerances':[]},indent=2)+'\n')
if __name__=='__main__':main()
