#!/usr/bin/env bash
# usage: logscan.sh <backend.log> — WARN/ERROR by level/logger/message, ERROR total, PR-area WARN/ERROR count
f=$1; echo "===== $(basename $f): JSON log level WARN/ERROR by logger | message (first 110 chars)"
grep -E '"level":"(WARN|ERROR)"' $f | python3 -I -c '
import sys,json,re
from collections import Counter
c=Counter()
for l in sys.stdin:
  try: j=json.loads(l[l.index("{"):])
  except Exception: continue
  lg=j.get("log",{}); m=re.sub(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}","<uuid>",j.get("message",""))[:110]
  c[(lg.get("level"),lg.get("logger","").split(".")[-1],m)]+=1
for (lv,lg,m),n in c.most_common(): print("%4d %s %s | %s"%(n,lv,lg,m))'
echo "ERROR total=$(grep -c '"level":"ERROR"' $f)"
echo "PR-area (MATTER_REQUIRED|MATTER_NOT_LINKED|Matter required|Matter not linked|value too long|Portal trust sync failed|TrustLedgerPortalSync|portal_trust) WARN/ERROR=$(grep -E '"level":"(WARN|ERROR)"' $f | grep -ciE 'MATTER_REQUIRED|MATTER_NOT_LINKED|Matter required|Matter not linked|value too long|Portal trust sync failed|TrustLedgerPortalSync|portal_trust')"
