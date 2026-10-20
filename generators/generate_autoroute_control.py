#!/usr/bin/env python3
"""Qualify routing-control initialization and repeated via rebuilds against personal Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile,struct
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/autoroute_control_java.tsv'
def cases():
 rng=random.Random(20261029)
 def bits(x):return struct.pack('>d',x).hex()
 vals=[0.,-0.,1.,-1.,0.1,12.5,100.,float('nan'),float('inf'),-float('inf')]
 for k in range(600):
  layers=rng.choice([0,1,2,2,4,6,-1]);n=max(layers,1);flags=rng.choice([0,0,0,0,1,1,1,2,4,16,32,64]);net=rng.choice([-1,0,1,1,1,2,3]);out=['K',str(layers),str(net),str(flags),str(rng.choice([0,1,1,2,-1,3])),str(rng.choice([-2147483648,-10,0,1,5,40,2147483647])),str(rng.choice([-1,-1,-1,1,2,3,4,5,6,10,20,30,100,101,201])),str(rng.choice([0,0,0,1,2,3])),str(rng.randrange(2)),str(rng.randrange(2))]
  for i in range(n):out += [str(rng.randrange(2)),str(rng.randrange(2)),str(rng.randrange(2)),str(rng.choice([-2147483648,-5,0,1,10,100,2147483647])),str(rng.choice([0,1,16,30,100])),bits(rng.choice(vals))]
  for rule in range(4):
   vc=rng.randrange(5);out.append(str(vc))
   for j in range(vc):
    count=rng.choice([-1,0,n,n,n+1]);out += [str(rng.choices([0,1,2],[1,1,10])[0]),str(rng.randrange(-1,4)),str(rng.randrange(2)),str(count)]
    for t in range(count):out.append(str(rng.randrange(8)))
  count=rng.randrange(5);out.append(str(count))
  for j in range(count):
   first=rng.randrange(n);out += [str(rng.choices([0,1,2],[1,8,1])[0]),str(first),str(first if rng.randrange(3) else rng.randrange(n))]
  size=rng.choice([-1,0,n,n+1]);out.append(str(size))
  for j in range(size):out += [str(rng.randrange(2)),bits(rng.choice(vals)),bits(rng.choice(vals))]
  out.append('7')
  for j in range(7):out += [str(rng.choice([-1,0,1,2,3])),str(rng.choice([-3,0,5,40,2147483647])),str(rng.choice([-1,0,1,2,7])),str(rng.choice([0,0,1,2,3])),str(rng.choice([-1,-1,-1,5,101,201])),str(rng.randrange(2)),str(rng.choice([-2147483648,-5,0,1,20,2147483647])),bits(rng.choice(vals)),bits(rng.choice(vals))]
  yield ' '.join(out)
 for layers in [1,2,4,6]:
  for kind in range(4):
   for attach in [0,1]:
    for default in [0,1]:
     for width in [1,10,100]:
      out=['D',str(layers),'1',str(default),'1','40','-1','0','1','1']
      for i in range(layers):out += [str(int(i%3!=1)),'1',str(int(i!=layers-1 or layers==1)),str(width+i),'16',bits(0.5*i)]
      for rule in range(4):
       vc=0 if rule==1 else 2 if rule==3 else 1;out.append(str(vc))
       for j in range(vc):
        out += ['2',str(1+j),str(attach),str(layers)]
        for i in range(layers):out.append(str(3 if rule==2 else 2 if rule==3 else 1 if i in [0,layers-1] else 0))
      count=0 if kind==0 else 2;out.append(str(count))
      for j in range(count):out += [str(0 if kind==3 and j==1 else 1),'0',str(layers-1 if kind==2 else 0)]
      out.append(str(layers))
      for i in range(layers):out+=['1',bits(1.),bits(2.)]
      out.append('7')
      for j in range(7):out += [str([1,2,3,1,0,1,2][j]),str([40,5,40,0,-3,40,2147483647][j]),'1',str(int(j==6)),'-1',str(int(j>=3)),str([width,1,100,0,-1,1,10][j]),bits([0.,0.,0.,-0.,float('nan'),float('inf'),-float('inf')][j]),bits([0.,0.,0.,-0.,0.,0.,0.][j])]
      yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/x for x in ['autoroute/maze/AutorouteControl.java','rules/BoardRules.java','settings/RouterSettings.java']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-autoroute-control-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/AutorouteControlOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.autoroute.maze.AutorouteControlOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
