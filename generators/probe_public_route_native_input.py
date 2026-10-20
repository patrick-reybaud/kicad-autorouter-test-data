#!/usr/bin/env python3
"""Probe C's actual imported input against the current native constructor; never route."""
from pathlib import Path
import json,subprocess,hashlib,argparse
parser=argparse.ArgumentParser()
parser.add_argument("--sanitized",action="store_true")
args=parser.parse_args()
prefix="C-native-input-sanitized" if args.sanitized else "C-native-input"
root=Path(__file__).resolve().parents[2];repo=root/'upstream/kicad';build=root/'build/public-route-native-input';build.mkdir(exist_ok=True)
val=root/'validation/freerouting-port-84-public-corpus';headers=repo/'qa/tests/pcbnew/autorouter'
for name in ['board_construction_test_support.h','neutral_board_model_test_support.h','neutral_board_items_test_support.h']:
 s=(headers/name).read_text()
 if name=='board_construction_test_support.h':
  before='                s += "~" + Shape( std::optional<TILE_SHAPE>( outline->GetShape( q ).Tile() ) );'
  after='''            {
                const auto shape=outline->GetShape(q);
                if(shape.IsPolygon()){
                    s+="~P:"+Text(shape.Polygon().Corners().size());
                    for(const auto& corner:shape.Polygon().Corners())s+="~"+Point(corner);
                }else s+="~"+Shape(std::optional<TILE_SHAPE>(shape.Tile()));
            }'''
  if before in s:s=s.replace(before,after)
  else:assert "shape.IsPolygon()" in s  # Promoted into shared QA in tranche 87.
 (build/name).write_text(s)
# The only copied-helper modification observes a polygon outline. It neither
# changes geometry nor preloads state. The actual engine library is linked below.
source=build/'probe.cpp';source.write_text(r'''
#define BOOST_TEST_MODULE FirstRouteNativeInputProbe
#include <boost/test/included/unit_test.hpp>
#include "neutral_board_items_test_support.h"
BOOST_AUTO_TEST_CASE( ProbeOnly )
{
    std::ifstream input(INPUT_PATH),reference(REFERENCE_PATH);
    JSON model,wanted;input>>model;reference>>wanted;
    auto description=model.at("input");int finalAngle=description.at("metadata").at("rules").at(0);
    description["metadata"]["rules"][0]=model.at("constructionTraceAngleRestriction");
    ITEMS_MODEL replay(description);
    replay.board->rules->SetTraceAngleRestriction(static_cast<ANGLE_RESTRICTION>(finalAngle));
    auto actual=replay.Snapshot();auto expected=wanted.at("expected");
    std::ofstream(OUTPUT_PATH)<<JSON({{"actual",actual},{"expected",expected},{"equal",actual==expected}}).dump(2)<<"\n";
    BOOST_CHECK_MESSAGE(actual==expected,"Complete initial state differs");
    for(auto it=expected.begin();it!=expected.end();++it)BOOST_CHECK_MESSAGE(actual.at(it.key())==it.value(),"Initial state field differs: "<<it.key());
}
''')
lib=root/'build/kicad-cpp/pcbnew/autorouter/engine/libautorouter_core.a';binary=build/('probe-sanitized' if args.sanitized else 'probe')
paths={'INPUT_PATH':val/'C-input-state.json','REFERENCE_PATH':val/'C-input-state.json','OUTPUT_PATH':val/(prefix+'-probe.json')}
cmd=['/usr/bin/clang++','-std=c++20','-ffp-contract=off','-O1','-I/opt/homebrew/include','-I'+str(repo/'thirdparty/nlohmann_json'),'-I'+str(repo/'pcbnew/autorouter/engine'),*[f'-D{k}="{v}"' for k,v in paths.items()],str(source),str(lib),'-o',str(binary)]
records={'library_sha256':hashlib.sha256(lib.read_bytes()).hexdigest(),'compile_argv':cmd,'copied_headers_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in build.glob('*.h')},'probe_source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'route_executed':False,'sanitized':args.sanitized}
if args.sanitized:
 commands=json.loads((root/'validation/freerouting-port-83-board-entry/final-sanitizer/sanitizer-commands.json').read_text())
 objects=[Path(unit['output']) for unit in commands['parallel_compile'] if '/pcbnew/autorouter/engine/' in unit['source'] or '/thirdparty/fdlibm/' in unit['source'] or '/thirdparty/java25_math/' in unit['source']]
 assert objects and all(path.exists() for path in objects)
 index=cmd.index(str(lib));cmd[index:index+1]=[str(path) for path in objects]
 cmd[1:1]=['-g','-fno-omit-frame-pointer','-fsanitize=address,undefined','-fno-sanitize-recover=all']
 records.pop('library_sha256')
 records['linked_engine_objects_sha256']={str(path.relative_to(root)):hashlib.sha256(path.read_bytes()).hexdigest() for path in objects}
with (val/(prefix+'-build.log')).open('w') as log:records['compile_exit_code']=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT).returncode
(val/(prefix+'-probe-summary.json')).write_text(json.dumps(records,indent=2)+'\n')
assert records['compile_exit_code']==0
cmd=[str(binary),'--report_level=detailed','--color_output=no'];records['test_argv']=cmd
with (val/(prefix+'-tests.log')).open('w') as log:records['test_exit_code']=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT).returncode
if paths['OUTPUT_PATH'].exists():
 d=json.loads(paths['OUTPUT_PATH'].read_text());records['state_equal']=d['equal'];records['different_fields']=[k for k in d['expected'] if d['actual'][k]!=d['expected'][k]]
 if not d['equal']:
  def first(a,e,path=''):
   if type(a)!=type(e):return {'path':path,'actual':a,'expected':e}
   if isinstance(e,dict):
    for k in e:
     if k not in a:return {'path':path+'/'+k,'actual':'<missing>','expected':e[k]}
     if a[k]!=e[k]:return first(a[k],e[k],path+'/'+k)
   elif isinstance(e,list):
    if len(a)!=len(e):return {'path':path+'/length','actual':len(a),'expected':len(e)}
    for i in range(len(e)):
     if a[i]!=e[i]:return first(a[i],e[i],path+'/'+str(i))
   else:return {'path':path,'actual':a,'expected':e}
  records['first_difference']=first(d['actual'],d['expected'])
(val/(prefix+'-probe-summary.json')).write_text(json.dumps(records,indent=2)+'\n');print(json.dumps({k:v for k,v in records.items() if not k.endswith('argv')}))

raise SystemExit(records["test_exit_code"])
