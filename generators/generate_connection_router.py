#!/usr/bin/env python3
"""Observe actual source BatchAutorouter connection routing with application-resolved settings on public boards."""
from pathlib import Path
import argparse,hashlib,json,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-108-connection-router'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')
MAX_ATTEMPT_NANOS=50_000_000_000  # half the source 100 s first-pass maze budget (D107-3)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    build=ROOT/'build/connection-router/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    corpus=json.loads((ROOT/'tests/corpus/public/manifest.json').read_text())
    histories=[
        {'board':'fr-ecc83','passes':3,'limit':40,'preferred':True,'neckWidthUm':None},
        {'board':'fr-ecc83','passes':3,'limit':40,'preferred':False,'neckWidthUm':None},
        {'board':'fr-ecc83','passes':3,'limit':40,'preferred':True,'neckWidthUm':150.0},
        {'board':'pcbench-avr-isp','passes':3,'limit':40,'preferred':True,'neckWidthUm':None},
        {'board':'pcbench-avr-isp','passes':3,'limit':40,'preferred':True,'neckWidthUm':150.0},
        {'board':'kicad-hierarchy','passes':2,'limit':20,'preferred':True,'neckWidthUm':None},
        {'board':'fr-coldfire','passes':1,'limit':12,'preferred':True,'neckWidthUm':None},
        {'board':'pcbench-ulx4m','passes':1,'limit':8,'preferred':True,'neckWidthUm':None},
        {'board':'fr-cm5-minima','passes':1,'limit':8,'preferred':True,'neckWidthUm':None},
    ]
    for i,h in enumerate(histories):h['case']=i
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    for fixture in corpus['fixtures']:
        if fixture['id'] in {h['board'] for h in histories}:
            path=ROOT/fixture.get('dsn',fixture['prepared']);assert hashlib.sha256(path.read_bytes()).hexdigest()==fixture.get('dsn_sha256',fixture['prepared_sha256']),fixture['id']
    support=['BoardEntryOracle.java','BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','PolylineTighteningOracle.java','MazeSearchOracle.java','PipelineOracleBridge.java','ConnectionRouterOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx8g','--add-opens=java.base/java.util=ALL-UNNAMED','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ConnectionRouterOracle',str(OUT/'input.json'),str(ROOT/'tests/corpus/public'),str(build.parent/name),str(build.parent/('timings-'+name))])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=7200);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    data=json.loads(raw)
    timings=[json.loads((build.parent/('timings-'+name)).read_text())['timings'] for name in ['java.json','java-repeat.json']]
    slowest=max(t[4] for run in timings for t in run)
    (OUT/'attempt-timings.json').write_text(json.dumps({'runs':timings,'unit':'nanoseconds','columns':['case','pass','item','net','elapsed']})+'\n')
    assert slowest<MAX_ATTEMPT_NANOS,('A source attempt approached its wall-clock budget; corpus refused',slowest)
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/connection_router_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'slowest_attempt_nanos':slowest,'attempt_budget_guard_nanos':MAX_ATTEMPT_NANOS,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),'slowest attempt ms',slowest/1e6,flush=True)

if __name__=='__main__':main()
