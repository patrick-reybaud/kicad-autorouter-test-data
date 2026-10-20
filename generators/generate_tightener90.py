#!/usr/bin/env python3
"""Observe actual source tightener90 decisions, geometry, identities, caches and board effects."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-91-tightener90'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/tightener90/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    histories=[];cx=1500000;cy=-1150000
    paths=[[[0,0],[12000,0]], [[0,0],[0,4000],[12000,4000]],
           [[0,0],[0,4000],[12000,4000],[12000,0]],
           [[0,0],[0,4000],[6000,4000],[6000,8000],[12000,8000]],
           [[0,0],[0,4000],[6000,4000],[6000,-4000],[12000,-4000],[12000,0]],
           [[0,0],[0,1],[6000,1],[6000,8000],[12000,8000]],
           [[0,0],[0,99],[6000,99],[6000,101],[12000,101]],
           [[0,0],[4000,0],[4000,4000],[8000,4000],[8000,0],[12000,0]],
           [[0,0],[3000,4000],[6000,4000],[6000,8000],[12000,8000]],
           [[0,0],[1,4000],[6000,4001],[6001,8000],[12000,8000]],
           [[0,0],[0,4000],[4000,4000],[4000,4000],[8000,4000],[8000,0]],
           [[0,0],[0,4000],[4000,4000],[4000,0],[4000,-4000],[8000,-4000]],
           [[0,0],[0,-4000],[4000,-4000],[4000,-8000],[12000,-8000]],
           [[0,0],[0,4000],[-4000,4000],[-4000,8000],[-12000,8000]],
           [[0,0],[0,4000],[4000,4000],[4000,8000],[8000,8000],[8000,12000],[12000,12000]],
           [[0,0],[0,1000],[1,1000],[1,1001],[12000,1001]]]
    for obstacle in range(4):
        for compensation in [False,True]:
            obs=[]
            if obstacle:obs=[{'points':[[cx+6000,cy-20000],[cx+6000,cy+20000]],'width':100 if obstacle<3 else 500,'net':3 if obstacle!=2 else 2}]
            queries=[]
            for index,path in enumerate(paths):
                for method in ['pull','repositionLines','skipSegmentsOfLength0','trySkipSecondCorner','trySkipCorners','line']:
                    queries.append({'op':method,'points':[[cx+x,cy+y] for x,y in path],'width':100,'net':2,'pinsNull':index%2==0,'minimum':[-1,99,100,101,1000][index%5], 'clip':None if index%3 else [cx-1000,cy-1000,cx+15000,cy+6000], 'stopAfter':0 if index==1 else 1 if index==2 else 1000,'keep':None,'index':2})
            if obs:
                for keep in [None,[cx+6000,cy],[cx+6000,cy+1000],[cx+6000,cy+20000]]:
                    for op in ['split','smooth']:
                        queries.append({'op':op,'points':[[cx,cy],[cx+12000,cy]],'width':100,'net':2,'pinsNull':False,'minimum':100,'clip':None,'stopAfter':1000,'keep':keep,'index':0})
            histories.append({'case':len(histories),'compensation':compensation,'obstacles':obs,'queries':queries})
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    support=['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','TraceTightener90Oracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx4g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.TraceTightener90Oracle',str(OUT/'input.json'),str(board),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=600);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/tightener90_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'public_queries_are_synthetic':True,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
