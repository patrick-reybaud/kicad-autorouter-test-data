#!/usr/bin/env python3
"""Neutral board model of a public corpus board as the application prepares it (tranche 120, J6 fidelity gate).

The bench preparation (tests/corpus/bench/pipeline.py prepare) loads a DSN with the pinned source and exports the
neutral model (NBF1) of the board as DsnReader produced it (before the application's post-load overrides); this
script archives that model as the QA fixture public_<board>_neutral.json, which the faithful board builder must
reproduce from the KiCad board. By default (D120-3) the DSN is the one the fork's own exporter writes for the QA
copy of the board (qa_autorouter_kicad, test FreeroutingExportCorpusDsn with FREEROUTING_CORPUS_DSN_DIR set).
"""
import argparse, gzip, hashlib, json, subprocess, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'upstream/kicad/qa/data/pcbnew/autorouter'
DEFAULT_BOARDS = ['kicad-ecc83', 'pcbench-avr-isp', 'kicad-hierarchy', 'pcbench-ulx4m']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--boards', nargs='+', default=DEFAULT_BOARDS)
    parser.add_argument('--check', action='store_true', help='Compare the fixtures without rewriting them')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/public-neutral')
    parser.add_argument('--exporter', choices=['corpus', 'fork'], default='fork',
                        help="'corpus': the archived DSN of the manifest (KiCad 10.0.1); 'fork': DSN exported by the fork's own "
                             "exporter (D120-3), read from --dsn-dir")
    parser.add_argument('--dsn-dir', type=Path, default=ROOT / 'build/fork-dsn',
                        help='Directory of <board>.dsn files written by qa_autorouter_kicad (FREEROUTING_CORPUS_DSN_DIR)')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    corpus = json.loads((ROOT / 'tests/corpus/public/manifest.json').read_text())
    fixtures = {f['id']: f for f in corpus['fixtures']}
    dsn_of = {}
    for board in args.boards:
        if args.exporter == 'corpus':
            dsn = ROOT / fixtures[board]['dsn']
            assert hashlib.sha256(dsn.read_bytes()).hexdigest() == fixtures[board]['dsn_sha256'], board
        else:
            dsn = args.dsn_dir / f'{board}.dsn'
            assert dsn.exists(), dsn
        dsn_of[board] = dsn
    command = [sys.executable, str(ROOT / 'tests/corpus/bench/pipeline.py'), 'prepare', '--boards', *args.boards,
               '--output', str(args.output)]
    if args.exporter == 'fork':
        command += ['--dsn-dir', str(args.dsn_dir)]
    with (args.output / 'prepare.log').open('w') as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=1800)
    provenance = {'reference_java': corpus['reference_java'], 'boards': {}}
    for board in args.boards:
        prepared = json.loads(gzip.decompress((args.output / board / 'input.json.gz').read_bytes()))
        assert prepared['sourceAccepted'], board
        content = (json.dumps(prepared['model'], separators=(',', ':')) + '\n').encode()
        # 'corpus' fixtures keep the historical names of the bench chain; 'fork' fixtures are the faithful builder's.
        target = FIXTURES / (f'faithful_{board}_neutral.json' if args.exporter == 'fork'
                             else f'public_{board.replace("pcbench-", "").replace("kicad-", "kicad-")}_neutral.json')
        if args.check:
            assert target.read_bytes() == content, board
        else:
            target.write_bytes(content)
        provenance['boards'][board] = {'exporter': args.exporter, 'dsn': str(dsn_of[board].relative_to(ROOT)) if dsn_of[board].is_relative_to(ROOT) else str(dsn_of[board]),
                                       'dsn_sha256': hashlib.sha256(dsn_of[board].read_bytes()).hexdigest(), 'fixture': target.name,
                                       'fixture_sha256': hashlib.sha256(content).hexdigest(),
                                       'items': len(prepared['model']['items']),
                                       'lastId': prepared['model']['communication']['lastId']}
        print(board, target.name, 'items', len(prepared['model']['items']), flush=True)
    (args.output / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')


if __name__ == '__main__':
    main()
