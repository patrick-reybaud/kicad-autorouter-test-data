#!/usr/bin/env python3
"""Index existing pinned-source diagnostics; do not infer unlogged branch decisions."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[2]
VALIDATION = ROOT / "validation/freerouting-port-83-board-entry"


def index_reference_decisions():
    events = []
    for line_number, line in enumerate(
        (VALIDATION / "C-java-route-trace.log").read_text().splitlines(), start=1
    ):
        start = line.find("compare_")
        if start < 0:
            continue
        message = line[start:]
        net = re.search(r"\bnet=(\d+)", message)
        events.append({
            "id": f"C/java/event-{len(events) + 1:06d}",
            "log_line": line_number,
            "net": int(net.group(1)) if net else None,
            "message": message,
        })
    result = {
        "native_execution": False,
        "comparison_executed": False,
        "events": events,
        "first_recorded_insertion_failure": next(
            (event for event in events if "decision=FAIL" in event["message"]), None
        ),
        "note": "Ordered existing source diagnostics; not an exhaustive trace of all branch decisions or a Java/native divergence.",
    }
    path = VALIDATION / "C-reference-decisions.json"
    path.write_text(json.dumps(result, indent=2) + "\n")
    return result


if __name__ == "__main__":
    result = index_reference_decisions()
    print(json.dumps({"events": len(result["events"]),
                      "first_failure": result["first_recorded_insertion_failure"]}))
