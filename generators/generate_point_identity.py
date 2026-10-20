#!/usr/bin/env python3
"""Observe real Polyline corner identities, separately from routing fidelity."""
from pathlib import Path
import argparse,hashlib,json,random,subprocess
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'validation/freerouting-port-86-point-identity'
JAVA=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    build=ROOT/'build/point-identity/classes';build.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources=[]
    for name,entry in manifest['source_files'].items():
        path=ROOT/'engine'/name;assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['sha256'];sources.append(str(path))
    jar=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'
    pins=next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values() if isinstance(d,dict) and 'reference_jvm_sha256' in d)
    for name,digest in pins.items():assert hashlib.sha256((JAVA/name).read_bytes()).hexdigest()==digest
    cases=[];rng=random.Random(8601)
    for i in range(800):
        mode=['points','segment','polygon','lines'][i%4]
        count=2 if mode=='segment' else (rng.randint(3,8)*2 if mode=='lines' else rng.randint(0,10))
        points=[['I',rng.randint(-300,300),rng.randint(-300,300)] for _ in range(max(2,count))]
        if i%13==0:points[1]=points[0].copy()
        if i%17==0:points[0]=['R','1','3','2']
        indices=list(range(count))
        if i%19==0 and count>1:indices[-1]=indices[0]
        cases.append({'case':i,'mode':mode,'points':points,'indices':indices,'origin':'synthetic-geometry-identity'})
    locator=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/locator_java.json'
    for case in json.loads(locator.read_text())['publicCases']:
        for index,(layer,corners) in enumerate(case['expected']['locator']['traces']):
            cases.append({'case':len(cases),'mode':'points','points':[['I',*p] for p in corners],
                          'indices':list(range(len(corners))),
                          'origin':f'fr-ecc83/locator-unit/angle={case["angle"]}/net={case["net"]}/trace={index}'})
    assert len(cases)==820
    (OUT/'input.json').write_text(json.dumps(cases,separators=(',',':'))+'\n')
    commands=[[str(JAVA/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,str(ROOT/'tests/oracle/PolylineIdentityOracle.java')]]
    for name in ['java.json','java-repeat.json']:
        commands.append([str(JAVA/'bin/java'),'-Xmx2g','-Djava.awt.headless=true','-cp',str(build)+':'+str(jar),'app.freerouting.geometry.planar.PolylineIdentityOracle',str(OUT/'input.json'),str(OUT/name)])
    records=[]
    with (OUT/'java.log').open('w') as log:
        for command in commands:
            p=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=180);records.append({'argv':command,'exit_code':p.returncode});(OUT/'java-commands.json').write_text(json.dumps(records,indent=2)+'\n');p.check_returncode()
    result=(OUT/'java.json').read_bytes();assert result==(OUT/'java-repeat.json').read_bytes()
    fixture=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/point_identity_java.json'
    if args.check:assert fixture.read_bytes()==result
    else:fixture.write_bytes(result)
    (OUT/'provenance.json').write_text(json.dumps({'reference_commit':manifest['reference_commit'],'source_files':len(sources),'jvm_files':pins,'public_locator_sha256':hashlib.sha256(locator.read_bytes()).hexdigest(),'synthetic_histories':800,'public_locator_geometry_histories':20,'native_route_executed':False},indent=2)+'\n')
    data=json.loads(result);print('histories',len(data),'states',sum(len(c['expected'].get('states',[])) for c in data),flush=True)

if __name__=='__main__':main()
