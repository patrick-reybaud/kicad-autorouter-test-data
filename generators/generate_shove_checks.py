#!/usr/bin/env python3
"""Observe actual mutually recursive source shove checks on the public board."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-97-shove-checks'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/shove-checks/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    histories=[]
    for variant in range(16):
        for angle in [45,90]:
            for compensation in [False,True]:
                queries=[]
                for n,(x,y,w,h) in enumerate([(0,0,3000,2000),(0,0,1000,1000),(7000,0,1500,5000),(-20000,20000,500,1000),(20000,0,4000,1000),(0,-7000,2000,3000)]):
                    for mode in range(6):
                        for op in ['pad','trace','drill','pin']:
                            queries.append({'op':op,'x':x,'y':y,'w':w,'h':h,'kind':n%3,
                                'nets':[2],'layer':n%2,'ignore':mode%4,'direction':mode%3-1,
                                'sideNull':mode==5,'stopAfter':[-1,0,1,4,100000,-1][mode],
                                'depth':[3,3,3,3,0,1][mode],'viaDepth':[2,2,2,2,0,1][mode],
                                'springDepth':mode%3,'sharing':mode%2==0,'front':mode%2==1})
                histories.append({'case':len(histories),'variant':variant,'angle':angle,'compensation':compensation,
                                  'pin':variant>=14,'contact':variant==15,'queries':queries})
    for angle in [45,90]:
        for compensation in [False,True]:
            queries=[]
            for kind in [0,1,2]:
                for sharing in [True,False]:
                    queries.append({'op':'pad','x':0,'y':0,'w':500,'h':500,'kind':kind,
                        'nets':[2],'layer':0,'ignore':3,'direction':-1,'sideNull':False,
                        'stopAfter':-1,'depth':3,'viaDepth':2,'springDepth':0,'sharing':sharing,'front':False})
            histories.append({'case':len(histories),'variant':16,'angle':angle,'compensation':compensation,
                              'pin':False,'contact':False,'queries':queries})
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    support=['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','ShoveChecksOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx4g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ShoveChecksOracle',str(OUT/'input.json'),str(board),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=600);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/shove_checks_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'public_queries_are_synthetic':True,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
