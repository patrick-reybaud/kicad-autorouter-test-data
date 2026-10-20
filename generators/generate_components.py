#!/usr/bin/env python3
"""Compare component placement, snapshots, instance replacement and observers to pinned Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/components_java.tsv'
def cases():
 rng=random.Random(20261028)
 def enc(s):return 'N' if s is None else s.encode('utf-16-be','surrogatepass').hex() or '-'
 names=[None,'','R1','r1','R2','C1','c1','Ω','ω','İ','i','ı','ß','SS','\U00010400','\U00010428']
 def bits(x):return struct.pack('>d',x).hex()
 def point():return [str(rng.choices([0,1,2],[1,6,3])[0]),str(rng.randrange(-100,101)),str(rng.randrange(-100,101)),str(rng.choice([1,2,3,5]))]
 def pole():return [str(int(rng.randrange(8)!=0)),str(rng.randrange(-20,21)),str(rng.randrange(-20,21))]
 def angle():return bits(rng.choice([-1080.25,-720.,-360.5,-0.,0.,12.5,45.,90.,359.999999999,360.,720.,float('nan')]))
 def add():return ['A',enc(rng.choice(names)),*point(),angle(),str(rng.randrange(2)),str(rng.randrange(3)),str(rng.randrange(3)),str(rng.randrange(2)),enc(rng.choice(names))]
 for kind,count in [('C',200),('H',240)]:
  for k in range(count):
   out=[kind];ops=[];added=0
   if kind=='C':out += [enc(rng.choice(names)),*point(),angle(),str(rng.randrange(2)),str(rng.randrange(3)),str(rng.randrange(3)),str(rng.choice([-2147483648,-1,0,1,2147483647])),str(rng.randrange(2)),enc(rng.choice(names))]
   else:
    for i in range(rng.randrange(1,9)):ops.append(add());added+=1
    ops.append(['S'])
   for j in range(28 if kind=='C' else 64):
    op=rng.choice(['M','T','B','L','H','Q'] if kind=='C' else ['M','M','T','B','L','H','A','G','S','S','U','R','I','N','F'])
    id=str(rng.choice([-1,0,1,max(1,added),added+1,2147483647,-2147483648]) if rng.randrange(6)==0 else rng.randrange(1,max(1,added)+1))
    if op=='A':a=add();added+=1
    elif op=='G':a=['G',*point(),angle(),str(rng.randrange(2)),str(rng.randrange(3))];added+=1
    elif op=='M':a=[op,id,*point()]
    elif op=='T':a=[op,id,str(rng.choice([-2147483648,2147483647,-7,-4,-1,0,1,2,3,4,5,8])),*pole()]
    elif op=='B':a=[op,id,*pole()]
    elif op=='L':a=[op,id,str(rng.randrange(3))]
    elif op=='H':a=[op,id,str(rng.randrange(2))]
    elif op in ['Q','N']:a=[op,enc(rng.choice(names))]
    elif op=='I':a=[op,id]
    elif op in ['U','R']:a=[op,str(rng.choice([0,1,1])),str(rng.choice([-1,-1,-1,1,2,3])),str(rng.randrange(3))]
    elif op=='F':a=[op,str(rng.randrange(2))]
    else:a=[op]
    ops.append(a)
   out.append(str(len(ops)))
   for a in ops:out+=a
   yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/structure/Component.java','board/model/structure/Components.java','datastructures/UndoableObjects.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-components-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ComponentsOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.structure.ComponentsOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
