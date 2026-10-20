#!/usr/bin/env python3
"""Install/check the explicitly qualified Java-25 arithmetic variant; strict fdlibm stays intact."""
import argparse,json,hashlib
from pathlib import Path
from build_trig_runtime_candidate import build,ROOT,BUILD,VAL
DEST=ROOT/'upstream/kicad/thirdparty/java25_math'
def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args();build();DEST.mkdir(exist_ok=True);result={}
 for f in sorted((BUILD/'candidate-source').glob('*')):
  name=f.name.replace('fdlibm_','java25_');s=f.read_text().replace('fdlibm_','java25_').replace('FREEROUTING_FDLIBM','FREEROUTING_JAVA25_MATH')
  target=DEST/name
  if args.check:assert target.read_text()==s,name
  else:target.write_text(s)
  result[name]=hashlib.sha256(s.encode()).hexdigest()
 (VAL/'native-import.json').write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__':main()
