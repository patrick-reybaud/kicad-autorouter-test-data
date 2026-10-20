#!/usr/bin/env python3
"""Qualify orthogonal room completion, dynamic traversal bounds and recursive box restraint."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/orthogonal_room_java.tsv'
def cases():
 from generate_room_completion import cases as general_cases
 # Replay all general histories against the actual specialized source. Additional
 # type gates/callback counts are expected to differ; reference output is new.
 for line in general_cases():yield 'O'+line[1:]
 for k in range(40):
  shapes=[(0,0,0,0,0),(1,-2000000000,-2000000000,2000000000,2000000000),(1,-1000000000,-1000000000,1000000000,1000000000)]
  coords=[(-1800000000,-1800000000,-1500000000,-1500000000),(1500000000,1500000000,1800000000,1800000000),(-1500000000,1500000000,-1500000000,1500000000),(-1800000000,-1800000000,1800000000,1800000000),(-2000000000,0,2000000000,0)]
  shapes.append((1,*coords[k%5]));shapes += [(1,0,0,1,1)]*16
  ops=[['R',1,2,0,0],['S',1,1,1,0],['R',1,2,0,0],['S',1,1,0,0],['R',1,0,0,0]]
  for code in [711,701,721]:ops += [['S',1,1,3,0],['F',code,k%3+1,0,0],['R',1,2,0,0],['F',-1,1,0,0]]
  out=['O','1'];out += [str(v) for shape in shapes for v in shape];out += list(map(str,[1,k%3,3,1,0,1,0,len(ops)]));out += [str(v) for o in ops for v in o];yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree90Degree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-orthogonal-room-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/OrthogonalRoomOracle.java'),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.OrthogonalRoomOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
