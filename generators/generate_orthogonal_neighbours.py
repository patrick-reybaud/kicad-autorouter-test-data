#!/usr/bin/env python3
"""Qualify orthogonal neighbour discovery, ordering, enlargement and incomplete rooms."""
from generate_room_discovery import cases as general_cases
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from concurrent.futures import ThreadPoolExecutor
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/orthogonal_neighbours_java.tsv'
def cases():
 rng=random.Random(20261112)
 for line in general_cases():
  tokens=line.split()
  for offset in range(31,len(tokens),5):
   if tokens[offset]=='T':tokens[offset]='B';tokens[offset+1]=str(rng.choice([0,1,9,12]));tokens[offset+2]=str(rng.randrange(-1,6))
  yield ' '.join(tokens)
 for k in range(160):
  out=['D']
  for i in range(1,5):out += list(map(str,[i+1,1,0,rng.choice([-2147483648,-1,0,1,2147483647]),1,int(i%2==0)]))
  out+=['1','2','3','4','5']
  def op(o,a=0,b=0,c=0,d=0):return list(map(str,[o,a,b,c,d]))
  # Construct from the normal incomplete room and an independently completed box.
  ops=[op('A',1,4),op('J',2),op('J',2),op('S',0,0,1,1),op('R',1)]
  for side in range(4):ops.append(op('I',side+1,14+side,18+side))
  for j in range(30):
   o=rng.choice(['I','I','I','N','R','E','B','A','J','Y','F','Z']);a=rng.randrange(1,5);b=rng.randrange(24);c=rng.randrange(24)
   if o=='A':a=rng.choice([0,1,2,3]);b=rng.choice([0,3,4])
   if o=='J':a=rng.choice([0,1,2])
   if o=='Y':a=rng.randrange(2)
   if o=='B':a=rng.randrange(24);b=rng.randrange(-2,6)
   if o=='F':a=rng.choice([-1,-1,101,102,103,104,721,610,830]);b=rng.randrange(1,4)
   ops.append(op(o,a,b,c))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
 for k in range(80):
  out=['D']
  for i in range(1,5):out += list(map(str,[i+1,1,0,rng.choice([-2147483648,-1,0,1,2147483647]),1,int(i%2==0)]))
  out+=['1','2','3','4','5']
  ops=[op('V',1),op('A',1,4),op('K',8,2),op('K',9,2),op('K',1,2),op('S',0,0,1,1),op('R',1),op('A',1,4)]
  for side in range(4):ops.append(op('I',side+1,14+side,18+side))
  ops+=[op('N',1),op('R',1),op('V',9),op('N',1)]
  for j in range(30):
   o=rng.choice(['I','I','N','B','K','R']);a=rng.randrange(1,5);b=rng.randrange(14,24);c=rng.randrange(14,24)
   if o=='B':a=rng.randrange(24);b=rng.randrange(-2,6)
   if o=='K':a=rng.choice([1,8,9]);b=2
   ops.append(op(o,a,b,c))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/expansion/SortedOrthogonalRoomNeighbours.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-orthogonal-neighbours-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/OrthogonalNeighboursOracle.java'),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  command=[str(home/'bin/java'),'-Xmx256m','-Doracle.trace=true','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.OrthogonalNeighboursOracle']
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
  issue_path=ROOT/'validation/freerouting-port-56/java-unqualified.json'
  issue_text=json.dumps(issues,indent=2)+'\n'
  if args.check:assert issue_path.read_text()==issue_text,'Java qualification limits changed'
  else:issue_path.write_text(issue_text)
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'input_cases':len(inputs),'unqualified_cases':len(issues),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
