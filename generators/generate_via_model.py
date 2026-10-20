#!/usr/bin/env python3
"""Qualify Via shapes, caches, copying and placement-side changes against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/via_model_java.tsv'
def cases():
 rng=random.Random(20261026)
 def op(c,a=0,b=0,d=0):return f'{c} {a} {b} {d}'
 base=[op(c) for c in ['r','F','L','N','W','B','R']]+[op('G',i) for i in [-1,0,1,2,3,4,5,6]]
 base += [op('f',i) for i in [-1,0,2,256,512,258,514,1023]]
 base += [op('e',1,2),op('x',72),op('C',1),op('G'),op('D'),op('G'),op('M',2,3),op('G'),op('P',0),op('G'),op('P',2),op('G'),op('D'),op('G'),op('Q',1,-2,4),op('G'),op('T',-5,6),op('G'),op('x',83)]
 for k in range(480):
  count=6;initial=rng.randrange(7);nc=rng.choice([-2,-1,0,1,3]);out=['V',str(rng.choice([0,0,0,1,2,4,3])),str(rng.randrange(5)),str(initial),str(rng.randrange(-1,4)),str(rng.randrange(2)),str(nc)]
  out += [str(rng.choice([0,1,2,49,10000001])) for _ in range(max(nc,0))];out.append(str(count))
  for i in range(count):
   n=rng.choice([-1,0,1,2,4,6]);out += [str(n),*[str(rng.randrange(8)) for _ in range(max(n,0))]]
  subset=rng.randrange(-1,9);out += [str(subset),*[str(rng.randrange(7)) for _ in range(max(subset,0))]]
  ops=base[:]
  for _ in range(30):
   code=rng.choice(['P','G','s','D','C','M','Q','T','F','L','B','W'])
   if code=='P':ops.append(op(code,rng.randrange(7)))
   elif code=='G':ops.append(op(code,rng.randrange(-1,7)))
   elif code=='s':ops.append(op(code,rng.randrange(1,7),rng.randrange(-1,7),rng.randrange(8)))
   elif code=='C':ops.append(op(code,rng.randrange(5)))
   else:ops.append(op(code,rng.randrange(-2,4),rng.randrange(-5,6),rng.randrange(-5,6)))
  out += [str(len(ops)),*ops];yield ' '.join(out)
 for k in range(400):
  def nets():
   n=rng.randrange(-1,5);return [str(n),*[str(rng.choice([0,1,2,49,10000001])) for _ in range(max(0,n))]]
  n=rng.randrange(1,11);out=['P',str(rng.randrange(2)),str(rng.choice([-1,-1,rng.randrange(1,n+1)*3+rng.randrange(3)])),*nets(),str(n)]
  for i in range(n):out += [str(rng.randrange(5)),str(rng.randrange(1,12)),str(rng.randrange(-1,5)),str(rng.randrange(-1,5)),str(rng.randrange(2)),*nets()]
  out.append('15')
  for i in range(15):
   count=rng.randrange(9);out += [str(rng.choices([0,1,2],[5,4,1])[0]),str(count),*[str(rng.randrange(n+1)) for _ in range(count)]]
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/items/DrillItem.java','board/model/items/Via.java','core/library/Padstack.java','core/library/BoardLibrary.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-via-model-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ViaModelOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.ViaModelOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
  if result.returncode:
   import sys
   sys.stderr.write(result.stderr);result.check_returncode()
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for expected,actual in zip(inputs,rows):assert actual.startswith(expected+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
