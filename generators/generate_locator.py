#!/usr/bin/env python3
"""Generate the locator oracle using unchanged pinned Java classes and public ECC83."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
VALIDATION = ROOT / 'validation/freerouting-port-85-locator'
JAVA = Path('/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home')
JAR = ROOT / 'engine/build/libs/freerouting-current-executable.jar'
BOARD_HASH = 'e7af5da592bd5c5d19e0082ddedacea2ba4da2a264b7cfb57c336a0c9c4a80d2'
JAR_HASH = '1c6a98174cb18b9953d38c581073f1e0754d02f7d53372c7d3ec4d8adfaf9e56'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Compare fixtures without rewriting them')
    args = parser.parse_args()
    build = ROOT / 'build/locator/java'
    build.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((ROOT / 'upstream/kicad/thirdparty/autorouter_core/source_manifest.json').read_text())
    sources = []
    for name, entry in manifest['source_files'].items():
        path = ROOT / 'engine' / name
        assert digest(path) == entry['sha256'], name
        sources.append(str(path))
    commit = subprocess.check_output(['git', '-C', str(ROOT / 'engine'), 'rev-parse', 'HEAD'], text=True).strip()
    assert commit == manifest['reference_commit']
    assert digest(JAR) == JAR_HASH
    pins = next(d['reference_jvm_sha256'] for d in manifest['runtime_dependencies'].values()
                if isinstance(d, dict) and 'reference_jvm_sha256' in d)
    for name, expected in pins.items():
        assert digest(JAVA / name) == expected, name
    (VALIDATION / 'reference-pins.json').write_text(json.dumps({
        'commit': commit, 'jar_sha256': JAR_HASH, 'jvm_files': pins, 'source_files': len(sources)
    }, indent=2) + '\n')
    board = ROOT / 'tests/corpus/public/fr-ecc83/prepared/fr-ecc83.dsn'
    assert digest(board) == BOARD_HASH
    support = ['BoardConstructionOracle.java', 'NeutralBoardModel.java', 'NeutralBoardModelOracle.java',
               'NeutralBoardItemsOracle.java', 'LocatorOracle.java']
    commands = [[str(JAVA / 'bin/javac'), '-cp', str(JAR), '-d', str(build), *sources,
                 *[str(ROOT / 'tests/oracle' / name) for name in support]]]
    for name in ['java.json', 'java-repeat.json']:
        commands.append([str(JAVA / 'bin/java'), '-Xmx2g', '-Djava.awt.headless=true', '-cp',
                         str(build) + ':' + str(JAR), 'app.freerouting.board.facade.LocatorOracle',
                         str(board), str(VALIDATION / name)])
    records = []
    with (VALIDATION / 'java.log').open('w') as log:
        for command in commands:
            result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=180)
            records.append({'argv': command, 'exit_code': result.returncode})
            (VALIDATION / 'java-commands.json').write_text(json.dumps(records, indent=2) + '\n')
            result.check_returncode()
    oracle = (VALIDATION / 'java.json').read_bytes()
    assert oracle == (VALIDATION / 'java-repeat.json').read_bytes()
    fixtures = ROOT / 'upstream/kicad/qa/data/pcbnew/autorouter'
    initial = json.loads((ROOT / 'validation/freerouting-port-84-public-corpus/C-input-state.json').read_text())['input']
    prepared = {
        'locator_java.json': oracle,
        'public_ecc83_neutral.json': (json.dumps(initial, separators=(',', ':')) + '\n').encode()
    }
    for name, content in prepared.items():
        if args.check:
            assert (fixtures / name).read_bytes() == content, name
        else:
            (fixtures / name).write_bytes(content)
    data = json.loads(oracle)
    print('Public cases', len(data['publicCases']), 'helpers', len(data['helpers']), flush=True)


if __name__ == '__main__':
    main()
