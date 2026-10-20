#!/usr/bin/env python3
"""Observe actual changed-area and removal source algorithms with traceCosts=null."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-94-changed-area'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/changed-area/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    histories=[]
    for mode in range(14):
        for angle in [90,45]:
            for compensation in [False,True]:
                traces=[{'points':[[1500000,-1150000],[1500000,-1146000],[1506000,-1146000],[1506000,-1142000],[1512000,-1142000]],'layer':0,'width':100,'net':2,'fixed':0},
                        {'points':[[1500000,-1150000],[1500000,-1146000],[1506000,-1146000],[1506000,-1142000],[1512000,-1142000]],'layer':1,'width':100,'net':2,'fixed':0}]
                if mode in [3,11]:traces.append({'points':[[1506000,-1170000],[1506000,-1130000]],'layer':0,'width':100,'net':3,'fixed':3})
                if angle==45 and mode in [1,5,7]:
                    traces[0]['points']=[[1500000,-1150000],[1504000,-1146000],[1508000,-1146000],[1512000,-1150000]]
                    traces.append({'points':[[1500000,-1150000],[1506000,-1150000]],'layer':0,'width':100,'net':2,'fixed':0})
                histories.append({'case':len(histories),'mode':mode,'angle':angle,'compensation':compensation,'mark':mode!=0,'traces':traces,'nets':[2] if mode==2 else [7] if mode==3 else [],'clip':{4:'empty',5:'duplicate-empty',6:'box'}.get(mode,'null'),'keep':([1506000,-1150000] if angle==90 else [1504000,-1146000]) if mode==12 else [1506000,-1146000] if mode==7 else None,'stopAfter':0 if mode in [8,13] else 1 if mode==9 else 100000,'tidyWidth':{0:-1,4:1,10:0,11:2147483647,13:2147483647}.get(mode,5000),'sorted':compensation,'remove':[] if mode==13 else [0,len(traces)-1]})
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    support=['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','ChangedAreaOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx4g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ChangedAreaOracle',str(OUT/'input.json'),str(board),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=600);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/changed_area_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'public_queries_are_synthetic':True,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
