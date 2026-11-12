#!/usr/bin/env python3
"""642A parity: compare an AFTER run against before/before-<run>.json, step by step.
UUIDs (client / saved-view ids) and rendered dates ("12 Nov 2026") are normalised, so two fresh
stacks built on different days compare equal when the behaviour is equal.
Usage: python3 -I compare-642a.py <run: pristine|rich> [after.json]
  default after.json = ../after-<run>.json (642A/ next to this before/ directory)
Writes parity-<run>.txt next to the after file and prints the summary + every DIFF."""
import json, re, sys, os

run = sys.argv[1]
here = os.path.dirname(os.path.abspath(__file__))
after_path = sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, "..", f"after-{run}.json")
B = json.load(open(os.path.join(here, f"before-{run}.json")))["steps"]
A = json.load(open(after_path))["steps"]
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
DATE = re.compile(r"\b\d{1,2} (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \d{4}\b")


def n(v):
    s = json.dumps(v, ensure_ascii=False)
    return json.loads(DATE.sub("<date>", UUID.sub("<uuid>", s)))


out, diffs, ident = [], [], [0]


def cmp(step, field, b, a):
    b, a = n(b), n(a)
    if b == a:
        ident[0] += 1
        out.append(f"IDENTICAL | {step} | {field} | {json.dumps(a, ensure_ascii=False)[:160]}")
    else:
        diffs.append((step, field, b, a))
        out.append(f"DIFF      | {step} | {field} | before={json.dumps(b, ensure_ascii=False)[:300]} | after={json.dumps(a, ensure_ascii=False)[:300]}")


g = lambda d, *ks: [d.get(k) if isinstance(d, dict) else None for k in ks]
hdr = lambda s: [(h["text"], h["visible"], h["ariaSort"], h["arrow"], h["sortHref"]) for h in s.get("headers", [])]

# 1 default
b, a = B["1-default"], A["1-default"]
for f in ["url", "h1", "h1Count", "subtitle", "caption", "rowNames", "rowFica", "rowTypes", "rowCells", "footer",
          "lifecyclePills", "showIncomplete", "viewTabs", "filtersTrigger", "searchValue"]:
    cmp("1-default", f, b.get(f), a.get(f))
cmp("1-default", "headers(text,visible,ariaSort,arrow,href)", hdr(b), hdr(a))
for k in ["rowClickPlainCell", "clientLinkClick"]:
    cmp("1-default", k, b.get(k), a.get(k))
cmp("1-default", "rowActions(items,navigatedOnOpen,openClient)",
    g(b.get("rowActions") or {}, "items", "navigatedOnOpen", "openClientNavigatedTo"),
    g(a.get("rowActions") or {}, "items", "navigatedOnOpen", "openClientNavigatedTo"))
rk = ["usesTable", "rowTag", "rowAttrs", "cellCount", "rowCursor", "rowTabIndex", "rowRole", "linksInRow", "buttonsInRow"]
cmp("1-default", "rowShape", g(b.get("rowShape") or {}, *rk), g(a.get("rowShape") or {}, *rk))

# 2 sort
bs = {e["column"]: e for e in B["2-sort"]}
as_ = {e["column"]: e for e in A.get("2-sort", [])}
cmp("2-sort", "sortable columns", list(bs), list(as_))
for col, be in bs.items():
    ae = as_.get(col, {})
    cmp(f"2-sort {col}", "visibleAtDefault/ariaSortAtDefault/defaultHref", g(be, "visibleAtDefault", "ariaSortAtDefault", "defaultHref"), g(ae, "visibleAtDefault", "ariaSortAtDefault", "defaultHref"))
    for k in ["first", "second"]:
        for f in ["url", "ariaSort", "arrow", "rowNames", "footer"]:
            cmp(f"2-sort {col} {k}", f, be[k].get(f), (ae.get(k) or {}).get(f))
    cmp(f"2-sort {col} first", "visibleHeaders", be["first"].get("visibleHeaders"), (ae.get("first") or {}).get("visibleHeaders"))
    for f in ["thirdHref", "thirdColumnHref", "otherAriaSort"]:
        cmp(f"2-sort {col}", f, be.get(f), ae.get(f))

# 3 views
bv, av = B["3-views"], A.get("3-views", {})
cmp("3-views", "tabs", bv.get("tabsAfterCreate", bv.get("tabsAtDefault")), av.get("tabsAfterCreate", av.get("tabsAtDefault")))
for f in ["createDialogTitles", "createDialogStep2Columns"]:
    if f in bv:
        cmp("3-views", f, bv[f], av.get(f))
for i, sw in enumerate(bv["switches"]):
    asw = (av.get("switches") or [])[i] if i < len(av.get("switches") or []) else {}
    for f in ["tab", "url", "caption", "selectedTab", "rowNames", "footer", "subtitle", "emptyText", "emptyStateText",
              "clearFiltersHref", "headers", "lifecycleGroupPresent", "lifecyclePills", "showIncomplete", "filtersTrigger"]:
        cmp(f"3-views switch {sw['tab']}", f, sw.get(f), asw.get(f))
for i, t in enumerate(bv["tagsDrop"]):
    at = (av.get("tagsDrop") or [])[i] if i < len(av.get("tagsDrop") or []) else {}
    for f in [k for k in t.keys()]:
        cmp(f"3-views tagsDrop→{t['switchTo']}", f, t.get(f), at.get(f))

# 4 lifecycle
b4, a4 = B["4-lifecycle"], A.get("4-lifecycle", {})
for f in ["pillsAtDefault", "showIncompleteAtDefault", "onActiveView", "onActiveViewClickAll", "showIncompleteOff"]:
    cmp("4-lifecycle", f, b4.get(f), a4.get(f))
for i, r in enumerate(b4["results"]):
    ar = (a4.get("results") or [])[i] if i < len(a4.get("results") or []) else {}
    for f in [k for k in r.keys() if k != "screenshot"]:
        cmp(f"4-lifecycle {r['pill']}", f, r.get(f), ar.get(f))
if "showIncompleteOn" in b4:
    for f in [k for k in b4["showIncompleteOn"].keys() if k != "screenshot"]:
        cmp("4-lifecycle showIncompleteOn", f, b4["showIncompleteOn"].get(f), (a4.get("showIncompleteOn") or {}).get(f))

# 5 search
b5, a5 = B["5-search"], A.get("5-search", {})
cmp("5-search", "term/placeholder/regionLabel/emailTerm", g(b5, "term", "termMatchesAll", "placeholder", "searchRegionLabel", "emailTerm"), g(a5, "term", "termMatchesAll", "placeholder", "searchRegionLabel", "emailTerm"))
for k in ["match", "noMatch"]:
    for f in ["url", "subtitle", "rowNames", "footer", "emptyText", "clearFiltersHref", "searchValue"]:
        cmp(f"5-search {k}", f, b5[k].get(f), (a5.get(k) or {}).get(f))
for k in ["emailMatch", "afterClearFilters"]:
    cmp("5-search", k, b5.get(k), a5.get(k))

# 6 filter sheet
b6, a6 = B["6-filter-sheet"], A.get("6-filter-sheet", {})
cmp("6-filter", "triggerPresent/text", g(b6, "triggerPresent", "triggerText"), g(a6, "triggerPresent", "triggerText"))
for f in ["sheet", "tagOptions", "pickedTag", "applied", "appliedPlusIncomplete", "afterSheetClear"]:
    if f in b6:
        cmp("6-filter", f, b6.get(f), a6.get(f))

# 7 create
for name, bb in B["7-create-buttons"].items():
    aa = A.get("7-create-buttons", {}).get(name, {})
    cmp(f"7-create {name}", "count/disabled/dialogTitle", g(bb, "count", "disabled", "dialogTitle"), g(aa, "count", "disabled", "dialogTitle"))

# 8 keyboard
b8, a8 = B["8-keyboard"], A.get("8-keyboard", {})
cmp("8-keyboard", "tabsFromSearch", b8["tabsFromSearch"], a8.get("tabsFromSearch"))
cmp("8-keyboard", "path(text,testid)", [(p["text"], p["testid"]) for p in b8["path"]], [(p["text"], p["testid"]) for p in a8.get("path", [])])
cmp("8-keyboard", "focused text/href", g(b8["focused"], "text", "href"), g(a8.get("focused") or {}, "text", "href"))
cmp("8-keyboard", "after url/rows", b8["after"], a8.get("after"))

# 9 combined
b9, a9 = B["9-combined"], A.get("9-combined", {})
for f in ["url", "rowNames", "footer", "sortHrefs", "lifecyclePills", "showIncompleteHref", "searchValue"]:
    cmp("9-combined start", f, b9["start"].get(f), (a9.get("start") or {}).get(f))
for k in ["afterFicaSort", "afterAllPill", "afterSearchCleared"]:
    for f, v in b9[k].items():
        cmp(f"9-combined {k}", f, v, (a9.get(k) or {}).get(f))

# 10 empty variants
for key, be in B["10-empty"].items():
    ae = A.get("10-empty", {}).get(key, {})
    for f in [k for k in be.keys() if k != "screenshot"]:
        cmp(f"10-empty {key}", f, be.get(f), ae.get(f))

# 1b viewports (headers + overflow numbers)
for vp, bvp in B["1b-viewports"].items():
    avp = A.get("1b-viewports", {}).get(vp, {})
    for f in ["url", "rowNames", "footer", "visibleHeaders", "hiddenHeaders"]:
        cmp(f"1b {vp}", f, bvp.get(f), avp.get(f))
    L = lambda x: x.get("layout") or {}
    T = lambda x: L(x).get("table") or {}
    cmp(f"1b {vp}", "htmlClass/pageScrollsSideways", g(L(bvp), "htmlClass", "pageScrollsSideways"), g(L(avp), "htmlClass", "pageScrollsSideways"))
    cmp(f"1b {vp}", "table(width,containerClientWidth,overflowPx,scrollsSideways)", g(T(bvp), "tableWidth", "containerClientWidth", "overflowPx", "tableScrollsSideways"), g(T(avp), "tableWidth", "containerClientWidth", "overflowPx", "tableScrollsSideways"))
    for k in ["sortedByCreated", "savedView"]:
        if k in bvp:
            cmp(f"1b {vp} {k}", "visibleHeaders", bvp[k].get("visibleHeaders"), (avp.get(k) or {}).get("visibleHeaders"))
            cmp(f"1b {vp} {k}", "table(width,containerClientWidth,overflowPx,scrollsSideways)", g(T(bvp[k]), "tableWidth", "containerClientWidth", "overflowPx", "tableScrollsSideways"), g(T(avp.get(k) or {}), "tableWidth", "containerClientWidth", "overflowPx", "tableScrollsSideways"))

# 11 Carol
for vp, bc in B["11-carol"].items():
    ac = A.get("11-carol", {}).get(vp, {})
    for f in ["url", "h1", "hasTable", "permissionDenied", "rowNames", "rowCells", "headers", "newClientButton", "saveViewButton"]:
        cmp(f"11-carol {vp}", f, bc.get(f), ac.get(f))

summary = f"{run}: IDENTICAL {ident[0]} / DIFF {len(diffs)}"
dest = os.path.join(os.path.dirname(os.path.abspath(after_path)), f"parity-{run}.txt")
open(dest, "w").write("\n".join([summary, ""] + out) + "\n")
print(summary, "->", dest)
for d in diffs:
    print("DIFF", d[0], "|", d[1], "|", json.dumps(d[2], ensure_ascii=False)[:400], "=>", json.dumps(d[3], ensure_ascii=False)[:400])
