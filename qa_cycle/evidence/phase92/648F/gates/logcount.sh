#!/bin/bash
# (copied from 648B gates-c952121f6 / 648E / 648A; hardened in the 648F CodeRabbit round 2)
# usage: logcount.sh <backend.log> [fromISO] [toISO] — level counts + WARN/ERROR breakdown (JSON log lines)
# An omitted (or empty) bound is open. Bounds and @timestamps are compared as parsed UTC instants,
# so 14:35:42.500Z is inside a 14:35:42Z lower bound. A missing/unreadable log fails (exit 1);
# a readable log with no matching lines is valid (prints {}).
set -euo pipefail
F=${1:?usage: logcount.sh <backend.log> [fromISO] [toISO]}
FROM=${2:-}; TO=${3:-}
if [[ ! -r "$F" ]]; then
  echo "logcount.sh: cannot read log file: $F" >&2
  exit 1
fi
# grep exits 1 on no match: that is a valid (empty) result, any other status is an error
{ sed 's/^e2e-backend *| //' "$F" | grep '^{' || [[ $? -eq 1 ]]; } | python3 -c '
import sys, json, re, collections
from datetime import datetime, timezone

def instant(s):
    # ISO-8601 with Z or an offset and up to nanosecond fractions (Python takes at most 6 digits)
    s = s.strip().replace("Z", "+00:00")
    m = re.match(r"^(.*T\d\d:\d\d:\d\d)(\.\d+)?(.*)$", s)
    if m:
        frac = (m.group(2) or ".0")[1:7].ljust(6, "0")  # pad/trim to 6 digits (Python 3.10)
        s = m.group(1) + "." + frac + m.group(3)
    t = datetime.fromisoformat(s)
    return t if t.tzinfo else t.replace(tzinfo=timezone.utc)

frm = instant(sys.argv[1]) if sys.argv[1] else None
to = instant(sys.argv[2]) if sys.argv[2] else None
lv = collections.Counter(); br = collections.Counter()
for l in sys.stdin:
    try: j = json.loads(l)
    except Exception: continue
    raw = j.get("@timestamp")
    if not raw: continue
    ts = instant(raw)
    if frm is not None and ts < frm: continue
    if to is not None and ts > to: continue
    lev = j["log"]["level"]; lv[lev] += 1
    if lev in ("WARN","ERROR"):
        m = re.sub(r"[0-9a-f]{8}-[0-9a-f-]{27}", "<uuid>", j.get("message",""))[:150]
        br[(lev, j["log"]["logger"].split(".")[-1], m)] += 1
print(dict(lv))
for (lev, lg, m), n in br.most_common(40): print(f"{n:4d} {lev} {lg}: {m}")
' "$FROM" "$TO"
