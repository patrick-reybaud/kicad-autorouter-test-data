#!/usr/bin/env python3
"""Gate A: original Java vs native fdlibm, then actual Java decision propagation.

The candidate is a TEST-ONLY copy with exactly four call-site token substitutions
in FloatPoint/Direction. This is not a C++ geometry port or accepted Math tolerance.
"""
import argparse,collections,difflib,hashlib,json,math,os,random,struct,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
VAL=ROOT/'validation/freerouting-port-68-A'
BUILD=ROOT/'build/trig-decision'
FD=ROOT/'upstream/kicad/thirdparty/fdlibm'
J=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'))
JAR=ROOT/'engine/build/libs/freerouting-current-executable.jar'
FIXTURE=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/trig_fdlibm_java.tsv'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def bits(x):return struct.pack('>d',x).hex()
def value(x):return struct.unpack('>d',bytes.fromhex(x))[0]
def nearby(x,delta):
    if delta==0:return x
    for _ in range(abs(delta)):x=math.nextafter(x,math.inf if delta>0 else -math.inf)
    return x

def inputs():
    rng=random.Random(26091868)
    for i,x in enumerate([0.1,-0.1,0.,math.pi/2,math.pi]):yield f'T old2_{i} {bits(x)}'
    # Finite raw values spanning every exponent, plus nonfinite payloads.
    for i in range(750000):yield f'T raw{i} {rng.getrandbits(64):016x}'
    for i in range(200000):yield f'T board{i} {bits(rng.uniform(-2*math.pi,2*math.pi))}'
    for raw in [0,0x0010000000000000,0x3e40000000000000,0x3fd3333300000000,0x3fe9000000000000,0x3fe921fb00000000,0x3ff921fb54442d18,0x4002d97c00000000,0x413921fb00000000,0x7fefffffffffffff,0x7ff0000000000000,0x7ff8000000000000]:
        for delta in range(-32,33):
            for sign in [0,1<<63]:
                if 0<=raw+delta<1<<63:yield f'T edge{raw:x}_{delta}_{sign>>63} {(raw+delta)|sign:016x}'
    # Re-run the exact tranche-13 input generation in addition to the wide corpus.
    rng=random.Random(260917)
    for i in range(50000):yield f'T old13raw{i} {rng.getrandbits(64):016x}'
    for i in range(10000):yield f'T old13unit{i} {bits(rng.uniform(-1,1))}'

def geometry():
    rng=random.Random(260918)
    def row(name,a,r=5000,k=0,px=0.,py=0.):return f'G {name} {bits(a)} {r} {k} {bits(px)} {bits(py)}'
    for i in range(3601):yield row(f'deg{i}',i*math.pi/1800)
    yield row('negative_zero',-0.)
    for i in range(2500):yield row(f'random{i}',rng.uniform(-2*math.pi,2*math.pi),5000,0,200000.,-200000.)
    # At grid half-integers: possible decisions, not a numerical tolerance.
    for radius in [5000,40000,10000]:
        for k in range(1,radius,37):
            a=math.acos((k+.5)/radius)
            for delta in range(-2,3):yield row(f'grid{radius}_{k}_{delta}',nearby(a,delta),radius,k)
    # Noninteger poles are legal FloatPoint inputs, common in approximate geometry.
    for i in range(1000):yield row(f'half_pole{i}',rng.uniform(-math.pi,math.pi),5000,i,0.5,-0.5)

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
    VAL.mkdir(exist_ok=True);BUILD.mkdir(exist_ok=True)
    base=ROOT/'engine/src/main/java/app/freerouting'
    sources=sorted((base/'geometry/planar').glob('*.java'))+[base/'datastructures'/f'{n}.java' for n in ['BigIntAux','Signum']]+[base/'autoroute/maze/MazeListElement.java',base/'autoroute/expansion/ExpansionDoor.java']
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    hashes={str(s.relative_to(ROOT/'engine')):sha(s) for s in sources}
    for path,h in hashes.items():assert manifest['source_files'][path]['sha256']==h,path
    commands=[]
    def run(cmd,**kw):
        commands.append(list(map(str,cmd)));return subprocess.run(list(map(str,cmd)),check=True,**kw)
    src=sorted(FD.glob('fdlibm_[sek]_*.cpp'))
    library=BUILD/'libtrig.dylib'
    run(['clang++','-std=c++20','-O2','-Wall','-Wextra','-Werror','-ffp-contract=off','-dynamiclib','-I'+str(FD),'-I'+str(J/'include'),'-I'+str(J/'include/darwin'),*src,ROOT/'tests/oracle/trig_jni.cpp','-o',library])
    helpers=[ROOT/'tests/oracle/NativeTrig.java',ROOT/'tests/oracle/TrigDecisionOracle.java']
    replacements=[];candidate_sources=[];diff=[]
    for source in sources:
        if source.name in ['FloatPoint.java','Direction.java']:
            original=source.read_text();candidate=original
            for f in ['sin','cos']:
                assert candidate.count('Math.'+f+'(')==1
                candidate=candidate.replace('Math.'+f+'(','NativeTrig.'+f+'(')
            dest=BUILD/'candidate-source'/source.name;dest.parent.mkdir(exist_ok=True);dest.write_text(candidate)
            candidate_sources.append(dest);replacements.append({'source':str(source.relative_to(ROOT)),'original_sha256':sha(source),'candidate_sha256':sha(dest)})
            diff.extend(difflib.unified_diff(original.splitlines(True),candidate.splitlines(True),fromfile='reference/'+source.name,tofile='test-only-candidate/'+source.name))
        else:candidate_sources.append(source)
    (VAL/'candidate-callsite.patch').write_text(''.join(diff))
    for kind,ss in [('reference',sources),('candidate',candidate_sources)]:run([J/'bin/javac','-cp',JAR,'-d',BUILD/kind,*ss,*helpers])
    trigs=list(inputs());geoms=list(geometry())
    (BUILD/'trig.in').write_text('\n'.join(trigs)+'\n');(BUILD/'geometry.in').write_text('\n'.join(geoms)+'\n')
    def execute(kind,input_name,output_name):
        with (BUILD/input_name).open() as inp,(BUILD/output_name).open('w') as out:
            run([J/'bin/java','--enable-native-access=ALL-UNNAMED','-Xmx1g','-Dfreerouting.trig.library='+str(library),'-cp',str(BUILD/kind)+os.pathsep+str(JAR),'app.freerouting.autoroute.maze.TrigDecisionOracle'],stdin=inp,stdout=out,timeout=180)
    old_rotation=(ROOT/'validation/freerouting-port-2/rotation-reference.tsv').read_text().splitlines()
    (BUILD/'old2.in').write_text('\n'.join(x.split(' | ')[0] for x in old_rotation)+'\n')
    execute('reference','old2.in','old2-reference.out');execute('candidate','old2.in','old2-candidate.out')
    old_ref=(BUILD/'old2-reference.out').read_text().splitlines();old_native=(BUILD/'old2-candidate.out').read_text().splitlines()
    assert old_ref==old_rotation,'Original 150 reference rotations changed'
    old2={'cases':len(old_rotation),'reference_changed':0,'fdlibm_changed_rows':sum(a!=b for a,b in zip(old_ref,old_native,strict=True))}
    execute('reference','trig.in','trig.out')
    stats={'cases':len(trigs),'strict_mismatches':[0,0],'math_mismatches':[0,0],'ulp_distance_histograms':[collections.Counter(),collections.Counter()],'max_ulp_distance':[0,0],'first_math_mismatches':[]}
    fixture=[];old=[0,0]
    with (BUILD/'trig.out').open() as f:
        for i,(input_row,output) in enumerate(zip(trigs,f,strict=True)):
            left,right=output.strip().split(' | ');assert left==input_row
            words=right.split();assert len(words)==6
            for fn in range(2):
                math_bits,strict_bits,native_bits=[int(words[fn+j],16) for j in [0,2,4]]
                if strict_bits!=native_bits:stats['strict_mismatches'][fn]+=1
                if math_bits!=native_bits:
                    stats['math_mismatches'][fn]+=1
                    assert ((math_bits^native_bits)>>63)==0,'unexpected sign/nonfinite mismatch'
                    distance=abs(math_bits-native_bits)
                    stats['ulp_distance_histograms'][fn][distance]+=1;stats['max_ulp_distance'][fn]=max(stats['max_ulp_distance'][fn],distance)
                    if len(stats['first_math_mismatches'])<10:stats['first_math_mismatches'].append({'input':input_row,'function':['sin','cos'][fn],'math':words[fn],'fdlibm':words[fn+4],'ulp_steps':distance})
                    if 'old13' in input_row:old[fn]+=1
            if i%100==0 or input_row.startswith('T edge'):fixture.append(left.split()[-1]+' '+ ' '.join(words[:4]))
    assert stats['strict_mismatches']==[0,0],stats
    stats['old13_60000_math_mismatches']=old
    fixture_text='\n'.join(fixture)+'\n'
    if args.check:assert FIXTURE.read_text()==fixture_text,'Java trig fixture changed'
    else:FIXTURE.write_text(fixture_text)
    execute('reference','geometry.in','reference.out');execute('candidate','geometry.in','candidate.out')
    numerical={'raw','vector_angle','vector_pair_angle','direction_angle'}
    counts=collections.Counter();examples={};changes=[];decision_cases=set();geometry_cases=set()
    ref_lines=(BUILD/'reference.out').read_text().splitlines();cand_lines=(BUILD/'candidate.out').read_text().splitlines()
    assert len(ref_lines)==len(geoms)==len(cand_lines)
    for inp,ref,cand in zip(geoms,ref_lines,cand_lines,strict=True):
        lr,rr=ref.split(' | ');lc,rc=cand.split(' | ');assert lr==lc==inp
        r=dict(x.split('=',1) for x in rr.split());c=dict(x.split('=',1) for x in rc.split());assert r.keys()==c.keys()
        changed={key:{'Math':r[key],'fdlibm':c[key]} for key in r if r[key]!=c[key]}
        if changed:
            geometry_cases.add(inp)
            if set(changed)-numerical:decision_cases.add(inp)
            changes.append({'input':inp,'changes':changed})
        for key in r:
            if r[key]!=c[key]:counts[key]+=1;examples.setdefault(key,{'input':inp,'Math':r[key],'fdlibm':c[key]})
    for key in r:counts.setdefault(key,0)
    geometry_stats={'cases':len(geoms),'cases_with_changed_fields':len(geometry_cases),'cases_with_changed_decisions':len(decision_cases),'changed_fields':dict(counts),'first_by_field':examples,'method':'Original Java source vs test-only source copy with four Math.sin/cos -> NativeTrig call-site substitutions. All downstream algorithms and Java cache identities are the actual source. Door choice is a local two-candidate TreeSet probe, not full maze search.'}
    report={'java_version':run([J/'bin/java','-version'],capture_output=True,text=True).stderr,'reference_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),'source_sha256':hashes,'jar_sha256':sha(JAR),'candidate_replacements':replacements,'old2_rotation':old2,'numeric':stats,'geometry':geometry_stats,'decision':'BLOCKED: downstream decisions differ; Pin and B/C remain unported' if decision_cases else 'No decision flip in this corpus; review domain before accepting','strict_fixture_cases':len(fixture),'strict_fixture_sha256':sha(FIXTURE),'artifacts_sha256':{n:sha(BUILD/n) for n in ['trig.in','trig.out','geometry.in','reference.out','candidate.out','old2.in','old2-reference.out','old2-candidate.out']}}
    text=json.dumps(report,indent=2)+'\n';ch=json.dumps(changes,indent=2)+'\n'
    if args.check:
        assert (VAL/'oracle.json').read_text()==text,'Measured result changed'
        assert (VAL/'decision-flips.json').read_text()==ch,'Decision changes differ'
    else:
        (VAL/'oracle.json').write_text(text);(VAL/'decision-flips.json').write_text(ch)
    (VAL/'oracle-commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    print(json.dumps({'old2_rotation':old2,'numeric':stats,'geometry':geometry_stats,'decision':report['decision'],'fixture_cases':len(fixture)},indent=2))
if __name__=='__main__':main()
