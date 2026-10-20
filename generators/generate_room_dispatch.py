#!/usr/bin/env python3
"""Qualify neighbour dispatch coupled to actual spatial queries and shape completion."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from concurrent.futures import ThreadPoolExecutor
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/room_dispatch_java.tsv'
def cases():
 rng=random.Random(20261114)
 def op(o,a=0,b=0,c=0,d=0):return list(map(str,[o,a,b,c,d]))
 for k in range(320):
  out=['O' if k%2 else 'D',str((k//2)%5)]
  for i in range(1,5):out+=list(map(str,[rng.choice([2,3,4,5,8]),rng.choice([1,6,10]),0,rng.choice([1,2,3,100+i,2147483647]),int(i%2!=0 or k%3==0),int(i%2==0)]))
  order=list(range(1,6));rng.shuffle(order);out+=list(map(str,order))
  ops=[op('S',0,0,0,1),op('P',1),op('C',5,1),op('C',3,1),op('P',2),op('C',5,1)]
  for j in range(18):
   o=rng.choice(['C','C','P','U','F','M','Z','S']);a=rng.randrange(1,6);b=rng.randrange(2);c=0;d=0
   if o=='P':a=rng.choice([1,2,5])
   if o=='U':a=rng.randrange(1,5);b=rng.choice([1,2,3,4,5,8,9]);d=rng.randrange(4)
   if o=='F':a=rng.choice([-1,-1,101,102,103,104,201,202,203,204,301,302,303,304,402,404,600,610,701,711,721,800,810,820,830,900]);b=rng.randrange(1,3)
   if o=='M':a=rng.randrange(1,3);b=rng.choice([0,1,9,12]);c=rng.choice([0,10,11])
   if o=='S':d=rng.randrange(1,3)
   ops.append(op(o,a,b,c,d))
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
 for k in range(40):
  kind=2+k%2;out=['D' if kind==2 else 'O',str(kind)];blocker=1+k%4
  for i in range(1,5):out+=list(map(str,[8 if i==blocker else 2,1,0,100+i,int(i==blocker),int(i%2==0)]))
  order=list(range(1,6));rng.shuffle(order);out+=list(map(str,order))
  ops=[op('F',100+blocker,1),op('P',1),op('F',-1),op('P',1),op('C',5,1),op('F',100+blocker,2),op('P',1),op('F',-1),op('U',blocker,2,0,1),op('P',1),op('C',5,1)]
  out.append(str(len(ops)))
  for x in ops:out+=x
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/expansion/SortedRoomNeighbours.java','autoroute/expansion/SortedOrthogonalRoomNeighbours.java','autoroute/expansion/Sorted45DegreeRoomNeighbours.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree90Degree.java','board/searchtree/ShapeSearchTree45Degree.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-room-dispatch-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/RoomDispatchOracle.java'),str(ROOT/'tests/oracle/CompleteRoomOracle.java')],check=True)
  command=[str(home/'bin/java'),'-Xmx256m','-Doracle.trace=true','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.RoomDispatchOracle']
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
  issue_path=ROOT/'validation/freerouting-port-58/java-unqualified.json'
  issue_text=json.dumps(issues,indent=2)+'\n'
  if args.check:assert issue_path.read_text()==issue_text,'Java qualification limits changed'
  else:issue_path.write_text(issue_text)
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'input_cases':len(inputs),'unqualified_cases':len(issues),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'categories':dict(Counter(s.split()[0] for s in rows)),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
