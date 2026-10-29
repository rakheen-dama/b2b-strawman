#!/usr/bin/env python3
"""629C marker re-derivation. Run from the worktree: python3 -I /tmp/629C-b1/derive.py /tmp/629C-b1/prs.json"""
import json, re, subprocess, sys

import datetime as _dt
def _p(t):
    t = (t or "").strip().replace("Z", "+00:00")
    if len(t) > 5 and t[-5] in "+-" and t[-3] != ":": t = t[:-2] + ":" + t[-2:]
    return _dt.datetime.fromisoformat(t)
def tsafter(ts, merged):
    try: return _p(ts) > _p(merged)
    except Exception: return False
def git(*a, check=False):
    r = subprocess.run(["git", *a], capture_output=True, text=True)
    if check and r.returncode:
        raise SystemExit(r.stderr)
    return r.returncode, r.stdout

# Audit set pinned at both ends (#1643-#1713, the Phase 88 PRs). An open upper bound would let later merges
# join the set on a re-run; a missing number in the range aborts instead of silently shrinking the audit.
LO, HI = 1643, 1713
prs = sorted((p for p in json.load(open(sys.argv[1])) if LO <= p["number"] <= HI), key=lambda p: p["number"])
_missing = sorted(set(range(LO, HI + 1)) - {p["number"] for p in prs})
if _missing:
    raise SystemExit(f"input is missing audited PRs: {_missing}")
VER = re.compile(r"^## Verdict: APPROVE @([0-9a-fA-F]{7,40})\s*$")
CR = re.compile(r"^## CodeRabbit: (.*)$")
out, crl, tsv = [], [], []
tsv.append("\t".join(["pr", "branch", "squash", "parents", "head", "areas", "marker_commits", "markers", "verdicts_bound", "verdicts_total", "coderabbit", "mergedAt", "mergedBy"]))
for p in prs:
    n = p["number"]; sq = p["mergeCommit"]["oid"]; head = p["headRefOid"]
    _, par = git("rev-list", "--parents", "-n1", sq)
    nparents = len(par.split()) - 1
    _, files = git("diff", "--name-only", f"{sq}^1", sq)
    files = files.split()
    areas = []
    if any(f.startswith("backend/") for f in files): areas.append("backend")
    if any(f.startswith("frontend/") for f in files) or any(f.startswith("packages/") for f in files): areas.append("frontend")
    if any(f.startswith("portal/") for f in files) or any(f.startswith("packages/") for f in files): areas.append("portal")
    tops = sorted({f.split("/")[0] for f in files})
    # candidate marker-bearing commits: the squash itself + commits whose message contains (#N)
    _, cands = git("log", "--format=%h", f"--grep=(#{n})", "--fixed-strings", "--", ".claude/markers/")
    cands = cands.split()
    _, sqm = git("show", "--name-only", "--format=", sq, "--", ".claude/markers/")
    sqm = [f for f in sqm.split() if f.endswith(".json")]
    found = {}
    log = [f"=== PR #{n} {p['headRefName']} squash={sq[:9]} parents={nparents} head={head[:9]} files={len(files)} tops={','.join(tops)} areas={','.join(areas) or 'docs-only'}",
           f"  grep '(#{n})' -- .claude/markers/ -> {cands or 'none'}; squash changed markers: {sqm or 'none'}"]
    order = ([sq[:9]] if sqm else []) + cands
    for c in order:
        _, ch = git("show", "--name-only", "--format=", c, "--", ".claude/markers/")
        ch = [f for f in ch.split() if f.endswith(".json")]
        _, subj = git("log", "-1", "--format=%s", c)
        log.append(f"  candidate {c} changed={ch} subject={subj.strip()[:110]}")
        for f in ch:
            a = f.split("verify-")[-1][:-5]
            if a in found: continue
            _, body = git("show", f"{c}:{f}")
            try:
                m = json.loads(body)
            except Exception:
                log.append(f"    {f}: unparseable"); continue
            mc = m.get("commit", "")
            rc, _ = git("merge-base", "--is-ancestor", mc, head)
            rel = "= head" if head.startswith(mc) else ("anc" if rc == 0 else "NOT-ANCESTOR")
            found[a] = (c, m, rel)
            log.append(f"    {a}: commit={mc} ts={m.get('ts')} exit={m.get('exit')} rel={rel} cmd={m.get('command','')[:120]}")
            log.append(f"      summary={m.get('summary','')[:400]}")
    mk = []
    for a in areas:
        if a in found:
            c, m, rel = found[a]; late = " TS-AFTER-MERGE" if tsafter(m.get('ts'), p.get('mergedAt')) else ""
            mk.append(f"{a}@{m.get('commit')[:9]}({rel};exit={m.get('exit')};ts={m.get('ts')};via {c}{late})")
        else:
            mk.append(f"{a}:NONE")
    extra = [a for a in found if a not in areas]
    if extra:
        mk.append("extra:" + ",".join(f"{a}@{found[a][1].get('commit')}" for a in extra))
    body = p.get("body") or ""
    vb = vt = 0; crs = []
    for line in body.splitlines():
        mv = VER.match(line)
        if mv:
            vt += 1
            if head.lower().startswith(mv.group(1).lower()): vb += 1
            crl.append(f"#{n}\t{line}\t{'bound' if head.lower().startswith(mv.group(1).lower()) else 'other-head'}")
        mc_ = CR.match(line)
        if mc_:
            crs.append(mc_.group(1)); crl.append(f"#{n}\t{line}")
    log.append(f"  verdicts bound={vb}/{vt}; coderabbit={crs}")
    log.append(f"  mergedAt={p.get('mergedAt')} mergedBy={(p.get('mergedBy') or {}).get('login')}")
    out.extend(log)
    tsv.append("\t".join([f"#{n}", p["headRefName"], sq[:9], str(nparents), head[:9], ",".join(areas) or "docs-only",
                          ",".join(order) or "-", "; ".join(mk) or "-", str(vb), str(vt), (crs[0] if crs else "MISSING")[:140].replace("\t", " "),
                          p.get("mergedAt", ""), (p.get("mergedBy") or {}).get("login", "")]))
open("/tmp/629C-b1/derivation.txt", "w").write("\n".join(out) + "\n")
open("/tmp/629C-b1/coderabbit-lines.txt", "w").write("\n".join(crl) + "\n")
open("/tmp/629C-b1/markers.tsv", "w").write("\n".join(tsv) + "\n")
print(len(prs), "PRs")
