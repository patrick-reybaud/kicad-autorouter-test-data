#!/usr/bin/env python3
"""Summarize immutable Java columns and experiment results; fail if source state drifted."""
from pathlib import Path
import collections,hashlib,json,math,struct,subprocess
from probe_trig_runtime import ROOT,BUILD,VAL,sha

def main():
 initial=json.loads((VAL/'initial-state.json').read_text())
 for f,h in initial['untracked_sha256'].items():assert sha(ROOT/f)==h,f
 m=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
 before=json.loads((VAL/'manifest-before.json').read_text());assert m['source_files']==before['source_files']
 for f,e in m['source_files'].items():assert sha(ROOT/'engine'/f)==e['sha256'],f
 old=json.loads((ROOT/'validation/freerouting-port-68-A/oracle.json').read_text())
 assert sha(BUILD/'geometry.in')==old['artifacts_sha256']['geometry.in']
 assert sha(BUILD/'numeric.in')==old['artifacts_sha256']['trig.in']
 assert sha(BUILD/'reference-geometry.out')==old['artifacts_sha256']['reference.out']
 stats={'cases':0,'math_mismatch':[0,0],'strict_mismatch':[0,0],'by_magnitude':{},'first':[]}
 for line in (BUILD/'reference-numeric.out').open():
  inp,right=line.strip().split(' | ');x=struct.unpack('>d',bytes.fromhex(inp.split()[2]))[0];w=right.split();stats['cases']+=1
  mag='nonfinite' if not math.isfinite(x) else 'abs<=2pi' if abs(x)<=2*math.pi else 'medium' if abs(x)<823550 else 'large'
  b=stats['by_magnitude'].setdefault(mag,{'cases':0,'math_mismatch':[0,0],'max_ulp_steps':[0,0]});b['cases']+=1
  for i in [0,1]:
   if w[i]!=w[i+4]:
    stats['math_mismatch'][i]+=1;b['math_mismatch'][i]+=1;b['max_ulp_steps'][i]=max(b['max_ulp_steps'][i],abs(int(w[i],16)-int(w[i+4],16)))
    if len(stats['first'])<8:stats['first'].append({'input':inp,'function':['sin','cos'][i],'Math':w[i],'candidate':w[i+4]})
   if w[i+2]!=w[i+4]:stats['strict_mismatch'][i]+=1
 assert stats['cases']==1011501
 (VAL/'numeric-comparison.json').write_text(json.dumps(stats,indent=2)+'\n')
 fixtures={}
 n=ROOT/'upstream/kicad'
 for p in subprocess.check_output(['git','ls-tree','-r','--name-only','171ae495','qa/data/pcbnew/autorouter'],cwd=n,text=True).splitlines():
  if p.endswith('.tsv'):
   assert subprocess.check_output(['git','show','171ae495:'+p],cwd=n)==(n/p).read_bytes(),p;fixtures[p]=sha(n/p)
 (VAL/'integrity.json').write_text(json.dumps({'java_sources_unchanged':238,'prior_fixtures_unchanged':len(fixtures),'fixture_sha256':fixtures,'untracked_experiments_unchanged':len(initial['untracked_sha256'])},indent=2)+'\n')
 print(json.dumps({'numeric':stats,'prior_fixtures':len(fixtures)},indent=2))
if __name__=='__main__':main()
