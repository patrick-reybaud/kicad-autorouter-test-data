#!/usr/bin/env python3
"""Generate exact DestinationDistance outputs by executing the personal Java reference."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import random
import subprocess
import tempfile
from generate_planar import ROOT, bits

DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/destination_distance_java.tsv'

def cases():
    rng=random.Random(20260919)
    def box():
        x,y=[rng.randint(-100000,100000) for _ in range(2)]
        return [x,y,x+rng.randint(0,100000),y+rng.randint(0,100000)]
    profiles=[]
    for n in [1,2,3,4,6,8]:
        for mask in range(1<<n):
            for mode in range(3): profiles.append((n,mask,mode))
    for _ in range(100):
        n=rng.choice([2,4,6,16,32]); profiles.append((n,rng.getrandbits(n),3))
    special=[0.,-0.,1.,2.,1e-300,1e300,math.nan,math.inf,-math.inf,-1.]
    for index,(n,mask,mode) in enumerate(profiles):
        values=[str(n)]
        for layer in range(n):
            h,v=[1.,1.] if mode==0 else rng.choices(special,k=2) if mode==3 else [rng.uniform(.1,20),rng.uniform(.1,20)]
            values += [str((mask>>layer)&1),bits(h),bits(v)]
        normal,cheap=rng.choices(special,k=2) if mode==3 else [rng.uniform(0,100),rng.uniform(0,100)]
        values += [bits(normal),bits(cheap)]
        layers=[0,n-1,n//2,0];rng.shuffle(layers)
        joins=[(layer,box()) for layer in layers]
        if mode==2: joins[0]=(layers[0],[33554432,33554432,-33554432,-33554432])
        if mode==3: joins[0]=(layers[0],[-2147483648,-2147483648,2147483647,2147483647])
        values += [str(len(joins))]
        for layer,b in joins: values += list(map(str,[layer,*b]))
        query=box()
        if mode==0: query=joins[0][1]
        if mode==3: query=rng.choices([-2147483648,2147483647,0,-1,1],k=4)
        point=rng.choices(special,k=2) if mode==3 else [rng.uniform(-1e5,1e5),rng.uniform(-1e5,1e5)]
        values+=list(map(str,query))+list(map(bits,point))
        yield ' '.join(values)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting'
    sources=sorted((base/'geometry/planar').glob('*.java'))
    sources += [base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    sources += [base/'autoroute/maze'/f'{n}.java' for n in ['DestinationDistance','AutorouteControl']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items(): assert manifest['source_files'][key]['sha256']==value,f'Reference changed: {key}'
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'))
    support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-destination-oracle-') as tmp:
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),
                        str(ROOT/'tests/oracle/DestinationDistanceOracle.java')],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-cp',tmp+os.pathsep+str(support),
            'app.freerouting.autoroute.maze.DestinationDistanceOracle'],input='\n'.join(vectors)+'\n',text=True)
    rows=[line for line in generated.splitlines() if ' | ' in line];assert len(rows)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check: assert DEST.read_text()==generated, 'Reference output changed'
    else: DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),
      'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),
      'scenarios':len(vectors),'distance_evaluations':sum(len(row.split(' | ')[1].split()) for row in rows),
      'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),
      'java_version':subprocess.run([str(home/'bin/java'),'-version'],text=True,capture_output=True,check=True).stderr},indent=2))

if __name__=='__main__':main()
