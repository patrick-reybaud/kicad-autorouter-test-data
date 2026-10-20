#!/usr/bin/env python3
"""Test-only Sun fdlibm arithmetic candidate. Never edits the accepted native dependency.

Explicit FMA and reduction scheduling are an experiment motivated by the observed
Java 25 aarch64 intrinsic. No OpenJDK source file is copied into this candidate.
The original Sun files/notices remain the input. The exact generated patch is archived.
Large-argument KernelRemPio2 is unchanged and is NOT claimed to match HotSpot.
"""
from pathlib import Path
import difflib,hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
BUILD=ROOT/'build/trig-runtime';VAL=ROOT/'validation/freerouting-port-71-A-runtime'
FD=ROOT/'upstream/kicad/thirdparty/fdlibm';J=Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home').resolve()
def build():
 dest=BUILD/'candidate-source';dest.mkdir(parents=True,exist_ok=True);patch=[];hashes={}
 def replace(s,a,b):
  assert a in s,a
  return s.replace(a,b)
 for p in sorted(FD.glob('fdlibm_*')):
  if p.suffix not in ['.h','.cpp'] or p.stem=='fdlibm_acos':continue
  old=p.read_text();s=old
  if p.name=='fdlibm_k_sin.cpp':
   a=s.index('    int    ix;');b=s.index('    z = x * x;',a);s=s[:a]+s[b:]
   s=replace(s,'S2 + z * ( S3 + z * ( S4 + z * ( S5 + z * S6 ) ) )','std::fma(z, std::fma(z, std::fma(z, std::fma(z, S6, S5), S4), S3), S2)')
   s=replace(s,'return x + v * ( S1 + z * r );','return std::fma(v, std::fma(z, r, S1), x);')
   s=replace(s,'return x - ( ( z * ( half * y - v * r ) - y ) - v * S1 );','return x + std::fma(v, S1, std::fma(-z, std::fma(-v, r, half*y), y));')
  if p.name=='fdlibm_k_cos.cpp':
   s=replace(s,'double KernelCos( double x, double y )','double KernelCos( double x, double y, int ix )')
   a=s.index('    int    ix;');b=s.index('    z = x * x;',a);s=s[:a]+s[b:]
   s=replace(s,'r = z * ( C1 + z * ( C2 + z * ( C3 + z * ( C4 + z * ( C5 + z * C6 ) ) ) ) );','r = std::fma(z, std::fma(z, std::fma(z, std::fma(z, std::fma(z,C6,C5),C4),C3),C2),C1);\n    double zz = z*z;\n    double xy = x*y;')
   s=replace(s,'if( ix < 0x3FD33333 )','if( ix <= 0x3FD33333 )')
   s=replace(s,'return one - ( 0.5 * z - ( z * r - x * y ) );','return one - std::fma(0.5, z, std::fma(-zz, r, xy));')
   s=replace(s,'hz = 0.5 * z - qx;','hz = std::fma(0.5, z, -qx);')
   s=replace(s,'return a - ( hz - ( z * r - x * y ) );','return a - ( hz - std::fma(zz, r, -xy) );')
  if p.name=='fdlibm_trig_internal.h':s=replace(s,'KernelCos( double x, double y )','KernelCos( double x, double y, int ix )')
  if p.name in ['fdlibm_s_sin.cpp','fdlibm_s_cos.cpp']:
   cosine=p.name=='fdlibm_s_cos.cpp'
   s=replace(s,'ix &= 0x7fffffff;','ix &= 0x7fffffff;\n    if( ix < 0x3e400000 ) return '+('1.0;' if cosine else 'x;'))
   s=replace(s,'KernelCos( y[0], y[1] )','KernelCos( y[0], y[1], ix )')
   if cosine:s=replace(s,'KernelCos( x, z )','KernelCos( x, z, ix )')
  if p.name=='fdlibm_e_rem_pio2.cpp':
   s=replace(s,'t * invpio2 + half','std::fma(t, invpio2, half)')
   s=replace(s,'r = t - fn * pio2_1;','r = std::fma(-fn, pio2_1, t);')
   s=replace(s,'n < 32 &&','n <= 32 &&')
   s=replace(s,'fn * pio2_2t - ( ( t - r ) - w )','std::fma(fn, pio2_2t, w - (t-r))')
   s=replace(s,'fn * pio2_3t - ( ( t - r ) - w )','std::fma(fn, pio2_3t, w - (t-r))')
  (dest/p.name).write_text(s)
  hashes[p.name]={'input_sha256':hashlib.sha256(old.encode()).hexdigest(),'candidate_sha256':hashlib.sha256(s.encode()).hexdigest()}
  patch.extend(difflib.unified_diff(old.splitlines(True),s.splitlines(True),fromfile='accepted-sun/'+p.name,tofile='test-only-fma/'+p.name))
 (VAL/'candidate-arithmetic.patch').write_text(''.join(patch))
 sources=sorted(dest.glob('fdlibm_[sek]_*.cpp'));cmd=['clang++','-std=c++20','-O2','-Wall','-Wextra','-Werror','-ffp-contract=off','-dynamiclib','-I'+str(dest),'-I'+str(J/'include'),'-I'+str(J/'include/darwin'),*map(str,sources),str(ROOT/'tests/oracle/trig_jni.cpp'),'-o',str(BUILD/'libtrig-fma.dylib')]
 subprocess.run(cmd,check=True)
 (VAL/'candidate-build.json').write_text(json.dumps({'files':hashes,'argv':cmd,'large_reduction':'unchanged Sun KernelRemPio2; not claimed to match HotSpot'},indent=2)+'\n')
if __name__=='__main__':build()
