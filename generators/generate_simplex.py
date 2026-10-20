#!/usr/bin/env python3
"""Compare the native Simplex algorithms with the unchanged personal Java reference."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import random
import subprocess
import tempfile
from generate_planar import ROOT,bits

DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/simplex_java.tsv'

def cases():
    rng=random.Random(20260923)
    def row(*v): return ' '.join(map(str,v))
    def point(x,y): return row('I',x,y)
    def line(a,b): return row(point(*a),point(*b))
    def simple(lines,mode='R'):return row(mode,len(lines),*lines)
    def polygon(points):return [line(p,points[(i+1)%len(points)]) for i,p in enumerate(points)]
    shapes=[[],[line((0,0),(1,0))],polygon([(0,0),(10,0),(10,10),(0,10)]),polygon([(0,0),(10,0),(0,10)]),
        polygon([(0,0),(5,0),(0,0)]),[line((0,0),(1,0)),line((1,0),(0,0))],
        [line((0,1),(1,1)),line((1,0),(0,0))],polygon([(0,0),(10,1),(2,8),(-3,4)])]
    ints=[-2147483648,-2147483647,-33554433,-33554432,-1,0,1,33554432,33554433,2147483646,2147483647]
    offsets=[-math.inf,-1e10,-30.,-1.,-.5,-0.,0.,.5,1.,30.,1e10,math.inf,math.nan]
    def random_lines(n):
        lines=[]
        for _ in range(n):
            a=(rng.randint(-100,100),rng.randint(-100,100));b=a
            while a==b:b=(rng.randint(-100,100),rng.randint(-100,100))
            lines.append(line(a,b))
        return lines
    for i in range(2100):
        if i<1500:
            lines=random_lines(rng.randrange(0,15)) if i%3 else rng.choice(shapes)
        else:
            # Stable equal-direction runs, with small and larger-than-TimSort-minimum arrays.
            lines=random_lines(rng.choice([1,2,3,31,32,33,64,96]))
            if lines:lines+=rng.choices(lines,k=rng.randrange(8))
            rng.shuffle(lines)
        mode='N' if i%2 else 'R'
        other=simple(rng.choice(shapes),'N')
        query=point(rng.choice(ints),rng.choice(ints));v=row('I',rng.choice(ints),rng.choice(ints))
        yield row('S',simple(lines,mode),other,query,v,rng.choice([-3,-1,0,1,2,3,7,100]),bits(rng.choice(offsets)))
    # Direct constructor preserves order, rational runtime types and zero-direction lines.
    for i in range(350):
        lines=random_lines(rng.randrange(1,7))
        x,y=rng.choice(ints),rng.choice(ints)
        lines.insert(rng.randrange(len(lines)+1),line((x,y),(x,y)) if i%2 else row('R',x,y,2,'R',x+1,y+3,2))
        # Standalone zero directions supplement the mixed short degenerate polygon above.
        # Large comparator-contract violations remain outside this qualified corpus.
        if i%2:lines=[line((x,y),(x,y))]
        yield row('S',simple(lines),simple([]),point(x,y),row('I',0,0),i%8-1,bits(0.))
    # Extreme endpoints without zero directions exercise wrapped deltas and predicates.
    for i in range(250):
        lines=[line((rng.choice(ints),rng.choice(ints)),(rng.choice(ints),rng.choice(ints))) for _ in range(4)]
        # Axis-aligned rectangles guarantee a valid comparator even across wrap boundaries.
        x,y=sorted(rng.sample(ints,2)),sorted(rng.sample(ints,2))
        lines=polygon([(x[0],y[0]),(x[1],y[0]),(x[1],y[1]),(x[0],y[1])])
        yield row('S',simple(lines),simple([]),point(0,0),row('I',0,0),i%7-1,bits(0.))
    for i in range(500):
        x,y=rng.randint(-50,50),rng.randint(-50,50);rx,uy=x+rng.randint(0,100),y+rng.randint(0,100)
        box=[x,y,rx,uy] if i%3 else rng.choices(ints,k=4)
        octagon=row('N',x,y,rx,uy,x-uy+rng.randrange(5),rx-y-rng.randrange(5),x+y+rng.randrange(5),rx+uy-rng.randrange(5))
        if i%11==0:octagon='E'
        yield row('B',*box,octagon)
    def tile(i):
        x,y=rng.randint(-20,20),rng.randint(-20,20);w,h=rng.randint(1,40),rng.randint(1,40)
        if i%3==0:return row('B',x,y,x+w,y+h)
        if i%3==1:return row('O','N',x,y,x+w,y+h,x-y-h+1,x+w-y-1,x+y+1,x+w+y+h-1)
        pts=[(x,y),(x+w,y+1),(x+w-1,y+h),(x-1,y+h-1)] if i%2 else [(x,y),(x+w,y+1),(x+1,y+h)]
        return row('S',simple(polygon(pts),'N'))
    for i in range(1400):yield row('C',tile(i),tile(i//3))
    # Degenerate/empty holes: null array and IntBox's reference null dereference are distinct.
    for a in ['B -10 -10 10 10','O N -10 -10 10 10 -15 15 -15 15','S '+simple(shapes[2],'N')]:
        for b in ['S '+simple([], 'N'),'S '+simple(shapes[4],'N'),'B 0 0 0 0','O E']:
            yield row('C',a,b)
    for i in range(400):
        a=row('I',*rng.choices(ints,k=2));b=row('I',*rng.choices(ints,k=2))
        if i%9==0:a=row('B',2**100+i,3)
        yield row('D',a,b)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting'
    sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,f'Reference changed: {key}'
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-simplex-oracle-') as tmp:
        helpers=[ROOT/'tests/oracle'/f for f in ['ExactCoordinatesOracle.java','RegularShapesOracle.java','SimplexOracle.java']]
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.SimplexOracle'],input='\n'.join(vectors)+'\n',text=True,timeout=120)
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
