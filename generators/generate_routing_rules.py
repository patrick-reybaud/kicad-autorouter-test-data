#!/usr/bin/env python3
"""Qualify ViaInfo/ViaInfos/ViaRule and NetClass/NetClasses against the personal source."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT,bits
from generate_clearance import text,layer
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/routing_rules_java.tsv'
def cases():
 rng=random.Random(20261005);names=[None,'','A','a','class1','class2','Class1','signal','SIGNAL','İ','ı','𐐀','𐐨','\ud800','\ue000'];values=[-2147483648,-1,0,1,9,350,2147483647]
 def vop(op,i=0,j=1,k=1,v=0,name=None):return f'{op} {i} {j} {k} {v} {text(name)}'
 base=[vop('A',1),vop('A',2),vop('A',3),vop('U',1),vop('U',2),vop('U',1),vop('S',1,2),vop('S',1,1),vop('U',0),vop('Y',0),vop('Z',0),vop('L',0,1,5),vop('X',0),vop('L',0,1,5),vop('U',4),vop('Z',0),vop('L',1,1,3),vop('P',4,5),vop('L',1,1,3),vop('N',2,name='A'),vop('A',3),vop('R',1),vop('A',3),vop('A',4),vop('H',name=None),vop('E',name='no'),vop('D',6,1)]
 yield f'V {len(base)} '+' '.join(base)
 for _ in range(260):
  ops=[]
  for k in range(70):
   op=rng.choice(['N','P','C','T','D','A','R','G','H','E','U','X','Y','Z','L','S','V']);i=rng.randrange(7);j=rng.randrange(7) if op in ['P','D'] else rng.randrange(1,3);rule=rng.randrange(1,3)
   if op=='S':j=rng.randrange(7)
   ops.append(vop(op,i,j,rng.randrange(-1,8) if op=='L' else rule,rng.choice(values),rng.choice(names)))
  yield f'V {len(ops)} '+' '.join(ops)
 def nop(op,i=1,j=0,k=0,v=0,name=None,a=0.,b=0.,widths=None):return f'{op} {i} {j} {k} {v} {text(name)} {bits(a)} {bits(b)} '+('-1' if widths is None else str(len(widths))+''.join(' '+str(w) for w in widths))
 structures=[None,[],[('F.Cu',True)],[('F.Cu',True),('B.Cu',True)],[('a',False),('b',True),('c',False),('d',True)],[('a',False),('b',False),('c',False),('d',False)],[('a',True),('b',False),('c',False),('d',False)],[('a',True),('b',True),('c',True),('d',False)],[(None,True),('x',True),None]]
 for ls in structures:
  for value in values:
   ops=[nop('A',name='class1'),nop('A',name='class1'),nop('B'),nop('B'),nop('G',i=-1),nop('H',name='Class1'),nop('H',name='class1'),nop('W',v=value),nop('I',v=30),nop('q'),nop('z'),nop('t'),nop('u',v=1),nop('a',k=-1,v=1),nop('a',k=20,v=1),nop('S',v=1),nop('T'),nop('Y',v=1),nop('X',v=1),nop('D',a=-0.,b=float('nan')),nop('V',j=1),nop('C',v=2),nop('W',v=value),nop('F',j=1,k=2,v=value),nop('F',j=2,k=2,v=value),nop('Z',j=1,k=2,widths=[value]*len(ls or [])),nop('Z',j=1,k=2),nop('K',i=2,j=1),nop('d',j=0,v=77),nop('e',v=8),nop('O'),nop('d',j=1,v=5),nop('h',k=-1),nop('l',k=20),nop('w',k=20,v=5),nop('R',i=1),nop('B'),nop('M',i=2,name=None),nop('H',name='missing'),nop('B')]
   yield f'N {layer(ls)} {len(ops)} '+' '.join(ops)
 for _ in range(200):
  ls=[(f'L{i}',rng.random()<.6) for i in range(rng.randrange(0,9))];ops=[nop('A',name='class1'),nop('B'),nop('A',name='class1'),nop('B')]
  for step in range(85):
   op=rng.choice(['A','B','R','G','H','F','Z','M','W','w','I','C','V','S','T','Y','X','D','a','t','u','q','z','h','l','d','e','K','O']);j=rng.randrange(0,8) if op=='K' else rng.randrange(0,6) if op=='d' else rng.randrange(0,4)
   ops.append(nop(op,rng.randrange(0,9),j,rng.randrange(-1,10),rng.choice(values),rng.choice(names),rng.choice([-float('inf'),-1.,-0.,0.,1.,float('inf'),float('nan'),rng.random()*1e10]),rng.random()*1e10,None if rng.random()<.5 else [rng.choice(values)]*rng.randrange(0,10)))
  yield f'N {layer(ls)} {len(ops)} '+' '.join(ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/'rules'/f'{n}.java' for n in ['ViaInfo','ViaInfos','ViaRule','NetClass','NetClasses','ClearanceMatrix','DefaultItemClearanceClasses']]+[base/'core/library'/f'{n}.java' for n in ['Padstack','Padstacks']]+[base/'board/model/structure'/f'{n}.java' for n in ['Layer','LayerStructure']]
 hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-rules-') as tmp:
  helpers=[ROOT/'tests/oracle'/f'{n}.java' for n in ['ClearanceOracle','RoutingRulesOracle']]
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources+helpers)],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.rules.RoutingRulesOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,check=True,timeout=120)
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for expected,actual in zip(inputs,rows):assert actual.startswith(expected+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
