#!/usr/bin/env python3
"""Run actual reduceTraceShapeAtTiePin against geometry, item caches and tree mutations."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/tie_pin_java.tsv'
def cases():
 rng=random.Random(20261119)
 for k in range(660):
  ops=[[k%6,k//6%11,0,-1,1],[1,2,0,-1,1],[4,4,0,-1,1]]
  for j in range(9):ops.append([rng.randrange(6),rng.randrange(11),rng.choice([0,0,0,1,2,4,8,16,32,64]),rng.choice([-1,-1,1,2,3,4,5,6,7,8]),rng.randrange(1,4)])
  yield ' '.join(map(str,['T',k%3,len(ops),*[v for row in ops for v in row]]))
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();source=ROOT/'engine/src/main/java/app/freerouting/board/searchtree/ShapeSearchTree.java';key=str(source.relative_to(ROOT/'engine'));h=hashlib.sha256(source.read_bytes()).hexdigest();manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-tie-pin-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,str(source),str(ROOT/'tests/oracle/TiePinOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx512m','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.TiePinOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
  if result.returncode:
   import sys
   sys.stderr.write(result.stderr);result.check_returncode()
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for a,b in zip(inputs,rows):assert b.startswith(a+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':{key:h},'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
