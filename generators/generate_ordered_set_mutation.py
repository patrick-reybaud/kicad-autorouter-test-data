#!/usr/bin/env python3
"""Qualify ordered-set lookup, deletion, bulk removal and mutation against the actual pinned JVM."""
import argparse,hashlib,json,os,random,subprocess,tempfile,zipfile
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/ordered_set_mutation_java.tsv'
def cases():
 rng=random.Random(20261021)
 def row(mode,eq,keys,ops):return 'S '+' '.join(map(str,[mode,eq,len(keys),*keys,len(ops)]))+' '+' '.join(' '.join(map(str,[*op[:5],len(op[5]),*op[5]])) for op in ops)
 for mode in range(3):
  for order in range(24):
   keys=list(range(1,32));ids=list(range(1,32));rng.shuffle(ids)
   ops=[(0,0,i,0,0,[]) for i in ids]
   rng.shuffle(ids)
   for i in ids:ops.extend([(1,0,i,0,0,[]),(2,0,i,0,0,[]),(1,0,i,0,0,[])])
   ops.extend([(1,0,0,0,0,[]),(2,0,0,0,0,[]),(0,0,0,0,0,[])])
   yield row(mode,0,keys,ops)
 for mode in range(3):
  for eq in range(2):
   for trial in range(100):
    keys=[rng.choice([-2147483648,-2147483647,-10,-1,0,0,1,5,10,2147483646,2147483647]) if mode else rng.randrange(-10,11) for _ in range(24)]
    ops=[(0,rng.randrange(2),i,0,0,[]) for i in range(1,25)]
    for k in range(80):
     op=rng.choices(range(9),weights=[8,7,7,1,3,3,4,2,1])[0];target=rng.randrange(2);x=rng.randrange(25)
     if op==5:x=rng.randrange(2)
     if op==7:x=rng.randrange(1,25)
     ops.append((op,target,x,rng.choice(keys),rng.choice([0,0,0,0,1,2,5,12]),[rng.randrange(25) for _ in range(rng.randrange(30))] if op==6 else []))
    yield row(mode,eq,keys,ops)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();home=Path(os.environ['JAVA_HOME']);inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-set-mutation-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-d',tmp,str(ROOT/'tests/oracle/OrderedSetMutationOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp,'OrderedSetMutationOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
  if result.returncode:
   import sys
   sys.stderr.write(result.stderr);result.check_returncode()
  rows=result.stdout.splitlines();assert len(rows)==len(inputs)
  for expected,actual in zip(inputs,rows):assert actual.startswith(expected+' | ')
  generated='\n'.join(rows)+'\n'
  if args.check:assert DEST.read_text()==generated,'Reference output changed'
  else:DEST.write_text(generated)
 with zipfile.ZipFile(home/'lib/src.zip') as z:hashes={p:hashlib.sha256(z.read('java.base/java/util/'+p)).hexdigest() for p in ['TreeMap.java','TreeSet.java','AbstractSet.java','ArrayList.java']}
 print(json.dumps({'cases':len(rows),'expected_fields':sum(len(s.split(' | ')[1].split()) for s in rows),'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),'java_library_source_sha256':hashes,'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
