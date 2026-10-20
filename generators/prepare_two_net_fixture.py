#!/usr/bin/env python3
"""Bench-only, checked reduction of one archived DSN; no routing or engine reader."""
from pathlib import Path
import hashlib,json,re,argparse
root=Path(__file__).resolve().parents[2]
folder=root/'tests/fixtures/freerouting-faithful-C'
source=folder/'crossing12-source.dsn';dest=folder/'crossing-two-nets.dsn'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
 s=source.read_text();assert len(re.findall(r'^    \(net TEST_',s,re.M))==12
 names={'TEST_3','TEST_4'};refs={'J03','J13','J04','J14'}
 s=re.sub(r'^      \(place (\S+)[^\n]*\n',lambda m:m[0] if m[1] in refs else '',s,flags=re.M)
 s=re.sub(r'^    \(net (TEST_\d+)\n      \(pins [^\n]+\n    \)\n',lambda m:m[0] if m[1] in names else '',s,flags=re.M)
 s,n=re.subn(r'\(class kicad_default TEST_[^(]+\(circuit','(class kicad_default TEST_3 TEST_4\n      (circuit',s);assert n==1
 s,n=re.subn(r'^    \(wire [^\n]*\(net TEST_1\)[^\n]*\n','',s,flags=re.M);assert n==1
 s=re.sub(r'^\(pcb "[^\n]+"','(pcb "crossing-two-nets"',s,count=1)
 assert set(re.findall(r'^    \(net (\S+)',s,re.M))==names
 assert set(re.findall(r'^      \(place (\S+)',s,re.M))==refs
 assert '(wire ' not in s and '(via ' not in s[s.index('  (wiring'):]
 if args.check:assert dest.read_text()==s
 else:dest.write_text(s)
 print(json.dumps({'source_sha256':sha(source),'reduced_sha256':sha(dest),'nets':sorted(names),'components':sorted(refs),'initial_tracks':0,'initial_vias':0,'layers':['F.Cu','B.Cu'],'width_um':200,'clearance_um':200,'pad_diameter_um':1000,'via_diameter_um':600,'via_drill_um':300,'source_resolution':['um',10],'route_executed':False},indent=2))
if __name__=='__main__':main()
