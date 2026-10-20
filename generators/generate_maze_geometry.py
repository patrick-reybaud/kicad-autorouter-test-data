#!/usr/bin/env python3
"""Run actual reference maze geometry and FloatLine endpoint choices."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct,math
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/maze_geometry_java.tsv'
def bits(v):return struct.pack('>d',v).hex()
def cases():
 rng=random.Random(20261121)
 coords=[(0.,0.),(-0.,0.),(0.,-0.),(10.,0.),(-10.,0.),(10.,20.),(10.,10.),(1e-150,-1e-150),(1e150,-1e150),(math.inf,0.),(math.nan,1.),(1073741823.,0.),(1073741822.,0.),(-1073741823.,-1073741824.)]
 for k in range(3600):
  if k<900:ps=rng.choices(coords,k=4)
  elif k<1500:ps=[(rng.uniform(-1000,1000),rng.uniform(-1000,1000)) for _ in range(4)]
  elif k<2100:ps=[(rng.uniform(-1000,1000),float(rng.randrange(3))) for _ in range(4)]
  elif k<2700:ps=[tuple(struct.unpack('>d',rng.getrandbits(64).to_bytes(8,'big'))[0] for _ in range(2)) for _ in range(4)]
  else:ps=rng.choices(coords[:9],k=4);ps[3]=ps[2]
  aliases=k%4 if k%5==0 else 0
  yield ' '.join(['P',*(bits(v) for p in ps for v in p),str(aliases)])
 for dim in [0,1,2,3]:
  for rooms in range(4):
   for shape in range(9):
    for angle in [-1,0,1,2]:
     for width in [0.,10.,math.nextafter(10.,math.inf),20.,25.,math.nan,math.inf]:
      yield ' '.join(map(str,['D',-1,-1,dim,rooms,shape,angle,bits(width)]))
 for k in range(360):
  yield ' '.join(map(str,['D',[-1,101,102][k%3],[-1,101,102][k//3%3],k//9%4,k//36%4,k%9,k%4-1,bits(float(k%35))]))
 edges=[0,-1,1,7,10,17,20,-2147483648,2147483647]
 for k in range(1200):
  yield ' '.join(map(str,['R',rng.choice([-1,201,202,203,204,205,206]),rng.choice([-1,201,202,203,204,205,206]),k%5,rng.choice([-1,0,1,2]),rng.choice([-2147483648,-1,0,1,10,2147483647]),rng.choice([-1,0,1,3]),rng.choice([-2147483648,-1,0,1,25,2147483647]),k%9,rng.choice(edges),rng.choice(edges)]))
 patterns=[[],[0],[1,2],[3,4],[4,3],[0,1,2,3,4],[-1,3],[3,-1],[1,4,-1,3],[0,1,0,2]]
 for k in range(660):
  p=patterns[k%len(patterns)];n=-1 if k%11==0 else len(p)
  yield ' '.join(map(str,['N',[-1,303,304,305,306][k%5],[-1,303,304,305,306][k//5%5],[-1,0,1,2][k%4],[-2147483648,-1,0,1,2147483647][k//4%5],[0,1,17,-1][k%4],n,*(p if n>=0 else [])]))
 for offset in [-33554433.,-33554432.,-33554431.,0.,33554431.,33554432.,33554433.]:
  for points in [[(2.,1.),(8.,1.),(0.,0.),(10.,0.)],[(-5.,1.),(15.,1.),(0.,0.),(10.,0.)],[(20.,1.),(25.,1.),(0.,0.),(10.,0.)],[(0.,0.),(0.,0.),(0.,0.),(0.,0.)],[(0.,0.),(10.,10.),(0.,10.),(10.,0.)],[(0.,0.),(1.,0.),(0.,-0.),(-0.,0.)]]:
   for reverse in range(4):
    ps=list(points)
    if reverse&1:ps[0],ps[1]=ps[1],ps[0]
    if reverse&2:ps[2],ps[3]=ps[3],ps[2]
    yield ' '.join(['P',*(bits(v) for x,y in ps for v in (x+offset,y)),'0'])
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting';sources=[base/'autoroute/maze/MazeSearchEngine.java',base/'geometry/planar/FloatLine.java'];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for k,h in hashes.items():assert manifest['source_files'][k]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-maze-geometry-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/MazeGeometryOracle.java'),str(ROOT/'tests/oracle/MazeGeometryTree.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx512m','-cp',tmp+os.pathsep+str(support),'app.freerouting.autoroute.maze.MazeGeometryOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
  if result.returncode:
   import sys
   sys.stderr.write(result.stderr);result.check_returncode()
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for a,b in zip(inputs,rows):assert b.startswith(a+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'case_types':{k:sum(x.startswith(k+' ') for x in inputs) for k in ['P','D','R','N']},'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
