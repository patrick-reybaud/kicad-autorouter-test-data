#!/usr/bin/env python3
from pathlib import Path
import json,hashlib,subprocess
root=Path(__file__).resolve().parents[2];val=root/'validation/freerouting-port-83-board-entry';build=root/'build/first-route-input-state';build.mkdir(exist_ok=True)
java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');jar=root/'engine/build/libs/freerouting-current-executable.jar';board=root/'tests/fixtures/freerouting-faithful-C/crossing-two-nets.dsn'
m=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[]
for name,e in m['source_files'].items():
 p=root/'engine'/name;assert hashlib.sha256(p.read_bytes()).hexdigest()==e['sha256'];sources.append(str(p))
commands=[[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(root/'tests/oracle'/s) for s in ['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','FirstRouteInputState.java']]],[str(java/'bin/java'),'-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.FirstRouteInputState',str(board),str(val/'C-input-state.json')]]
with (val/'C-input-state-java.log').open('w') as log:
 for cmd in commands:subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,check=True)
(val/'C-input-state-commands.json').write_text(json.dumps({'commands':commands,'reference_commit':m['reference_commit'],'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'fixture_sha256':hashlib.sha256(board.read_bytes()).hexdigest(),'same_exporter_for_input_and_expected':True,'matching_observer_configuration':True,'prior_java_expectations_changed':False},indent=2)+'\n')
