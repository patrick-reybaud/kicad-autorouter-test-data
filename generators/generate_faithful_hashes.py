#!/usr/bin/env python3
"""Decision hashes of the reference after routing the fork-exported corpus boards (tranche 120, D120-3).

Reads the outputs of tests/corpus/bench/pipeline.py probe --dsn-dir (Java run of the actual batch loop on the DSN
the fork's exporter wrote, native replay of the neutral model) and archives, per board, the application-resolved
settings and the decision hash the reference reached, as the QA fixture faithful_decision_hashes.json. The faithful
builder test routes the board built from the KiCad file with the same settings and must reach the same hash.
"""
import argparse, gzip, hashlib, json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'upstream/kicad/qa/data/pcbnew/autorouter'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--probe', action='append', required=True, metavar='DIR',
                        help='Probe output directory (pipeline.py probe --output); may be repeated')
    parser.add_argument('--check', action='store_true', help='Compare the fixture without rewriting it')
    args = parser.parse_args()
    boards = {}
    for probe in args.probe:
        probe = Path(probe)
        for folder in sorted(p for p in probe.iterdir() if p.is_dir()):
            prepared = json.loads(gzip.decompress((folder / 'input.json.gz').read_bytes()))
            java = json.loads((folder / 'probe-0-java.json').read_text())
            native = json.loads((folder / 'probe-0-native.json').read_text())
            java_hash = java['runs'][0]['deterministic']['decisionHash']
            native_hash = native['runs'][0]['deterministic']['decisionHash']
            assert java_hash == native_hash, (folder.name, java_hash, native_hash)
            provenance = json.loads((folder / 'provenance.json').read_text())
            seconds = java['runs'][0]['routeNanoseconds'] / 1e9
            boards[folder.name] = {'resolvedSettings': prepared['resolvedSettings'], 'decisionHash': java_hash,
                                   'maxPasses': prepared['input']['maxPasses'],
                                   # Boards the reference routes in more than a minute run only on request
                                   # (FREEROUTING_FAITHFUL_LONG_BOARDS=1) in the routine QA suite.
                                   'long': seconds > 60, 'javaSeconds': round(seconds, 1),
                                   'dsn_sha256': provenance['dsn_sha256'],
                                   'javaPasses': java['runs'][0].get('passes'),
                                   'java_commit': provenance['java_commit']}
    content = (json.dumps({'version': 'FDH1', 'boards': boards}, indent=1, sort_keys=True) + '\n').encode()
    target = FIXTURES / 'faithful_decision_hashes.json'
    if args.check:
        assert target.read_bytes() == content
    else:
        target.write_bytes(content)
    for name, entry in boards.items():
        print(name, 'maxPasses', entry['maxPasses'], 'javaPasses', entry['javaPasses'], entry['decisionHash'])
    print('sha256', hashlib.sha256(content).hexdigest())


if __name__ == '__main__':
    main()
