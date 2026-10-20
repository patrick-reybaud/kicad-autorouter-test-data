#!/usr/bin/env python3
"""Observe the actual source sequential optimizer (BatchOptimizer.runBatchLoop) on public boards first routed by the actual batch
loop, application-resolved settings, frozen test clock (D120-1): per-item and per-pass decisions, task events, decision hashes."""
from pathlib import Path
import argparse,hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-126-optimizer'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')
MAX_CASE_NANOS=3_600_000_000_000  # one hour per case (D120-1)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');parser.add_argument('--admit-only',action='store_true',help='Reuse the two archived Java runs; compare and encode without executing Java');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    build=ROOT/'build/optimizer-oracle/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    corpus=json.loads((ROOT/'tests/corpus/public/manifest.json').read_text())
    cases=[
        {'board':'fr-ecc83','routePasses':None,'optPasses':2,'optMaxItems':None},
        {'board':'pcbench-avr-isp','routePasses':None,'optPasses':2,'optMaxItems':None},
        {'board':'kicad-hierarchy','routePasses':None,'optPasses':1,'optMaxItems':40},
        {'board':'fr-cm5-minima','routePasses':1,'optPasses':1,'optMaxItems':2},
        {'board':'fr-coldfire','routePasses':1,'optPasses':1,'optMaxItems':1},
    ]
    for i,c in enumerate(cases):c['case']=i
    (OUT/'input.json').write_text(json.dumps({'cases':cases},separators=(',',':'))+'\n')
    for fixture in corpus['fixtures']:
        if fixture['id'] in {c['board'] for c in cases}:
            path=ROOT/fixture.get('dsn',fixture['prepared']);assert hashlib.sha256(path.read_bytes()).hexdigest()==fixture.get('dsn_sha256',fixture['prepared_sha256']),fixture['id']
    support=['BoardEntryOracle.java','BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','PolylineTighteningOracle.java','MazeSearchOracle.java','PipelineOracleBridge.java','OptimizerOracleBridge.java','ConnectionRouterOracle.java','PassRunnerOracle.java','DecisionHash.java','OptimizerOracle.java']
    frozen=build.parent/'frozen-classes';frozen.mkdir(parents=True,exist_ok=True)
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]],
              [str(JAVA/'bin/javac'),'-cp',str(build)+':'+str(jar),'-d',str(frozen),str(ROOT/'tests/oracle/frozen/app/freerouting/datastructures/TimeLimit.java')]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx8g','--add-opens=java.base/java.util=ALL-UNNAMED','-Djava.awt.headless=true','-Dfreerouting.test.clock=frozen','-cp',str(frozen)+':'+str(build)+':'+str(jar),'app.freerouting.board.facade.OptimizerOracle',str(OUT/'input.json'),str(ROOT/'tests/corpus/public'),str(build.parent/name),str(build.parent/('timings-'+name))])
    if not args.admit_only:
        records=[]
        with (OUT/'java.log').open('w') as log:
            for command in commands:
                p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=14400);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    first=json.loads((build.parent/'java.json').read_text());second=json.loads((build.parent/'java-repeat.json').read_text())
    admitted=[];rejected=[]
    for c1,c2 in zip(first['cases'],second['cases']):
        if json.dumps(c1,sort_keys=True)==json.dumps(c2,sort_keys=True):admitted.append(c1)
        else:rejected.append({'input':c1['input'],'first':c1,'second':c2})
    (OUT/'nondeterministic-cases.json').write_text(json.dumps({'count':len(rejected),'inputs':[r['input'] for r in rejected],'reason':'The two independent source runs differ despite the frozen test clock.','cases':rejected})+'\n')
    timings=[json.loads((build.parent/('timings-'+name)).read_text())['timings'] for name in ['java.json','java-repeat.json']]
    slowest=max(t[1]+t[2] for run in timings for t in run)
    (OUT/'attempt-timings.json').write_text(json.dumps({'runs':timings,'unit':'nanoseconds','columns':['case','routeElapsed','optimizeElapsed']})+'\n')
    assert slowest<MAX_CASE_NANOS,('A source case exceeded the documented budget; corpus refused',slowest)
    result=(json.dumps({'cases':admitted,'nativeRoutingExecuted':False},separators=(',',':'))+'\n').encode()
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/optimizer_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_cases':len(cases),'slowest_case_nanos':slowest,'case_budget_guard_nanos':MAX_CASE_NANOS,'native_route_executed':False,'admitted_cases':len(admitted),'nondeterministic_cases':len(rejected),'test_clock':'frozen','fixture_sha256':hashlib.sha256(result).hexdigest(),'fixture_bytes':len(result)},indent=2)+'\n')
    print('cases',len(admitted),'items',sum(len(c.get('items',[])) for c in admitted),'passes',sum(len(c.get('passes',[])) for c in admitted),'slowest case s',slowest/1e9,'fixture bytes',len(result),flush=True)

if __name__=='__main__':main()
