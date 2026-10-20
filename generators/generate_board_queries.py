#!/usr/bin/env python3
"""Qualify Board connectivity/component queries and item net metadata against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/board_queries_java.tsv'
def cases():
 rng=random.Random(20261024)
 def sp(kind,ids):return ' '.join(map(str,[kind,len(ids),*ids]))
 for k in range(400):
  n=rng.randrange(10);nodes=[]
  for i in range(1,n+1):
   ns=[rng.choice([-1,0,1,2,3,49,10000001]) for _ in range(rng.randrange(5))]
   contact=sp(rng.choices([0,1,2],weights=[6,3,1])[0],[rng.randrange(n+1) for _ in range(rng.randrange(6))])
   nodes.append(' '.join(map(str,[rng.randrange(4),rng.randrange(1,15),rng.randrange(-1,4),rng.randrange(-1,4),rng.randrange(-1,4),rng.choice([0,0,0,1]),len(ns),*ns]))+' '+contact+' '+sp(0,[])+' '+sp(0,[]))
  flags=rng.choice([0,0,0,1,2,4,16,32,17,34]);fail=rng.choice([-1,-1,-1,rng.randrange(1,n+2)*3]);codes=[rng.randrange(-2,4) for _ in range(4)]
  yield 'B '+' '.join(map(str,[flags,fail,n,*codes]))+(' '+' '.join(nodes) if nodes else '')
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['rules/Net.java','rules/Nets.java','board/facade/BoardConnectivityQueries.java','board/model/items/Item.java','board/model/items/Trace.java','board/model/items/ConductionArea.java','board/model/items/Pin.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-board-queries-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/BoardQueriesOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.model.items.BoardQueriesOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
