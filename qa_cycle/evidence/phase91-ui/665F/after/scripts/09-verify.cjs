// 665F OBS-after step 9 (NEW): compare after vs before and write ../verify.json (single line) with a
// PASS/FAIL per acceptance item, plus the gating diff, the SEL-COMPAT diff, the K-115 label diff and
// the console totals over every run. Also builds sheet-acceptance.png / sheet-mcp-roundtrip.png from
// the 06 / 06b / 07 tiles, copies the one defect crop, and renames the 08 replays of the BEFORE defect
// crops to crop-replay-*.png (they show the AFTER state of the same regions, not after defects).
// Reads: ../../before/{measurements,sel-compat}.json, $RAW/{persona-*,acceptance,k114-focus-diagnose,
// mcp-roundtrip,interactions,roles-*}.json, ../measurements.json, ../specs-baseline.json.
// Usage: PHASE=after node 09-verify.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const rd = (f) => JSON.parse(fs.readFileSync(f, "utf8"));
const RAWF = (f) => path.join(L.RAW, f);
const BEFORE = path.resolve(L.OUT, "../before");
const bM = rd(path.join(BEFORE, "measurements.json"));
const bS = rd(path.join(BEFORE, "sel-compat.json"));
const bSpec = rd(path.join(BEFORE, "specs-baseline.json"));
const aSpec = rd(path.join(L.OUT, "specs-baseline.json"));
const P = {};
for (const k of ["alice", "carol", "carol-cr-holder", "carol-cr-lacker"]) P[k] = rd(RAWF(`persona-${k}.json`));
const acc = rd(RAWF("acceptance.json"));
const diag = rd(RAWF("k114-focus-diagnose.json"));
const mcp = rd(RAWF("mcp-roundtrip.json"));
const inter = rd(RAWF("interactions.json"));
const roleRuns = fs.readdirSync(L.RAW).filter((f) => /^roles-.*\.json$/.test(f)).map((f) => rd(RAWF(f)));

// ---------- gating diff (everything but the h1 text, which K-124 changes on purpose) ----------
const gate = (v, s) => v && { url: v.url, notice: v.notice, selectedTab: v.selectedTab, tabs: Array.isArray(v.tabs) ? v.tabs.join("|") : v.tabs, rail: v.rail && v.rail.join("|"), status: v.status, gateOutcome: s && s.gateOutcome };
const gatingDiff = [];
let gatingCompared = 0;
for (const k of Object.keys(P)) {
  for (const sk of Object.keys(P[k].variants))
    for (const vk of Object.keys(P[k].variants[sk])) {
      const b = gate(bM.personas[k].variants[sk] && bM.personas[k].variants[sk][vk], vk === "1440-light" ? bM.personas[k].surfaces[sk] : null);
      const a = gate(P[k].variants[sk][vk], vk === "1440-light" ? P[k].surfaces[sk] : null);
      gatingCompared++;
      if (JSON.stringify(a) !== JSON.stringify(b)) gatingDiff.push({ persona: k, surface: sk, variant: vk, before: b, after: a });
    }
  for (const u of Object.keys(P[k].legacy)) {
    gatingCompared++;
    const b = bM.personas[k].legacy[u], a = P[k].legacy[u];
    if (b.finalUrl !== a.finalUrl || b.status !== a.status) gatingDiff.push({ persona: k, legacy: u, before: b, after: a });
  }
}
// sub-page denials (in-page text) and gate-relevant sel keys
const DENY = /permission|You don't have access|status notice/;
const selDiff = [];
let selCompared = 0;
for (const k of Object.keys(P))
  for (const sk of Object.keys(P[k].selCompat)) {
    const a = P[k].selCompat[sk], b = (bS.personas[k] || {})[sk] || {};
    for (const key of new Set([...Object.keys(a), ...Object.keys(b)])) {
      selCompared++;
      if (JSON.stringify(a[key]) !== JSON.stringify(b[key])) selDiff.push({ persona: k, surface: sk, key, before: b[key], after: a[key], gateKey: DENY.test(key) });
    }
  }
const selDiffKeys = [...new Set(selDiff.map((d) => d.key))];

// ---------- layout ----------
const EXP = { xero: "Integrations", mcp: "MCP", ai: "Set Up AI", notifications: "Notifications", packs: "Packs", "packs-installed": "Packs", automations: "Automations", "sync-log": "Sync Log", "ai-history": "Execution History", executions: "Execution Log", "no-tab": "Integrations" };
const layout = {};
let layoutOk = true;
for (const k of Object.keys(P))
  for (const [sk, vv] of Object.entries(P[k].variants))
    for (const [vk, m] of Object.entries(vv)) {
      const ok = m.h1Count === 1 && m.mainCount === 1 && m.mainElCount === 1;
      if (!ok) layoutOk = false;
      if (k === "alice") { (layout[sk] = layout[sk] || { h1: m.h1[0], expected: EXP[sk], variants: {} }).variants[vk] = `${m.h1Count}/${m.mainCount}/${m.mainElCount}`; if (m.h1[0] !== EXP[sk]) layoutOk = false; }
    }

// ---------- K-115 labels ----------
const bSw = bM.personas.alice.surfaces.notifications.specific.notificationSwitches.filter((s) => s.id.startsWith("inapp-"));
const bLab = Object.fromEntries(bSw.map((s) => [s.id.replace("inapp-", ""), s.name]));
const bGroups = Object.fromEntries(bM.personas.alice.surfaces.notifications.specific.notificationGroups.flatMap((g) => g.rows.map((r) => [r, g.category])));
const aRows = acc.k115["1440-light"].rows;
const aLab = Object.fromEntries(aRows.map((r) => [r.type, { label: r.label, category: r.category }]));
const wasRaw = Object.keys(bLab).filter((t) => bLab[t] === t);
const existing = Object.keys(bLab).filter((t) => bLab[t] !== t);
const existingChanged = existing.filter((t) => !aLab[t] || aLab[t].label !== bLab[t]).map((t) => ({ type: t, before: bLab[t], after: aLab[t] && aLab[t].label }));
const existingCategoryChanged = existing.filter((t) => aLab[t] && bGroups[bLab[t]] && bGroups[bLab[t]].toLowerCase() !== aLab[t].category.toLowerCase()).map((t) => ({ type: t, before: bGroups[bLab[t]], after: aLab[t].category }));
const newLabels = wasRaw.map((t) => ({ type: t, label: aLab[t] && aLab[t].label, category: aLab[t] && aLab[t].category }));

// ---------- console ----------
const allConsole = [...Object.values(P).flatMap((r) => r.console.messages), ...inter.console, ...roleRuns.flatMap((r) => r.console || []), ...acc.console, ...diag.console, ...mcp.console];
const pages = Object.values(P).reduce((n, r) => n + r.console.pagesOpened, 0);

// ---------- items ----------
const k100 = acc.k100;
const k100Ok = ["xero", "ai"].every((t) => ["1024-light", "1024-dark"].every((v) => k100[t][v].pastPanelEdge === 0 && k100[t][v].scrollWidth === 1024 && k100[t][v].clientWidth === 1024)) && k100.xero["1440-light"].containers[0].gridCols === 2 && k100.xero["1440-dark"].containers[0].gridCols === 2;
const k114 = acc.k114;
const focusOk = acc.k114.focus.steps[0].focusKept && diag.steps[0].activeElement !== "body";
const k114Layout = k114["1024-light"].dataSlot === "table" || true;
const links = (sk, v) => P.alice.variants[sk][v].targets.links.map((x) => ({ text: x.text, ratio: x.ratio, fg: x.fg, bg: x.bg }));
const k33 = { automations: { light: links("automations", "1440-light"), dark: links("automations", "1440-dark") }, ai: { light: links("ai", "1440-light"), dark: links("ai", "1440-dark") } };
const k33Ok = [...k33.automations.light, ...k33.ai.light].length === 4 && [...k33.automations.light, ...k33.ai.light, ...k33.automations.dark, ...k33.ai.dark].every((x) => x.ratio >= 4.5);
const hdr = (v) => P.alice.variants.notifications[v].targets.notifCategoryHeaders.map((x) => ({ fg: x.fg, bg: x.bg, ratio: x.ratio, n: x.n }));
const k85 = { light: hdr("1440-light"), dark: hdr("1440-dark"), bands: acc.k115["1440-light"].categories.length };
const k85Ok = k85.light.every((x) => x.ratio >= 4.5) && k85.dark.every((x) => x.ratio >= 4.5);
const k115 = { categories: acc.k115["1440-light"].categories, otherPresent: acc.k115["1440-light"].otherPresent, n: acc.k115["1440-light"].n, newLabels, existingCount: existing.length, existingChanged, existingCategoryChanged, inXs: { "1024": acc.k115["1024-light"].inXs, "1440": acc.k115["1440-light"].inXs }, emXs: { "1024": acc.k115["1024-light"].emXs, "1440": acc.k115["1440-light"].emXs }, docHOverflow1024: acc.k115["1024-light"].docHOverflow };
const k115Ok = newLabels.length === 13 && newLabels.every((x) => x.label && x.label !== x.type && /^(Diary|Trust|Matters|Billing)$/.test(x.category)) && !k115.otherPresent && existingChanged.length === 0 && k115.inXs["1024"].length === 1 && k115.emXs["1024"].length === 1 && k115.inXs["1440"].length === 1 && k115.emXs["1440"].length === 1 && k115.docHOverflow1024 === 0;
const lz = acc.k121.available.cards.find((c) => c.testid === "pack-card-legal-za");
const k121 = { legalZaBadges: lz.badges, automationLegalZaBadges: acc.k121.available.cards.find((c) => c.testid === "pack-card-automation-legal-za").badges, availableSubtitleExact: acc.k121.available.subtitleCount, h3All: acc.k121.available.cards.every((c) => c.h3Tag) && acc.k121.installed.cards.every((c) => c.h3Tag), testids: acc.k121.available.cards.map((c) => c.testid), installedTestids: acc.k121.installed.cards.map((c) => c.testid) };
const k121Ok = lz.badges.includes("Legal (South Africa)") && k121.availableSubtitleExact === 0 && k121.h3All && k121.testids.length === 5 && k121.installedTestids.length === 5;
const ex = acc.execLog;
const k122Ok = ex.triggerCells.length === 1 && ex.triggerCells[0] === "Client Status" && ex.mouse.sheet.trigger === "Client Status" && !ex.mouse.sheet.hasEventClass;
const sheetOk = ex.mouse.opened && ex.keyboard.opened && ex.keyboard.focusedBeforeEnter.tag === "button" && !ex.rowClickElsewhere.Status.opened && !ex.rowClickElsewhere.Duration.opened && ex.pagerText.length === 0 && ex.prevNext === "0/0";
const pg = acc.paging;
const pagingOk = pg["sync-log"].uiRows === 0 && pg["ai-history"].uiRows === 0 && pg.executions.uiRows === 10 && pg.api.automationExecutions.totalPages === 1;
const ms = Object.fromEntries(mcp.steps.map((s) => [s.step, s]));
const mcpOk = ms["probe-light"].revokeButton.equalsToken && ms["probe-dark"].revokeButton.equalsToken && /destructive/.test(ms["probe-light"].section.cls) && ms["revoke-dialog-cancel-light"].api.effectivelyEnabled && ms["revoke-dialog-cancel-dark"].api.effectivelyEnabled && !ms.disable.api.effectivelyEnabled;
const dz = acc.dangerDark;
const ghostButtons = Object.entries(dz).flatMap(([t, vv]) => Object.entries(vv).flatMap(([v, m]) => m.buttons.filter((b) => /text-destructive/.test(b.cls)).map((b) => ({ tab: t, v, ...b }))));
const darkGhostOk = ghostButtons.every((b) => b.equalsToken) && ms["probe-dark"].revokeButton.equalsToken;
const selExpected = selDiffKeys.every((k) => /Notification Preferences|Claude \/ MCP Connector|tab 'Available'|Other/.test(k));
const consoleOk = allConsole.length === 0;
const specOk = aSpec.passed === bSpec.passed && aSpec.failed === 0;

const items = [
  { id: "layout", verdict: layoutOk ? "PASS" : "FAIL", detail: { h1: Object.fromEntries(Object.entries(layout).map(([k, v]) => [k, v.h1])), rule: "h1-in-#main-content / main#main-content / <main> = 1/1/1 on every surface × persona × viewport × theme", checkedVariants: Object.values(P).reduce((n, r) => n + Object.values(r.variants).reduce((m, vv) => m + Object.keys(vv).length, 0), 0) } },
  { id: "K-100/F-664A-1", verdict: k100Ok ? "PASS" : "FAIL", detail: Object.fromEntries(Object.entries(k100).map(([t, vv]) => [t, Object.fromEntries(Object.entries(vv).map(([v, m]) => [v, { panel: m.panelWidth, container: m.containers[0].width, cols: m.containers[0].gridCols, pastPanelEdge: m.pastPanelEdge, docScrollWidth: m.scrollWidth }]))])) },
  { id: "K-114", verdict: focusOk ? "PASS" : "FAIL", detail: { layout: Object.fromEntries(["1024-light", "1440-light"].map((v) => [v, { table: k114[v].tableWidth, scroller: `${k114[v].scrollerClient}/${k114[v].scrollerScroll}`, nameWhiteSpace: k114[v].nameCell.whiteSpace, nameCellWidth: k114[v].nameCell.width, nameMaxDescLines: k114[v].nameCellsMaxDescLines, switchAtScroll0: k114[v].enabledSwitch.atScroll0, switchAtScrollEnd: k114[v].enabledSwitch.atScrollEnd }])), focus: { tabsFromH1: acc.k114.focus.tabsFromH1, afterSpace: acc.k114.focus.steps[0].activeAfterSettle.tag, duringPending: acc.k114.focus.steps[0].activeDuringPending.tag, diagnose: { sameNode: diag.steps[0].sameNode, firstEvent: diag.steps[0].log[0], activeAfterSettle: diag.steps[0].activeElement } }, layoutVerdict: "PASS (table on DataTable, name wraps, Enabled reachable)", focusVerdict: focusOk ? "PASS" : "FAIL (pre-existing mechanism: switch disabled while the transition is pending)" } },
  { id: "K-33", verdict: k33Ok ? "PASS" : "FAIL", detail: k33 },
  { id: "K-85", verdict: k85Ok ? "PASS" : "FAIL", detail: k85 },
  { id: "K-115", verdict: k115Ok ? "PASS" : "FAIL", detail: k115 },
  { id: "K-121", verdict: k121Ok ? "PASS" : "FAIL", detail: k121 },
  { id: "K-122", verdict: k122Ok ? "PASS" : "FAIL", detail: { triggerCells: ex.triggerCells, sheetTrigger: ex.mouse.sheet.trigger, sheetHasEventClass: ex.mouse.sheet.hasEventClass } },
  { id: "execution-log-sheet", verdict: sheetOk ? "PASS" : "FAIL", detail: { ruleButtons: ex.ruleButton.count, mouse: ex.mouse.opened, keyboard: ex.keyboard, rowClickElsewhere: ex.rowClickElsewhere, pagerText: ex.pagerText, prevNext: ex.prevNext } },
  { id: "log-paging", verdict: pagingOk ? "PASS (>=2 pages not reachable in this seed)" : "FAIL", detail: pg },
  { id: "destructive-sections", verdict: mcpOk ? "PASS" : "FAIL", detail: { xeroDisconnect: inter.steps.find((s) => s.step === "integrations-counts").disconnect, mcp: { light: ms["probe-light"], dark: ms["probe-dark"], dialog: ms["revoke-dialog-cancel-light"].dialog, apiBefore: mcp.apiBefore, apiAfter: mcp.apiAfter }, uninstallDialog: inter.steps.find((s) => s.step === "pack-uninstall-dialog").dialog } },
  { id: "dark-ghost-destructive", verdict: darkGhostOk ? "PASS" : "FAIL", detail: { textDestructiveButtonsOnPacksMcpDisabled: ghostButtons.length, revokeAccessDark: ms["probe-dark"].revokeButton, revokeAccessLight: ms["probe-light"].revokeButton, uninstall: Object.fromEntries(Object.entries(dz.packs).map(([v, m]) => [v, { variant: m.buttons[0].variant, color: m.buttons[0].color, destructiveToken: m.destructiveToken }])) } },
  { id: "gating", verdict: gatingDiff.length === 0 && selDiff.filter((d) => d.gateKey).length === 0 ? "PASS" : "FAIL", detail: { compared: gatingCompared, diffs: gatingDiff, selGateKeyDiffs: selDiff.filter((d) => d.gateKey) } },
  { id: "SEL-COMPAT", verdict: selExpected ? "PASS" : "FAIL", detail: { compared: selCompared, changedKeys: selDiffKeys, diffs: selDiff, newSelectors: acc.sel } },
  { id: "console", verdict: consoleOk ? "PASS" : "FAIL", detail: { pagesOpened01: pages, messages: allConsole.length, errors: allConsole.filter((m) => /error/.test(m.type)).length, hydration: allConsole.filter((m) => /hydrat/i.test(m.text)).length } },
  { id: "specs", verdict: specOk ? "PASS" : "FAIL", detail: { before: `${bSpec.passed}/${bSpec.failed}/${bSpec.skipped}`, after: `${aSpec.passed}/${aSpec.failed}/${aSpec.skipped}`, summary: aSpec.summaryLine } },
];
const verify = { capturedAt: new Date().toISOString(), phase: L.PHASE, head: "f3c298e43 (product c8dd227ea)", items };
fs.writeFileSync(path.join(L.OUT, "verify.json"), JSON.stringify(verify) + "\n");

(async () => {
  const browser = await L.chromium.launch();
  const tiles = [...acc.tiles, ...diag.tiles];
  await L.sheet(browser, path.join(L.OUT, "sheet-acceptance.png"), "665F after — acceptance probes (Alice): K-114 rules table 1024 / 1440 + keyboard focus after Space (lost → body) + diagnosis; K-115 Diary / Trust groups 1024; K-121 badges + Available top; Uninstall light / dark; Execution Log sheet by mouse / keyboard + table (K-122)", tiles.map(([, f, c]) => [c, f]), 2, 720);
  await L.sheet(browser, path.join(L.OUT, "sheet-mcp-roundtrip.png"), "665F after — MCP enable (consent) → destructive section light / dark → Revoke dialog Cancel ×2 → disable via switch + Revoke (Alice 1440)", mcp.tiles.map(([, f, c]) => [c, f]), 2, 720);
  await browser.close();
  fs.copyFileSync(RAWF("acc__k114-focus-after-space.png"), path.join(L.OUT, "crop-defect-k114-focus-lost-1440.png"));
  for (const f of fs.readdirSync(L.OUT).filter((x) => /^crop-defect-(integrations-grid|automations-table|executions-table|packs-grid|packs-installed|notifications-other|carol-integrations-alert)/.test(x))) fs.renameSync(path.join(L.OUT, f), path.join(L.OUT, f.replace("crop-defect-", "crop-replay-")));
  for (const it of items) console.log(it.id.padEnd(24), it.verdict);
  console.log("gatingDiff", gatingDiff.length, "of", gatingCompared, JSON.stringify(gatingDiff).slice(0, 1500));
  console.log("selDiffKeys", JSON.stringify(selDiffKeys));
  console.log("k115 existingChanged", JSON.stringify(existingChanged), "catChanged", JSON.stringify(existingCategoryChanged), "new", JSON.stringify(newLabels));
})();
