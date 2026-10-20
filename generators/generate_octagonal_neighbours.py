#!/usr/bin/env python3
"""Qualify 45-degree neighbours, eight-edge contact flags and room expansion."""
from generate_orthogonal_neighbours import cases as orthogonal_cases
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from concurrent.futures import ThreadPoolExecutor
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/octagonal_neighbours_java.tsv'
def cases():
 base=list(orthogonal_cases())
 for line in base:yield line
 for line in base:yield 'O'+line[1:]
 rng=random.Random(20261113)
 def op(o,a=0,b=0,c=0,d=0):return list(map(str,[o,a,b,c,d]))
 for k in range(80):
  out=['O']
  for i in range(1,5):out+=list(map(str,[25+(i+k)%8,24,0,rng.choice([-2147483648,-1,0,1,2147483647]),1,int(i%2==0)]))
  order=list(range(1,6));rng.shuffle(order);out+=list(map(str,order))
  ops=[op('M',1,24,10),op('V',24),op('S',5,0,0,1),op('Q',1,1,50),op('N',1),op('R',1),op('C',1,1),op('A',1,4)]
  for side in range(8):ops.append(op('H',1+side%4,25+side))
  ops+=[op('N',1),op('R',1),op('Q',3,1,60),op('E',0,7,1),op('N',1)]
  for j in range(25):
   o=rng.choice(['H','N','R','E','B','Q','C','K']);a=rng.randrange(1,5);b=rng.randrange(25,33);c=1;d=0
   if o=='E':a=rng.randrange(8);b=rng.randrange(8)
   if o=='B':a=rng.choice([0,24,25,32]);b=rng.randrange(256);c=rng.choice([-1,0,6,8,8,8,9])
   if o in ['Q','C']:a=rng.choice([1,2,3]);b=1
   if o=='K':a=rng.choice([1,8,9,24]);b=2
   ops.append(op(o,a,b,c,d))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/expansion/Sorted45DegreeRoomNeighbours.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-octagonal-neighbours-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/OctagonalNeighboursOracle.java'),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  command=[str(home/'bin/java'),'-Xmx256m','-Doracle.trace=true','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.OctagonalNeighboursOracle']
  def evaluate(case):
   index,line=case
   try:
    result=subprocess.run(command,input=line+'\n',capture_output=True,text=True,timeout=10)
   except subprocess.TimeoutExpired as e:
    trace=(e.stderr or b'').decode() if isinstance(e.stderr,bytes) else (e.stderr or '')
    return None,{'case':index,'input':line,'status':'java_timeout','last_operation':trace.splitlines()[-1] if trace else '', 'timeout_seconds':10}
   if result.returncode:
    import sys
    sys.stderr.write(result.stderr);result.check_returncode()
   matches=[s for s in result.stdout.splitlines() if ' | ' in s]
   assert len(matches)==1 and matches[0].startswith(line+' | ')
   return matches[0],None
  with ThreadPoolExecutor(max_workers=4) as executor: results=list(executor.map(evaluate,enumerate(inputs)))
  rows=[row for row,issue in results if row is not None]
  issues=[issue for row,issue in results if issue is not None]
  issue_path=ROOT/'validation/freerouting-port-57/java-unqualified.json'
  issue_text=json.dumps(issues,indent=2)+'\n'
  if args.check:assert issue_path.read_text()==issue_text,'Java qualification limits changed'
  else:issue_path.write_text(issue_text)
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'input_cases':len(inputs),'unqualified_cases':len(issues),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
