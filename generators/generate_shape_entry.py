#!/usr/bin/env python3
"""Observe actual entry-side geometry and public-board shape/caches."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-87-shape-entry'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/shape-entry/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    rng=random.Random(8701);geometry=[]
    points=[[-160,0],[-100,0],[-100,-100],[0,-100],[0,0],[0,100],[100,100],[100,0],[160,0],[100,160],[0,160],[100,100]]
    for i in range(1200):
        p=rng.choice(points);q=rng.choice(points)
        ps=[p,q] if i%4 else [p,[rng.randint(-200,200),rng.randint(-200,200)],q]
        geometry.append({'mode':i%3,'shape':i%7,'index':rng.choice([-1,0,1,2,3,7]),'left':bool(i%2),'points':ps,'from':None if i%29==0 else rng.choice(points)})
    locator=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/locator_java.json'
    public=[]
    for case in json.loads(locator.read_text())['publicCases']:
        for ti,(layer,corners) in enumerate(case['expected']['locator']['traces']):
            for idx in range(max(1,len(corners)-1)):
                for ortho in [False,True]:
                    for check in [False,True]:
                        public.append({'origin':f'fr-ecc83/locator-unit/angle={case["angle"]}/net={case["net"]}/trace={ti}',
                                       'net':case['net'],'layer':layer,'corners':corners,'index':idx,'orthogonal':ortho,'check':check})
    assert len(public)==328
    (OUT/'input.json').write_text(json.dumps({'geometry':geometry,'public':public},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    support=['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx2g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ShapeEntryOracle',str(OUT/'input.json'),str(board),str(OUT/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=180);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    result=(OUT/'java.json').read_bytes();assert result==(OUT/'java-repeat.json').read_bytes()
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/shape_entry_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_locator_sha256':hashlib.sha256(locator.read_bytes()).hexdigest(),'synthetic_geometry_cases':1200,'public_board_cases':328,'raw_constructors':12,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(result);print('geometry',len(data['geometry']),'public',len(data['public']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
