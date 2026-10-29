#!/usr/bin/env python3
"""O-11 extract: per session transcript, the marker writes (cat > …markers/verify-*.json), the merge command
+ its tool result, and the `git add` part of the docs commit. usage: o11.py <label>=<jsonl> ..."""
import json, os, re, sys
MERGE = re.compile(r"gh\s+pr\s+merge\s+(\d+)")
MW = re.compile(r"cat > (\S*markers/verify-\w+\.json) <<'?EOF'?\n(.*?)\nEOF", re.S)
def clean(s):
    return s.replace(os.path.expanduser("~") + "/Projects/2026/", "$P/")
for arg in sys.argv[1:]:
    label, path = arg.split("=", 1)
    print(f"##### {label} (session {path.split('/')[-1][:8]})")
    pend = {}
    seen = set()
    for raw in open(path, errors="replace"):
        try:
            o = json.loads(raw)
        except Exception:
            continue
        if not o.get("timestamp"):
            continue  # sidechain / progress duplicates carry no timestamp
        ts = o["timestamp"]; cwd = o.get("cwd", "")
        content = (o.get("message") or {}).get("content")
        if not isinstance(content, list):
            continue
        for c in content:
            if c.get("type") == "tool_use":
                cmd = (c.get("input") or {}).get("command") or ""
                for m in MW.finditer(cmd):
                    key = (m.group(1), m.group(2))
                    if key in seen: continue
                    seen.add(key)
                    body = " ".join(m.group(2).split())
                    print(clean(f"{ts} cwd={cwd} MARKER-WRITE {m.group(1)} :: {body}"))
                if MERGE.search(cmd) and cmd.strip().startswith("gh"):
                    print(clean(f"{ts} cwd={cwd} MERGE :: {cmd.strip()}"))
                    pend[c["id"]] = "MERGE"
                if "docs: Slice" in cmd and "git commit" in cmd:
                    pre = cmd[: cmd.find("git commit")]
                    adds = re.findall(r"git add[^&;\n]*", pre)
                    print(clean(f"{ts} cwd={cwd} DOCS-COMMIT git-add :: {' | '.join(a.strip() for a in adds)}"))
            elif c.get("type") == "tool_result" and c.get("tool_use_id") in pend:
                r = c.get("content")
                if isinstance(r, list):
                    r = " ".join(x.get("text", "") for x in r if isinstance(x, dict))
                print(clean(f"{ts} RESULT[{pend.pop(c['tool_use_id'])}] is_error={c.get('is_error')} :: {str(r)[:300]}"))
    print()
