#!/usr/bin/env python3
"""Qualify Package/logical-part libraries and via-padstack selection against original Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from collections import Counter
from pathlib import Path
from generate_planar import ROOT
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/board_library_java.tsv'
def enc(s):return 'N' if s is None else s.encode('utf-16-be',errors='surrogatepass').hex() or '-'
def cases():
 rng=random.Random(20261025)
 names=[None,'','a','A','b','Ω','ω','a::1','a::001','a::1::2','a::-1','a::١','a::1\n','a\n','a::1\r\n','a\r\n','a::1\u0085','a\u0085','a::1\u2028','a\u2028','a::1\u2029','a\u2029','a::1\n\n','a::','İ','i','ẞ','ß','𐐀','𐐨']
 def pins():
  n=rng.randrange(-1,6);out=[str(n)]
  for i in range(max(n,0)):
   kind=rng.randrange(3);out.append(str(kind))
   if kind:out += [enc(rng.choice(names)),str(rng.randrange(-2,8)),str(rng.randrange(-10,11)),str(rng.randrange(-10,11)),rng.choice(['0','8000000000000000','3ff0000000000000','7ff8000000000000','7ff0000000000000'])]
  return out
 for k in range(260):
  out=['P'];n=rng.randrange(8);out.append(str(n))
  for i in range(n):out += [enc(rng.choice(names)),str(rng.randrange(2)),str(rng.randrange(8)==0 and 1 or 0),*pins()]
  out += [str(len(names)),*map(enc,names)];yield ' '.join(out)
 # Deterministic side fallback, suffix matching, terminal newline and Unicode cases.
 for name in names:
  out=['P','4',enc('a'),'0','0','0',enc('a'),'0','0','0',enc('A'),'1','0','0',enc(name),'0','0','0',str(len(names)),*map(enc,names)];yield ' '.join(out)
 for k in range(240):
  n=rng.choice([-1,0,1,2,5,31,32,63,128,320,1024]);out=['L',enc(rng.choice(names)),str(n)]
  for i in range(max(n,0)):
   present=int(rng.random()>(0.04 if k%3==0 else 0));out.append(str(present))
   if present:out += [str(rng.choice([-2147483648,-2147483647,-1,0,1,2,2147483646,2147483647]) if k%2 else rng.randrange(-20,21)),enc(rng.choice(names))]
  yield ' '.join(out)
 for k in range(240):
  layers=rng.choice([-1,0,1,2,4,6]);n=rng.randrange(1,12);out=['B',str(layers),str(rng.choice([0,0,0,1,2,3])),str(n)]
  for i in range(n):
   count=rng.choice([-1,0,1,2,4,6]);out += [enc(rng.choice(names)),str(count),*[str(rng.randrange(2)) for j in range(max(count,0))]]
  out.append('65')
  for i in range(65):
   op=rng.choice(['S','A','R','M','M','G','I','C']);out.append(op)
   if op=='S':
    size=rng.randrange(-1,9);out += [str(size),*[str(rng.randrange(n+1)) for j in range(max(size,0))]]
   elif op in ['A','R','M']:out.append(str(rng.randrange(n+1)))
   elif op=='G':out.append(enc(rng.choice(names)))
   elif op=='I':out.append(str(rng.randrange(-2,12)))
  yield ' '.join(out)
 for k in range(200):
  n=rng.randrange(6);out=['U',str(rng.choice([0,0,0,1,2,4,8,9])),str(rng.choice([-1,-1,1,2,5])),str(n)]
  for i in range(n):out += pins()
  count=rng.randrange(8);out.append(str(count))
  for i in range(count):out += [str(rng.randrange(1,12)),str(rng.randrange(7))]
  yield ' '.join(out)
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args();base=ROOT/'engine/src/main/java/app/freerouting'
 sources=[base/('core/library/'+x+'.java') for x in ['Package','Packages','LogicalPart','LogicalParts','BoardLibrary','Padstack','Padstacks']];hashes={str(p.relative_to(ROOT/'engine')):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources};manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 for key,h in hashes.items():assert manifest['source_files'][key]['sha256']==h
 home=Path(os.environ['JAVA_HOME']);support=ROOT/'engine/build/libs/freerouting-current-executable.jar';inputs=list(cases())
 with tempfile.TemporaryDirectory(prefix='freerouting-board-library-') as tmp:
  subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,*map(str,sources),str(ROOT/'tests/oracle/BoardLibraryOracle.java')],check=True)
  result=subprocess.run([str(home/'bin/java'),'-Xmx1g','-cp',tmp+os.pathsep+str(support),'app.freerouting.core.library.BoardLibraryOracle'],input='\n'.join(inputs)+'\n',capture_output=True,text=True,timeout=120)
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
