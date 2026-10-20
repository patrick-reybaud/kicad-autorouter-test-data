#!/usr/bin/env python3
"""Qualify the timespan parsing of TextManager against unchanged Java."""
import argparse,hashlib,json,os,random,subprocess,tempfile
from pathlib import Path
from collections import Counter
ROOT=Path(__file__).resolve().parents[2]
DEST=ROOT/'upstream/kicad/qa/data/pcbnew/autorouter/timespan_java.tsv'

def encode(value):
    if value is None:return '\\0'
    if value=='':return '\\e'
    escapes={' ':'\\s','\t':'\\t','\n':'\\n','\r':'\\r','\f':'\\f','\v':'\\v','\\':'\\\\'}
    return ''.join(escapes.get(c,c) for c in value)

def cases():
    rng=random.Random(20260923)
    # The shapes the documentation names, and the ones a settings file can still hold.
    fixed=[None,'','  ','\t','60','0','1','3600','00:00','01:30','1:30','12:34:56','99:99:99',
           '+5','-5','-0','+0','5.','5,','5.0','5.5','-5.5','-0.5','-0.0','0.5','5.000000000',
           '5.0000000000','5.123456789','5.1234567890','1:-30.5','-1:30.5','1:2:3:4',':','::',':30',
           '30:','1:','1::','::1','1: 2','a','5s','5 ','  5','1e3','0x10','2147483647','2147483648',
           '-2147483648','-2147483649','596523','596524','-596523','-596524','35791394','35791395',
           '9223372036854775807','9223372036854775808','-9223372036854775808','-9223372036854775809',
           '596523:35791394:2147483647','1:2:99999999999999999999','.5','-.5','5..5','5.5.5','1:2.3:4',
           '-9223372036854775808.5','-9223372036854775808.0','9223372036854775807.5','0.000000001',
           '-0.000000001','-1.000000001','9223372036854775807:0:0','0:0:9223372036854775807',
           '1:9223372036854775807','2562047788015215:0:8','2562047788015215:0:7','-2562047788015215:0:-9',
           '153722867280912930:0','153722867280912931:0','0:153722867280912930','4611686018427387903:1']
    for value in fixed:yield encode(value)
    alphabet='0123456789::..,,-+ hHmMsSxPT\t'
    for _ in range(20000):
        yield encode(''.join(rng.choice(alphabet) for _ in range(rng.randrange(0,9))))
    for _ in range(4000):
        parts=[str(rng.choice([0,1,9,59,60,99,rng.randrange(-10**11,10**11)])) for _ in range(rng.randrange(1,5))]
        if rng.random()<0.4:parts[-1]+=rng.choice(['.','.5','.05',',25','.000000001','.1234567890'])
        yield encode(':'.join(parts))

PINNED='/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home'

def pinned_jdk():
    """The fixture only means something if it comes from the JVM the port is qualified against."""
    for candidate in [os.environ.get('JAVA_HOME'),PINNED]:
        if not candidate:continue
        java=Path(candidate)/'bin/java'
        if not java.is_file():continue
        banner=subprocess.run([str(java),'-version'],capture_output=True,text=True).stderr
        if 'version "25' in banner:return Path(candidate)
    raise SystemExit(f'no OpenJDK 25 found; set JAVA_HOME or install it at {PINNED}')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    source=ROOT/'engine/src/main/java/app/freerouting/util/TextManager.java'
    manifest=json.loads((ROOT/'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    key=str(source.relative_to(ROOT/'engine'))
    digest=hashlib.sha256(source.read_bytes()).hexdigest()
    assert manifest['source_files'][key]['sha256']==digest,key
    home=pinned_jdk()
    support=ROOT/'engine/build/libs/freerouting-current-executable.jar'
    vectors=list(dict.fromkeys(cases()))
    with tempfile.TemporaryDirectory(prefix='freerouting-timespan-') as tmp:
        helper=ROOT/'tests/oracle/TimespanOracle.java'
        subprocess.run([str(home/'bin/javac'),'-cp',str(support),'-d',tmp,str(source),str(helper)],check=True)
        generated=subprocess.check_output([str(home/'bin/java'),'-cp',tmp+os.pathsep+str(support),
                                           'app.freerouting.util.TimespanOracle'],
                                          input='\n'.join(vectors)+'\n',text=True,timeout=240)
    rows=[r for r in generated.splitlines() if ' | ' in r];assert len(rows)==len(vectors),(len(rows),len(vectors))
    generated='\n'.join(rows)+'\n'
    if args.check:assert DEST.read_text()==generated,'Reference output changed'
    else:DEST.write_text(generated)
    print(json.dumps({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT/'engine',text=True).strip(),
                      'source_sha256':{key:digest},
                      'support_jar_sha256':hashlib.sha256(support.read_bytes()).hexdigest(),
                      'cases':len(rows),
                      'parsed':sum(1 for r in rows if not r.rsplit(' ',1)[1]=='null'),
                      'fixture':str(DEST.relative_to(ROOT)),
                      'fixture_sha256':hashlib.sha256(generated.encode()).hexdigest(),
                      'java_version':subprocess.run([str(home/'bin/java'),'-version'],capture_output=True,text=True,check=True).stderr},indent=2))
if __name__=='__main__':main()
