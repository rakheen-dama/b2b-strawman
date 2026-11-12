#!/usr/bin/env python3
"""641A parity: compare after-<run>.json against before/before.json[<run>] step by step.
Fields compared per step: url, aria-sort, visible row order, footer (+ a few step-specific fields).
UUIDs are normalised (saved-view / matter ids differ between fresh stacks).
Usage: python3 -I compare-641a.py <run> (pristine|rich)  -> prints a table, writes parity-<run>.txt"""
import json, re, sys, os
run = sys.argv[1]
here = os.path.dirname(os.path.abspath(__file__))
B = json.load(open(os.path.join(here, "before", "before.json")))[run]["steps"]
A = json.load(open(os.path.join(here, f"after-{run}.json")))["steps"]
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
def n(v): return json.loads(UUID.sub("<uuid>", json.dumps(v, ensure_ascii=False)))
out = []; ident = 0; diffs = []
def cmp(step, field, b, a):
    global ident
    b, a = n(b), n(a)
    if b == a: ident += 1; out.append(f"IDENTICAL | {step} | {field} | {json.dumps(a, ensure_ascii=False)[:160]}")
    else:
        diffs.append((step, field, b, a))
        out.append(f"DIFF      | {step} | {field} | before={json.dumps(b, ensure_ascii=False)[:300]} | after={json.dumps(a, ensure_ascii=False)[:300]}")
def hdr(s): return [(h["text"], h["visible"], h["ariaSort"], h["arrow"], h["sortHref"]) for h in s["headers"]]
# 1 default
b, a = B["1-default"], A["1-default"]
for f in ["url", "h1", "subtitle", "caption", "rowNames", "rowStatuses", "footer", "statusChips", "viewTabs", "filtersTrigger", "searchValue"]:
    cmp("1-default", f, b.get(f), a.get(f))
cmp("1-default", "headers(text,visible,ariaSort,arrow,href)", hdr(b), hdr(a))
cmp("1-default", "rowClickOutsideLink.navigates", b["rowClickOutsideLink"].get("navigates"), a["rowClickOutsideLink"].get("navigates"))
cmp("1-default", "rowShape.usesTable/rowTag/cellCount/cursor/tabIndex/role", [b["rowShape"][k] for k in ["usesTable","rowTag","cellCount","rowCursor","rowTabIndex","rowRole"]], [a["rowShape"][k] for k in ["usesTable","rowTag","cellCount","rowCursor","rowTabIndex","rowRole"]])
cmp("1-default", "rowShape.links/buttons", [b["rowShape"]["linksInRow"], b["rowShape"]["buttonsInRow"]], [a["rowShape"]["linksInRow"], a["rowShape"]["buttonsInRow"]])
# 2 sort
bs = {e["column"]: e for e in B["2-sort"]}; as_ = {e["column"]: e for e in A["2-sort"]}
cmp("2-sort", "columns sortable", list(bs), list(as_))
for col in bs:
    if col not in as_: continue
    for k in ["first", "second"]:
        for f in ["url", "ariaSort", "arrow", "rowNames", "footer"]:
            cmp(f"2-sort {col} {k}", f, bs[col][k].get(f), as_[col][k].get(f))
    cmp(f"2-sort {col}", "thirdHref", bs[col]["thirdHref"], as_[col]["thirdHref"])
    cmp(f"2-sort {col}", "visibleAtDefault", bs[col]["visibleAtDefault"], as_[col]["visibleAtDefault"])
# 3 views
bv, av = B["3-views"], A["3-views"]
cmp("3-views", "tabs", bv.get("tabsAfterCreate", bv.get("tabsAtDefault")), av.get("tabsAfterCreate", av.get("tabsAtDefault")))
if "createDialogTitles" in bv: cmp("3-views", "createDialogTitles", bv["createDialogTitles"], av.get("createDialogTitles"))
for i, sw in enumerate(bv["switches"]):
    asw = av["switches"][i] if i < len(av["switches"]) else {}
    for f in ["tab", "url", "caption", "selectedTab", "rowNames", "footer", "emptyText", "headers"]:
        cmp(f"3-views switch {sw['tab']}", f, sw.get(f), asw.get(f))
for i, t in enumerate(bv["tagsDrop"]):
    at = av["tagsDrop"][i] if i < len(av["tagsDrop"]) else {}
    for f in ["switchTo", "urlBefore", "filtersTriggerBefore", "emptyBefore", "urlAfter", "rowNamesAfter", "footerAfter", "filtersTriggerAfter"]:
        cmp(f"3-views tagsDrop→{t['switchTo']}", f, t.get(f), at.get(f))
# 4 status
cmp("4-status", "chipsAtDefault", B["4-status"]["chipsAtDefault"], A["4-status"]["chipsAtDefault"])
for i, r in enumerate(B["4-status"]["results"]):
    ar = A["4-status"]["results"][i]
    for f in ["chip", "url", "rowNames", "rowStatuses", "footer", "subtitle", "emptyText", "activeChip"]:
        cmp(f"4-status {r['chip']}", f, r.get(f), ar.get(f))
# 5 search
b5, a5 = B["5-search"], A["5-search"]
cmp("5-search", "term/placeholder", [b5["term"], b5["placeholder"]], [a5["term"], a5["placeholder"]])
for k in ["match", "noMatch"]:
    for f in ["url", "subtitle", "rowNames", "footer", "emptyText", "clearFiltersHref"]:
        cmp(f"5-search {k}", f, b5[k].get(f), a5[k].get(f))
cmp("5-search", "afterClearFilters", b5.get("afterClearFilters"), a5.get("afterClearFilters"))
# 6 filter sheet
b6, a6 = B["6-filter-sheet"], A["6-filter-sheet"]
cmp("6-filter", "triggerPresent/text", [b6.get("triggerPresent"), b6.get("triggerText")], [a6.get("triggerPresent"), a6.get("triggerText")])
if b6.get("triggerPresent"):
    cmp("6-filter", "sheet", b6.get("sheet"), a6.get("sheet"))
    cmp("6-filter", "tagOptions", b6.get("tagOptions"), a6.get("tagOptions"))
    for f in ["url", "rowNames", "footer", "filtersTrigger", "emptyText"]:
        cmp("6-filter applied", f, b6["applied"].get(f), a6.get("applied", {}).get(f))
# 7 create
for name in ["New Matter", "New from Template"]:
    bb, aa = B["7-create-buttons"][name], A["7-create-buttons"][name]
    cmp(f"7-create {name}", "count/disabled/dialogTitle", [bb.get("count"), bb.get("disabled"), bb.get("dialogTitle")], [aa.get("count"), aa.get("disabled"), aa.get("dialogTitle")])
# 8 keyboard
b8, a8 = B["8-keyboard"], A["8-keyboard"]
cmp("8-keyboard", "tabsFromSearch", b8["tabsFromSearch"], a8["tabsFromSearch"])
cmp("8-keyboard", "focused text/href", [b8["focused"]["text"], b8["focused"]["href"]], [a8["focused"]["text"], a8["focused"]["href"]])
cmp("8-keyboard", "after url/rows", b8["after"], a8["after"])
# 9 combined
b9, a9 = B["9-combined"], A["9-combined"]
for f in ["url", "rowNames", "footer", "sortHrefs", "statusChipHrefs"]:
    cmp("9-combined start", f, b9["start"].get(f), a9["start"].get(f))
for k in ["afterClientSort", "afterActiveChip", "afterSearchCleared"]:
    for f, v in b9[k].items():
        cmp(f"9-combined {k}", f, v, a9[k].get(f))
# 1b viewports
for vp, bvp in B["1b-viewports"].items():
    avp = A["1b-viewports"].get(vp, {})
    for f in ["htmlClass", "url", "rowNames", "footer", "visibleHeaders", "hiddenHeaders", "tableContainerWidth"]:
        if f in bvp: cmp(f"1b {vp}", f, bvp.get(f), avp.get(f))
summary = f"{run}: IDENTICAL {ident} / DIFF {len(diffs)}"
txt = "\n".join([summary, ""] + out)
open(os.path.join(here, f"parity-{run}.txt"), "w").write(txt + "\n")
print(summary)
for d in diffs: print("DIFF", d[0], "|", d[1], "|", json.dumps(d[2], ensure_ascii=False)[:400], "=>", json.dumps(d[3], ensure_ascii=False)[:400])
