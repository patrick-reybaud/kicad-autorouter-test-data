#!/usr/bin/env python3
"""Qualify inherited TileShape/PolylineShape algorithms against unchanged Java sources."""
import argparse,hashlib,json,math,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT,bits
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/tile_shape_java.tsv'

def cases():
    rng=random.Random(20260926)
    def row(*xs):return ' '.join(map(str,xs))
    def point(x,y):return row('I',x,y)
    def line(a,b):return row(point(*a),point(*b))
    def polygon(points):return [line(p,points[(i+1)%len(points)]) for i,p in enumerate(points)]
    def simple(lines,mode='R'):return row('S',mode,len(lines),*lines)
    rect=polygon([(0,0),(10,0),(10,10),(0,10)])
    triangle=polygon([(0,0),(10,0),(0,10)])
    general=polygon([(0,0),(10,1),(2,8),(-3,4)])
    special=['B 0 0 10 10','B 0 0 0 0','B 0 0 0 10','B 10 10 0 0','O E',
        'O N 0 0 10 10 -8 8 2 18','O R 0 0 10 10 -30 30 -30 30',simple([]),
        simple([line((0,0),(1,0))]),simple([line((0,1),(1,1)),line((1,0),(0,0))]),
        simple([line((0,0),(1,0)),line((1,0),(0,0))]),simple([line((0,0),(0,0))]),
        simple(rect),simple(triangle),simple(general),simple(polygon([(0,0),(10,0),(0,0)])),
        simple(['R 1 2 3 R 5 6 7','I 0 1 I 1 0','I 2 0 I 0 2'])]
    limits=[-2147483648,-2147483647,-33554433,-33554432,-1,0,1,33554432,33554433,2147483646,2147483647]
    reals=[-math.inf,-1e20,-1.,-0.,0.,.5,1.,1e20,math.inf,math.nan]
    def tile(i,extreme=False):
        if i%7==0:return rng.choice(special)
        x,y=rng.randrange(-40,41),rng.randrange(-40,41);w,h=rng.randrange(0,41),rng.randrange(0,41)
        if i%4==0:return row('B',*rng.choices(limits,k=4)) if extreme else row('B',x,y,x+w,y+h)
        if i%4==1:return row('O',rng.choice(['R','N']),x,y,x+w,y+h,x-y-h+2,x+w-y-2,x+y+2,x+w+y+h-2)
        pts=[(x,y),(x+w,y+1),(x+w-1,y+h),(x-1,y+h-1)]
        if extreme:pts=[tuple(rng.choices(limits,k=2)) for _ in range(4)]
        return simple(polygon(pts),'R')
    def query(a,b,p,e,fp,li,di,edge,count,tol):return row('Q',a,b,p,e,*map(bits,fp),li,di,edge,count,bits(tol))
    # Explicit border/tie/inside/outside and empty/unbounded queries on each concrete kind.
    grid=[(0,0),(10,0),(10,10),(0,10),(5,5),(5,0),(-1,5),(11,5),(5,-1),(5,11),(3,4)]
    for a in special:
        for x,y in grid:
            for count in [0,1,2,4,12]:
                yield query(a,'B 10 0 20 10',point(x,y),point(5,5),(x,y),line((x,y),(5,5)), 'I 1 0',count-2,count,0.)
    for i in range(3300):
        a,b=tile(i,i%5==0),tile(i//3,i%11==0)
        if i%4==0:
            p=row('R',rng.choice(limits),rng.choice(limits),rng.choice([1,2,3,0]));e=point(*rng.choices(limits,k=2))
        else:p=point(rng.randrange(-70,71),rng.randrange(-70,71));e=point(rng.randrange(-70,71),rng.randrange(-70,71))
        fp=rng.choices(reals,k=2) if i%5==0 else [rng.uniform(-70,70),rng.uniform(-70,70)]
        li=row(p,e) if i%3 else line(tuple(rng.choices(limits,k=2)),tuple(rng.choices(limits,k=2)))
        di=row('I',*rng.choices(limits if i%3 else [-1,0,1],k=2)) if i%9 else row('B',2**100+i,-2**99)
        yield query(a,b,p,e,fp,li,di,rng.choice([-2147483648,-3,-1,0,1,2,3,4,7,8,2147483647]),rng.choice([-2,0,1,2,3,4,8,12]),rng.choice(reals))
    # Actual shared edges, crossings, corner touches, parallel rays and exact fallback at tiny distances.
    for a in ['B 0 0 10 10','O N 0 0 10 10 -8 8 2 18',simple(rect),simple(general)]:
        for x,y in grid:
            for ex,ey in grid:
                yield query(a,'B 10 0 20 10',point(x,y),point(ex,ey),(x+2**-40,y-2**-40),line((x,y),(ex,ey)),'I -1 1',0,4,1.)
    # Quarter-turn normalization uses repeated +/-4 in the reference. Bound the
    # factors here instead of executing hundreds of millions of loop iterations.
    for i in range(1100):
        v=row('I',*rng.choices(limits,k=2)) if i%3 else row('R',rng.randrange(-20,21),rng.randrange(-20,21),2)
        yield row('T',tile(i,i%8==0),tile(i//3),v,*rng.choices(limits,k=2),rng.choice([-9,-4,-1,0,1,2,3,4,9]),bits(rng.choice(reals+[-100.,-3.5,3.5,100.])))
    # Bounded, small allocation requests; unbounded sections and memory exhaustion are excluded.
    for i in range(600):
        a=tile(i+1)
        if a in special:a=['B 10 10 0 0','B 0 0 0 0','O E',simple([])][i%4]
        yield row('D',a,bits(rng.choice([-1.,0.,2.,3.5,10.,1000.,math.inf,math.nan])))
    for i in range(700):
        points=[point(rng.choice(limits),rng.choice(limits)) for _ in range(rng.randrange(0,8))]
        if points and i%5==0:points[0]=row('R',rng.choice(limits),rng.choice(limits),2)
        lines=rng.choice([rect,triangle,general,[]]) if i%2 else [row(point(*rng.choices(limits,k=2)),point(*rng.choices(limits,k=2))) for _ in range(rng.randrange(0,15))]
        p=point(*rng.choices(limits,k=2)) if i%3 else row('R',*rng.choices(limits,k=2),3)
        yield row('F',len(lines),*lines,len(points),*points,p,*rng.choices(limits,k=12))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting';sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,key
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-tile-shape-') as tmp:
        helpers=[ROOT/'tests/oracle'/f for f in ['ExactCoordinatesOracle.java','RegularShapesOracle.java','SimplexOracle.java','TileShapeOracle.java']]
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.TileShapeOracle'],input='\n'.join(vectors)+'\n',text=True,timeout=120)
    rows=[r for r in generated.splitlines() if ' | ' in r];assert len(rows)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'categories':dict(Counter(r.split()[0] for r in rows)),'expected_fields':sum(len(r.split(' | ')[1].split()) for r in rows),'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
