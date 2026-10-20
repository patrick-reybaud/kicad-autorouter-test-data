#!/usr/bin/env python3
"""Pinned Java rotation edge cases, independent of native results. --check never rewrites expectations."""
import argparse,hashlib,json,math,random,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')
build=root/'build/rotation-model';val=root/'validation/freerouting-port-72-rotation'
fixture=root/'upstream/kicad/qa/data/pcbnew/autorouter/rotation_model_java.tsv'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def bits(x):return struct.pack('>d',x).hex()
def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    build.mkdir(exist_ok=True);val.mkdir(exist_ok=True)
    flips=json.loads((root/'validation/freerouting-port-68-A/decision-flips.json').read_text())
    angles=[x['input'].split()[2] for x in flips if any(k!='raw' for k in x['changes'])]
    assert len(angles)==28
    angles += [bits(x) for x in [0.,-0.,math.pi/2,math.pi,2*math.pi,1e-300,1e300,-1e300]]
    rng=random.Random(26091972)
    angles += [bits(rng.uniform(-2*math.pi,2*math.pi)) for _ in range(200)]
    inp=build/'input.txt';inp.write_text(''.join(f'{k} {a} {pole}\n' for a in angles for k in range(12) for pole in range(3)))
    jar=root/'engine/build/libs/freerouting-current-executable.jar'
    # Compile the actual pinned source geometry, before the jar, never candidate-modified copies.
    source=root/'engine/src/main/java/app/freerouting'
    sources=sorted((source/'geometry/planar').glob('*.java'))+[source/'datastructures/BigIntAux.java',source/'datastructures/Signum.java']
    manifest=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(s.relative_to(root/'engine')):sha(s) for s in sources}
    for path,h in hashes.items():assert manifest['source_files'][path]['sha256']==h
    cmds=[[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*map(str,sources),str(root/'tests/oracle/RotationModelOracle.java')],
          [str(java/'bin/java'),'-cp',f'{build}:{jar}','app.freerouting.geometry.planar.RotationModelOracle',str(inp),str(build/'reference.tsv')]]
    with (val/'model-java.log').open('w') as log:
        for cmd in cmds:subprocess.run(cmd,check=True,stdout=log,stderr=subprocess.STDOUT)
    result=build/'reference.tsv'
    assert len(result.read_text().splitlines())==8496
    if args.check:assert fixture.read_bytes()==result.read_bytes()
    else:fixture.write_bytes(result.read_bytes())
    (val/'model-oracle.json').write_text(json.dumps({'reference_commit':'97ba40483e578e8a116aba7a300a1eee591e5f26','cases':8496,'known_flip_angles':28,'input_sha256':sha(inp),'fixture_sha256':sha(fixture),'sources':hashes,'commands':cmds,'tolerances':[]},indent=2)+'\n')
if __name__=='__main__':main()
