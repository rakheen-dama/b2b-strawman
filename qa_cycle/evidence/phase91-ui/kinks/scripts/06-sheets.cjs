// 662A step 6: contact sheets (Chromium-rendered, lib.sheet662) into kinks/contact/, the single-line
// kinks/walk-index.json and kinks/console.json, and the (uncommitted) triage manifest in .tmp/.
// Sheet plan (orchestrator ruling D-12): ONE sheet per route / tab / sub-page with owner and member as two
// rows × 4 tiles (1024 L, 1024 D, 1440 L, 1440 D); detail tabs, the task sheet, the 390 sets, dialogs and
// the scripted checks get grouped sheets. Names: <t1..t5>-<slug>.png so a triage agent can ls contact/t3-*.
// Usage (worktree root): NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/kinks/scripts/06-sheets.cjs [--only=<regex>]
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const CONTACT = path.join(L.EV, "contact");
const ONLY = (process.argv.find((a) => a.startsWith("--only=")) || "").slice(7);
fs.mkdirSync(CONTACT, { recursive: true });
const ID = L.ids();
const CHECKS = L.readJson(path.join(L.RAW, "checks.json"), {});
const TOPUPS = L.readJson(path.join(L.RAW, "topups.json"), {});
const SKIP02 = L.readJson(path.join(L.RAW, "skipped-02.json"), {});

// ---- Load every per-shot JSON.
const caps = [];
for (const set of ["S1", "S2", "S3", "S4", "S5", "S6", "S7"]) {
  const d = path.join(L.RAW, set);
  if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((f) => f.endsWith(".json"))) {
    const j = JSON.parse(fs.readFileSync(path.join(d, f), "utf8"));
    j.png = path.join(d, f.replace(/\.json$/, ".png"));
    j.hasPng = fs.existsSync(j.png);
    caps.push(j);
  }
}
const find = (set, name, who, vp, theme) => caps.find((c) => c.set === set && (c.name || "") === name && c.persona === who && c.vp === vp && c.theme === theme);
const TW = { 390: 195, 768: 256, 1024: 341, 1440: 480 };
const cap = (c, who, vp, theme) => {
  if (!c) return `${who} · ${vp} · ${theme} · not captured`;
  const con = c.console ? ` · con ${c.console.e}/${c.console.w}/${c.console.h}` : "";
  const h = c.docHeight ? ` · ${c.docHeight > 2700 ? "clip 2700/" + c.docHeight : c.docHeight + "px"}` : "";
  return `${who === "alice" ? "owner" : "member"} · ${vp} · ${theme} · ${c.status || "-"} · ${c.h1 && c.h1.length ? c.h1.join(" | ").slice(0, 40) : "no h1"}${h}${con}`;
};
const tile = (c, who, vp, theme, scale = null) => {
  const vpH = { 390: 844, 768: 1024, 1024: 768, 1440: 900 }[vp];
  const tileW = scale ? Math.round(vp * scale) : TW[vp];
  if (c && c.skip) return { cap: `${who === "alice" ? "owner" : "member"} · ${vp} · ${theme}`, file: null, vpW: vp, vpH, tileW, miss: c.skip };
  return { cap: cap(c, who, vp, theme), file: c && c.hasPng ? c.png : null, vpW: vp, vpH, tileW, miss: c ? c.error || "no PNG" : "not in the walk matrix" };
};
const sheets = []; // {file, group, title, kind, covers:[names]}
const jobs = [];
const job = (group, slug, title, rows, opts = {}, covers = []) => {
  const file = `${group}-${slug}.png`;
  sheets.push({ file, group, title, covers });
  if (!ONLY || new RegExp(ONLY).test(file)) jobs.push([file, title, rows, opts]);
  return file;
};
const sheetOf = {}; // capture key → sheet file
const key = (c) => `${c.set}/${c.name || c.pageKey + "::" + c.trigger}__${c.persona}-${c.vp}-${c.theme}`;

// ---- S1 (+S2 Carol 404 tiles live in the PA sheet): one sheet per route pattern.
const COMBO4 = [[1024, "light"], [1024, "dark"], [1440, "light"], [1440, "dark"]];
const s1Names = [...new Set(caps.filter((c) => c.set === "S1" && c.name !== "settings" && c.name !== "matters-id-owner-matter").map((c) => c.name))];
for (const name of s1Names) {
  const any = caps.find((c) => c.set === "S1" && c.name === name);
  const rows = ["alice", "carol"].map((who) => {
    const tiles = COMBO4.map(([vp, th]) => tile(find("S1", name, who, vp, th), who, vp, th));
    if (who === "carol" && name === "matters-id") for (const th of ["light", "dark"]) tiles.push({ ...tile(find("S1", "matters-id-owner-matter", "carol", 1440, th), "carol", 1440, th), cap: "member on the OWNER's matter (not-on-team branch) · " + cap(find("S1", "matters-id-owner-matter", "carol", 1440, th), "carol", 1440, th) });
    return { label: who === "alice" ? "OWNER (Alice)" : "MEMBER (Carol)", tiles };
  });
  const f = job(any.group, name, `662A walk · ${any.route}${any.topup ? " (end-of-walk UI top-up, D-14)" : ""} · rows owner/member × 1024/1440 × light/dark · fold = dashed red`, rows);
  caps.filter((c) => c.set === "S1" && (c.name === name || (name === "matters-id" && c.name === "matters-id-owner-matter"))).forEach((c) => (sheetOf[key(c)] = f));
}
// /settings redirect: no sheet of its own (recorded in the index; it lands on /settings/firm?tab=general).
// ---- S2 platform-admin (light only): one sheet, a row per route.
{
  const rows = L.PA_ROUTES.map((r) => {
    const n = L.slugOf(r);
    return { label: r.replace("/platform-admin/", "PA "), tiles: [tile(find("S2", n, "alice", 1024, "light"), "alice", 1024, "light"), tile(find("S2", n, "alice", 1440, "light"), "alice", 1440, "light"), tile(find("S2", n, "carol", 1440, "light"), "carol", 1440, "light")] };
  });
  const f = job("t3", "platform-admin", "662A walk · /platform-admin/{access-requests,billing,demo} · light only (outside /org/) · owner 1024/1440 + member 1440 (expected 404)", rows);
  caps.filter((c) => c.set === "S2").forEach((c) => (sheetOf[key(c)] = f));
}
// ---- S3 task detail sheet.
{
  const rows = ["alice", "carol"].map((who) => ({ label: who === "alice" ? "OWNER" : "MEMBER", tiles: [...COMBO4.map(([vp, th]) => ({ ...tile(find("S3", "task-sheet-matter", who, vp, th), who, vp, th), cap: "matter ?tab=tasks&taskId · " + cap(find("S3", "task-sheet-matter", who, vp, th), who, vp, th) })), ...["light", "dark"].map((th) => ({ ...tile(find("S3", "task-sheet-my-work", who, 1440, th), who, 1440, th), cap: "/my-work?taskId · " + cap(find("S3", "task-sheet-my-work", who, 1440, th), who, 1440, th) }))] }));
  const f = job("t1", "task-sheet", "662A walk · task detail sheet (URL-driven) · matter Tasks tab ×4 combos + /my-work at 1440", rows);
  caps.filter((c) => c.set === "S3").forEach((c) => (sheetOf[key(c)] = f));
}
// ---- S4 detail tabs: a row per tab; tiles owner 1024 L, owner 1440 L/D, member 1440 L/D (scale 1/5).
const S4COMBOS = [["alice", 1024, "light"], ["alice", 1440, "light"], ["alice", 1440, "dark"], ["carol", 1440, "light"], ["carol", 1440, "dark"]];
for (const [ent, chunks] of [["matters-id", [8, 7, 7]], ["clients-id", [7, 7]]]) {
  const tabs = [...new Set(caps.filter((c) => c.set === "S4" && c.name.startsWith(ent + "--")).map((c) => c.name))];
  const order = (ent === "matters-id" ? "details fields team overview documents correspondence clients tasks time budget financials activity rates generated requests comments court-dates adverse-parties trust disbursements statements audit" : "details fields tags overview matters documents generated invoices rates financials trust onboarding requests audit").split(" ").map((t) => `${ent}--${t}`).filter((t) => tabs.includes(t));
  let i = 0;
  chunks.forEach((n, k) => {
    const part = order.slice(i, i + n);
    i += n;
    const rows = part.map((name) => ({ label: "?tab=" + name.split("--")[1], tiles: S4COMBOS.map(([who, vp, th]) => tile(find("S4", name, who, vp, th), who, vp, th, 0.2)) }));
    const f = job("t1", `${ent}-tabs-${k + 1}`, `662A walk · /${ent.replace("-id", "/[id]")} detail tabs (${k + 1}/${chunks.length}) · owner 1024L, owner 1440 L/D, member 1440 L/D`, rows, { clipH: 1800 });
    caps.filter((c) => c.set === "S4" && part.includes(c.name)).forEach((c) => (sheetOf[key(c)] = f));
  });
}
// ---- S5 390 shell: rows = persona × theme; tiles = 3 routes × (closed, Sheet open, Sheet scrolled to bottom).
const P4 = [["alice", "light"], ["alice", "dark"], ["carol", "light"], ["carol", "dark"]];
{
  const names = ["dashboard", "matters", "matters-id"].flatMap((r) => [`${r}--closed`, `${r}--sheet`, `${r}--sheet-bottom`]);
  const rows = P4.map(([who, th]) => ({ label: `${who === "alice" ? "OWNER" : "MEMBER"} ${th}`, tiles: names.map((n) => ({ ...tile(find("S5", n, who, 390, th), who, 390, th), cap: `${n} · ` + cap(find("S5", n, who, 390, th), who, 390, th) })) }));
  const f = job("t1", "shell-390", "662A walk · 390×844 shell: closed / mobile Sheet open / Sheet scrolled to bottom (K-05) · /dashboard, /matters, /matters/[id]", rows, { clipH: 1700 });
  caps.filter((c) => c.set === "S5").forEach((c) => (sheetOf[key(c)] = f));
}
// ---- S6 390 settings: one sheet per family (matters: tabs and sub-pages apart); columns = surfaces.
{
  const fam = (f) => caps.filter((c) => c.set === "S6" && c.persona === "alice" && c.theme === "light" && (c.route.startsWith(`/settings/${f}?`) || (f === "firm" && c.route === "/settings"))).map((c) => c.name);
  const subs = (f) => caps.filter((c) => c.set === "S6" && c.persona === "alice" && c.theme === "light" && c.route.startsWith(`/settings/${f}/`)).map((c) => c.name);
  const tabOrder = (f) => L.TABS.filter(([x]) => x === f).map(([x, t]) => L.slugOf(`/settings/${x}?tab=${t}`));
  const plan = [
    ["t4", "settings-390-firm", ["settings", ...tabOrder("firm")]],
    ["t4", "settings-390-matters-tabs", tabOrder("matters")],
    ["t4", "settings-390-matters-sub-pages", subs("matters").sort()],
    ["t5", "settings-390-documents", [...tabOrder("documents"), ...subs("documents").sort()]],
    ["t5", "settings-390-fees", tabOrder("fees")],
    ["t5", "settings-390-connections", [...tabOrder("connections"), ...subs("connections").sort()]],
  ];
  void fam;
  for (const [g, slug, names] of plan) {
    const rows = P4.map(([who, th]) => ({ label: `${who === "alice" ? "OWNER" : "MEMBER"} ${th}`, tiles: names.map((n) => ({ ...tile(find("S6", n, who, 390, th), who, 390, th), cap: `${n.replace(/^settings-/, "")} · ` + cap(find("S6", n, who, 390, th), who, 390, th) })) }));
    const f = job(g, slug, `662A walk · 390×844 settings · ${slug.replace("settings-390-", "")} · family-switch probe in walk-index (K-02)`, rows, { clipH: 1700 });
    caps.filter((c) => c.set === "S6" && names.includes(c.name)).forEach((c) => (sheetOf[key(c)] = f));
  }
}
// ---- S7 dialogs: a row per (page, trigger); tiles owner 1024 L/D, 1440 L/D + member 1440 L/D (viewport shots, 1/4).
{
  const D = caps.filter((c) => c.set === "S7" && c.hasPng);
  const groups = {};
  for (const c of D) {
    const k = `${c.pageKey}::${c.trigger}`;
    (groups[c.group] = groups[c.group] || {})[k] = groups[c.group][k] || [];
    groups[c.group][k].push(c);
  }
  const DC = [["alice", 1024, "light"], ["alice", 1024, "dark"], ["alice", 1440, "light"], ["alice", 1440, "dark"], ["carol", 1440, "light"], ["carol", 1440, "dark"]];
  for (const g of Object.keys(groups).sort()) {
    const keys = Object.keys(groups[g]).sort();
    const n = Math.ceil(keys.length / 12);
    for (let k = 0; k < n; k++) {
      const part = keys.slice(k * 12, k * 12 + 12);
      const rows = part.map((kk) => {
        const list = groups[g][kk];
        const one = list[0];
        return { label: `${one.pageKey} · ${one.trigger}`.slice(0, 60), tiles: DC.map(([who, vp, th]) => {
          const c = list.find((x) => x.persona === who && x.vp === vp && x.theme === th);
          const vpH = vp === 1024 ? 768 : 900;
          return { cap: c ? `${who === "alice" ? "owner" : "member"} · ${vp} · ${th} · "${(c.dialog && c.dialog.title) || "?"}"${c.dialog && !c.dialog.fitsViewport ? " · DOES NOT FIT VIEWPORT" : ""}${c.dialog && c.dialog.offscreenButtons && c.dialog.offscreenButtons.length ? " · off-screen: " + c.dialog.offscreenButtons.join("/") : ""}` : `${who === "alice" ? "owner" : "member"} · ${vp} · ${th}`, file: c ? c.png : null, vpW: vp, vpH, tileW: Math.round(vp / 4), miss: "not opened for this combo" };
        }) };
      });
      const f = job(g, `dialogs-${k + 1}`, `662A walk · ${g.toUpperCase()} create/edit dialogs (${k + 1}/${n}) · open-only, Escape-closed · owner 1024/1440 L/D, member 1440 L/D`, rows, { clipH: 99999 });
      for (const kk of part) groups[g][kk].forEach((c) => (sheetOf[key(c)] = f));
    }
  }
}
// ---- S8 scripted checks: tiles from checks.json.
const pngSize = (f) => {
  const b = fs.readFileSync(f);
  return [b.readUInt32BE(16), b.readUInt32BE(20)];
};
const ctile = (rel, cap, scale = 0.4) => {
  const f = rel ? path.join(L.W, rel) : null;
  if (!f || !fs.existsSync(f)) return { cap, file: null, vpW: 1, vpH: 1, tileW: 200, miss: "no tile" };
  const [w, h] = pngSize(f);
  return { cap, file: f, vpW: w, vpH: h, tileW: Math.round(w * scale) };
};
const C_ = (k) => CHECKS[k] || {};
const k11 = C_("K-11");
job("t1", "check-K-11-saved-view-client-column", `662A S8 · K-11 (FD-11) · ${k11.verdict || "not run"}`, [{ label: "K-11", tiles: ["1024-light", "1024-dark", "1440-light", "1440-dark"].map((m) => { const x = (k11.measure || {})[m] || {}; return ctile(x.tile, `${m} · Client th ${x.clientTh}px (default view ${x.defaultViewClientTh}px) · links ${JSON.stringify(x.linkWidths || []).slice(0, 40)} · truncated ${x.truncated}/${x.links}`, m.startsWith("1024") ? 0.45 : 0.4); }) }]);
const k07 = C_("K-07");
const sh = C_("K-05-I-02-I-05");
const i06 = C_("I-06");
job("t1", "check-shell-K-07-K-05-I-02-I-05-I-06", "662A S8 · shell checks: K-07 palette focus return · K-05/I-02 rail fold · I-05 breadcrumb wrap (1024×768 collapsed) · I-06 768×1024", [
  { label: "K-07", tiles: ["header-light", "sidebar-light", "header-dark", "sidebar-dark"].map((k) => ctile((k07[k] || {}).tile, `${k} palette open · after Escape focus → ${(k07[k] || {}).focusReturnedToTrigger ? "trigger" : "NOT trigger (" + String((k07[k] || {}).activeElement).split("\n")[0] + ")"}`, 0.3)) },
  { label: "I-02/I-05", tiles: ["dashboard-light", "matter-light", "dashboard-dark", "matter-dark"].map((k) => ctile((sh[k] || {}).tile, `${k} · collapsed 1024×768 · rail items below fold ${((sh[k] || {}).fold || {}).hidden ? sh[k].fold.hidden.length : "?"} · crumb h ${((sh[k] || {}).crumb || {}).navH}px`, 0.35)) },
  { label: "K-05 / I-06", tiles: [ctile((sh["fold-1440x900-all-groups-open-alice"] || {}).tile, `1440×900 owner, all rail groups opened · below fold: ${((sh["fold-1440x900-all-groups-open-alice"] || {}).hidden || []).join(", ").slice(0, 80)}`, 0.3), ctile((sh["fold-1440x900-all-groups-open-carol"] || {}).tile, `1440×900 member, all groups opened · below fold: ${((sh["fold-1440x900-all-groups-open-carol"] || {}).hidden || []).join(", ").slice(0, 80)}`, 0.3), ctile((i06.light || {}).tile, `768×1024 light · scrollWidth ${(i06.light || {}).scrollWidth}`, 0.3), ctile((i06.dark || {}).tile, `768×1024 dark · scrollWidth ${(i06.dark || {}).scrollWidth}`, 0.3)] },
]);
const k69 = C_("K-06-K-09");
const k20 = C_("K-20");
job("t1", "check-K-06-K-20", "662A S8 · K-06 matters table vs container at 1024 · K-20 matter Activity tab label", [{ label: "K-06 / K-20", tiles: [ctile((k69["K-06-matters-1024-light"] || {}).tile, `K-06 /matters 1024 light · table ${((k69["K-06-matters-1024-light"] || {}).tables || [{}])[0].tableW}px in ${((k69["K-06-matters-1024-light"] || {}).tables || [{}])[0].containerClientW}px`, 0.45), ctile(k20.tile, `K-20 matter ?tab=activity · selected "${(k20.selected || []).join(" | ")}"`, 0.35)] }]);
job("t3", "check-K-09-my-work-tables", "662A S8 · K-09 /my-work task tables at 1440 (owner/member × light/dark)", [{ label: "K-09", tiles: ["light-alice", "dark-alice", "light-carol", "dark-carol"].map((k) => { const x = k69[`K-09-my-work-1440-${k}`] || {}; const t = (x.tables || [{}])[0]; return ctile(x.tile, `${k} · table ${t.tableW}px in card ${t.cardW}px (${t.containerOverflowX})`, 0.3); }) }], { clipH: 99999 });
const k12 = C_("K-12");
const x5 = C_("X5");
const k04 = C_("K-04-K-21-I-03-I-04");
job("t4", "check-K-12-X5-K-04-I-04", "662A S8 · K-12 forbidden-tab notice (member) · X5 tab switch with unsaved edit · K-04/K-21/I-04 focused row-menu item (custom fields)", [
  { label: "K-12 / X5", tiles: [ctile((k12.light || {}).tile, `K-12 light · notice ${(k12.light || {}).fgHex} on ${(k12.light || {}).bgHex} = ${(k12.light || {}).ratio}`, 0.35), ctile((k12.dark || {}).tile, `K-12 dark · ${(k12.dark || {}).fgHex} on ${(k12.dark || {}).bgHex} = ${(k12.dark || {}).ratio}`, 0.35), ...(x5.tiles || []).map((t, i) => ctile(t, i ? `X5 after ${x5.otherTab} and back · value "${x5.valueAfterReturn}" · lost=${x5.lost}` : `X5 typed "${x5.typed}" into ${x5.input && x5.input.label}`, 0.35))] },
  { label: "K-04/I-04", tiles: ["light", "dark"].flatMap((th) => (k04[`row-menu-${th}`] || []).map((p, i) => ctile(p && p.tile, `${th} ArrowDown×${i + 1} "${p && p.text}" · ${p && p.fgHex} on ${p && p.bgHex} = ${p && p.textRatio}`, 0.45))) },
]);
job("t2", "check-K-04-K-21-fee-note-accent", "662A S8 · K-04 (light) / K-21 (dark) accent buttons on the fee-note detail", [{ label: "K-04/K-21", tiles: ["light", "dark"].map((th) => { const x = k04[`accent-buttons-fee-note-${th}`] || {}; return ctile(x.tile, `${th} · ${(x.buttons || []).map((b) => `"${b.text}" ${b.fgHex} on ${b.bgHex} = ${b.ratio}`).join(" · ")}`, 0.45); }) }]);

// ---- Render, then the index, console log and triage manifest.
(async () => {
  const browser = await L.chromium.launch();
  for (const [file, title, rows, opts] of jobs) {
    await L.sheet662(browser, path.join(CONTACT, file), title, rows, opts);
    console.log("sheet", file, Math.round(fs.statSync(path.join(CONTACT, file)).size / 1024) + "KB");
  }
  await browser.close();
  if (ONLY) return console.log("ONLY run — index not rewritten");

  const t3 = (a) => (a || []).slice(0, 3);
  const flags = (c) => {
    const f = [];
    if (c.skip) return f;
    if (c.error) f.push("capture error: " + c.error.slice(0, 60));
    if (c.set !== "S7") {
      if (c.status && c.status !== 200) f.push(`HTTP ${c.status}`);
      if (c.errorText) f.push("error UI: " + c.errorText.slice(0, 50));
      if (c.h1 && c.h1.length !== 1) f.push(`h1 count ${c.h1.length}${c.h1.length ? " " + JSON.stringify(c.h1) : ""}`);
      if (c.h1 && c.h1.length === 1 && !c.h1[0]) f.push("empty h1");
      if (c.docOverflowX) f.push(`page scrolls sideways +${c.docOverflowX}px`);
      const o = c.overflow || {};
      if (o.pastCardN) f.push(`${o.pastCardN} past card edge (${o.pastCard.slice(0, 2).map((x) => `"${x.text.slice(0, 20)}" +${x.by}`).join("; ")})`);
      if (o.pastViewportN) f.push(`${o.pastViewportN} past viewport`);
      if (o.narrowThN) f.push(`narrow th: ${o.narrowTh.slice(0, 2).map((x) => `"${x.text.slice(0, 12)}" ${x.w}px`).join("; ")}`);
      if (o.clippedN) f.push(`${o.clippedN} clipped/ellipsis`);
      if (c.darkSlabsN) f.push(`${c.darkSlabsN} light slabs in dark (${(c.darkSlabs || []).slice(0, 2).map((x) => `${x.testid || x.tag} ${x.bg} ${x.w}x${x.h}`).join("; ")})`);
      const es = (c.empty || []).filter((e) => e.inCard || e.inSection || e.inSheet || e.inTabpanel || e.inDialog);
      if (es.length) f.push(`EmptyState inside card/section/sheet/tabpanel ×${es.length} (${es.slice(0, 2).map((e) => `${e.variant || "no variant"} ${e.h}px`).join("; ")})`);
      const a = c.audit || {};
      const lt3 = (a.lowText || []).filter((x) => x.ratio < 3 && !x.disabled);
      if (lt3.length) f.push(`text < 3:1 ×${lt3.length}+ (min ${a.minRatio}: "${lt3[0].text.slice(0, 20)}" ${lt3[0].fg} on ${lt3[0].bg})`);
      if ((a.sameColour || []).length) f.push(`text ≈ background ×${a.sameColour.length}`);
    } else {
      const d = c.dialog || {};
      if (c.error) f.push("dialog did not open");
      if (d.fitsViewport === false) f.push(`dialog does not fit viewport (${d.rect && d.rect.y}/${d.rect && d.rect.bottom})`);
      if (d.offscreenButtons && d.offscreenButtons.length) f.push(`off-screen buttons: ${d.offscreenButtons.join("/")}`);
      if (d.overflowX) f.push("dialog scrolls sideways");
      if (c.closedByEscape === false) f.push("Escape did not close");
      if (c.focusAfterClose && c.focusAfterClose.isTrigger === false) f.push(`focus after close → ${c.focusAfterClose.tag} "${c.focusAfterClose.text}"`);
    }
    if (c.console && (c.console.e || c.console.h)) f.push(`console e/w/h ${c.console.e}/${c.console.w}/${c.console.h}: ${(c.console.msgs || []).filter((m) => m.type !== "warning").slice(0, 1).map((m) => m.text.slice(0, 80)).join("")}`);
    else if (c.console && c.console.w) f.push(`console warnings ${c.console.w}: ${(c.console.msgs || [])[0] ? c.console.msgs[0].text.slice(0, 70) : ""}`);
    if (c.set === "S6" && c.after && c.after.otherFamilyLinks && !c.after.otherFamilyLinks.length && c.after.settingsRail && !c.after.settingsRail.visible) f.push("no control reaches another settings family without the Sheet (K-02)");
    return f;
  };
  const compact = (c) => {
    const o = c.overflow || {};
    const a = c.audit || {};
    const r = { set: c.set, g: c.group, route: c.route, url: c.url || c.requested, who: c.persona, vp: c.vp, th: c.theme, st: c.status, h1: c.h1, mains: c.mains, dark: c.dark, con: c.console ? { e: c.console.e, w: c.console.w, h: c.console.h } : null, raw: c.hasPng ? L.rel(c.png) : null, sheet: sheetOf[key(c)] || null };
    if (c.skip) r.skip = c.skip;
    if (c.error) r.err = c.error.slice(0, 120);
    if (c.topup) r.topup = true;
    if (c.set !== "S7") {
      r.p = { ox: c.docOverflowX, docH: c.docHeight, clipN: o.clippedN, scrN: o.scrollN, pcN: o.pastCardN, pvN: o.pastViewportN, nthN: o.narrowThN, pc: t3(o.pastCard).map((x) => [x.text.slice(0, 30), x.testid, x.by]), nth: t3(o.narrowTh).map((x) => [x.text.slice(0, 20), x.w, x.links]), scr: t3(o.scroll).map((x) => [x.testid || x.tag, x.clientW, x.scrollW]), emp: (c.empty || []).map((e) => [e.variant, e.h, ["card", "section", "sheet", "tabpanel", "dialog"].filter((k) => e["in" + k[0].toUpperCase() + k.slice(1)]).join("+"), e.text.slice(0, 30)]), slabN: c.darkSlabsN, slab: t3(c.darkSlabs).map((x) => [x.testid || x.tag, x.bg, x.w, x.h]), fold: c.fold ? { items: c.fold.items, hidden: c.fold.hidden } : null, low: a.lowTextCount, min: a.minRatio, lt: (a.lowText || []).slice(0, 5).map((x) => [x.text.slice(0, 24), x.ratio, x.fg, x.bg, x.preScheme]), li: (a.lowIcons || []).slice(0, 3).map((x) => [x.label, x.ratio]), faintN: (a.faint || []).length, same: (a.sameColour || []).length };
      if (c.before) r.before = c.before;
      if (c.after) r.after = c.after;
    } else {
      r.trigger = c.trigger;
      r.via = c.via || "harvest";
      r.d = c.dialog ? { title: c.dialog.title, role: c.dialog.role, fits: c.dialog.fitsViewport, rect: c.dialog.rect, off: c.dialog.offscreenButtons, focusIn: c.dialog.focus && c.dialog.focus.inDialog, closed: c.closedByEscape, back: c.focusAfterClose } : null;
    }
    r.flags = flags(c);
    return r;
  };
  const captures = caps.map(compact);
  const probes = fs.readdirSync(L.RAW).filter((f) => f.startsWith("probe-")).map((f) => L.readJson(path.join(L.RAW, f)));
  const skipped = [{ route: "/trust-accounting/reconciliation/lists/[listId]", reason: "no completed reconciliation on a lifecycle stack (0 locked monthly lists); D-14: may be skipped" }];
  for (const [combo, list] of Object.entries(SKIP02)) for (const s of list) if (!skipped.some((x) => x.route === s.route)) skipped.push({ ...s, combo });
  const index = {
    capturedAt: new Date().toISOString(), head: "16423ca4bc7ae4fa78a1d5cb294a19144e090a69", stack: "e2e mock-auth :3001/:8081, fresh (e2e-down + builder prune + e2e-up) 2026-11-25",
    data: { "legal-lifecycle": "105 passed / 0 failed", "legal-depth-ii": "40 passed / 4 skipped / 0 failed" },
    probeCaptured: probes.every((p) => p.probeCaptured), probes: probes.map((p) => [p.combo, p.probeCaptured]),
    routesOnDisk: L.ROUTES.length, tabs: L.TABS.length, subPages: L.ROUTES.filter(L.isSub).length, ids: ID, topups: TOPUPS, skipped,
    counts: Object.fromEntries(["S1", "S2", "S3", "S4", "S5", "S6", "S7"].map((s) => [s, { json: caps.filter((c) => c.set === s).length, png: caps.filter((c) => c.set === s && c.hasPng).length }])),
    sheets, checks: CHECKS, captures,
  };
  fs.writeFileSync(path.join(L.EV, "walk-index.json"), JSON.stringify(index) + "\n");
  const consoleAll = caps.flatMap((c) => ((c.console && c.console.msgs) || []).map((m) => ({ capture: key(c), type: m.type, text: m.text })));
  fs.writeFileSync(path.join(L.EV, "console.json"), JSON.stringify(consoleAll) + "\n");
  console.log("walk-index.json", Math.round(fs.statSync(path.join(L.EV, "walk-index.json")).size / 1024) + "KB", captures.length, "captures;", consoleAll.length, "console entries");

  // ---- Triage manifest (scratch, not committed).
  const G = { t1: "T1 — Shell, Home, Matters, Clients, Team", t2: "T2 — Fees, legal registers, reports, compliance", t3: "T3 — Trust, personal pages, platform-admin", t4: "T4 — Settings I: Firm + Matters", t5: "T5 — Settings II: Documents + Fees & Trust + Connections" };
  let md = `# 662A triage manifest (generated by kinks/scripts/06-sheets.cjs at ${index.capturedAt})\n\nHead \`16423ca4b\`. Raw PNG + per-shot JSON: \`.tmp/662A-walk-raw/<set>/<slug>__<persona>-<w>-<theme>.{png,json}\` (S1 routes, S2 platform-admin, S3 task sheet, S4 detail tabs, S5 390 shell, S6 390 settings, S7 dialogs \`<pageKey>__dlg-<trigger>__…\`, S8 checks). Full probe data: \`qa_cycle/evidence/phase91-ui/kinks/walk-index.json\` (\`captures[].flags\` = the lines below). Checks: \`.tmp/662A-walk-raw/checks.json\`.\n\nFlags are probe hints, not findings — confirm each on the image. Expected (not kinks): member 404 / gated pages, tabs hidden for the member, empty data on a lifecycle stack.\n`;
  md += `\n## Scripted checks (S8)\n\n- K-11: ${k11.verdict}\n- K-07: palette focus returned to trigger? ${Object.entries(k07).filter(([k]) => /light|dark/.test(k)).map(([k, v]) => `${k}=${v.focusReturnedToTrigger}`).join(", ")}\n- X5: lost=${x5.lost} (typed "${x5.typed}" into "${x5.input && x5.input.label}", switched to ${x5.otherTab} and back → "${x5.valueAfterReturn}")\n- K-12: light ${(k12.light || {}).fgHex} on ${(k12.light || {}).bgHex} = ${(k12.light || {}).ratio}; dark ${(k12.dark || {}).fgHex} on ${(k12.dark || {}).bgHex} = ${(k12.dark || {}).ratio}\n- K-10: ${C_("K-10").verdict}\n- K-20: selected tab text "${(k20.selected || []).join(" | ")}"\n- I-06: 768 scrollWidth light ${(i06.light || {}).scrollWidth} / dark ${(i06.dark || {}).scrollWidth}\n`;
  for (const g of ["t1", "t2", "t3", "t4", "t5"]) {
    md += `\n## ${G[g]}\n\nContact sheets (\`qa_cycle/evidence/phase91-ui/kinks/contact/\`):\n\n${sheets.filter((s) => s.group === g).map((s) => `- \`contact/${s.file}\``).join("\n")}\n\nAnomalies per route (combo → probe flags; combos with no flag omitted):\n`;
    const byRoute = {};
    for (const c of captures.filter((x) => x.g === g)) {
      const k = c.set === "S7" ? `${c.route} — dialog "${c.trigger}"` : `${c.route}${c.set !== "S1" ? ` [${c.set}]` : ""}`;
      if (c.flags.length) (byRoute[k] = byRoute[k] || []).push(`${c.who === "alice" ? "owner" : "member"}-${c.vp}-${c.th}: ${c.flags.join(" · ")}`);
    }
    for (const k of Object.keys(byRoute).sort()) md += `\n- **${k}**\n${byRoute[k].map((l) => `  - ${l}`).join("\n")}`;
    md += "\n";
  }
  fs.writeFileSync(path.join(L.W, ".tmp/662A-triage-manifest.md"), md);
  console.log("ALL DONE 06-sheets", sheets.length, "sheets");
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
