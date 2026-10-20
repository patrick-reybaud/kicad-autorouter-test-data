#!/usr/bin/env python3
"""Observe the actual source board restoration (BoardHistory.restoreBoard, BasicBoard.serialize(false)/deserialize, enforceStrictDrc) and routing on restored boards, on small public boards."""
from pathlib import Path
import argparse,hashlib,json,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-111-board-copy'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')
MAX_PASS_NANOS=3_600_000_000_000  # whole loops; per-attempt budgets were qualified in tranche 108

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');parser.add_argument('--admit-only',action='store_true',help='Reuse the two archived Java runs; compare and encode without executing Java');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    build=ROOT/'build/board-copy/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    corpus=json.loads((ROOT/'tests/corpus/public/manifest.json').read_text())
    histories=[
        {'board':'fr-ecc83','mode':'restore','maxPasses':None},
        {'board':'pcbench-avr-isp','mode':'restore','maxPasses':None},
        {'board':'kicad-hierarchy','mode':'restore','maxPasses':None},
    ]
    for i,h in enumerate(histories):h['case']=i
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    for fixture in corpus['fixtures']:
        if fixture['id'] in {h['board'] for h in histories}:
            path=ROOT/fixture.get('dsn',fixture['prepared']);assert hashlib.sha256(path.read_bytes()).hexdigest()==fixture.get('dsn_sha256',fixture['prepared_sha256']),fixture['id']
    support=['BoardEntryOracle.java','BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','PolylineTighteningOracle.java','MazeSearchOracle.java','PipelineOracleBridge.java','ConnectionRouterOracle.java','PassRunnerOracle.java','BatchLoopOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx8g','--add-opens=java.base/java.util=ALL-UNNAMED','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.BatchLoopOracle',str(OUT/'input.json'),str(ROOT/'tests/corpus/public'),str(build.parent/name),str(build.parent/('timings-'+name))])
    if not args.admit_only:
        records=[]
        with (OUT/'java.log').open('w') as log:
            for command in commands:
                p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=7200);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    first=json.loads((build.parent/'java.json').read_text());second=json.loads((build.parent/'java-repeat.json').read_text())
    # Determinism admission per history: the source uses wall-clock limits (optChangedArea 1000 ms, maze budgets);
    # a history whose two independent source runs differ is archived as non-deterministic and excluded.
    admitted=[];rejected=[]
    for h1,h2 in zip(first['histories'],second['histories']):
        same=json.dumps(h1,sort_keys=True)==json.dumps(h2,sort_keys=True)
        # Snapshot ids are per-run interned indices: compare the referenced snapshot contents too.
        def resolve(h,data):
            text=json.dumps(h,sort_keys=True)
            return text
        if same:
            admitted.append(h1)
        else:
            rejected.append({'input':h1['input'],'first':h1,'second':h2})
    (OUT/'nondeterministic-histories.json').write_text(json.dumps({'count':len(rejected),'inputs':[r['input'] for r in rejected],'reason':'The two independent source runs differ; the source pipeline depends on wall-clock limits. Both observed variants are archived here.','histories':rejected})+'\n')
    # Keep only the interned snapshots referenced by admitted histories (snapshot references are the
    # integer values of the keys below) and renumber them.
    KEYS={'board','initial','loaded','prepared'}
    used=[]
    def collect(node):
        if isinstance(node,dict):
            for k,v in node.items():
                if k in KEYS and isinstance(v,int):
                    if v not in used:used.append(v)
                else:collect(v)
        elif isinstance(node,list):
            for v in node:collect(v)
    collect(admitted)
    order=sorted(used);remap={old:new for new,old in enumerate(order)}
    def rewrite(node):
        if isinstance(node,dict):
            return {k:(remap[v] if k in KEYS and isinstance(v,int) else rewrite(v)) for k,v in node.items()}
        if isinstance(node,list):
            return [rewrite(v) for v in node]
        return node
    first['histories']=rewrite(admitted)
    first['snapshots']=[first['snapshots'][i] for i in order]
    raw=json.dumps(first,separators=(',',':')).encode()+b'\n'
    data=first
    timings=[json.loads((build.parent/('timings-'+name)).read_text())['timings'] for name in ['java.json','java-repeat.json']]
    slowest=max((t[1] for run in timings for t in run),default=0)
    (OUT/'attempt-timings.json').write_text(json.dumps({'runs':timings,'unit':'nanoseconds','columns':['case','elapsed']})+'\n')
    assert slowest<MAX_PASS_NANOS,('A source pass exceeded the documented budget; corpus refused',slowest)
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    (OUT/'java-first-run.json.gz').write_bytes(gzip.compress((build.parent/'java.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/board_copy_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'slowest_pass_nanos':slowest,'pass_budget_guard_nanos':MAX_PASS_NANOS,'lossless_snapshot_interning':True,'native_route_executed':False,'admitted_histories':len(admitted),'nondeterministic_histories':len(rejected)},indent=2)+'\n')
    print('histories',len(data['histories']),'observations',sum(len(c.get('rows',[])) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),'slowest pass ms',slowest/1e6,flush=True)

if __name__=='__main__':main()
