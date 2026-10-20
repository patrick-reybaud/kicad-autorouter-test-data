#!/usr/bin/env python3
"""Execute pinned Java geometry for C++ differential fixtures (no Java needed by C++ QA)."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import random
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/planar_java.tsv'

def bits(x):
    return format(struct.unpack('>Q', struct.pack('>d', x))[0], 'x')

def cases():
    rng=random.Random(20260918)
    edges=[0.,-0.,.5,-.5,1.5,-1.5,2.5,-2.5,math.nextafter(.5,0),math.nextafter(.5,1),
           math.nextafter(-.5,-1),math.nextafter(-.5,0),2**31-1.,-2**31,2**32+1.,
           2**52-1.,2**52+1.,math.nextafter(float(2**63),0),float(2**63),-float(2**63),
           1e-300,-1e-300,1e300,-1e300,math.inf,-math.inf,math.nan]
    for a in edges:
        for b in edges: yield 'N '+bits(a)+' '+bits(b)
    for _ in range(1500):
        yield 'N '+' '.join(format(rng.getrandbits(64),'x') for _ in range(2))
    integers=[0,1,-1,2,-2,255,256,65536,2**30,2**31-1,-2**31]
    for a in integers:
        for b in integers: yield f'G {a} {b}'
    for _ in range(3000):
        yield f'G {rng.randint(-2**31,2**31-1)} {rng.randint(-2**31,2**31-1)}'
    for i in range(400):
        size=rng.choice([0,1,31,32,64,128,1024,4096])
        values=[rng.getrandbits(size)*rng.choice([-1,1]) for _ in range(6)]
        if i%2==0: values[5]=values[2]
        yield 'B '+' '.join(map(str,values))
    coords=[(0.,0.),(-0.,0.),(1.,0.),(0.,1.),(1.,1.),(-1.,-1.),(5.,12.),
            (33554432.,33554431.5),(1e-150,-1e-150),(1e150,-1e150),(1e308,1e308),
            (math.inf,0.),(math.nan,1.)]
    coords += [(rng.uniform(-1e6,1e6),rng.uniform(-1e6,1e6)) for _ in range(200)]
    for i,a in enumerate(coords):
        for j in range(5):
            b=a if j==0 else rng.choice(coords); c=rng.choice(coords); e=rng.choice(coords)
            h=rng.choice(edges) if j==4 else rng.uniform(-2,10)
            v=rng.choice(edges) if j==4 else rng.uniform(-2,10)
            yield 'P '+' '.join(map(bits,(*a,*b,*c,*e,h,v)))+f' {rng.randint(-16,16)} {rng.choice([0,-1,1,2,5,1000])} {rng.choice([0,-1,1,2,5,1000])}'
            yield 'T '+' '.join(map(bits,(*a,*b,h)))
            yield 'L '+' '.join(map(bits,(*a,*b,*c,*e,*rng.choice(coords),h)))+f' {rng.choice([-1,0,1,2,3,7])}'
    # Explicit geometry branches: parallel/reversed/degenerate segments, projection clamping,
    # critical coordinate cutoffs, tangency, either tangent stability orientation, overlapping boxes.
    for a,b,c,d in [((0.,0.),(10.,0.),(2.,1.),(8.,1.)),
                     ((0.,0.),(10.,0.),(-5.,1.),(15.,1.)),
                     ((0.,0.),(10.,0.),(20.,1.),(25.,1.)),
                     ((0.,0.),(10.,0.),(8.,1.),(2.,1.)),
                     ((0.,0.),(0.,10.),(1.,2.),(1.,8.)),
                     ((0.,0.),(0.,0.),(1.,1.),(1.,1.))]:
        for offset in [0.,-2.,2.,5.,10.,math.nan]:
            for p in [a,b,(5.,.01),(5.,10.),(-1.,-1.)]:
                yield 'L '+' '.join(map(bits,(*a,*b,*c,*d,*p,offset)))+' 3'
    for a in [(5.,0.),(0.,5.),(3.,4.),(0.,0.),(-5.,0.)]:
        for radius in [0.,1.,5.,6.,-1.]: yield 'T '+' '.join(map(bits,(*a,0.,0.,radius)))
    for i in range(700):
        values=rng.choices(integers,k=8) if i<100 else [rng.randint(-100000,100000) for _ in range(8)]
        h,v=rng.choices(edges,k=2) if i<100 else [rng.uniform(0,10),rng.uniform(0,10)]
        yield 'X '+' '.join(map(str,values))+' '+bits(h)+' '+bits(v)
    for i in range(150):
        a=rng.choice(coords); b=rng.choice(coords); angle=rng.choice([0.,-0.,.1,-.1,math.pi/2,math.pi,-math.pi])
        yield 'R '+' '.join(map(bits,(*a,*b,angle)))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true'); args=parser.parse_args()
    source_root=ROOT/'engine/src/main/java/app/freerouting'
    sources=sorted((source_root/'geometry/planar').glob('*.java'))
    sources += [source_root/'datastructures'/f'{name}.java' for name in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():
        assert manifest['source_files'][key]['sha256']==value,f'Reference changed: {key}'
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'))
    support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    assert support.is_file(), 'Build the reference engine JAR first for logger/interface dependencies'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-planar-oracle-') as tmp:
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,
                        *map(str,sources),str(ROOT/'tests/oracle/PlanarOracle.java')],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-cp',tmp+os.pathsep+str(support),
            'app.freerouting.geometry.planar.PlanarOracle'],input='\n'.join(vectors)+'\n',text=True)
    # Logger output, if enabled, must not silently enter the fixture.
    rows=[row for row in generated.splitlines() if ' | ' in row]
    assert len(rows)==len(vectors)
    rotation_rows=[row for row in rows if row.startswith('R ')]
    rows=[row for row in rows if not row.startswith('R ')]
    rotation='\n'.join(rotation_rows)+'\n'
    pending=ROOT/'validation/freerouting-port-2/rotation-reference.tsv'
    if args.check: assert pending.read_text()==rotation, 'Unported rotation reference changed'
    else: pending.write_text(rotation)
    generated='\n'.join(rows)+'\n'
    if args.check: assert DEST.read_text()==generated, 'Oracle differs from committed fixture'
    else: DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),
      'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),
      'validated_port_cases':len(rows),'unported_rotation_cases':len(rotation_rows),'fixture':str(DEST.relative_to(ROOT)),
      'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),
      'java_version':subprocess.run([str(home/'bin/java'),'-version'],text=True,capture_output=True,check=True).stderr},indent=2))

if __name__=='__main__':main()
