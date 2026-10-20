# Runtime source used by the native TimSort port

`ComparableTimSort.java` is an unmodified Apache-2.0 AOSP source archive, not
compiled into the oracle. The oracle executes `java.util.Arrays.sort` from the
pinned reference JVM. The archive documents the licensed basis for the native
C++ adaptation in `upstream/kicad/thirdparty/java_timsort/`.

Source: [AOSP libcore, android-4.4_r1](https://android.googlesource.com/platform/libcore/+/e239af5ac0b3989363b2fed49076fa8a9d69d454/luni/src/main/java/java/util/ComparableTimSort.java).
Commit: `e239af5ac0b3989363b2fed49076fa8a9d69d454`.
SHA-256: `09bae44306cbe6c950583bad01bfe42e4b24f3ce91c8a5a37d40593576d9e329`.
Copyright (C) 2008 The Android Open Source Project. See `LICENSE`.

Runtime provenance and compatibility changes are recorded in
`validation/freerouting-port-8/runtime-reference.json` and the native README.
The separate `../line_sort_merge_lo_inputs.txt` contains twelve input cases
selected by executing the actual Java Line comparator on nearly parallel integer
lines. They deliberately exercise TimSort.mergeLo's contract exception. The
fixture generator recomputes all their expected results from unchanged Java sources.
