#!/usr/bin/env python3
"""Qualify personal PolygonShape decomposition, caches and Java Random behavior."""
import argparse,hashlib,json,math,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT,bits
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/polygon_shape_java.tsv'

def cases():
    rng=random.Random(20261001)
    def row(*a):return ' '.join(map(str,a))
    def ip(x,y):return row('I',x,y)
    def pts(a):return row(len(a),*a)
    shapes=[[],[ip(0,0)],[ip(0,0),ip(0,0)],[ip(0,0),ip(10,0)],
        [ip(0,0),ip(10,0),ip(10,10),ip(0,10)],
        [ip(0,0),ip(10,0),ip(10,3),ip(3,3),ip(3,10),ip(0,10)],
        [ip(0,0),ip(12,0),ip(12,12),ip(9,12),ip(9,3),ip(3,3),ip(3,12),ip(0,12)],
        [ip(0,0),ip(0,10),ip(5,5),ip(10,10),ip(10,0)],
        [ip(0,0),ip(10,10),ip(0,10),ip(10,0)]]
    queries=[ip(0,0),ip(3,3),ip(5,5),ip(10,10),ip(20,20),'R 7 7 2','R 1 1 0']
    for j,poly in enumerate(shapes):
      for rev in [False,True]:
        points=list(reversed(poly)) if rev else poly
        for i,q in enumerate(queries):
          yield row('Q',i%2,pts(points),q,bits(float(i)),bits(float(i)),ip(0,0),0,0,(i%5)-2,i-2,bits(0. if i%2 else 2.),-5,-5,15,15,ip(-1,0),ip(20,0))
          yield row('D',pts(points),q,bits(float(i)),bits(float(i)),'B 4 4 8 8',5,5,3)
    # Simple angularly ordered polygons with concavities, translations and rational corners.
    for i in range(500):
        n=rng.randrange(3,18);cx,cy=rng.randrange(-200,201),rng.randrange(-200,201)
        coords=[]
        for k in range(n):
            angle=2*math.pi*k/n;r=rng.randrange(20,100)
            coords.append((cx+round(r*math.cos(angle)),cy+round(r*math.sin(angle))))
        points=[ip(x,y) if i%7 else row('R',x*3+1,y*3+2,3) for x,y in coords]
        if i%3==0:points.reverse()
        if i%5==0:points.append(points[0])
        query=ip(cx,cy) if i%4 else row('R',cx*3+2,cy*3-1,3)
        yield row('Q',i%2,pts(points),query,bits(float(cx)),bits(float(cy)),ip(rng.randrange(-20,21),rng.randrange(-20,21)),cx,cy,rng.randrange(-5,6),rng.randrange(-2,n+2),bits(rng.choice([-0.,0.,-3.,3.,math.nan])),-1000,-1000,1000,1000,ip(cx-10,cy),ip(cx+10,cy))
        yield row('D',pts(points),query,bits(float(cx)),bits(float(cy)),'O N -10 -10 10 10 -15 15 -15 15',cx,cy,rng.randrange(0,30))
    # Bounded geometry-only pathological inputs do not invoke recursive decomposition.
    limits=[-2147483648,-2147483647,-33554432,-1,0,1,33554432,2147483646,2147483647]
    for i in range(300):
        points=[ip(*rng.choices(limits,k=2)) for _ in range(rng.randrange(0,15))]
        if i%4==0:points=[row('R',rng.randrange(-100,100),rng.randrange(-100,100),rng.choice([0,1,3])) for _ in range(rng.randrange(1,10))]
        yield row('Q',i%2,pts(points),ip(*rng.choices(limits,k=2)),bits(rng.choice([0.,math.nan,math.inf])),bits(-0.),ip(0,0),0,0,0,rng.choice(limits),bits(1.),*rng.choices(limits,k=4),ip(0,0),ip(1,1))
    # Exercise wrapping in the personal cached bounding-box rejection near int limits.
    for origin in [-2147483648,-2147483647,-33554432,33554432,2147483643,2147483646]:
        length=1 if origin==2147483646 else 3
        points=[ip(origin,origin),ip(origin+length,origin),ip(origin+length,origin+length),ip(origin,origin+length)]
        for q in [ip(origin,origin),ip(0,0),row('R',origin*3+1,origin*3+1,3),'R 1 1 0']:
            yield row('D',pts(points),q,bits(float(origin)),bits(float(origin)),'B -5 -5 5 5',origin,origin,1)
    bounds=[-2147483648,-1,0,1,2,3,4,7,8,16,31,64,65,1024,1073741824,1073741825,2147483647]
    for seed in [-9223372036854775808,-1,0,1,99,9223372036854775807]+[rng.randrange(-2**63,2**63) for _ in range(150)]:
        seq=bounds+[rng.choice(bounds) for _ in range(120)]
        yield row('R',seed,len(seq),*seq)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting';sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,key
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-polygon-') as tmp:
        helpers=[ROOT/'tests/oracle'/f for f in ['ExactCoordinatesOracle.java','RegularShapesOracle.java','SimplexOracle.java','TileShapeOracle.java','PolylineOracle.java','CircleOracle.java','PolygonShapeOracle.java']]
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'--add-opens=java.base/java.util=ALL-UNNAMED','-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.PolygonShapeOracle'],input='\n'.join(vectors)+'\n',text=True,timeout=240)
    rows=[r for r in generated.splitlines() if ' | ' in r];assert len(rows)==len(vectors)
    rejected=[r for r in rows if r.endswith(' | source_stack_overflow')]
    quarantine=ROOT/'validation/freerouting-port-14/java-stack-overflow-inputs.tsv'
    quarantine_text='\n'.join(rejected)+('\n' if rejected else '')
    if args.check:assert quarantine.read_text()==quarantine_text,'Reference stack-overflow cases changed'
    else:quarantine.write_text(quarantine_text)
    rows=[r for r in rows if not r.endswith(' | source_stack_overflow')]
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'attempted_cases':len(vectors),'unqualified_java_stack_overflow_cases':len(rejected),'categories':dict(Counter(r.split()[0] for r in rows)),'expected_fields':sum(len(r.split(' | ')[1].split()) for r in rows),'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
