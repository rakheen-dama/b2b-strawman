#!/usr/bin/env python3
"""O-11 extract: per session transcript, the marker writes (cat > …markers/verify-*.json), the merge command
+ its tool result, any merge-gate hook denial, and the docs commit (the `cd <dir>` it runs in + its `git add` part).
usage: o11.py <label>=<jsonl> ...  (inputs: ~/.claude/projects/-Users-rakheendama-Projects-2026-b2b-strawman/<session>.jsonl)
Prints no prompts, no tool output beyond the 300-char merge/deny result, and no tokens."""
import json, os, re, sys
MERGE = re.compile(r"gh\s+pr\s+merge\s+(\d+)")
CD = re.compile(r"^\s*cd\s+(\S+)")
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
                    cd = CD.match(pre)
                    var = " ".join(re.findall(r"(?<![\w$])([A-Z]=[^\s&;]+)", pre))  # $D / $F used in the git add list
                    print(clean(f"{ts} cwd={cwd} DOCS-COMMIT cd={cd.group(1) if cd else '(none)'} vars=[{var}] git-add :: {' | '.join(a.strip() for a in adds)}"))
            elif c.get("type") == "tool_result" and c.get("tool_use_id") not in pend and c.get("is_error"):
                r = c.get("content")
                if isinstance(r, list):
                    r = " ".join(x.get("text", "") for x in r if isinstance(x, dict))
                if "pre-pr-merge-gate" in str(r):
                    print(clean(f"{ts} HOOK-DENY is_error=True :: {str(r)[:300]}"))
            elif c.get("type") == "tool_result" and c.get("tool_use_id") in pend:
                r = c.get("content")
                if isinstance(r, list):
                    r = " ".join(x.get("text", "") for x in r if isinstance(x, dict))
                print(clean(f"{ts} RESULT[{pend.pop(c['tool_use_id'])}] is_error={c.get('is_error')} :: {str(r)[:300]}"))
    print()
