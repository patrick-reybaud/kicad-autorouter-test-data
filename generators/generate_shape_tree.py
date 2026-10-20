#!/usr/bin/env python3
"""Execute unchanged personal ShapeTree/MinAreaTree/ArrayStack and the real JVM TreeSet."""
import argparse,hashlib,json,math,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/shape_tree_java.tsv'

def cases():
    rng=random.Random(20260929)
    def row(*x):return ' '.join(map(str,x))
    def obj(key,shapes,count=None,fail=-1,setfail=0):return row(key,len(shapes) if count is None else count,fail,setfail,len(shapes),*shapes)
    def scenario(objects,ops,mode=0):return row('T',mode,len(objects),*objects,len(ops),*ops)
    allq='B -1000000 -1000000 1000000 1000000'
    tiles=['N','B 0 0 10 10','B 2 2 8 8','B 10 0 20 10','B 0 0 0 0','B 10 10 0 0','O E',
      'O N 0 0 10 10 -8 8 2 18','O R 0 0 10 10 -30 30 -30 30',
      'S R 0','S R 1 I 0 0 I 1 0','S R 2 I 0 1 I 1 1 I 1 0 I 0 0',
      'S R 4 I 0 0 I 10 0 I 10 0 I 10 10 I 10 10 I 0 10 I 0 10 I 0 0',
      'S R 3 I 0 0 I 10 1 I 10 1 I 2 8 I 2 8 I 0 0']
    queries=[allq,'B 100 100 110 110','B 0 0 0 0','B 10 0 10 10','B 2 2 8 8','B 10 10 0 0','O E','O N 0 0 10 10 -8 8 2 18','N']
    limits=[-2147483648,-2147483647,-1073741824,-1,0,1,33554432,1073741824,2147483646,2147483647]
    for mode in [0,1,2]:
      yield scenario([],['Q 0 N','V 1 2 0 N','N 0','X 1'],mode)
      for shape in tiles:
        objects=[obj(3,[shape]),obj(3,['B 2 2 8 8']),obj(-1,[]),obj(0,['B 0 0 1 1'],count=-1)]
        ops=['I 0 0','Q 0 '+allq,'I 0 1','I 1 0','I 1 1','I 0 2','I 1 3']
        ops+=['Q 0 '+q for q in queries]
        ops+=['V 0 0 0 '+allq,'V 0 1 1 '+allq,'V 1 2 0 '+allq,'R 0 0','R 1 1','Q 0 '+allq,'R 0 1','R 1 0','I 0 0','Q 0 '+allq,'V 0 3 0 '+allq]
        yield scenario(objects,ops,mode)
    for i in range(150):
        n=rng.randrange(2,7);objects=[]
        for k in range(n):
            shapes=[]
            for _ in range(rng.randrange(0,4)):
                if i%3==0:shapes.append(rng.choice(tiles))
                elif i%5==0:shapes.append(row('B',*rng.choices(limits,k=4)))
                else:
                    x,y=rng.randrange(-30,31),rng.randrange(-30,31);w,h=rng.randrange(0,20),rng.randrange(0,20)
                    shapes.append(row('B',x,y,x+w,y+h))
            objects.append(obj(rng.choice(limits if i%3 else [-1,0,1]),shapes))
        order=list(range(n));rng.shuffle(order);ops=[]
        for k in order:ops += [row('I',0,k),row('I',1,k),row('Q',k%2,rng.choice(queries))]
        ops += ['V 0 0 0 '+allq,'V 1 1 2 '+allq,'V 0 2 0 '+rng.choice(queries)]
        rng.shuffle(order)
        for k in order:ops += [row('R',0,k),row('Q',0,rng.choice(queries))]
        ops+=['Q 0 N','V 1 3 0 '+allq,'X 0','N 1']
        yield scenario(objects,ops,i%3)
    # Partial insertion remains in the tree if a callback or shape index fails.
    for fail in [-1,0,1,2,3]:
      for setfail in [0,1]:
        objects=[obj(1,['B 0 0 2 2','N','B 3 3 5 5'],count=4 if fail==3 else 3,fail=fail,setfail=setfail)]
        yield scenario(objects,['I 0 0','Q 0 '+allq,'I 1 0','V 1 0 0 '+allq,'X 0'])
    # Duplicate insertion and partial per-leaf removal exercise retained entry identities.
    yield scenario([obj(5,['B 0 0 1 1','B 2 0 3 1'])],['I 0 0','I 0 0','Q 0 '+allq,'V 0 0 0 '+allq,'L 0 0 0','Q 0 '+allq,'L 0 0 1','Q 0 '+allq,'V 0 3 0 '+allq])
    # Single-leaf depth throws in the original; repeated removal from an empty tree underflows size.
    yield scenario([obj(1,['B 0 0 1 1'])],['I 0 0','R 0 0','R 0 0','Q 0 '+allq])
    for n in [70,257,1025]:
      for same in [False,True]:
        shapes=[row('B',0 if same else k*3,0,1 if same else k*3+1,1) for k in range(n)]
        ops=['I 0 0','Q 0 '+allq,'V 0 0 0 '+allq,'V 0 3 0 '+allq,'Q 0 '+allq]
        # Rebuild the deepest trees so native scope teardown also sees live skewed nodes.
        if n==1025:ops+=['I 0 0']
        yield scenario([obj(0,shapes)],ops)
    # Sorted-set runtime: all comparison calls, stable duplicate representatives, exceptions,
    # both rotations, and deliberately nontransitive/wrapping comparators.
    for mode in [0,1,2]:
      for n in [0,1,2,3,7,16,31,32,64,128,257,1025]:
        for style in range(4):
          keys=list(range(n)) if style==0 else list(reversed(range(n))) if style==1 else [0]*n if style==2 else rng.choices(limits,k=n)
          yield row('S',mode,-1,n,*keys)
    for i in range(450):
        n=rng.randrange(0,150);keys=rng.choices(limits,k=n) if i%3 else [rng.randrange(-150,151) for _ in range(n)]
        yield row('S',i%3,rng.choice([-1,1,2,3,4,7,10,30,100]),n,*keys)
    for capacity in [-1,0,1,2,4,63,64,65,10000]:
        ops=['O','P N','O','O','R','P 9','P -1','O','R','O','P 2']
        yield row('A',capacity,len(ops),*ops)
    for i in range(80):
        ops=[row('P',rng.choice(limits+['N'])) for _ in range(rng.randrange(0,90))]
        for _ in range(40):ops.append(rng.choice(['O','R',row('P',rng.choice(limits+['N']))]))
        yield row('A',rng.choice([0,1,2,3,4,7,16,64]),len(ops),*ops)
    for a in limits:
      for b in limits:
        yield row('L',a,b,rng.choice(limits),rng.choice(limits))
        yield row('L',a,a,rng.choice(limits),rng.choice(limits))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    base=ROOT/'engine/src/main/java/app/freerouting';sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum','ArrayStack','ShapeTree','MinAreaTree']]
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    for key,value in hashes.items():assert manifest['source_files'][key]['sha256']==value,key
    home=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'));support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(cases())
    with tempfile.TemporaryDirectory(prefix='freerouting-tree-') as tmp:
        helpers=[ROOT/'tests/oracle'/f for f in ['ExactCoordinatesOracle.java','RegularShapesOracle.java','SimplexOracle.java','TileShapeOracle.java','PolylineOracle.java','CircleOracle.java','TreeOracle.java']]
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.TreeOracle'],input='\n'.join(vectors)+'\n',text=True,timeout=240)
    rows=[r for r in generated.splitlines() if ' | ' in r];assert len(rows)==len(vectors)
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'categories':dict(Counter(r.split()[0] for r in rows)),'expected_fields':sum(len(r.split(' | ')[1].split()) for r in rows),'fixture':str(DEST.relative_to(ROOT)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
