#!/usr/bin/env python3
"""Pinned Java state exporter; neither source changes nor expected-output rewrites."""
import argparse,hashlib,json,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')
build=root/'build/neutral-board-model';val=root/'validation/freerouting-port-80-neutral-model'
fixture=root/'upstream/kicad/qa/data/pcbnew/autorouter/neutral_model_java.tsv'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args();build.mkdir(exist_ok=True);val.mkdir(exist_ok=True)
 manifest=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[]
 for s,v in manifest['source_files'].items():assert sha(root/'engine'/s)==v['sha256'];sources.append(str(root/'engine'/s))
 inp=build/'input.txt';inp.write_text(''.join(f'{i}\n' for i in range(60)));jar=root/'engine/build/libs/freerouting-current-executable.jar';out=build/'reference.tsv'
 commands=[[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(root/'tests/oracle'/s) for s in ['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java']]],[str(java/'bin/java'),'-Xmx2g','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.NeutralBoardModelOracle',str(inp),str(out)]]
 with (val/'java.log').open('w') as f:
  for cmd in commands:subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT,check=True)
 rows=out.read_text().splitlines();assert len(rows)==60
 if a.check:assert fixture.read_bytes()==out.read_bytes()
 else:fixture.write_bytes(out.read_bytes())
 (val/'oracle.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_sha256':{s:v['sha256'] for s,v in manifest['source_files'].items()},'jar_sha256':sha(jar),'input_sha256':sha(inp),'fixture_sha256':sha(fixture),'cases':len(rows),'observations':sum(len(json.loads(r.split(' | ')[1])['observations']) for r in rows),'commands':commands,'tolerances':[]},indent=2)+'\n')
 print('Neutral model Java histories:',len(rows))
if __name__=='__main__':main()
