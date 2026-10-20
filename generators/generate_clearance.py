#!/usr/bin/env python3
"""Compare personal clearance/layer rules and extract pinned Java case-mapping data."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/clearance_matrix_java.tsv'
MAPPING=ROOT/'upstream/kicad/thirdparty/autorouter_core/java_case_mapping.inc'
def text(s):
 if s is None:return '-'
 return s.encode('utf-16-be','surrogatepass').hex() or '.'
def layer(names):
 if names is None:return '-1'
 return ' '.join([str(len(names))]+['N' if x is None else ('R '+str(x)) if isinstance(x,int) else 'V '+text(x[0])+' '+str(int(x[1])) for x in names])
def cases(mapping):
 rng=random.Random(20261003)
 strings=[None,'','null','default','DEFAULT','DefAult','i','I','İ','ı','ß','ẞ','Σ','σ','ς','K','K','Å','Å','\ud800','\udc00','𐐀','𐐨']
 for a in strings:
  for b in strings:yield 'F '+text(a)+' '+text(b)
 for c,f in mapping:
  a,b=chr(c),chr(f)
  for x,y in [(a,b),('A'+a+'Z','a'+b+'z'),(a+b,b+a),(a,b+'\x00')]:yield 'F '+text(x)+' '+text(y)
 for _ in range(1000):
  a=''.join(chr(rng.randrange(0x110000)) for _ in range(rng.randrange(1,9)));b=a if rng.random()<.5 else a[:-1]+chr(rng.randrange(0x110000));yield 'F '+text(a)+' '+text(b)
 structures=[None,[],[('F.Cu',True)],[('F.Cu',False)],[('F.Cu',True),('B.Cu',True)],[('a',False),('b',True),('c',False),('d',True)],[('same',True),0,('same',True)],[None],[('x',True),None,('y',False)],[(None,True)]]
 for ls in structures:
  for name in [None,'','F.Cu','f.cu','same','unknown']:
   for no in [-2,-1,0,1,2,5]:
    for chosen in [-2,-1]+list(range(len(ls or []))):yield f'L {layer(ls)} {text(name)} {no} {chosen}'
 def op(code,i=0,j=0,l=0,v=0,name=None):return f'{code} {i} {j} {l} {v} {text(name)}'
 for count in [-1,0,1,2,4]:
  for lc in [0,1,2,3,6]:
   names=['null','default','POWER','signal'];ls=[(f'L{i}',i%3!=1) for i in range(lc)]
   for value in [-2147483648,-1,0,1,17,2147483647]:
    ops=[op('K',0),op('K',1),op('T',v=value),op('V',1,1,0,2),op('P',name='new'),op('K',2),op('A',0,1,v=31),op('I',1,1,v=55),op('P',name='NEW'),op('U',l=2,v=91)]
    for i,j,l in [(-1,0,-1),(0,0,0),(1,1,1),(2,1,lc-1),(9,1,9)]:
     ops.extend([op('Q',i,j,l,0),op('Q',i,j,l,1),op('X',i,j,l),op('Y',l=l),op('B',i,j),op('E',i,j),op('C',i,l=l),op('Z',i,j)])
    ops +=[op('R',1),op('V',0,0,0,9),op('P',name=None),op('N',name='power'),op('H',2),op('Z',-1,-1),op('R',99),op('Q',0,0,0,1)]
    yield f'M {count} {layer(ls)} 4 '+ ' '.join(text(n) for n in names)+f' C 0 {len(ops)} '+' '.join(ops)
 for i in range(140):
  lc=rng.randrange(0,8);ls=rng.choice(structures) if i<10 else [(f'L{j}',bool(j%2)) for j in range(lc)];count=rng.randrange(1,6);names=[rng.choice(strings[:16]) for _ in range(count)];ops=[]
  for step in range(40):
   code=rng.choice(['V','A','T','U','I','P','R','Q','X','Y','B','E','H','N','C','Z','K'])
   ops.append(op(code,rng.randrange(-1,7),rng.randrange(-1,7),rng.randrange(-1,9),rng.choice([-2147483648,-1,0,1,3,24,7000,2147483646,2147483647]),rng.choice(strings)))
  mode='D' if i%3==0 else 'C';yield f'M {count} {layer(ls)} {count} '+' '.join(text(n) for n in names)+f' {mode} 17 {len(ops)} '+' '.join(ops)
 for n in [0,1,3]:yield f'M 4 {layer([])} {n} '+' '.join(text('x') for _ in range(n))+' C 0 0'
 for _ in range(150):
  ops=[f'{rng.choice(["S","A","U","K"])} {rng.randrange(-1,8)} {rng.choice([-2147483648,-1,0,1,5,2147483647])}' for i in range(25)]
  yield f'D {len(ops)} '+' '.join(ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
 names=['rules/ClearanceMatrix.java','rules/DefaultItemClearanceClasses.java','board/model/structure/Layer.java','board/model/structure/LayerStructure.java']
 sources=[ROOT/'engine/src/main/java/app/freerouting'/n for n in names];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
 manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for n,h in hashes.items():assert manifest['source_files'][n]['sha256']==h
 with tempfile.TemporaryDirectory(prefix='freerouting-clearance-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/ClearanceOracle.java'),str(ROOT/'tests/oracle/JavaCaseMapping.java')],check=True)
  mapping_text=subprocess.check_output([str(home/'bin/java'),'-cp',tmp,'JavaCaseMapping'],text=True)
  mapping=[tuple(map(int,s.split())) for s in mapping_text.splitlines()]
  include='// Generated from Character.toLowerCase(Character.toUpperCase(codePoint)).\n// OpenJDK 25.0.2, Homebrew; regenerate with tests/oracle/generate_clearance.py.\n'+''.join('{ %s, %s },\n'%p for p in mapping)
  if args.check:assert MAPPING.read_text()==include,'Case mapping changed'
  else:MAPPING.write_text(include)
  inputs=list(cases(mapping));result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.rules.ClearanceOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,check=True,timeout=120)
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs),(len(rows),len(inputs))
  for expected,actual in zip(inputs,rows):assert actual.startswith(expected+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'case_mapping_entries':len(mapping),'case_mapping_sha256':hashlib.sha256(include.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
