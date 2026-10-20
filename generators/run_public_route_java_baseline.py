#!/usr/bin/env python3
"""Execute the unchanged reference engine twice on C's input; never replay a prepared path."""
from pathlib import Path
import hashlib,json,subprocess
root=Path(__file__).resolve().parents[2];val=root/'validation/freerouting-port-84-public-corpus';build=root/'build/public-route-java';build.mkdir(exist_ok=True)
java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');jar=root/'engine/build/libs/freerouting-current-executable.jar';board=root/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
m=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[]
for name,e in m['source_files'].items():
 p=root/'engine'/name;assert hashlib.sha256(p.read_bytes()).hexdigest()==e['sha256'];sources.append(str(p))
commands=[[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(root/'tests/oracle'/s) for s in ['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','PublicRouteJavaBaseline.java']]]]
for name in ['C-java-route-baseline.json','C-java-route-repeat.json']:commands.append([str(java/'bin/java'),'-Xmx2g','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.PublicRouteJavaBaseline',str(board),str(val/name)])
trace=commands[-1][:];trace.insert(1,'-Doracle.trace.file='+str(val/'C-java-route-trace.log'));trace[-1]=str(val/'C-java-route-traced.json');commands.append(trace)
records=[]
with (val/'C-java-route.log').open('w') as log:
 for cmd in commands:
  result=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,timeout=180);records.append({'argv':cmd,'exit_code':result.returncode});(val/'C-java-route-commands.json').write_text(json.dumps(records,indent=2)+'\n');result.check_returncode()
a=val/'C-java-route-baseline.json';b=val/'C-java-route-repeat.json';assert a.read_bytes()==b.read_bytes(),'Reference execution is not reproducible'
assert a.read_bytes()==(val/'C-java-route-traced.json').read_bytes(),'Trace logging altered reference state'
d=json.loads(a.read_text());(val/'C-java-route-summary.json').write_text(json.dumps({'reference_commit':m['reference_commit'],'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'fixture_sha256':hashlib.sha256(board.read_bytes()).hexdigest(),'result_sha256':hashlib.sha256(a.read_bytes()).hexdigest(),'repeat_identical':True,'trace_logging_state_identical':True,'native_route_executed':False,'comparison_executed':False,'entry':d['entry'],'results':d['results'],'tolerances':[]},indent=2)+'\n');print(json.dumps(d['results']))
