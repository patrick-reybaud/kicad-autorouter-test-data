#!/usr/bin/env python3
"""Actual pinned-JVM integer map and board normalization oracles; sources never modified."""
import argparse,gzip,hashlib,json,random,subprocess,shutil
from pathlib import Path
root=Path(__file__).resolve().parents[2];java=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home');build=root/'build/board-normalization';val=root/'validation/freerouting-port-79-normalization';fixtures=root/'upstream/kicad/qa/data/pcbnew/autorouter'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
 return h.hexdigest()
def map_cases():
 def key(h):
  h&=0xffffffff;k=h^(h>>16);return k if k<0x80000000 else k-0x100000000
 def row(ops):return str(len(ops))+' '+' '.join(f'{o} {k}' for o,k in ops)
 rng=random.Random(2026091979)
 for offset in [0,1,3,15,63,0x80000000,0xffffffff]:
  for shift in [4,6,8,12]:
   for order in range(4):
    keys=[key(offset+i*(1<<shift)) for i in range(40)]
    if order==1:keys.reverse()
    if order==2:rng.shuffle(keys)
    if order==3:keys=keys[::2]+keys[1::2]
    ops=[('G',k) for k in keys]+[('G',k) for k in keys[::3]]
    ops+=[('G',key(i)) for i in range(65)]+[('C',0)]+[('G',k) for k in keys[:12]]
    yield row(ops)
 # Tree bins split into <=6, >6, empty/single partitions across resize boundaries.
 for low in range(4,10):
  for high in range(4,10):
   keys=[key(1+i*128) for i in range(low)]+[key(65+i*128) for i in range(high)]
   rng.shuffle(keys);yield row([('G',k) for k in keys]+[('G',i) for i in range(2,96)])
 for _ in range(128):
  pool=[key(rng.getrandbits(32)) for i in range(12)]+[key(3+i*256) for i in range(12)]
  ops=[('G',rng.choice(pool)) if rng.randrange(15) else ('C',0) for _ in range(100)]
  yield row(ops)
def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--only',choices=['map','board']);args=p.parse_args();build.mkdir(exist_ok=True);val.mkdir(exist_ok=True)
 j=json.loads((root/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text());sources=[];hashes={}
 for k,v in j['source_files'].items():
  f=root/'engine'/k;assert sha(f)==v['sha256'];sources.append(str(f));hashes[k]=v['sha256']
 jar=root/'engine/build/libs/freerouting-current-executable.jar'
 commands=[[str(java/'bin/javac'),'-cp',str(jar),'-d',str(build),*sources,*[str(root/'tests/oracle'/f) for f in ['BoardConstructionOracle.java','BoardNormalizationOracle.java','IntegerGroupMapOracle.java']]]]
 with (val/'java.log').open('w') as log:subprocess.run(commands[0],check=True,stdout=log,stderr=subprocess.STDOUT)
 dataSets={}
 for kind,clazz,inputs,name in [('map','IntegerGroupMapOracle',list(map_cases()),'integer_group_map_java.tsv'),('board','app.freerouting.board.facade.BoardNormalizationOracle',[f'{c} {s} {m} 0' for c in range(12) for s in range(12) for m in range(4)]+[f'{c} 0 {m} 0' for c in range(3) for m in [4,5]]+[f'{c} {s} 0 {f}' for c in range(3) for s in [0,4,8] for f in range(1,6)],'board_normalization_java.tsv')]:
  if args.only and args.only!=kind:continue
  inp=build/(kind+'-input.txt');inp.write_text('\n'.join(inputs)+'\n');out=build/(kind+'-reference.tsv');cmd=[str(java/'bin/java'),'-Xmx2g','--add-opens','java.base/java.util=ALL-UNNAMED','-cp',str(build)+':'+str(jar),clazz,str(inp),str(out)];commands.append(cmd)
  with (val/(kind+'-java.log')).open('w') as log:subprocess.run(cmd,check=True,stdout=log,stderr=subprocess.STDOUT,timeout=1800)
  count=fields=0
  with out.open('rb') as f:
   for row in f:count+=1;fields+=len(row.split(b' | ')[1].split())
  assert count==len(inputs);dest=fixtures/name
  dataSets[kind]={'cases':len(inputs),'fields':fields,'input_sha256':sha(inp),'fixture_sha256':sha(out),'bytes':out.stat().st_size}
  if kind=='board':
   parts=[]
   with out.open('rb') as source:
    for part in range(5):
     raw=source.read(256*1024*1024);assert raw;target=Path(str(dest)+f'.part{part}.gz')
     if args.check:
      with gzip.open(target,'rb') as f:assert f.read()==raw,'Java expectation changed'
     else:
      with target.open('wb') as f,gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as g:g.write(raw)
     parts.append({'name':target.name,'bytes':target.stat().st_size,'sha256':sha(target)})
    assert not source.read(1)
   dataSets[kind]['parts']=parts
  elif args.check:assert dest.read_bytes()==out.read_bytes(),'Java expectation changed'
  else:shutil.copyfile(out,dest)

 (val/('oracle-'+(args.only or 'all')+'.json')).write_text(json.dumps({'reference_commit':j['reference_commit'],'source_sha256':hashes,'data':dataSets,'jar_sha256':sha(jar),'commands':commands,'tolerances':[]},indent=2)+'\n');print(dataSets)
if __name__=='__main__':main()
