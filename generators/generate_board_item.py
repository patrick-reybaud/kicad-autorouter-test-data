#!/usr/bin/env python3
"""Qualify Item metadata, nets, selection, copies and reversible assignments."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/board_item_java.tsv'
def cases():
 rng=random.Random(20261008)
 def op(c,i=0,j=0,k=0):return f'{c} {i} {j} {k}'
 def row(initial,ops):return 'I '+str(len(initial))+' '+' '.join(' '.join(map(str,x)) for x in initial)+' '+str(len(ops))+' '+' '.join(ops)
 initial=[(0,1,0,0,0,1,0,2,0),(2,2,10,1,1,1,1,1,1),(3,-2,11,-1,2,1,2,2,2),(1,8,12,0,3,0,3,1,3),(2,0,13,0,-1,1,-1,4,-1)]
 for item in range(1,6):
  ops=[op('V',item),op('m',2,0,7),op('C',item,1),op('O',item,0),op('T',item,-1),op('S',item,2),op('s',item,0),op('E',item,2),op('e',item,0),op('N',item),op('n',item),op('R',item,1),op('V',item),op('F',item,-1),op('f',item),op('h',item),op('U',item),op('f',item),op('b',item,1),op('v',item,99),op('K',item),op('X',item,-1),op('X',item,1),op('A',item,2),op('D',item,0),op('D',item,-1),op('L',item,2),op('l',item,2),op('r',item,2)]
  yield row(initial,ops)
 for first in [-2147483648,-1,0,1,1073741823,2147483647]:
  for second in [-2147483648,0,1,1073741823,2147483647]:
   yield row([(0,0,first,0,0,1,0,1,0),(0,0,second,0,0,1,0,1,0)],[op('D',1,2),op('D',2,1),op('D',1,-1),op('D',2,0)])
 for mode in range(4):
  for net in [-1,0,1,2,8,9,9999999,10000000]:
   ops=[op('I',1),op('I',2),op('Q'),op('b',1,1),op('Z',1,mode),op('A',1,net),op('M',2,0),op('A',2,1),op('u'),op('d'),op('p'),op('K',1)]
   yield row(initial,ops)
 for fx in range(-1,4):
  for ch in range(-1,10):
   ops=[op('F',1,fx),op('W',1,ch),op('W',2,0,2),op('W',3,1),op('W',4),op('H',1,0),op('J',1,1),op('w',1,8,0),op('w',1,9,0),op('J',1,1),op('Y',1),op('a',1),op('z',3),op('Y',1),op('y',1),op('t',1,ch)]
   yield row(initial,ops)
 for _ in range(280):
  ops=[]
  for step in range(95):
   c=rng.choice(['C','O','T','S','s','E','e','N','n','G','R','A','M','m','V','v','D','L','l','r','F','U','f','h','B','b','c','X','K','k','Z','I','Q','u','d','p','W','w','a','z','t','H','J','Y','y'])
   i=rng.randrange(0,6);j=rng.choice([-1,0,1,2,3,4,5,7,8,9,9999999,10000000]);k=rng.randrange(-1,4)
   if c in ['F','Z']:j=rng.randrange(-1,4) if c=='F' else rng.randrange(4)
   if c in ['M','s','e','S','E','L','l','r','H','J','D']:j=rng.randrange(-1,12)
   if c=='W':i=rng.randrange(5);j=rng.randrange(-1,10);k=rng.randrange(-1,10)
   if c in ['w','t']:j=rng.randrange(-1,10)
   if c=='k':j=rng.choice([-1,0,1,10,11,12])
   ops.append(op(c,i,j,k))
  yield row(initial,ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/model/items/Item.java','board/model/structure/FixedState.java','board/actions/ItemSelectionFilter.java','rules/Nets.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-board-item-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/BoardItemOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.BoardItemOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
