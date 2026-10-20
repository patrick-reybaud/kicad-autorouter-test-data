#!/usr/bin/env python3
"""Record actual JVM comparison schedules, partial outputs and Freerouting Simplex factories."""
import argparse,hashlib,json,os,random,subprocess,tempfile,zipfile
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/line_sort_java.tsv'

def cases():
    rng=random.Random(20260924)
    def point(x,y):return f'I {x} {y}'
    def line(x,y,dx,dy):
        wrap=lambda v:(v+2**31)%2**32-2**31
        return point(x,y)+' '+point(wrap(x+dx),wrap(y+dy))
    compass=[line(0,0,x,y) for x,y in [(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,-1),(1,-1),(0,0)]]
    near=[line(0,0,2147483647-i,2147483646-i) for i in range(12)]
    near += [line(0,0,-2147483647+i,-2147483646+i) for i in range(12)]
    limits=[-2147483648,-2147483647,-33554432,-1,0,1,33554432,2147483646,2147483647]
    def row(palette,order):return ' '.join(map(str,[len(palette),*palette,len(order),*order]))
    sizes=[0,1,2,3,15,16,17,30,31,32,33,47,63,64,65,119,120,121,127,128,129,255,256,257,511,512,513,1023,1024,1541,1542,1543,4096]
    for n in sizes:
        for palette in [compass[:8],compass,near]:
            for pattern in range(6):
                order=[rng.randrange(len(palette)) for _ in range(n)]
                if pattern==0:order=[i%len(palette) for i in range(n)]
                elif pattern==1:order=list(reversed([i%len(palette) for i in range(n)]))
                elif pattern==2:order=sorted(order)
                elif pattern==3:order=sorted(order,reverse=True)
                elif pattern==4:order=[(i//max(1,n//7))%len(palette) for i in range(n)]
                yield row(palette,order)
    # Undefined ordering in the comparator is deliberate: the JVM's behavior remains the oracle.
    for i in range(2200):
        n=rng.choice(sizes[8:]) if i%3 else rng.randrange(32,300)
        if i%4==0:palette=compass+near
        elif i%4==1:palette=compass
        elif i%4==2:palette=[line(*rng.choices(limits,k=4)) for _ in range(24)]+compass
        else:palette=[line(rng.randrange(-50,51),rng.randrange(-50,51),rng.randrange(-10,11),rng.randrange(-10,11)) for _ in range(24)]
        yield row(palette,[rng.randrange(len(palette)) for _ in range(n)])
    # Actual Line comparisons with non-transitive floating determinant ties.
    # Captured from the unchanged Java reference; exercises mergeLo contract failures.
    yield from (ROOT/'tests/oracle/line_sort_merge_lo_inputs.txt').read_text().splitlines()
    # ClassCastException may occur after mutations of a preceding run.
    for i in range(80):
        palette=compass+['R 1 2 3 I 1 0']
        n=rng.choice([2,31,32,64,127,512]);order=[rng.randrange(8) for _ in range(n)]
        order[rng.randrange(n)]=9
        yield row(palette,order)
    # Long runs, galloping, buffer growth and both large-stack-capacity boundaries.
    for n in [65536,119150,119151,160000]:
        for pattern in range(3):
            palette=compass[:8]
            if pattern==0:order=[(i//257)%8 for i in range(n)]
            elif pattern==1:order=[rng.randrange(8) for _ in range(n)]
            else:order=[(i//17)%8 for i in range(n)][::-1]
            yield row(palette,order)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting';sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,key
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    with zipfile.ZipFile(home/'lib/src.zip') as z: runtime_hash=hashlib.sha256(z.read('java.base/java/util/ComparableTimSort.java')).hexdigest()
    assert runtime_hash==manifest['runtime_dependencies']['java.util.Arrays.sort(Object[])']['reference_jvm_source_sha256'],'Reference JVM source changed'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-line-sort-') as tmp:
        helpers=[ROOT/'tests/oracle'/f for f in ['ExactCoordinatesOracle.java','LineSortOracle.java']]
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
        result=subprocess.run([str(home/'bin/java'),'-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.LineSortOracle'],input='\n'.join(vectors)+'\n',capture_output=True,text=True,check=True,timeout=120)
    rows=[r for r in result.stdout.splitlines() if ' | ' in r];assert len(rows)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    expected=[r.split(' | ')[1].split() for r in rows]
    stats={s:sum(fields[0]==s for fields in expected) for s in ['ok','invalid_argument','type_error','index_error']}
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'jdk_timsort_sha256':runtime_hash,'cases':len(rows),'expected_fields':sum(map(len,expected)),'comparisons':sum(int(f[1]) for f in expected),'status_counts':stats,'jvm_merge_failures':json.loads(result.stderr.strip().splitlines()[-1]),'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
