#!/usr/bin/env python3
"""Qualify circles and fixed-direction bounds against unchanged personal Java."""
import argparse,hashlib,json,math,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT,bits
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/circle_java.tsv'

def cases():
    rng=random.Random(20260928)
    def row(*a):return ' '.join(map(str,a))
    def pt(x,y):return row('I',x,y)
    limits=[-2147483648,-2147483647,-1073741825,-1073741824,-33554433,-33554432,-2,-1,0,1,2,33554432,33554433,1073741823,1073741824,2147483646,2147483647]
    offsets=[-math.inf,-1e300,-float(2**63),-float(2**32),-float(2**31),-5.,-2.5,-1.5,-.5,math.nextafter(-.5,-1),math.nextafter(-.5,0),-0.,0.,math.nextafter(.5,0),.5,math.nextafter(.5,1),1.5,2.5,5.,float(2**31-1),float(2**31),float(2**32),float(2**52),float(2**63),1e300,math.inf,math.nan]
    special=['B 0 0 10 10','B 0 0 0 0','B 0 0 0 10','B 10 10 0 0','O E','O R 0 0 10 10 -30 30 -30 30','O N 0 0 10 10 -8 8 2 18',
       'S R 0','S R 1 I 0 0 I 1 0','S R 2 I 0 1 I 1 1 I 1 0 I 0 0',
       'S R 2 I 0 0 I 1 0 I 1 0 I 0 0',
       'S R 4 I 0 0 I 10 0 I 10 0 I 10 10 I 10 10 I 0 10 I 0 10 I 0 0',
       'S R 3 I 0 0 I 10 0 I 10 0 I 0 10 I 0 10 I 0 0',
       'S R 4 I 0 0 I 10 1 I 10 1 I 2 8 I 2 8 I -3 4 I -3 4 I 0 0',
       'S R 1 I 0 0 I 0 0',
       'S R 3 R 1 2 3 R 5 6 7 I 0 1 I 1 0 I 2 0 I 0 2']
    def tile(i):
        if i%4==0:return rng.choice(special)
        x,y=rng.randrange(-40,41),rng.randrange(-40,41);w,h=rng.randrange(0,41),rng.randrange(0,41)
        if i%4==1:return row('B',*rng.choices(limits,k=4)) if i%7==0 else row('B',x,y,x+w,y+h)
        if i%4==2:return row('O',rng.choice(['R','N']),*rng.choices(limits,k=8)) if i%7==0 else row('O','N',x,y,x+w,y+h,x-y-h+2,x+w-y-2,x+y+2,x+w+y+h-2)
        points=[(x,y),(x+w,y+1),(x+w-1,y+h),(x-1,y+h-1)]
        if i%7==0:points=[tuple(rng.choices(limits,k=2)) for _ in range(4)]
        return row('S','R',4,*[row(pt(*points[j]),pt(*points[(j+1)%4])) for j in range(4)])
    # Integer radii, negative-radius wrapping, exact tangencies, shrink/offset/enlarge distinction.
    for radius in limits+[3,4,5,7,10,1000]:
        for offset in offsets:
            for px,py in [(0,0),(3,4),(5,0),(6,0)]:
                yield row('Q',0,0,radius,pt(px,py),bits(px),bits(py),'I 0 0',0,0,0,bits(offset),-5,-5,5,5)
    for i in range(2200):
        x,y,r=rng.choices(limits,k=3) if i%3==0 else [rng.randrange(-1000,1001),rng.randrange(-1000,1001),rng.randrange(-100,101)]
        point=pt(*rng.choices(limits,k=2)) if i%3 else row('R',rng.randrange(-10**30,10**30),rng.randrange(-10**30,10**30),rng.choice([0,1,2,3]))
        fp=rng.choices(offsets,k=2) if i%4==0 else [rng.uniform(-1100,1100),rng.uniform(-1100,1100)]
        v=row('R',rng.randrange(-10,11),rng.randrange(-10,11),rng.choice([0,1,2])) if i%3==0 else pt(*rng.choices(limits,k=2))
        yield row('Q',x,y,r,point,*map(bits,fp),v,*rng.choices(limits,k=2),rng.randrange(-9,10),bits(rng.choice(offsets)),*rng.choices(limits,k=4))
    # Circle/circle and all tile overloads, including degenerate/unbounded shapes.
    grid=[(-5,5),(0,0),(0,5),(5,5),(5,10),(10,10),(15,5)]
    for t in special:
        for x,y in grid:
            for r in [-2147483648,-10,-1,0,1,5,10,2147483647]:yield row('I',x,y,r,5,5,5,t)
    for i in range(1800):
        a=rng.choices(limits,k=6) if i%3==0 else [rng.randrange(-70,71) for _ in range(6)]
        yield row('I',*a,tile(i))
    for t in special:
        for dirs in [4,8]:yield row('B',t,0,0,5,dirs)
    for i in range(1600):yield row('B',tile(i),*rng.choices(limits,k=3),4 if i%2 else 8)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting';sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,key
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-circle-') as tmp:
        helpers=[ROOT/'tests/oracle'/f for f in ['ExactCoordinatesOracle.java','RegularShapesOracle.java','SimplexOracle.java','TileShapeOracle.java','PolylineOracle.java','CircleOracle.java']]
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.CircleOracle'],input='\n'.join(vectors)+'\n',text=True,timeout=240)
    rows=[r for r in generated.splitlines() if ' | ' in r];assert len(rows)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'categories':dict(Counter(r.split()[0] for r in rows)),'expected_fields':sum(len(r.split(' | ')[1].split()) for r in rows),'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
