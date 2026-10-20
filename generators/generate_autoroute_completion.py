#!/usr/bin/env python3
"""Complete engine room transactions coupled to actual neighbours and spatial trees."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from concurrent.futures import ThreadPoolExecutor
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/autoroute_completion_java.tsv'
def bits(x):return struct.pack('>d',x).hex()
def cases():
 rng=random.Random(20261116)
 def op(o,a=0,b=0,c=0,d=0,v=1.):return list(map(str,[o,a,b,c,d,bits(v)]))
 for k in range(160):
  kind=k%4
  ops=[op('i',1,5),op('i',2,2),op('i',3,7),op('i',4,8),op('N',1,1,1),op('A',4,0,0,4)]
  if k%5==0:ops +=[op('C',1,1),op('D',1,4,2,3)]
  if k%7==0:ops+=[op('g',2147483647)]
  ops +=[op('c',4),op('V'),op('h'),op('c',5),op('o',1),op('d',8),op('n',8),op('n',6)]
  for j in range(10):
   o=rng.choice(['A','c','a','d','h','o','N','F','V','B','K','X']);a=0;b=0;c=0;d=0
   if o=='A':a=4;b=rng.choice([0,1,3,9,11]);d=rng.choice([0,4,10,12])
   if o in ['c','a']:a=rng.choice([0,4,5])
   if o=='d':a=rng.choice([0,4,5,6,7,8])
   if o=='o':a=rng.randrange(1,5);b=rng.randrange(2)
   if o=='N':a=rng.choice([1,2,3]);b=1;c=1
   if o=='F':a=rng.choice([-1,-1,701,702,801,802,851,852,900,901,970,971,972,973]);b=rng.randrange(1,3)
   if o=='X':a=rng.choice([0,6,7])
   ops.append(op(o,a,b,c,d))
  yield ' '.join(['E',str(k%2),'0',str(kind),'1','1','2','2','3','3',str(len(ops)),*[x for row in ops for x in row]])
 for k in range(96):
  kind=k%4;failure=[970,971,972,973,980,981,982,-1][k//4%8];occurrence=k//32+1
  ops=[op('i',1,5),op('i',2,2),op('i',3,7),op('i',4,8),op('N',1,1,1),op('r',4,[0,1,11][k//32],0,4),op('F',failure,occurrence),op('c',4),op('V'),op('F',-1),op('c',4),op('h'),op('c',5),op('B'),op('K')]
  yield ' '.join(['E','1','0',str(kind),'1','1','2','2','3','3',str(len(ops)),*[x for row in ops for x in row]])
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 names=['autoroute/maze/AutorouteEngine.java','datastructures/TimeLimit.java','autoroute/expansion/SortedRoomNeighbours.java','autoroute/expansion/SortedOrthogonalRoomNeighbours.java','autoroute/expansion/Sorted45DegreeRoomNeighbours.java','board/searchtree/ShapeSearchTree.java','board/searchtree/ShapeSearchTree90Degree.java','board/searchtree/ShapeSearchTree45Degree.java'];sources=[base/x for x in names];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-autoroute-completion-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/AutorouteCompletionOracle.java')],check=True)
  command=[str(home/'bin/java'),'-Xmx256m','-Doracle.trace=true','-cp',tmp+os.pathsep+str(support),'app.freerouting.board.searchtree.AutorouteCompletionOracle']
  def evaluate(case):
   index,line=case
   try:result=subprocess.run(command,input=line+'\n',capture_output=True,text=True,timeout=10)
   except subprocess.TimeoutExpired as e:
    trace=(e.stderr or b'').decode() if isinstance(e.stderr,bytes) else (e.stderr or '')
    return None,{'case':index,'input':line,'status':'java_timeout','last_operation':trace.splitlines()[-1] if trace else '', 'timeout_seconds':10}
   if result.returncode:
    import sys
    sys.stderr.write('case='+str(index)+'\n'+result.stderr);result.check_returncode()
   rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==1 and rows[0].startswith(line+' | ')
   return rows[0],None
  with ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(evaluate,enumerate(inputs)))
  rows=[row for row,issue in results if row is not None];issues=[issue for row,issue in results if issue is not None];issue_path=ROOT/'validation/freerouting-port-60/java-unqualified.json';issue_text=json.dumps(issues,indent=2)+'\n';generated='\n'.join(rows)+'\n'
  if args.check:assert issue_path.read_text()==issue_text and DEST.read_text()==generated,'Reference output or qualification limits changed'
  else:issue_path.write_text(issue_text);DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'input_cases':len(inputs),'unqualified_cases':len(issues),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
