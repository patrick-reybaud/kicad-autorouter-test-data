#!/usr/bin/env python3
"""Observe actual source shove insertions and undo/redo on the public board."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-100-forced-trace'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/forced-trace/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    histories=[]
    for variant in [0,1,2,5,16,17,18,21]:
        for angle in [45,90]:
            for compensation in [False,True]:
                for mode in ['polyline','segment','via']:
                    histories.append({'case':len(histories),'variant':variant,'angle':angle,'compensation':compensation,'mode':mode,'points':[[-40000,0],[40000,0]],'depth':3,'viaDepth':3,'springDepth':2,'tidy':10000,'withCheck':True,'stopAfter':-1,'rational':False})
    for variant in [0,2,17,18,21]:
        for angle in [45,90]:
            for mode in ['polyline','segment']:
                for option in ['noCheck','zeroDepth','stop0','stop2','noTidy','unboundedTidy','rational','equal','bend']:
                    h={'case':len(histories),'variant':variant,'angle':angle,'compensation':False,'mode':mode,'points':[[-40000,0],[40000,0]],'depth':3,'viaDepth':3,'springDepth':2,'tidy':10000,'withCheck':True,'stopAfter':-1,'rational':False,'option':option}
                    if option=='noCheck':h['withCheck']=False
                    elif option=='zeroDepth':h['depth']=h['viaDepth']=h['springDepth']=0
                    elif option=='stop0':h['stopAfter']=0
                    elif option=='stop2':h['stopAfter']=2
                    elif option=='noTidy':h['tidy']=0
                    elif option=='unboundedTidy':h['tidy']=2147483647
                    elif option=='rational':h['rational']=True
                    elif option=='equal':h['points']=[[-40000,0],[-40000,0]]
                    else:h['points']=[[-40000,-30000],[0,-30000],[0,30000]]
                    histories.append(h)
    # Append positive insertion layouts; retain all 276 initial cases unchanged.
    for variant in [0,1,2,5,16,17,18,22]:
        for angle in [45,90]:
            for compensation in [False,True]:
                for mode in ['polyline','segment','bend']:
                    histories.append({'case':len(histories),'variant':variant,'angle':angle,'compensation':compensation,'mode':'polyline' if mode=='bend' else mode,'points':[[-6000,-6000],[0,-6000],[0,6000]] if mode=='bend' else [[-5000,0],[5000,0]],'depth':3,'viaDepth':3,'springDepth':2,'tidy':10000,'withCheck':True,'stopAfter':-1,'rational':False,'option':'positive-short-'+mode})
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    support=['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','PolylineTighteningOracle.java','ForcedTraceOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx4g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ForcedTraceOracle',str(OUT/'input.json'),str(board),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=600);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/forced_trace_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'public_queries_are_synthetic':True,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
