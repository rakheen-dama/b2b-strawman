#!/bin/bash
# (copied from 647D) usage: logcount.sh <backend.log> [fromISO] [toISO] — level counts + WARN/ERROR breakdown (JSON log lines)
F=$1; FROM=${2:-0000}; TO=${3:-9999}
sed 's/^e2e-backend *| //' "$F" | grep '^{' | python3 -c '
import sys, json, re, collections
frm, to = sys.argv[1], sys.argv[2]
lv = collections.Counter(); br = collections.Counter()
for l in sys.stdin:
    try: j = json.loads(l)
    except Exception: continue
    ts = j.get("@timestamp","")
    if not (frm <= ts <= to): continue
    lev = j["log"]["level"]; lv[lev] += 1
    if lev in ("WARN","ERROR"):
        m = re.sub(r"[0-9a-f]{8}-[0-9a-f-]{27}", "<uuid>", j.get("message",""))[:150]
        br[(lev, j["log"]["logger"].split(".")[-1], m)] += 1
print(dict(lv))
for (lev, lg, m), n in br.most_common(40): print(f"{n:4d} {lev} {lg}: {m}")
' "$FROM" "$TO"
