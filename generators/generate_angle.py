#!/usr/bin/env python3
"""Qualify fdlibm acos, vector/direction angles and Polygon winding against unchanged Java."""
import argparse,hashlib,json,math,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT,bits
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/angle_java.tsv'

def cases():
    rng=random.Random(20260930)
    def row(*a):return ' '.join(map(str,a))
    limits=[-2147483648,-2147483647,-33554432,-2,-1,0,1,2,33554432,2147483646,2147483647]
    def point(i):
        if i%4:return row('I',*rng.choices(limits,k=2))
        scale=1<<rng.choice([0,31,63,255,1023,2048])
        return row('R',rng.randrange(-10,11)*scale,rng.randrange(-10,11)*scale,rng.choice([0,1,2,3,scale]))
    # Domain edges, branch word thresholds, subnormals, arbitrary NaNs and raw binary64 values.
    import struct
    for raw in [0,0x3c60000000000000,0x3fe0000000000000,0x3ff0000000000000,0x7ff0000000000000,0x7ff8000000000000]:
        for delta in range(-16,17):
            if 0<=raw+delta<2**63:
                for sign in [0,1<<63]:yield row('A',format((raw+delta)|sign,'016x'))
    for i in range(50000):yield row('A',format(rng.getrandbits(64),'016x'))
    for i in range(10000):yield row('A',bits(rng.uniform(-1,1)))
    vecs=[row('I',x,y) for x in [-1,0,1] for y in [-1,0,1]]
    vecs += ['R 0 0 0','R 1 2 0','R 1 2 -3','R 1 2 3']
    for a in vecs:
        for b in vecs:yield row('V',a,b)
    for i in range(6000):yield row('V',point(i),point(i+1))
    for i in range(1500):
        p=point(i).split();yield row('D',*(['B']+p[1:3] if p[0]=='R' else p))
    shapes=[[],['I 0 0'],['I 0 0','I 0 0'],['I 0 0','I 1 0'],['I 0 0','I 5 0','I 5 5','I 0 5'],['I 0 0','I 5 5','I 0 5','I 5 0']]
    for pts in shapes:
        for rev in [False,True]:
            a=list(reversed(pts)) if rev else pts
            yield row('P',len(a),*a)
    for i in range(1500):
        pts=[point(i+k) for k in range(rng.randrange(0,30))]
        if i%3==0 and pts:pts.append(pts[0])
        yield row('P',len(pts),*pts)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting';sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,key
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-angle-') as tmp:
        helpers=[ROOT/'tests/oracle'/f for f in ['ExactCoordinatesOracle.java','AngleOracle.java']]
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.AngleOracle'],input='\n'.join(vectors)+'\n',text=True,timeout=240)
    rows=[r for r in generated.splitlines() if ' | ' in r];assert len(rows)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'categories':dict(Counter(r.split()[0] for r in rows)),'expected_fields':sum(len(r.split(' | ')[1].split()) for r in rows),'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
