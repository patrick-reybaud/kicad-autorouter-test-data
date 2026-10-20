"""Lossless RFC 6902 encoding for complete observed board snapshots.

This changes storage only: every snapshot is reconstructed and compared with the
original Java observation before the native fixture is written.
"""
import copy
import gzip
import hashlib
import json


def differences(before, after, path=""):
    if type(before) is not type(after):
        return [{"op": "replace", "path": path, "value": after}]
    if isinstance(before, dict) and before.keys() == after.keys():
        result = []
        for key in before:
            escaped = key.replace("~", "~0").replace("/", "~1")
            result.extend(differences(before[key], after[key], path + "/" + escaped))
        return result
    if isinstance(before, list) and len(before) == len(after):
        result = []
        for index, (a, b) in enumerate(zip(before, after)):
            result.extend(differences(a, b, path + "/" + str(index)))
        return result
    return [] if before == after else [{"op": "replace", "path": path, "value": after}]


def restore(before, patch):
    result = copy.deepcopy(before)
    for operation in patch:
        assert operation["op"] == "replace"
        if operation["path"] == "":
            result = copy.deepcopy(operation["value"])
            continue
        parts = [p.replace("~1", "/").replace("~0", "~")
                 for p in operation["path"].split("/")[1:]]
        node = result
        for part in parts[:-1]:
            node = node[int(part) if isinstance(node, list) else part]
        node[int(parts[-1]) if isinstance(node, list) else parts[-1]] = copy.deepcopy(operation["value"])
    return result


def encode(raw, output):
    observed = json.loads(raw)
    snapshots = observed.pop("snapshots")
    patches = []
    previous = None
    for state in snapshots:
        patch = differences(previous, state)
        restored = restore(previous, patch)
        assert restored == state
        patches.append(patch)
        previous = restored
    observed["snapshotPatches"] = patches
    observed["snapshotEncoding"] = "RFC6902: each patch applies to the preceding complete snapshot; initial value null"
    # Preserve the exact original Java bytes as well as the losslessly encoded fixture.
    zipped = gzip.compress(raw, mtime=0)
    assert gzip.decompress(zipped) == raw
    (output / "java.json.gz").write_bytes(zipped)
    encoded = (json.dumps(observed, separators=(",", ":"), ensure_ascii=True) + "\n").encode()
    (output / "snapshot-encoding.json").write_text(json.dumps({
        "complete_snapshots": len(snapshots), "all_reconstructed_equal_to_java": True,
        "raw_java_bytes": len(raw), "encoded_fixture_bytes": len(encoded),
        "raw_java_sha256": hashlib.sha256(raw).hexdigest(),
        "encoded_fixture_sha256": hashlib.sha256(encoded).hexdigest(),
        "tolerances": [], "omitted_fields": []}, indent=2) + "\n")
    return encoded
