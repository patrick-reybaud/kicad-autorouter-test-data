#!/usr/bin/env python3
"""Exercise the pinned Java Line class and its cached/uncached construction paths."""
import argparse
import hashlib
import itertools
import json
import math
import os
from pathlib import Path
import random
import subprocess
import tempfile
from generate_planar import ROOT,bits

DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/exact_line_java.tsv'

def cases():
    rng=random.Random(20260921)
    def row(*args):return ' '.join(map(str,args))
    def ip(x,y):return row('I',x,y)
    crit=33554432;limit=2147483647
    ints=[-limit-1,-limit,-crit-1,-crit,-crit+1,-3,-1,0,1,2,3,crit-1,crit,crit+1,limit-1,limit]
    directions=[(0,0),(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,-1),(1,-1),(2,1),(1,2),(5,3),(3,5),(2,2),(-6,8)]
    points=[ip(x,y) for x,y in directions]+[ip(limit,limit),ip(-limit-1,0),ip(0,-limit-1)]
    rationals=[row('R',x,y,z) for x,y,z in [(0,0,1),(0,0,0),(0,1,0),(3,4,2),(-6,8,4),(2**64,1,1),(2**1024,1,2),(2**1024,2**1024,2**1024)]]
    allpoints=points+rationals
    def make_direction():
        return row(rng.choice(['I','B']),*rng.choice(directions)) if rng.randrange(3) else row('B',2**100+3,rng.choice([-2,0,1,2]))
    def line():
        mode=rng.randrange(4)
        if mode==0:return row(0,rng.choice(allpoints),rng.choice(allpoints))
        if mode==1:return row(1,*rng.choices(ints,k=4))
        return row(mode,rng.choice(allpoints),make_direction())
    def tail():
        numbers=[-math.inf,-1e300,-1.,-0.,0.,.5,1.,1e-10,1e300,math.inf,math.nan]
        def real():return bits(rng.choice(numbers) if rng.randrange(3)==0 else rng.uniform(-1000,1000))
        return row(rng.choice(allpoints),real(),real(),rng.choice(allpoints),*[real() for _ in range(4)],rng.randint(-9,9),*rng.choices(ints,k=6))
    fixtures=[]
    for x,y in directions:
        fixtures += [row(1,0,0,x,y),row(1,3,5,x+3,y+5)]
        fixtures += [row(mode,ip(3,5),kind,x,y) for mode in [2,3] for kind in ['I','B']]
    for a,b in itertools.product(fixtures,repeat=2):
        yield row(a,b,rng.choice(fixtures),tail())
    for _ in range(1500):yield row(line(),line(),line(),tail())
    # General intersections close to collinearity exercise the tolerance/exact fallback.
    for n in [1,100,crit-1,crit,crit+1,limit-1]:
        for delta in [-2,-1,0,1,2]:
            a=row(1,0,0,n,n-1);b=row(1,0,1,n,n);c=row(1,delta,0,n+delta if n+delta<=limit else limit,n-1)
            yield row(a,b,c,ip(n//2,n//2),bits(n/2),bits(n/2),ip(0,0),bits(1.),bits(0.),bits(1.),bits(1.),0,0,0,-100,-100,100,100)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting'
    sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,f'Reference changed: {key}'
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-exact-line-oracle-') as tmp:
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ExactCoordinatesOracle.java'),str(ROOT/'tests/oracle/ExactLineOracle.java')],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.ExactLineOracle'],input='\n'.join(vectors)+'\n',text=True)
    rows=[line for line in generated.splitlines() if ' | ' in line];assert len(rows)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),
        'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(vectors),
        'expected_fields':sum(len(row.split(' | ')[1].split()) for row in rows),'fixture':str(DEST.relative_to(ROOT)),
        'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),
        'java_version':subprocess.run([str(home/'bin/java'),'-version'],text=True,capture_output=True,check=True).stderr},indent=2))

if __name__=='__main__':main()
