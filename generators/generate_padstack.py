#!/usr/bin/env python3
"""Qualify personal padstack geometry/lookup, exit directions and cached drill-radius parsing."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT,bits
from generate_clearance import text
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/padstack_java.tsv'
def cases():
 rng=random.Random(20261004)
 strings=[None,'','A','a','ABC','abc','i','İ','ı','Σ','ς','σ','ß','ẞ','𐐀','𐐨','\ud800','\udc00','\ud801','\udc28','\ud801\udc00','\ud801\udc28','\ud801X','\ud801\udc00X','X\udc00','𐐀a','𐐨A','\ue000','𐀀']
 for a in strings:
  for b in strings:yield 'F '+text(a)+' '+text(b)
 for _ in range(2000):
  a=''.join(chr(rng.randrange(0x110000)) for _ in range(rng.randrange(0,8)));b=a if rng.random()<.1 else ''.join(chr(rng.randrange(0x110000)) for _ in range(rng.randrange(0,8)));yield 'F '+text(a)+' '+text(b)
 names=[None,'','via','V_1.2:0.6','V_0:0.6','V_-12um:3um_x','V_1e2:3e1','1:2','V_1:0','V_1:','V_1:.','V_1:1.2.3','V_1:0,5','V_.5:.25','V_1.:2.','V_9999999999999999:0.3333333333333333333333333333333','V_'+('9'*400)+':'+('9'*400),'V_1:0.'+('0'*400)+'1','V_0.'+('0'*400)+'1:1']
 shapes=['N','B -10 -5 10 5','B -10 -10 10 10','B 10 10 -10 -10','B -2147483648 -1 2147483647 1','O E','O N -10 -5 10 5 -12 12 -12 12','S R 0','S R 1 I 0 0 I 1 0','S R 3 I 0 0 I 10 0 I 10 0 I 0 10 I 0 10 I 0 0','C 0 0 15']
 arrays=['-1','0']+['1 '+s for s in shapes]+['4 N '+shapes[1]+' '+shapes[6]+' N']
 factors=[0.,-1.,1.,1.5,2.,2.0000000000000004,float('nan'),float('inf'),-float('inf')]
 for i,name in enumerate(names):
  for j,array in enumerate(arrays):
   for layer in [-1,0,1,4]:yield f'Q {text(name)} {array} {i%2} {j%2} {layer} {bits(factors[(i+j)%len(factors)])}'
 for _ in range(700):
  outer=''.join(str(rng.randrange(10)) for _ in range(rng.randrange(1,30)));drill=''.join(str(rng.randrange(10)) for _ in range(rng.randrange(1,70)));position=rng.randrange(len(drill)+1);drill=drill[:position]+'.'+drill[position:];name='P_'+outer+':'+drill+'_test'
  size=rng.randrange(0,10000000);array=f'2 C 0 0 {size} B {-size} {-size//2} {size} {size//2}';yield f'Q {text(name)} {array} 1 0 0 {bits(rng.random()*4)}'
 for lc in [-1,0,1,2,6]:
  for repeat in range(20):
   ops=[]
   for i in range(12):
    op=rng.choice(['A','B','C']);name=rng.choice(names[:10]);array=rng.choice(arrays);shape=rng.choice(shapes);fr=rng.randrange(-2,8);to=rng.randrange(-2,8)
    ops.append(f'{op} {text(name)} {array} {shape} {fr} {to} {rng.randrange(-1,15)} {i%2} {(i//2)%2}')
   yield f'L {lc} {len(ops)} '+' '.join(ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]+[base/'core/library'/f'{n}.java' for n in ['Padstack','Padstacks']]+[base/'board/model/structure'/f'{n}.java' for n in ['Layer','LayerStructure']]
 hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-padstack-') as tmp:
  helpers=[ROOT/'tests/oracle'/f'{n}.java' for n in ['ExactCoordinatesOracle','RegularShapesOracle','SimplexOracle','TileShapeOracle','PolylineOracle','CircleOracle','PadstackOracle']]
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.geometry.planar.PadstackOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,check=True,timeout=120)
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for expected,actual in zip(inputs,rows):assert actual.startswith(expected+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
