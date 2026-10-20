#!/usr/bin/env python3
"""Observe actual source shove insertions and undo/redo on the public board."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-101-connection-inserter'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/connection-inserter/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    histories=[]
    for mode in ['trace','via','neck','try','micro','connect']:
        for pin in ([0,3] if mode in ['trace','via','micro'] else [3] if mode in ['neck','try'] else [0]):
            for angle in [45,90]:
                for compensation in [False,True]:
                    for width in [1000,12000]:
                        histories.append({'case':len(histories),'mode':mode,'pin':pin,'angle':angle,'compensation':compensation,'width':width,'neckdown':True,'fanout':True,'obstacle':False,'fixed':False,'points':[[0,0],[10000,0]] if pin else [[-5000,0],[5000,0]],'startPin':True,'endPin':False,'fromLayer':0,'toLayer':1})
    for mode in ['trace','via','connect','micro','neck','try']:
        for option in ['obstacle','reverse','point','empty','noNeck','noFanout']:
            h={'case':len(histories),'mode':mode,'pin':3 if mode in ['neck','try'] else 0,'angle':45,'compensation':False,'width':12000,'neckdown':True,'fanout':True,'obstacle':option=='obstacle','fixed':True,'points':[[10000,0],[0,0]] if option=='reverse' else [[0,0]] if option=='point' else [] if option=='empty' else [[0,0],[10000,0]],'startPin':option!='reverse','endPin':option=='reverse','fromLayer':1,'toLayer':0,'option':option}
            if option=='noNeck':h['neckdown']=False
            if option=='noFanout':h['fanout']=False
            histories.append(h)
    # Append controlled near-pin obstacles to exercise actual neck-width insertion.
    for mode in ['neck','try']:
        for angle in [45,90]:
            for width in [12000,30000]:
                for distance in [10000,30000]:
                    for diagonal in [False,True]:
                        histories.append({'case':len(histories),'mode':mode,'pin':3,'angle':angle,'compensation':False,'width':width,'neckdown':True,'fanout':False,'obstacle':False,'neckObstacle':True,'fixed':True,'points':[[-distance,-distance if diagonal else 0],[0,0]],'startPin':False,'endPin':True,'fromLayer':0,'toLayer':1})
    # The pin has 10,000-unit halfwidth and 4,000-unit clearance. Move the
    # added obstacle beyond the pin clearance to allow a real narrow insertion.
    for previous in histories[108:140]:
        histories.append({**previous,'case':len(histories),'neckOffset':16000})
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    support=['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','PolylineTighteningOracle.java','ConnectionInserterOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx4g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ConnectionInserterOracle',str(OUT/'input.json'),str(board),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=600);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/connection_inserter_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'public_queries_are_synthetic':True,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
