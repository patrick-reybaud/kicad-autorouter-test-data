#!/usr/bin/env python3
"""Qualify BoardRules mutations, default selection and clearance renumbering against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct,itertools
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/board_rules_java.tsv'
def cases():
 rng=random.Random(20261030)
 def bits(x):return struct.pack('>d',x).hex()
 def enc(x):return 'N' if x is None else x.encode('utf-16-be').hex() or '-'
 names=[None,'','default','class1','class2','A','a','Ω','ω']
 values=[-2147483648,-1,0,1,2,3,4,5,100,100000,2147483647]
 for k in range(420):
  layers=rng.choice([0,1,2,4,6]);classes=rng.randrange(1,7);out=['R',str(layers),str(classes),*[str(rng.randrange(2)) for _ in range(layers)]]
  vias=rng.randrange(8);out.append(str(vias))
  for i in range(vias):
   count=rng.choice([-1,0,layers,layers+1]);out += [str(rng.randrange(-1,5)),str(count)]
   for j in range(count):out += [str(rng.randrange(4)),str(rng.choice([0,1,2,5,10,20,100,2147483647]))]
  count=rng.randrange(6);out.append(str(count))
  for i in range(count):out += [str(rng.randrange(-1,classes+2)),str(rng.choice([0,1,1,1]))]
  ops=[]
  # Exercise lazy defaults as well as append-before-default behavior.
  def op(o,a=0,b=0,c=0,d=0.,name=None):return [o,str(a),str(b),str(c),bits(d),enc(name)]
  ops.append(op(['D','N','A','C'][k%4],k%2,name=names[k%len(names)]))
  for j in range(75):
   o=rng.choice(['D','C','N','A','W','S','G','H','L','n','b','t','w','c','z','V','V','v','d','u','p','r','X','R','h','e','i','s','a','F','Q','I'])
   a=rng.randrange(-1,8);b=rng.randrange(-1,7);c=rng.choice(values)
   if o in ['N','A','i','s']:a=rng.randrange(2)
   if o in ['W','G']:a=rng.randrange(-1,layers+2)
   if o in ['S','h']:a=rng.choice(values)
   if o=='W':b=rng.choice(values)
   if o=='c':b=rng.randrange(6)
   if o=='a':a=rng.randrange(-1,3)
   if o=='F':a=rng.choice([0,0,0,1,2,3,4])
   if o in ['I','r']:a=rng.randrange(3)
   if o=='z':b=rng.randrange(2)
   ops.append(op(o,a,b,c,rng.choice([-0.,0.,1.,-1.,float('inf'),float('nan'),12.5]),rng.choice(names)))
  out.append(str(len(ops)))
  for o in ops:out+=o
  yield ' '.join(out)
 for layers in [2,4,6]:
  for widths in itertools.permutations([10,5,2]):
   for variant in [0,1]:
    out=['D',str(layers),'6',*['1']*layers,'8']
    for i,width in enumerate([*widths,2,3,4,30,40]):
     out += [str(1 if i<6 else 2),str(layers)]
     for j in range(layers):out += [str((1+variant) if i<4 and j in [0,layers-1] or i>=6 or i==4 and j==0 or i==5 and j==layers-1 else 0),str(width)]
    out += ['2','3','1','4','1']
    ops=[op('D'),op('W',0,1000),op('S',5),op('S',0),op('G',-1),op('n',name='N1'),op('n',name='N2'),op('w',0,layers-1,30),op('L',1),op('L',2),op('V',0,name='baseline'),op('d'),op('Q'),op('V',0,name='same'),op('d'),op('c',0,0,3),op('A',1,name='clone'),op('c',0,0,0),op('A',1,name='clone'),op('N',1,name='clone'),op('c',1,0,0),op('X',2,1),op('V',0,name='merged'),op('R',2),op('d'),op('X',2,1),op('R',2),op('Q'),op('v',name='baseline'),op('F',3),op('N',1,name='partial'),op('F',0),op('R',-1)]
    out.append(str(len(ops)))
    for o in ops:out+=o
    yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['rules/BoardRules.java','rules/DefaultItemClearanceClasses.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-board-rules-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/BoardRulesOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.rules.BoardRulesOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
