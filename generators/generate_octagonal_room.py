#!/usr/bin/env python3
"""Qualify octagonal room completion, conservative Simplex conversion and eight-edge cut helpers."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/octagonal_room_java.tsv'
def cases():
 from generate_room_completion import cases as general_cases
 for line in general_cases():yield 'G'+line[1:]
 for line in general_cases():
  tokens=line.split();tokens[0]='G'
  for i in range(20):
   at=2+5*i
   if tokens[at]=='1':tokens[at]='2'
  n=int(tokens[108]);count_at=109+5*n;extra=[]
  for index in [-1,0,1,2,3,4,5,6,7,8]:
   for mode in range(4):extra += ['H','7',str(index),'1',str(mode)]
  for mode in range(4):extra += ['H','0','8','0',str(mode)]
  tokens[count_at]=str(int(tokens[count_at])+len(extra)//5);yield ' '.join(tokens+extra)
 for k in range(30):
  tokens=next(general_cases()).split()[:108];tokens[0]='G';tokens[102:108]=list(map(str,[1,0,3,2,1,4]))
  ops=[['D',1,1,2,0],['F',950,k%3+1,0,0],['P',1,2,0,0],['F',-1,1,0,0],['P',2,0,0,0],['D',1,1,1,0],['P',1,0,0,0],['D',0,1,1,0],['P',0,0,0,0],['P',1,0,0,0],['D',-1,1,0,0],['P',1,0,0,0]]
  tokens+=['0',str(len(ops))];tokens += [str(v) for o in ops for v in o];yield ' '.join(tokens)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree45Degree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-octagonal-room-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/OctagonalRoomOracle.java'),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.OctagonalRoomOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
