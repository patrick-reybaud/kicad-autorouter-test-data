#!/usr/bin/env python3
"""Check native geometry fixture against immutable Java tranche-71 reference output hashes."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[2]
fixture=root/'upstream/kicad/qa/data/pcbnew/autorouter/rotation_java.tsv'
rows=fixture.read_bytes().splitlines(keepends=True)
assert len(rows)==95787
hashes=[hashlib.sha256(b''.join(rows[:14547])).hexdigest(),hashlib.sha256(b''.join(rows[14547:])).hexdigest()]
assert hashes==['1fce0f6d40f1f09eb4a107cbcb642f4b132ad85ff702cdbdcccc419d1dd1932a','e08a9f2d48bb06ef4f8b142dec4d0aae30dde99ffb7a6c142edc32500d868554']
print(json.dumps({'original_cases':14547,'expanded_cases':81240,'original_java_output_hashes':hashes,'native_fixture_sha256':hashlib.sha256(fixture.read_bytes()).hexdigest(),'expected_fields':sum(len(r.split(b' | ')[1].split()) for r in rows)},indent=2))
