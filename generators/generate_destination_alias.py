#!/usr/bin/env python3
"""Exercise actual DestinationDistance shared input arrays and constructor/error boundaries."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/destination_alias_java.tsv'
def bits(v):return struct.pack('>d',v).hex()
def cases():
 rng=random.Random(20261117)
 def op(o,a=0,b=0,c=0,x=1.,y=2.):return list(map(str,[o,a,b,c,bits(x),bits(y)]))
 numbers=[-0.,0.,-1.,1.,0.5,2.,10.,1e100,float('nan'),float('inf'),-float('inf')]
 for k in range(600):
  count=k%6+1;ops=[op('A',count),op('B',count,(1<<count)-1)]
  ops+=[op('M',i,1,0,1+i,2+i) for i in range(count)]
  ops += [op('C',0,0,0,50.,10.),op('C',1,0,0,70.,5.),op('J',0,1,k%count),op('J',1,2,k%count),op('Q',0,2,k%count),op('M',k%count,1,0,7.,9.),op('Q',0,2,k%count),op('Q',1,1,k%count),op('M',k%count,0),op('H',0,2,k%count),op('A',rng.randrange(-1,7)),op('B',rng.randrange(-1,7),rng.randrange(64)),op('R',k%count,1,0,3.,4.),op('Q',0,2,k%count)]
  for j in range(24):
   o=rng.choice(['A','B','M','R','L','l','C','J','Q','P','H']);a=rng.randrange(-1,7);b=rng.randrange(2);c=rng.randrange(-1,7)
   if o=='B':b=rng.randrange(64)
   if o in ['C','J','Q','P','H']:a=rng.randrange(2);b=rng.randrange(5)
   ops.append(op(o,a,b,c,rng.choice(numbers),rng.choice(numbers)))
  yield ' '.join(['D',str(len(ops)),*[x for row in ops for x in row]])
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();source=ROOT/'engine/src/main/java/app/freerouting/autoroute/maze/DestinationDistance.java';key=str(source.relative_to(ROOT/'engine'));h=hashlib.sha256(source.read_bytes()).hexdigest();manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-destination-alias-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,str(source),str(ROOT/'tests/oracle/DestinationAliasOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx512m','-cp',tmp+os.pathsep+str(support),'app.freerouting.autoroute.maze.DestinationAliasOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
