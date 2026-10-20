#!/usr/bin/env python3
"""Observe source drill/page/layer expansion and live engine state on public boards."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-104-maze-expansion'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/maze-expansion/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    histories=[]
    for board,layers in [('fr-ecc83',2),('fr-coldfire',4)]:
        for angle in [45,90]:
            for compensation in ([False,True] if layers==2 else [False]):
                for variant in (range(9) if layers==2 else [0,2]):
                    queries=[]
                    for op in ['check','drill','page','other']:
                        for layer in range(layers):
                            for option in range(4):
                                queries.append({'op':op,'layer':layer,'ripup':option!=0,'lower':0,'upper':layers-1,'occupied':(1<<(layers-1)) if option==3 else 0,'page':option>=2,'backtrack':option%3,'cost':option*10,'ripped':option==1,'matching':True})
                    # Actual DrillPage.getDrills and actual engine room completion.
                    if variant in [0,4,8]:
                        for matching,occupied in [(False,0),(True,0),(True,1)]:
                            queries.append({'op':'scan','layer':0,'ripup':False,'lower':0,'upper':layers-1,'occupied':occupied,'page':True,'backtrack':1,'cost':0,'ripped':False,'matching':matching})
                    histories.append({'case':len(histories),'board':board,'angle':angle,'compensation':compensation,'variant':variant,'pin':3 if layers==2 else 5,'initializeRooms':False,'x':1500000,'y':-1150000 if layers==2 else -1000000,'queries':queries})
    for h in list(histories):
        if h['variant'] in [0,4]:histories.append({**h,'case':len(histories),'initializeRooms':True})
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    assert hashlib.sha256((ROOT/'tests/corpus/public/fr-coldfire/prepared/fr-coldfire.dsn').read_bytes()).hexdigest()=='10ff1dc405d27bb279f031facfaddf97b0f3407dd9b37c9c7e8df5c5f675cfe9'
    support=['BoardEntryOracle.java','BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','PolylineTighteningOracle.java','MazeExpansionOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx4g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.MazeExpansionOracle',str(OUT/'input.json'),str(ROOT/'tests/corpus/public'),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=600);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/maze_expansion_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'public_queries_are_synthetic':True,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
