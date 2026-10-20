#!/usr/bin/env python3
"""Load/export the candidate C board with the pinned JVM and existing Java reader; no routing."""
from pathlib import Path
import json,hashlib,subprocess
root=Path(__file__).resolve().parents[2];build=root/'build/first-route-input';build.mkdir(exist_ok=True)
val=root/'validation/freerouting-port-83-board-entry';java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');jar=root/'engine/build/libs/freerouting-current-executable.jar'
manifest=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[]
for name,entry in manifest['source_files'].items():
 p=root/'engine'/name;assert hashlib.sha256(p.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(p))
board=root/'tests/fixtures/freerouting-faithful-C/crossing-two-nets.dsn';out=val/'C-java-input-candidate.json'
commands=[[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(root/'tests/oracle'/s) for s in ['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','PrepareFirstRouteInput.java']]],[str(java/'bin/java'),'-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.PrepareFirstRouteInput',str(board),str(out)]]
with (val/'C-java-input.log').open('w') as f:
 for cmd in commands:subprocess.run(cmd,stdout=f,stderr=subprocess.STDOUT,check=True)
(val/'C-java-input-provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'dsn_sha256':hashlib.sha256(board.read_bytes()).hexdigest(),'export_sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'commands':commands,'native_replay_executed':False,'route_executed':False,'tolerances':[]},indent=2)+'\n')
