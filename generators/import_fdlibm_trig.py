#!/usr/bin/env python3
"""Reproduce C++ adaptations from archived original Sun sources; never downloads OpenJDK."""
from pathlib import Path
import re,subprocess,argparse
ROOT=Path(__file__).resolve().parents[2]
D=ROOT/'upstream/kicad/thirdparty/fdlibm'
NAMES=['s_sin','s_cos','k_sin','k_cos','e_rem_pio2','k_rem_pio2']
def generated(n):
    s=(D/(n+'.c.reference')).read_text()
    s=re.sub(r'#ifdef __STDC__\n(.*?)#else\n.*?#endif',r'\1',s,flags=re.S)
    s=s.replace('#include "fdlibm.h"','#include "fdlibm_trig_internal.h"\nnamespace FREEROUTING_FDLIBM {')
    for a,b in {'__kernel_sin':'KernelSin','__kernel_cos':'KernelCos','__ieee754_rem_pio2':'RemPio2','__kernel_rem_pio2':'KernelRemPio2','__HI':'High','__LO':'Low'}.items(): s=s.replace(a,b)
    s=re.sub(r'\b(sin|cos)\(double x\)',lambda m:m[1].capitalize()+'(double x)',s)
    s=re.sub(r'(High|Low)\((\w+)\)\s*=\s*([^;]+);',r'Set\1(\2,\3);',s)
    # Word setters preserve the other word: seed objects before their two stores.
    s=s.replace('double z,w,t,r,fn;','double z=0,w,t,r,fn;').replace('double a,hz,z,r,qx;','double a,hz,z,r,qx=0;')
    # e0 is [-3,1000] here; multiplication is defined, unlike a negative signed shift.
    s=s.replace('(e0<<20)','(e0*1048576)')
    for f in ['fabs','floor','scalbn']: s=re.sub(r'\b'+f+r'\(', 'std::'+f+'(',s)
    return subprocess.check_output(['/opt/homebrew/opt/llvm/bin/clang-format','--style=file:'+str(ROOT/'upstream/kicad/_clang-format')],input='\n'.join(line.rstrip() for line in (s+'\n}\n').splitlines())+'\n',text=True)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    for n in NAMES:
        s=generated(n);target=D/('fdlibm_'+n+'.cpp')
        if args.check: assert target.read_text()==s,target
        else: target.write_text(s)
