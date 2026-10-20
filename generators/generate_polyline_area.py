#!/usr/bin/env python3
"""Qualify PolylineArea holes, ordered clipping, caches and cooperative stop."""
import argparse,hashlib,json,math,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT,bits
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/polyline_area_java.tsv'

def cases():
    rng=random.Random(20261002)
    def row(*a):return ' '.join(map(str,a))
    shapes=['N','B 0 0 20 20','B 0 0 0 0','B 10 10 0 0','O E','O N 0 0 20 20 -15 15 5 35',
      'S R 0','S R 1 I 0 0 I 1 0','S R 2 I 0 0 I 1 0 I 1 0 I 0 0',
      'S R 3 I 0 0 I 20 0 I 20 0 I 0 20 I 0 20 I 0 0',
      'P 6 I 0 0 I 20 0 I 20 5 I 5 5 I 5 20 I 0 20']
    holes=['N','B 5 5 10 10','B 0 0 20 20','B 0 0 0 0','B 30 30 40 40','O E','O N 4 4 12 12 -6 6 10 20',
      'S R 0','S R 1 I 0 5 I 1 5','P 6 I 2 2 I 12 2 I 12 4 I 4 4 I 4 12 I 2 12']
    points=['I 0 0','I 5 5','I 7 7','I 10 10','I 20 20','I 30 30','R 15 15 2','R 1 1 0']
    for i,border in enumerate(shapes):
      for j,h in enumerate(holes+['EMPTY','NULL']):
        hs='0' if h=='EMPTY' else '-1' if h=='NULL' else row(1,h)
        for k,p in enumerate(points):
          yield row('Q',border,hs,p,bits(float(k)),bits(float(k)), 'I 0 0' if k%2 else 'I 2 -3',0,0,k%5-2,-100,-100,100,100)
        for threshold in [-1,0,1,2,5]:yield row('S',border,hs,threshold,-1,0)
    for i in range(200):
        x,y=rng.randrange(-100,101),rng.randrange(-100,101);w,h=rng.randrange(5,80),rng.randrange(5,80)
        border=row('B',x,y,x+w,y+h) if i%2 else row('P',6,'I',x,y,'I',x+w,y,'I',x+w,y+h//2,'I',x+w//2,y+h//2,'I',x+w//2,y+h,'I',x,y+h)
        hs=[]
        for k in range(rng.randrange(0,5)):
            a,c=rng.randrange(x-5,x+w+5),rng.randrange(y-5,y+h+5);hs.append(row('B',a,c,a+rng.randrange(0,20),c+rng.randrange(0,20)))
        hs=row(len(hs),*hs)
        yield row('S',border,hs,rng.choice([-1,0,1,2,3,10,20]),rng.choice([-1,1,2,5]),i%7==0 and 1 or 0)
        yield row('Q',border,hs,'I',x+w//2,y+h//2,bits(float(x)),bits(float(y)),rng.choice(['I 0 0','I 10 -10','R 0 0 1','R 1 2 3']),x,y,rng.randrange(-4,5),-1000,-1000,1000,1000)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting';sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum','Stoppable']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,key
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-area-') as tmp:
        helpers=[ROOT/'tests/oracle'/f for f in ['ExactCoordinatesOracle.java','RegularShapesOracle.java','SimplexOracle.java','TileShapeOracle.java','PolylineOracle.java','CircleOracle.java','PolygonShapeOracle.java','AreaOracle.java']]
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
        # Isolate source algorithms that do not return. The engine itself is unchanged.
        # One row at a time allows exact identification and restart without losing other cases.
        import queue, threading
        command=[str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.AreaOracle']
        rows=[];rejected=[];proc=None
        def start_process():
            p=subprocess.Popen(command,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,bufsize=1)
            q=queue.Queue()
            def read():
                for line in p.stdout:
                    if line.strip()=='ORACLE_READY' or ' | ' in line:q.put(line.rstrip())
                q.put(None)
            threading.Thread(target=read,daemon=True).start()
            assert q.get(timeout=20)=='ORACLE_READY'
            return p,q
        try:
            for vector in vectors:
                if proc is None:proc,output=start_process()
                proc.stdin.write(vector+'\n');proc.stdin.flush()
                try:result=output.get(timeout=3)
                except queue.Empty:
                    rejected.append(vector+' | source_timeout_3s')
                    proc.kill();proc.communicate();proc=None
                    continue
                assert result is not None,'Java oracle exited unexpectedly'
                assert result.startswith(vector+' | '),result
                rows.append(result)
        finally:
            if proc is not None:proc.stdin.close();proc.wait(timeout=10)
        quarantine=ROOT/'validation/freerouting-port-15/java-timeout-inputs.tsv'
        rejected_text='\n'.join(rejected)+('\n' if rejected else '')
        if args.check:assert quarantine.read_text()==rejected_text,'Source timeout cases changed'
        else:quarantine.write_text(rejected_text)
        generated='\n'.join(rows)+'\n'

    rows=[r for r in generated.splitlines() if ' | ' in r];assert len(rows)+len(rejected)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'attempted_cases':len(vectors),'unqualified_java_timeout_cases':len(rejected),'categories':dict(Counter(r.split()[0] for r in rows)),'expected_fields':sum(len(r.split(' | ')[1].split()) for r in rows),'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
