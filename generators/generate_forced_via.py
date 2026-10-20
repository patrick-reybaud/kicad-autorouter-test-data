#!/usr/bin/env python3
"""Observe actual source forced-via and lengths and undo/redo on the public board."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-99-forced-via'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/forced-via/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    histories=[]
    for mode in ['via','length']:
        for variant in [0,1,2,5,16,17,18,20]:
            for angle in [45,90]:
                for compensation in [False,True]:
                    histories.append({'case':len(histories),'board':'fr-ecc83','mode':mode,'variant':variant,'angle':angle,'compensation':compensation,'x':1500000,'y':-1150000,'layer':0,'hole':20000 if variant==20 else 0,'attach':True})
    for angle in [45,90]:
        for layer in range(4):
            for hole in [0,20000]:
                histories.append({'case':len(histories),'board':'fr-coldfire','mode':'via','variant':20,'angle':angle,'compensation':False,'x':760200,'y':-1424200,'layer':layer,'hole':hole,'attach':True})
    for h in histories:
        q=[];layers=4 if h['board']=='fr-coldfire' else 2
        if h['mode']=='length':
            for segment in [[-40000,0,40000,0],[40000,0,-40000,0],[-40000,-40000,40000,40000],[40000,40000,-40000,-40000],[0,-40000,0,40000],[-40000,8000,40000,8000],[-40000,20000,40000,20000],[-1000000,0,0,0],[0,0,0,0]]:
                for left in [False,True]:
                    for depth,via in [(0,0),(3,0),(3,3)]:
                        q.append({'op':'length','segment':segment,'left':left,'width':1000,'depth':depth,'viaDepth':via})
        else:
            q.extend([{'op':'hole'},{'op':'hole','rational':True},{'op':'hole','nullPoint':True}])
            rooms=[[-50000,-50000,50000,50000],[-100, -100,100,100],[-1000,-20000,1000,-5000],[5000,-1000,20000,1000],[-1000,5000,1000,20000],[-20000,-1000,-5000,1000],[5000,-20000,20000,-5000],[5000,5000,20000,20000],[-20000,5000,-5000,20000],[-20000,-20000,-5000,-5000]]
            if h['board']=='fr-coldfire':rooms=rooms[:2]
            for room in rooms:
                q.append({'op':'side','room':room,'radius':10000})
                q.append({'op':'layer','room':room,'radius':1000.25,'width':15000 if h['variant']==20 else 0})
            q.extend([{'op':'layer','room':rooms[0],'radius':0,'width':0,'nullPoint':True},{'op':'layer','room':rooms[0],'radius':1000,'width':0,'rational':True}])
            for widths in [None,[],[0]*layers,[15000]*layers]:q.append({'op':'check','widths':widths})
            q.append({'op':'insert','widths':[0]*layers});q.append({'op':'undo'});q.append({'op':'redo'})
        h['queries']=q
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    assert hashlib.sha256((ROOT/'tests/corpus/public/fr-coldfire/prepared/fr-coldfire.dsn').read_bytes()).hexdigest()=='10ff1dc405d27bb279f031facfaddf97b0f3407dd9b37c9c7e8df5c5f675cfe9'
    support=['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','ForcedViaOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx4g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ForcedViaOracle',str(OUT/'input.json'),str(ROOT/'tests/corpus/public'),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=600);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/forced_via_java.json.gz'
    compressed=gzip.compress(result,mtime=0);assert gzip.decompress(compressed)==result
    if args.check:assert fixture.read_bytes()==compressed
    else:fixture.write_bytes(compressed)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'public_queries_are_synthetic':True,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
