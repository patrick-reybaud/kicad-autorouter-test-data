#!/usr/bin/env python3
"""Observe source PolylineTrace edits, pin exits and local tightening against the admitted public board."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess,gzip
from json_snapshot_archive import encode
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-93-polyline-tightening'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/polyline-tightening/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    histories=[]
    layouts=[
      {'name':'free','points':[[0,0],[0,4000],[6000,4000],[6000,8000],[12000,8000]]},
      {'name':'obstacle','obstacle':True,'points':[[0,0],[0,4000],[6000,4000],[6000,8000],[12000,8000]]},
      {'name':'pin3','pin':3,'points':[[0,0],[25000,-25000],[45000,-25000]]},
      {'name':'pin4-end','pin':4,'reverse':True,'points':[[0,0],[25000,-25000],[45000,-25000]]},
      {'name':'pin202','pin':202,'points':[[0,0],[25000,-25000],[45000,-25000]]},
      {'name':'fixed','fixed':1,'points':[[0,0],[0,4000],[6000,4000],[6000,8000],[12000,8000]]},
      {'name':'not-normal-net','nets':[0],'points':[[0,0],[0,4000],[6000,4000],[6000,8000],[12000,8000],[12000,12000]]},
      {'name':'net-filter','onlyNets':[3],'classPullTight':False,'points':[[0,0],[0,4000],[6000,4000],[6000,8000],[12000,8000]]}]
    for layout in layouts:
        for angle in [90,45]:
            for compensation in [False,True]:
                histories.append({'case':len(histories),'angle':angle,'compensation':compensation,'pin':0,'nets':[2],'reverse':False,'width':100,'fixed':0,'obstacle':False,'classPullTight':True,'onlyNets':[],'ownOnly':False,**layout})
    for reverse in [False,True]:
        for length in [5000,10000,25000,50000]:
            for compensation in [False,True]:
                histories.append({'case':len(histories),'name':'pin3-stub-swap','angle':45,'compensation':compensation,'pin':3,'nets':[2],'reverse':reverse,'width':100,'fixed':0,'obstacle':False,'classPullTight':True,'onlyNets':[],'ownOnly':False,'points':[[length,0],[length-30000,30000],[length-60000,30000]],'contact':[[0,0],[length,0]],'operations':['observe','checkStart','checkEnd','swapEnd' if reverse else 'swapStart','pull','smoothFork','undo','redo']})
    for angle in [90,45]:
        for compensation in [False,True]:
            histories.append({'case':len(histories),'name':'pin3-correct-end','angle':angle,'compensation':compensation,'pin':3,'nets':[2],'reverse':True,'width':100,'fixed':0,'obstacle':False,'classPullTight':True,'onlyNets':[],'ownOnly':True,'points':[[0,0],[25000,-25000],[45000,-25000]],'operations':['observe','checkEnd','correctEnd','checkEnd','pull','smoothFork','undo','redo']})
    for atEnd in [False,True]:
        for compensation in [False,True]:
            histories.append({'case':len(histories),'name':'free-fork','angle':45,'compensation':compensation,'pin':0,'nets':[2],'reverse':False,'width':100,'fixed':0,'obstacle':False,'classPullTight':True,'onlyNets':[],'ownOnly':atEnd,'points':[[0,0],[4000,4000],[8000,4000],[12000,0]],'contact':[[12000,0],[6000,0]] if atEnd else [[0,0],[6000,0]],'contactFixed':0,'operations':['observe','smoothFork','pullOwn','undo','redo']})
    for angle in [90,45]:
        for compensation in [False,True]:
            for kind in ['class-disabled','offboard-edit']:
                histories.append({'case':len(histories),'name':kind,'angle':angle,'compensation':compensation,'pin':0,'nets':[2],'reverse':False,'width':100,'fixed':0,'obstacle':False,'classPullTight':kind!='class-disabled','onlyNets':[],'ownOnly':True,'points':[[0,0],[0,4000],[6000,4000],[6000,8000],[12000,8000]],'operations':['observe','pull','pullOwn','undo','redo'] if kind=='class-disabled' else ['observe','undo','pull','pullOwn','changeMiddle','observe','redo','pullOwn']})
    for angle in [90,45]:
        for compensation in [False,True]:
            for kind in ['retained-after-undo','own-wrapper']:
                histories.append({'case':len(histories),'name':kind,'angle':angle,'compensation':compensation,'pin':0,'nets':[2],'reverse':False,'width':100,'fixed':0,'obstacle':False,'classPullTight':True,'onlyNets':[],'ownOnly':compensation,'points':[[0,0],[0,4000],[6000,4000],[6000,8000],[12000,8000]],'operations':['observe','changeMiddle','undo','pull','pullOwn','changeMiddle','observe','redo'] if kind=='retained-after-undo' else ['observe','pullOwn','undo','redo']})
    for h in histories:
        h.setdefault('operations',['observe','checkStart','checkEnd','changeAlias','changeReconstruct','changeMiddle','correctStart','correctEnd','swapStart','swapEnd','pull','smoothFork','undo','redo'])
    (OUT/'input.json').write_text(json.dumps({'histories':histories},separators=(',',':'))+'\n')
    board=ROOT/'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert hashlib.sha256(board.read_bytes()).hexdigest()=='e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
    support=['BoardConstructionOracle.java','NeutralBoardModel.java','NeutralBoardModelOracle.java','NeutralBoardItemsOracle.java','ShapeEntryOracle.java','CollisionChecksOracle.java','ShapeTraceEntriesOracle.java','PolylineTighteningOracle.java']
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(ROOT/'tests/oracle'/name) for name in support]]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx4g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.board.facade.PolylineTighteningOracle',str(OUT/'input.json'),str(board),str(build.parent/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=600);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    raw=(build.parent/'java.json').read_bytes();assert raw==(build.parent/'java-repeat.json').read_bytes()
    result=encode(raw,OUT)
    (OUT/'java-repeat.json.gz').write_bytes(gzip.compress((build.parent/'java-repeat.json').read_bytes(),mtime=0))
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/polyline_tightening_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_board_histories':len(histories),'public_queries_are_synthetic':True,'lossless_snapshot_interning':True,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(raw);print('histories',len(data['histories']),'observations',sum(len(c['rows']) for c in data['histories']),'unique complete snapshots',len(data['snapshots']),flush=True)

if __name__=='__main__':main()
