#!/usr/bin/env python3
import argparse,hashlib,json,math,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2];java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');build=root/'build/special-areas';val=root/'validation/freerouting-port-78-special-areas';fixture=root/'upstream/kicad/qa/data/pcbnew/autorouter/special_areas_java.tsv'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def bits(x):return struct.pack('>d',x).hex()
def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args();build.mkdir(exist_ok=True);val.mkdir(exist_ok=True)
 inputs=[f'{t} {k} {c} {mode}' for t in range(3) for k in range(6) for c in range(12) for mode in range(3)]
 inputs += [f'{t} {k} {c} 3' for t in range(3) for k in [0,1,7] for c in range(4)]
 inp=build/'input.txt';inp.write_text('\n'.join(inputs)+'\n')
 x=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[];hashes={}
 for k,v in x['source_files'].items():
  f=root/'engine'/k;assert sha(f)==v['sha256'];sources.append(str(f));hashes[k]=v['sha256']
 jar=root/'engine/build/libs/freerouting-current-executable.jar'
 cmds=[[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,str(root/'tests/oracle/BoardConstructionOracle.java'),str(root/'tests/oracle/ComponentOutlineOracle.java'),str(root/'tests/oracle/ObstacleAreaOracle.java'),str(root/'tests/oracle/SpecialAreasOracle.java')],[str(java/'bin/java'),'-Xmx2g','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.SpecialAreasOracle',str(inp),str(build/'reference.tsv')]]
 with (val/'java.log').open('w') as log:
  for cmd in cmds:subprocess.run(cmd,check=True,stdout=log,stderr=subprocess.STDOUT)
 data=(build/'reference.tsv').read_bytes();assert len(data.splitlines())==len(inputs)
 if a.check:assert fixture.read_bytes()==data
 else:fixture.write_bytes(data)
 (val/'oracle.json').write_text(json.dumps({'reference_commit':x['reference_commit'],'source_sha256':hashes,'cases':len(inputs),'fields':sum(len(r.split(b' | ')[1].split()) for r in data.splitlines()),'fixture_sha256':sha(fixture),'input_sha256':sha(inp),'jar_sha256':sha(jar),'commands':cmds,'tolerances':[]},indent=2)+'\n')
if __name__=='__main__':main()
