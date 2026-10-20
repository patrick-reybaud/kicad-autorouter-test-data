#!/usr/bin/env python3
"""Observe actual source ViaOptimizer and non-null-cost changed-area optimization on routed public boards."""
from pathlib import Path
import argparse,hashlib,json,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-107-via-optimizer'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    build=ROOT/'build/via-optimizer/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    histories=[]
    for kind in range(6):
        for accuracy in [500,100]:
            for depth in [10,1,0]:
                histories.append({'case':len(histories),'board':'fr-ecc83','mode':'direct','attempts':8,'costsKind':kind,'accuracy':accuracy,'depth':depth})
    for kind in [1,0,5,3]:
        histories.append({'case':len(histories),'board':'fr-coldfire','mode':'direct','attempts':8,'costsKind':kind,'accuracy':500,'depth':10})
    for board in ['fr-ecc83','fr-coldfire']:
        for kind in [1,5,3,0]:
            for accuracy in [500,100]:
                if board=='fr-coldfire' and (accuracy!=500 or kind==0):continue
                histories.append({'case':len(histories),'board':board,'mode':'changedArea','attempts':8,'costsKind':kind,'accuracy':accuracy,'depth':10})
    for variant in range(13):
        histories.append({'case':len(histories),'board':'fr-ecc83','mode':'synthetic','variant':variant})
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    assert hashlib.sha256((ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn').read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    assert hashlib.sha256((ROOT/'tests/corpus/public/fr-coldfire/prepared/fr-coldfire.dsn').read_bytes()).hexdigest()=='10ff1dc405d27bb279f031facfaddf97b0f3407dd9b37c9c7e8df5c5f675cfe9'
    support=['BoardEntryOracle.java','BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','PolylineTighteningOracle.java','MazeSearchOracle.java','ViaOptimizerOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx6g','--add-opens=java.base/java.util=ALL-UNNAMED','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ViaOptimizerOracle',str(OUT/'input.json'),str(ROOT/'tests/corpus/public'),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=3600);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/via_optimizer_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
