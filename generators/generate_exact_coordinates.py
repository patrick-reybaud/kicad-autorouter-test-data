#!/usr/bin/env python3
"""Generate exact point/vector/direction fixtures by running the pinned Java source."""
import argparse
from collections import Counter
import hashlib
import itertools
import json
import math
import os
from pathlib import Path
import random
import subprocess
import tempfile
from generate_planar import ROOT, bits

DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/exact_coordinates_java.tsv'

def cases():
    rng=random.Random(20260920)
    def row(*args):return ' '.join(map(str,args))
    ints=[-2147483648,-2147483647,-33554433,-33554432,-33554431,-13,-3,-1,0,1,2,3,13,33554431,33554432,33554433,2147483646,2147483647]
    bigs=[0,1,-1]
    for n in [31,32,52,53,54,63,64,95,127,255,511,1022,1023,1024,2048,4096]:
        for offset in [-3,-2,-1,0,1,2,3]:
            bigs += [2**n+offset,-(2**n+offset)]
    # Midpoints and rounding carry around the finite/infinite binary64 boundary.
    for exponent in [54,55,64,100,500,1023,1024]:
        for sig in [2**52,2**52+1,2**53-2,2**53-1]:
            for offset in [-1,0,1]:
                n=(sig<<(exponent-53))+(1<<(exponent-54))+offset
                bigs.extend([n,-n])
    for _ in range(600):
        n=rng.getrandbits(rng.randint(1,4096));bigs.append(n if rng.randrange(2) else -n)
    for a in bigs:
        yield row('B',a,rng.choice(bigs))
    for a,b in itertools.product([0,1,-1,2,-2,2**100,-2**100],repeat=2):yield row('B',a,b)
    for x,y in itertools.product(ints,repeat=2):
        for z in [0,1,2,-3]:yield row('C',x,y,x,y,z)
    for _ in range(600):yield row('C',rng.choice(ints),rng.choice(ints),rng.choice(bigs),rng.choice(bigs),rng.choice(bigs))

    small=[(0,0),(1,0),(0,1),(1,1),(-1,0),(0,-1),(-1,-1),(-1,1),(1,-1),(3,4),(4,3),(-6,8)]
    ips=small+[(x,y) for x in ints for y in ints if x in [0,-2147483648,2147483647] or y in [0,-2147483648,2147483647]]
    coords=[row('I',x,y) for x,y in small]
    for x,y,z in [(0,0,1),(0,0,0),(1,0,0),(0,1,0),(3,4,2),(6,8,4),(-6,8,4),(1,1,1),(2,2,2),(0,1,2),(1,0,2),(33554433,2,1),(2**1024,1,2),(2**1024,2**1024,2**1024)]:coords.append(row('R',x,y,z))
    def coord(negative=False):
        if rng.randrange(3)==0:return rng.choice(coords)
        if rng.randrange(2)==0:return row('I',*rng.choices(ints,k=2))
        scale=rng.choice([0,1,2,3,7,2**54+1,2**100,2**1024])
        if negative and rng.randrange(4)==0:scale=-scale
        return row('R',rng.choice(bigs),rng.choice(bigs),scale)
    pairs=list(itertools.product(coords,repeat=2))+[(coord(),coord()) for _ in range(3000)]
    for idx,(a,b) in enumerate(pairs):
        l1=rng.choice(ips);l2=l1 if idx%7==0 else rng.choice(ips)
        yield row('P',a,b,coord(),coord(),*l1,*l2,*rng.choices(ints,k=4),rng.randint(-12,12))
    lengths=[-math.inf,-1e300,-1.,-0.,0.,.5,1.,100.,1e300,math.inf,math.nan]
    for a,b in pairs:
        yield row('V',a,b,coord(),rng.randint(-12,12),bits(rng.choice(lengths)))
    for _ in range(500):yield row('V',coord(True),coord(True),coord(),rng.randint(-12,12),bits(rng.uniform(-1e5,1e5)))

    dirs=[row('I',x,y) for x,y in small]+[row('B',x,y) for x,y in small]
    dirs += [row('I',x,y) for x,y in ips]+[row('B',2**1024,1),row('B',-2**1024,2**1024),row('B',1,2**100)]
    floats=[-math.inf,-1e300,-2147483649.,-3.5,-.5,-0.,0.,.5,3.5,2147483648.,1e300,math.inf,math.nan]
    for a,b in itertools.product(dirs[:24],repeat=2):
        for n in [-1,0,1,7,8]:yield row('D',a,b,rng.choice(dirs),n,*map(bits,rng.choices(floats,k=2)))
    for _ in range(3000):yield row('D',rng.choice(dirs),rng.choice(dirs),rng.choice(dirs),rng.choice([-2147483648,-17,-9,-8,-7,-6,-5,-4,-3,-2,-1,0,1,2,3,4,5,6,7,8,17,2147483647]),*map(bits,rng.choices(floats,k=2)))
    for a,b in itertools.product(ips,repeat=2):
        for left in [0,1]:yield row('I',*a,*b,*rng.choice(ips),left)
    for x,y in itertools.product(range(-5,6),repeat=2):
        for left in [0,1]:yield row('I',0,0,x,y,3,4,left)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting'
    sources=sorted((base/'geometry/planar').glob('*.java'))
    sources += [base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,f'Reference changed: {key}'
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'))
    support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-exact-coordinates-oracle-') as tmp:
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ExactCoordinatesOracle.java')],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-cp',tmp+os.pathsep+str(support),
            'app.freerouting.geometry.planar.ExactCoordinatesOracle'],input='\n'.join(vectors)+'\n',text=True)
    rows=[line for line in generated.splitlines() if ' | ' in line];assert len(rows)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),
        'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),
        'cases':len(vectors),'case_categories':dict(Counter(row.split(' ')[0] for row in vectors)),
        'expected_fields':sum(len(row.split(' | ')[1].split()) for row in rows),
        'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),
        'java_version':subprocess.run([str(home/'bin/java'),'-version'],text=True,capture_output=True,check=True).stderr},indent=2))

if __name__=='__main__':main()
