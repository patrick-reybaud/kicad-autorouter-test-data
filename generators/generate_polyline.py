#!/usr/bin/env python3
"""Qualify Polyline/LineSegment/Polygon and cutout using unchanged personal Java."""
import argparse,hashlib,json,math,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT,bits
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/polyline_java.tsv'

def cases():
    rng=random.Random(20260927)
    def row(*xs):return ' '.join(map(str,xs))
    def pt(x,y):return row('I',(x+2**31)%2**32-2**31,(y+2**31)%2**32-2**31)
    def poly(ps,mode='P'):return row(mode,len(ps),*[pt(*p) for p in ps])
    def lines(ls):return row('L',len(ls),*[row(pt(*a),pt(*b)) for a,b in ls])
    def two(a,b):return row('D',pt(*a),pt(*b))
    paths=[[],[(0,0)],[(0,0),(0,0)],[(0,0),(10,0)],[(0,0),(10,0),(20,0)],[(0,0),(10,0),(0,0)],
        [(0,0),(10,0),(10,10)],[(0,0),(10,0),(10,10),(0,10),(0,0)],
        [(0,0),(3,0),(3,2),(1,2),(1,1),(2,1)],[(0,0),(10,0),(9,1),(11,2),(10,3),(10,8)],
        [(0,0),(10,1),(9,2),(10,3),(9,4),(10,5)],[(0,0),(2,7),(-4,5),(4,0)],
        [(1,1),(1,1),(1,1),(2,2),(3,3),(2,2),(1,1)],
        [(-15,5),(5,5),(5,15),(15,15),(15,5),(5,5),(5,-5),(-5,-5),(-5,5),(15,5)]]
    special=[poly(ps) for ps in paths]+[poly(ps,'G') for ps in paths]
    special += [two((0,0),(0,0)),two((0,0),(10,0)),two((0,0),(13,7)),
        lines([]),lines([((0,0),(0,1))]),lines([((0,0),(0,1)),((0,0),(1,0))]),
        lines([((0,0),(0,1)),((0,0),(1,0)),((10,0),(10,1))]),
        lines([((0,0),(0,0)),((0,0),(1,0)),((0,0),(0,1)),((2,0),(2,1))]),
        'P 4 R 1 1 3 R 7 3 2 R -4 11 5 I 10 10', 'D R 1 1 3 R 7 3 2']
    limits=[-2147483648,-2147483647,-33554433,-33554432,-1,0,1,33554432,33554433,2147483646,2147483647]
    floats=[-math.inf,-1e20,-1.,-0.,0.,.5,1.,1e20,math.inf,math.nan]
    def anypoly(i):
        if i%4==0:return rng.choice(special)
        pts=[(rng.randrange(-30,31),rng.randrange(-30,31)) for _ in range(rng.randrange(0,12))]
        if i%13==0:pts=[tuple(rng.choices(limits,k=2)) for _ in range(rng.randrange(2,7))]
        if i%4==1:return poly(pts)
        if i%4==2:return poly(pts,'G')
        return lines([(p,(p[0]+rng.randrange(-2,3),p[1]+rng.randrange(-2,3))) for p in pts])
    rect='S R 4 I 0 0 I 10 0 I 10 0 I 10 10 I 10 10 I 0 10 I 0 10 I 0 0'
    tiles=['B 0 0 10 10','B 0 0 0 10','B 0 0 0 0','B 10 10 0 0','O E',
           'O N 0 0 10 10 -8 8 2 18',rect,'S R 3 I 0 0 I 10 0 I 10 0 I 0 10 I 0 10 I 0 0',
           'S R 0','S R 1 I 0 0 I 1 0','S R 2 I 0 0 I 1 0 I 1 10 I 0 10']
    for ps in paths:yield row('G',poly(ps)[2:])
    for i in range(800):
        vals=[rng.choice(limits) if i%3==0 else rng.randrange(-5,6) for _ in range(2*rng.randrange(0,15))]
        pts=[row('R',vals[k],vals[k+1],rng.choice([1,2,3,0])) if i%7==0 else pt(vals[k],vals[k+1]) for k in range(0,len(vals),2)]
        yield row('G',len(pts),*pts)
    def query(a,i):
        idx=rng.randrange(-2,15);fr=rng.randrange(-2,16);to=rng.randrange(-2,16)
        p=row('R',*rng.choices(limits,k=2),rng.choice([0,1,3])) if i%7==0 else pt(rng.randrange(-20,21),rng.randrange(-20,21))
        fp=rng.choices(floats,k=2) if i%9==0 else [rng.uniform(-40,40),rng.uniform(-40,40)]
        v='I 0 0' if i%4==0 else row('R',1,-1,3) if i%6==0 else pt(rng.randrange(-10,11),rng.randrange(-10,11))
        return row('Q',a,p,*map(bits,fp),v,0,0,rng.randrange(-9,10),idx,fr,to,rng.choice([-2147483648,-5,-1,0,1,2,5,10,2147483647]),bits(rng.choice(floats+[2.,5.,10.])))
    for i,a in enumerate(special):
        for _ in range(5):yield query(a,i)
    for i in range(2200):yield query(anypoly(i),i)
    for a in paths[3:]:
        for b in paths[3:]:
            for idx in [-1,0,1,2,3,8]:yield row('B',poly(a),poly(b),idx,pt(5,-10),pt(5,20))
    for i in range(700):yield row('B',anypoly(i),anypoly(i//2),rng.randrange(-1,12),pt(rng.randrange(-10,11),-10),pt(rng.randrange(-10,11),20))
    def segment(i):
        if i%5==0:return row('T',rng.choice(tiles),rng.randrange(-2,10))
        if i%5 in [1,2]:return row('P',anypoly(i),rng.randrange(-2,10))
        if i%5==3:
            pts=[pt(*rng.choices(limits,k=2)) if i%3==0 else row('R',rng.randrange(-100,101),rng.randrange(-100,101),rng.choice([0,1,3,7])) for _ in range(6)]
            return row('R',*pts)
        return row('P',two((rng.randrange(-15,16),rng.randrange(-15,16)),(rng.randrange(-15,16),rng.randrange(-15,16))),1)
    grid=[(-5,0),(-5,5),(0,0),(0,5),(0,10),(5,-5),(5,0),(5,5),(5,10),(5,15),(10,0),(10,5),(10,10),(15,5)]
    for a in grid:
        for b in grid:
            sa=row('P',two(a,b),1)
            yield row('S',sa,row('P',two((0,5),(10,5)),1),pt(5,5),rng.choice(tiles[:8]),bits(3.5))
    for i in range(1600):yield row('S',segment(i),segment(i+2),pt(0,0) if i%2 else 'R 0 0 1',rng.choice(tiles),bits(rng.choice(floats+[3.5])))
    for x in [-13,-7,-1,0,1,7,13]:
        for y in [-13,-7,-1,0,1,7,13]:
            for width in [-5.,-1.,-0.,.25,1.,2.5,5.,50.,math.inf,math.nan]:
                for right in [0,1]:yield row('W','P',two((2,-3),(2+x,-3+y)),1,bits(width),right)
    for i in range(500):
        a=(rng.randrange(-100,101),rng.randrange(-100,101));b=(rng.randrange(-100,101),rng.randrange(-100,101))
        yield row('W','P',two(a,b),1,bits(rng.choice([-2.,0.,.25,1.,3.,10.])),i%2)
    for tile in tiles:
        for a in grid:
            for b in grid:yield row('C',tile,two(a,b))
        for a in special:yield row('C',tile,a)
    for i in range(600):yield row('C',rng.choice(tiles),anypoly(i))

    for i in range(600):yield row('A',anypoly(i),segment(i),rng.randrange(-2,15))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting';sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,key
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-polyline-') as tmp:
        helpers=[ROOT/'tests/oracle'/f for f in ['ExactCoordinatesOracle.java','RegularShapesOracle.java','SimplexOracle.java','TileShapeOracle.java','PolylineOracle.java']]
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.PolylineOracle'],input='\n'.join(vectors)+'\n',text=True,timeout=240)
    rows=[r for r in generated.splitlines() if ' | ' in r];assert len(rows)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'categories':dict(Counter(r.split()[0] for r in rows)),'expected_fields':sum(len(r.split(' | ')[1].split()) for r in rows),'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
