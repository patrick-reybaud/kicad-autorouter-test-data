#!/usr/bin/env python3
"""Qualify source snapshots, undo/redo histories, cloning and serial cursor mutations."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/undoable_objects_java.tsv'
def cases():
 rng=random.Random(20261006)
 def op(c,i=0,j=0,k=0,v=0):return f'{c} {i} {j} {k} {v}'
 def row(initial,ops):return 'U '+str(len(initial))+''.join(' '+' '.join(map(str,o)) for o in initial)+' '+str(len(ops))+' '+' '.join(ops)
 for mode in range(4):
  for flags in range(8):
   seq=[op('I',1),op('I',2),op('Q'),op('F',10,77),op('I',3),op('S',1),op('U',flags),op('A'),op('R',flags),op('Q'),op('D',1),op('D',2),op('U',flags),op('R',flags),op('P'),op('U',flags),op('I',2),op('R',flags),op('S',0),op('I',0),op('D',0)]
   yield row([(10,1,mode,0),(5,2,0,1),(7,3,0,0)],seq)
 # Existing-key replacement, deleted prefetched entries, and insertion around stale cursors.
 for action in ['I','D','S','M']:
  for target in range(1,6):
   ops=[op('I',i) for i in [1,2,3]]+[op('B',0),op(action,target,99),op('T',0),op('D',2),op('I',4),op('T',0),op('I',5),op('T',0),op('T',0),op('B',1),op('D',1),op('D',3),op('D',4),op('D',5),op('T',1),op('T',1)]
   yield row([(10,1,0,0),(5,2,0,0),(0,3,0,0),(7,4,0,0),(10,5,0,0)],ops)
 for seq in range(250):
  initial=[(rng.randrange(0,12),rng.randrange(-10,11),0,seq%3==0) for i in range(8)];initial=[tuple(int(v) for v in o) for o in initial];ops=[op('I',i) for i in range(1,7)]
  for k in range(100):
   c=rng.choice(['I','D','S','M','Q','U','R','P','B','T','A','N','K','E','O','Z','F']);i=rng.randrange(0,20);j=rng.randrange(-20,21);v=0
   if c in ['B','T']:i=rng.randrange(4)
   if c in ['U','R']:i=rng.randrange(8)
   if c in ['O','Z']:i=rng.randrange(2);j=rng.randrange(0,15)
   if c=='K':j=rng.randrange(4) if seq%4==0 else 0
   if c=='N':i=rng.randrange(12);v=rng.randrange(2)
   ops.append(op(c,i,j,0,v))
  yield row(initial,ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();source=ROOT/'engine/src/main/java/app/freerouting/datastructures/UndoableObjects.java';key=str(source.relative_to(ROOT/'engine'));sha=hashlib.sha256(source.read_bytes()).hexdigest();manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());assert manifest['source_files'][key]['sha256']==sha
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-undo-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,str(source),str(ROOT/'tests/oracle/UndoableObjectsOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.datastructures.UndoableObjectsOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,check=True,timeout=120)
  rows=[s for s in result.stdout.splitlines() if ' | ' in s];assert len(rows)==len(inputs)
  for expected,actual in zip(inputs,rows):assert actual.startswith(expected+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':{key:sha},'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
