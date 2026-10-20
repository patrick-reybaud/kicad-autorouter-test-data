#!/usr/bin/env python3
"""Generate regular-shape results from unchanged personal Java source files."""
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

DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/regular_shapes_java.tsv'

def cases():
    rng=random.Random(20260922)
    def row(*args):return ' '.join(map(str,args))
    ints=[-2147483648,-2147483647,-33554433,-33554432,-1,0,1,33554432,33554433,2147483646,2147483647]
    small=[[-10,-10,10,10],[0,0,0,0],[0,0,10,0],[0,0,0,10],[1,1,-1,-1],[0,0,1,1],[33554432,33554432,-33554432,-33554432]]
    numbers=[-math.inf,-1e300,-2147483649.,-2.5,-.5,-0.,0.,.5,2.5,2147483648.,1e300,math.inf,math.nan]
    def real():return bits(rng.choice(numbers) if rng.randrange(3)==0 else rng.uniform(-100,100))
    def box():
        if rng.randrange(3)==0:return rng.choice(small)
        if rng.randrange(4)==0:return rng.choices(ints,k=4)
        x,y=rng.randint(-100,100),rng.randint(-100,100)
        return [x,y,x+rng.randint(0,100),y+rng.randint(0,100)]
    def vec():
        if rng.randrange(8)==0:return row('R',rng.choice(ints),rng.choice(ints),rng.choice([0,1,2]))
        return row('I',*rng.choices([-3,-1,0,0,0,1,3,*ints],k=2))
    def octagon():
        if rng.randrange(10)==0:return 'E'
        if rng.randrange(4)==0:return row(rng.choice(['O','N']),*rng.choices(ints,k=8))
        x,y,rx,ty=box()
        return row(rng.choice(['O','N']),x,y,rx,ty,max(-2147483648,min(2147483647,x-ty+rng.randint(-3,3))),
            max(-2147483648,min(2147483647,rx-y+rng.randint(-3,3))),max(-2147483648,min(2147483647,x+y+rng.randint(-3,3))),max(-2147483648,min(2147483647,rx+ty+rng.randint(-3,3))))
    boxes=list(itertools.product(small,repeat=2))+[(box(),box()) for _ in range(2200)]
    for a,b in boxes:
        for edge in range(-1,5):
            p=rng.choice([(a[0]+a[2])//2,(a[0]),rng.choice(ints)]),rng.choice([(a[1]+a[3])//2,a[1],rng.choice(ints)])
            yield row('B',*a,*b,*p,*rng.choices(ints,k=2),real(),real(),vec(),edge,rng.choice([-1,0,1,2,3,8,100]),rng.randint(-8,8),rng.choice(ints),real())
    for _ in range(5000):
        yield row('O',octagon(),octagon(),*box(),*rng.choices(list(range(-12,13))+ints,k=2),real(),real(),vec(),rng.randint(-1,8),rng.choice([-1,0,1,2,4,8,9,100]),rng.randrange(8),real(),real())
    # Empty singleton versus an equal raw constructor; centered octagons with tied projections.
    for a in ['E','O 33554432 33554432 -33554432 -33554432 33554432 -33554432 33554432 -33554432','O -10 -10 10 10 -15 15 -15 15','N -10 -10 10 10 -15 15 -15 15','O -10 -10 10 10 -20 20 -20 20']:
        for edge,count in itertools.product(range(-1,9),range(10)):
            yield row('O',a,a,-5,-5,5,5,0,0,bits(0.),bits(0.),'I 0 0',edge,count,edge%8,bits(0.),bits(0.))
    # Interior/edge/exterior cuts with unequal surrounding widths force the divide-line switches.
    for x,y,w,h in itertools.product([-12,-5,0,5,12],[-12,-5,0,5,12],[1,3,9],[1,3,9]):
        for bevel in [0,1,2,5]:
            hole=row('N',x,y,x+w,y+h,x-y-h+bevel,x+w-y-bevel,x+y+bevel,x+w+y+h-bevel)
            outer=row('N',-15,-15,15,15,-25,25,-25,25)
            yield row('C',hole,outer,-15,-15,15,15)
    for _ in range(1200):yield row('C',octagon(),octagon(),*box())
    for _ in range(2000):
        a=row('B',*box()) if rng.randrange(2) else row('O',octagon())
        b=row('B',*box()) if rng.randrange(2) else row('O',octagon())
        yield row('G',a,b,rng.randint(-1,8))
    for a in [[0,0,x,y] for x,y in itertools.product([-10,-1,0,1,3,10],repeat=2)]:
        for width in [-math.inf,-1.,0.,.5,1.,3.,100.,math.inf,math.nan]:yield row('S',*a,bits(width))
    yield row('S',0,0,65536,65536,bits(1.)) # wrapped zero array size, then index exception
    for _ in range(400):
        a=box();width=max(abs(a[2]-a[0]),abs(a[3]-a[1]),1)/rng.randint(1,10)
        yield row('S',*a,bits(width))
    for _ in range(400):
        p=vec();n=rng.randrange(8)
        yield row('F',p,n,*[real() for _ in range(2*n)])

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting'
    sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,f'Reference changed: {key}'
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-regular-shapes-oracle-') as tmp:
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ExactCoordinatesOracle.java'),str(ROOT/'tests/oracle/RegularShapesOracle.java')],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.RegularShapesOracle'],input='\n'.join(vectors)+'\n',text=True)
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
