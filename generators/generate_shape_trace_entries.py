#!/usr/bin/env python3
"""Observe actual trace-entry ordering, stack levels, substitute pieces and board cutouts."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-90-shape-trace-entries'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/shape-trace-entries/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    rng=random.Random(9001);histories=[];cx=1500000;cy=-1150000
    def trace(points,nets,width=100,fixed=0):
        return {'points':[[cx+x,cy+y] for x,y in points],'nets':nets,'width':width,'fixed':fixed}
    for layout in range(12):
        for compensated in [False,True]:
            for sorted_items in [False,True]:
                traces=[];vias=[];existing=[]
                if layout==0:traces=[trace([[-20000,0],[20000,0]],[3])]
                elif layout==1:traces=[trace([[-20000,-1500],[20000,-1500]],[3]),trace([[-20000,1500],[20000,1500]],[4])]
                elif layout==2:traces=[trace([[-20000,0],[20000,0]],[3]),trace([[0,-20000],[0,20000]],[4])]
                elif layout==3:traces=[trace([[-20000,y],[20000,y]],[3 if y<=0 else 4]) for y in [-1500,0,1500]]
                elif layout==4:traces=[trace([[-20000,0],[20000,0]],[2]),trace([[-20000,1500],[20000,1500]],[3])]
                elif layout==5:traces=[trace([[0,0],[20000,0]],[3])]
                elif layout==6:traces=[trace([[-20000,0],[0,0]],[3]),trace([[0,0],[20000,0]],[3],200)]
                elif layout==7:traces=[trace([[-20000,0],[20000,0]],[0])]
                elif layout==8:traces=[trace([[-20000,0],[20000,0]],[3],fixed=1)]
                elif layout==9:
                    traces=[trace([[0,0],[20000,0]],[3])];vias=[{'point':[cx,cy],'nets':[3]}]
                elif layout==10:existing=[3]
                else:existing=[2]
                side=[None,-99,0,2,6][(layout+int(sorted_items))%5]
                histories.append({'case':len(histories),'layout':layout,'compensation':compensated,'sorted':sorted_items,
                    'traces':traces,'vias':vias,'existing':existing,'box':[cx-5000,cy-5000,cx+5000,cy+5000],
                    'shapeKind':(layout+int(sorted_items))%3,'ownNets':[2],'side':side,
                    'sidePoint':[cx,cy-5000] if layout%3==0 else None,'padCheck':layout%2==0,'copperSharing':sorted_items})
    net_cases=[{'a':[1,1],'b':[1,2]},{'a':[1,2],'b':[1,1]},{'a':None,'b':[]},{'a':[],'b':None}]
    for i in range(1000):net_cases.append({'a':[rng.randrange(-1,5) for _ in range(rng.randrange(6))],'b':[rng.randrange(-1,5) for _ in range(rng.randrange(6))]})
    (OUT/'input.json').write_text(json.dumps({'histories':histories,'netCases':net_cases},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    support=['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx2g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.ShapeTraceEntriesOracle',str(OUT/'input.json'),str(board),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=300);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/shape_trace_entries_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'net_equality_cases':len(net_cases),'public_queries_are_synthetic':True,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
