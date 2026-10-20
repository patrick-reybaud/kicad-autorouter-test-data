# Reference recordings for the KiCad built-in autorouter

The built-in autorouter proposed for KiCad is a C++ translation of
[Freerouting](https://github.com/freerouting/freerouting) at revision 97ba404 (v2.4.1 plus two
fixes). Its engine test suite, `qa_autorouter_core`, does not check a few end results: it replays,
decision by decision, what the Java engine did on the same inputs. This repository holds those
recordings, about 400 MB, and the programs that produced them. They are too large for the KiCad
tree, so KiCad only knows where to find them.

## Using it with KiCad

```sh
git clone https://github.com/patrick-reybaud/kicad-autorouter-test-data.git
cmake -S kicad -B build -DKICAD_BUILTIN_AUTOROUTER=ON \
      -DKICAD_AUTOROUTER_TEST_DATA=/absolute/path/to/kicad-autorouter-test-data
cmake --build build --target qa_autorouter_core
ctest --test-dir build -R qa_autorouter_core
```

Without `KICAD_AUTOROUTER_TEST_DATA`, KiCad configures and builds normally and simply leaves
`qa_autorouter_core` out. The KiCad-side suites (`qa_autorouter_kicad`, `qa_autorouter_editor`)
only need the boards and models kept in the KiCad tree, under `qa/data/pcbnew/autorouter/`.

The complete suite takes about 26 minutes on a recent desktop (244 cases); every case is expected
to pass on macOS (AppleClang), Linux (gcc 13 and 14), Windows (MSVC, x64 and ARM64) and under
ASan + UBSan.

## Contents

- The recordings themselves, at the root: `*_java.tsv`, `*_java.tsv.gz`, `*_java.json`,
  `*_java.json.gz`, and the `.partN.gz` shards of the largest ones. Files ending in `.gz` are read
  through `fixture_stream.h` in the KiCad tree, which inflates the gzip twin of the name a test asks
  for. Never convert line endings (`.gitattributes` marks everything as binary).
- `FIXTURES.md`: every file, its size, the generator that wrote it and the KiCad tests that read it.
- `generators/`: the Java probes and the Python drivers that produced each recording, kept as they
  were run. They expect the layout of the working repository they come from (the pinned Freerouting
  sources in `upstream/freerouting`, the KiCad fork in `upstream/kicad`) and a fixed JVM; they are
  provided so that any recording can be regenerated and audited, not as a turnkey tool.
- `LICENSE`: GPL-3.0-or-later, the licence of Freerouting, which the generators link against and
  whose behaviour the recordings capture.

## How the recordings were made

Each generator feeds the Java engine a set of inputs (synthetic cases, or the public boards also
used by the KiCad-side suites: the KiCad demo boards, boards from the pcbench collection, and two
boards attached to Freerouting issues) under a frozen clock and a fixed random seed, and writes every
intermediate decision of the loop under test. The C++ tests read the same inputs, run the translated
code and compare each decision. A recording is regenerated only when the pinned Java source changes,
which the generators refuse to do silently: they check the source manifest first.
