#!/usr/bin/env python3
"""Deterministic adversarial geometry inputs; no reference expected output is edited."""
import json,math,random
from pathlib import Path
from probe_trig_runtime import BUILD,VAL,NUMERIC,execute,compare,bits,value,nearby

def expanded():
 def row(name,a,r,k,px=0.,py=0.):return f'G {name} {bits(a)} {r} {k} {bits(px)} {bits(py)}'
 old=json.loads((VAL.parent/'freerouting-port-68-A/decision-flips.json').read_text())
 flips=[x for x in old if set(x['changes'])-NUMERIC];assert len(flips)==28
 # Every known flip, both neighboring doubles and sign, integer/half-integer poles.
 for idx,x in enumerate(flips):
  t=x['input'].split();angle=value(t[2]);r=int(t[3]);k=int(t[4])
  for d in range(-32,33):
   for sign in [1,-1]:
    for px,py in [(0.,0.),(.5,-.5),(200000.,-200000.)]:
     yield row(f'known{idx}_{d}_{sign}_{px}',nearby(angle,d)*sign,r,k+int(px),px,py)
 rng=random.Random(2026091971)
 for i in range(4000):
  r=rng.choice([37,127,1000,5000,10000,40000,350000]);k=rng.randrange(-r,r)
  a=math.acos((k+.5)/r)
  for delta in [-2,-1,0,1,2]:
   for turn in [0,1,-1]:yield row(f'half{i}_{delta}_{turn}',nearby(a+turn*2*math.pi,delta),r,k)
 # Exact binary neighbors of implementation branches and cardinal directions.
 thresholds=[0.,math.pi/4,math.pi/2,math.pi,2*math.pi,math.ldexp(1.,-27),value('3fd3333300000000'),value('3fe921fb00000000'),value('4002d97c00000000'),value('413921fb00000000')]
 for i,a in enumerate(thresholds):
  for d in range(-16,17):
   for sign in [1,-1]:
    for r in [5000,40000]:yield row(f'branch{i}_{d}_{sign}_{r}',nearby(a,d)*sign,r,0)
 # Large reduction and powers of two are explicitly challenged, not silently excluded.
 for i in range(3000):
  exponent=rng.randrange(4,1024);a=math.ldexp(rng.uniform(.5,1.),exponent)
  yield row(f'large{i}',a if i%2 else -a,5000,0)
 # Use previously measured Java Math values solely to generate adversarial inputs.
 # No case is filtered based on the resulting downstream comparison.
 chosen=0
 for line in (BUILD/'reference-numeric.out').open():
  t,out=line.strip().split(' | ');angle=value(t.split()[2]);w=out.split();co=value(w[1])
  if not math.isfinite(angle) or not math.isfinite(co):continue
  if w[0]==w[2] and w[1]==w[3]:continue
  for r in [5000,40000]:
   pole=.5-r*co
   yield row(f'measured_half{chosen}_{r}',angle,r,0,pole,0.)
  chosen+=1
  if chosen==3000:break

def main():
 rows=list(expanded());(BUILD/'expanded.in').write_text('\n'.join(rows)+'\n');print('expanded cases',len(rows),flush=True)
 r=execute('reference','expanded');c=execute('candidate','expanded');compare(r,c,'expanded')
if __name__=='__main__':main()
