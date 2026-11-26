// 665A OBS-AFTER copy of ../../before/scripts/04-assemble.cjs. Adaptations (README "Scripts"):
//   D1 measurements.json also carries 03's `probes` (K-104 / K-106 / K-39 / Retention Policies).
//   D2 sheet-roles-ui.png → sheet-k102-create-role.png (same roles__/team__ tiles, now K-102 at
//      1024/1280/1440); new sheet-k104-roles-1024.png (k104__ tiles); sheet-saves.png also takes the
//      k39__ and retention__ crops.
//   D3 writes ../verdicts.json: items 1–8 + K-102 computed from the after raw files against
//      ../../before/measurements.json and ../../before/sel-compat.json.
// 665A OBS step 4: merge the raw run files in $RAW (persona-*.json from 01, roles-*.json from 02,
// saves.json from 03) into ../measurements.json, ../sel-compat.json, ../console.json, ../saves.json
// (single-line JSON) and build the contact sheets with the shared 661B sheet() helper.
// EVIDENCE-BUDGET: one sheet per tab × persona (cols 1024 light | 1024 dark | 1440 light | 1440 dark;
// row 1 viewport, row 2 full page), the two custom-role personas share one sheet per tab, plus
// sheet-roles-ui.png (custom-role creation + assignment) and sheet-saves.png (save-model crops + FICA).
// Usage: PHASE=before STACK="<provenance>" node 04-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const STACK = process.env.STACK || "(STACK not given)";
const rd = (f) => (fs.existsSync(path.join(L.RAW, f)) ? JSON.parse(fs.readFileSync(path.join(L.RAW, f), "utf8")) : null);
const PERSONAS = [
  ["alice", "Alice (owner)"],
  ["carol", "Carol (member)"],
  ["carol-cr-holder", "Carol with custom role '665A OBS Oversight' (TEAM_OVERSIGHT)"],
  ["carol-cr-lacker", "Carol with custom role '665A OBS No Oversight' (PROJECT_MANAGEMENT, no TEAM_OVERSIGHT)"],
];
const VARIANTS = ["1024-light", "1024-dark", "1440-light", "1440-dark"];

(async () => {
  const P = {};
  for (const [k] of PERSONAS) P[k] = rd(`persona-${k}.json`);
  const roleFiles = fs.readdirSync(L.RAW).filter((f) => /^roles-.*\.json$/.test(f)).sort();
  const roles = Object.fromEntries(roleFiles.map((f) => [f.replace(/^roles-|\.json$/g, ""), rd(f)]));
  const saves = rd("saves.json");
  const now = new Date().toISOString();

  const meas = { capturedAt: now, stack: STACK, phase: L.PHASE, personas: {}, customRoles: {}, kinks: saves ? saves.kinks : null, probes: saves ? saves.probes : null };
  const sel = { capturedAt: now, stack: STACK, note: "counts per selector, 1024×768 light, each 665A tab; legacyRoutes = rbac-capabilities.spec routes (+ the old per-page routes)", personas: {} };
  const cons = { capturedAt: now, stack: STACK, totals: { pagesOpened: 0, errors: 0, warnings: 0, hydration: 0 }, byRun: {} };
  for (const [k] of PERSONAS) {
    const r = P[k];
    if (!r) continue;
    meas.personas[k] = { capturedAt: r.capturedAt, caps: r.caps, tabs: r.tabs, variants: r.variants, capabilityReference: r.capabilityReference };
    sel.personas[k] = { tabs: r.selCompat, legacyRoutes: r.legacyRoutes };
    cons.byRun[`01-capture ${k}`] = { pagesOpened: r.console.pagesOpened, errors: r.console.errors, warnings: r.console.warnings, hydration: r.console.hydration, messages: r.console.messages };
    cons.totals.pagesOpened += r.console.pagesOpened;
    cons.totals.errors += r.console.errors;
    cons.totals.warnings += r.console.warnings;
    cons.totals.hydration += r.console.hydration;
  }
  for (const [k, r] of Object.entries(roles)) {
    meas.customRoles[k] = { at: r.at, cmd: r.cmd, arg: r.arg, steps: r.steps };
    cons.byRun[`02-roles ${k}`] = { messages: r.console };
  }
  if (saves) cons.byRun["03-saves"] = { messages: saves.console };
  const allMsgs = Object.values(cons.byRun).flatMap((x) => x.messages || []);
  cons.totals.allMessages = allMsgs.length;
  cons.totals.allErrors = allMsgs.filter((m) => m.type === "error" || m.type === "pageerror").length;
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(meas) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify(sel) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify(cons) + "\n");
  if (saves) {
    const { tiles, console: c, probes, ...rest } = saves;
    fs.writeFileSync(path.join(L.OUT, "saves.json"), JSON.stringify({ stack: STACK, ...rest }) + "\n");
  }

  // ---------- sheets ----------
  const T = {};
  for (const r of [...Object.values(P), ...Object.values(roles), saves].filter(Boolean)) for (const [k, f, c] of r.tiles || []) T[k] = { f, c };
  const tile = (key) => [T[key] ? T[key].c : `${key} (not shot)`, T[key] ? T[key].f : null];
  const browser = await L.chromium.launch();
  const written = [];
  const sheet = async (name, title, tiles, cols, tileW) => {
    await L.sheet(browser, path.join(L.OUT, name), title, tiles, cols, tileW);
    written.push(name);
  };
  const ph = L.PHASE;
  for (const tab of L.TABS) {
    for (const [k, label] of PERSONAS.slice(0, 2)) {
      if (!P[k]) continue;
      const t = [...VARIANTS.map((v) => tile(`tab__${tab}-${k}-${v}-vp`)), ...VARIANTS.map((v) => tile(`tab__${tab}-${k}-${v}-full`))];
      if (tab === "roles" && T[`tab__roles-${k}-1440-light-capref`]) t.push(tile(`tab__roles-${k}-1440-light-capref`));
      await sheet(`sheet-${tab}-${k}.png`, `665A ${ph} — Settings › Firm ?tab=${tab}, ${label}: cols 1024 light | 1024 dark | 1440 light | 1440 dark; row 1 viewport, row 2 full page`, t, 4, 520);
    }
    if (P["carol-cr-holder"] || P["carol-cr-lacker"]) {
      const t = [];
      for (const k of ["carol-cr-holder", "carol-cr-lacker"]) for (const kind of ["vp", "full"]) for (const v of VARIANTS) t.push(tile(`tab__${tab}-${k}-${v}-${kind}`));
      await sheet(`sheet-${tab}-custom-role.png`, `665A ${ph} — Settings › Firm ?tab=${tab}, Carol holding a custom role: rows 1–2 '665A OBS Oversight' (TEAM_OVERSIGHT) viewport / full, rows 3–4 '665A OBS No Oversight' (no TEAM_OVERSIGHT) viewport / full; cols 1024 light | 1024 dark | 1440 light | 1440 dark`, t, 4, 520);
    }
  }
  const rk = Object.keys(T).filter((k) => /^(roles__|team__)/.test(k));
  if (rk.length) await sheet("sheet-k102-create-role.png", `665A ${ph} — K-102 Create Role dialog (Alice) at 1024×768 light/dark, 1280×720 and 1440×900: opened, body wheel-scrolled, filled + real click on Create (role 1 at 1280, role 2 at 1024); Roles tab after creation; Team › Carol's member panel per assignment (custom role → custom role → restored to Member)`, rk.map(tile), 3, 640);
  const kk = Object.keys(T).filter((k) => /^k104__/.test(k));
  if (kk.length) await sheet("sheet-k104-roles-1024.png", `665A ${ph} — K-104 Roles at 1024×768 (Alice): viewport scrolled to the Member card and the Member card crop, light / dark`, kk.map(tile), 2, 760);
  const sk = Object.keys(T).filter((k) => /^(save__|fica__|k39__|retention__)/.test(k));
  if (sk.length) await sheet("sheet-saves.png", `665A ${ph} — one save per save model (Alice 1440 light, element crops taken when the message appeared) + Phase 93 FICA fieldset light / dark + K-39 Branding section light / dark + Retention Policies at 1024 light / dark and the Retention Days focus test`, sk.map(tile), 2);
  await browser.close();
  console.log("sheets", written.length, written.join(" "));
  writeVerdicts(P, roles, saves, cons, now);
  console.log("console totals", JSON.stringify(cons.totals));
})();

// ---------- D3 verdicts ----------
function writeVerdicts(P, roles, saves, cons, now) {
  const B = JSON.parse(fs.readFileSync(path.join(L.OUT, "../before/measurements.json"), "utf8"));
  const BS = JSON.parse(fs.readFileSync(path.join(L.OUT, "../before/sel-compat.json"), "utf8"));
  const V = { capturedAt: now, items: {} };
  const keys = PERSONAS.map(([k]) => k);
  // 1. landmarks / h1 / headings / gating
  const expectH1 = (tab, b) => (tab === "subscription" && b === "Billing" ? "Subscription" : b);
  const h1Rows = [], headingDiffs = [], gateDiffs = [], landmarkFails = [];
  for (const k of keys) for (const t of L.TABS) {
    const a = P[k].tabs[t], b = B.personas[k].tabs[t];
    for (const [v, m] of Object.entries(P[k].variants[t])) if (m.mainCount !== 1 || m.h1Count !== 1) landmarkFails.push(`${k}/${t}/${v}: main ${m.mainCount} h1 ${m.h1Count}`);
    const bh1 = b.h1.map((x) => x.text).join("|"), ah1 = a.h1.map((x) => x.text).join("|");
    h1Rows.push({ persona: k, tab: t, before: bh1, after: ah1, ok: ah1 === expectH1(t, bh1) });
    const bh = b.headings.map((h) => `${h.level}:${h.text}`), ah = a.headings.map((h) => `${h.level}:${h.text}`);
    if (JSON.stringify(bh) !== JSON.stringify(ah)) headingDiffs.push({ persona: k, tab: t, removed: bh.filter((x) => !ah.includes(x)), added: ah.filter((x) => !bh.includes(x)) });
    if (JSON.stringify(a.tabBar) !== JSON.stringify(b.tabBar) || a.notice !== b.notice || a.gateOutcome !== b.gateOutcome) gateDiffs.push({ persona: k, tab: t, before: [b.gateOutcome, b.notice, b.tabBar], after: [a.gateOutcome, a.notice, a.tabBar] });
  }
  const explained = (d) => d.removed.concat(d.added).every((x) => /^[23]:No organization documents yet$/.test(x) || /^1:(Billing|Subscription)$/.test(x) || x === "2:Managed Account");
  V.items["1-landmarks-h1-headings-gating"] = { verdict: landmarkFails.length === 0 && h1Rows.every((r) => r.ok) && headingDiffs.every(explained) && gateDiffs.length === 0 ? "PASS" : "FAIL", landmarkFails, h1Mismatches: h1Rows.filter((r) => !r.ok), h1Changed: h1Rows.filter((r) => r.before !== r.after), headingDiffs, headingDiffsAllExplained: headingDiffs.every(explained), gateDiffs, checked: `${keys.length} personas × ${L.TABS.length} tabs × 4 variants` };
  // 2. SEL-COMPAT
  const selDiffs = [];
  for (const k of keys) {
    for (const t of L.TABS) { const a = P[k].selCompat[t], b = BS.personas[k].tabs[t]; for (const sel of new Set([...Object.keys(a), ...Object.keys(b)])) if (a[sel] !== b[sel]) selDiffs.push({ persona: k, tab: t, sel, before: b[sel], after: a[sel] }); }
    for (const r of Object.keys(BS.personas[k].legacyRoutes)) { const a = P[k].legacyRoutes[r], b = BS.personas[k].legacyRoutes[r]; const pick = (x) => JSON.stringify([x.finalUrl, x.somethingWentWrong, x.deniedRegex, x.h1.map((h) => (h === "Billing" ? "Subscription" : h))]); if (pick(a) !== pick(b)) selDiffs.push({ persona: k, route: r, before: b, after: a }); }
  }
  V.items["2-sel-compat"] = { verdict: selDiffs.length === 0 ? "PASS" : "FAIL", diffs: selDiffs, note: "legacy-route h1 'Billing' is compared as 'Subscription' (K-106)" };
  // 3. saves
  const sv = saves.saves.map((x) => ({ model: x.model, message: x.message, where: x.messageWhere && x.messageWhere.slots, ms: x.ms, stall: x.stall, actions: x.serverActions.map((a) => a.status), apiAfter: x.apiAfter, console: x.console.length }));
  const msgOk = sv.filter((x) => !/Features/.test(x.model)).every((x) => x.message && !/Failed|error|valid hex|Fix the/.test(x.message));
  V.items["3-save-models"] = { verdict: sv.every((x) => !x.stall && x.actions.every((s) => s === 200) && x.console === 0) && msgOk && saves.restored ? "PASS" : "FAIL", restored: saves.restored, saves: sv };
  // 4. retention table
  const R = saves.probes.retention;
  V.items["4-retention-table"] = { verdict: R.focus.sameNodeThroughout && R.focus.restoredEqualsOriginal && R.deleteConfirm.dismissed && R.deleteConfirm.dialog.type === "confirm" && R.deleteConfirm.rowStillPresent && R.deleteConfirm.apiUnchanged ? "PASS" : "FAIL", focus: { original: R.focus.original, valuesAfter123: R.focus.valuesAfter123, sameNodeThroughout: R.focus.sameNodeThroughout, restored: R.focus.restoredValue }, deleteConfirm: R.deleteConfirm };
  // 5. K-104
  const K = saves.probes.k104;
  V.items["5-K-104"] = { verdict: ["light", "dark"].every((t) => K[t].pageHOverflow === 0 && K[t].allInside && K[t].newLabelsShown.every((x) => x.shown) && K[t].rawEnumShown.length === 0) ? "PASS" : "FAIL", before: { light: 81, dark: 86 }, after: { light: K.light.pageHOverflow, dark: K.dark.pageHOverflow }, memberCard: { light: K.light.memberCard, dark: K.dark.memberCard }, maxBadgeRight: { light: Math.max(...K.light.badges.map((b) => b.right)), dark: Math.max(...K.dark.badges.map((b) => b.right)) }, truncated: K.light.badges.filter((b) => b.truncated).map((b) => b.text), labels: K.light.newLabelsShown };
  // 6. K-106 + K-39
  const k106 = saves.probes.k106, k39 = saves.probes.k39;
  const k39ok = ["light", "dark"].every((t) => Object.values(k39[t]).every((x) => x && x.ratio >= 4.5));
  V.items["6-K-106-K-39"] = { verdict: k106.h1 === "Subscription" && k106.h1InViewport && JSON.stringify(k106.toolbarChips) === JSON.stringify(["Trial period", "Invoiced manually"]) && k106.sectionHeading === "Managed Account" && /^Your Kazi subscription is managed by b2mash\. Contact support to change your plan or billing details\.$/.test(k106.body) && k106.bodyInViewport && k39ok ? "PASS" : "FAIL", k106, k39: Object.fromEntries(["light", "dark"].map((t) => [t, Object.fromEntries(Object.entries(k39[t]).map(([k, x]) => [k, x && `${x.fg} on ${x.bg} = ${x.ratio}:1`]))])) };
  // 7. layout
  const ovf = [], clip = [], darkBg = [], bars = [];
  for (const k of keys) for (const t of L.TABS) for (const [v, m] of Object.entries(P[k].variants[t])) {
    if (m.hOverflow) ovf.push(`${k}/${t}/${v}: ${m.hOverflow}px`);
    for (const c of m.layout.clipped.filter((c) => !c.inScroller)) clip.push({ at: `${k}/${t}/${v}`, ...c });
    if (v.endsWith("dark")) for (const s of m.layout.sections) if (s.bgLum > 0.2) darkBg.push({ at: `${k}/${t}/${v}`, ...s });
    if (k === "alice" && t === "general") bars.push({ v, ...m.layout.actionBar });
  }
  const barOk = bars.length === 4 && bars.every((b) => b.afterSection === "Branding" && b.nextSectionInDoc === "Letterhead" && b.prevSectionRight === b.right && b.buttons.length === 1 && b.buttons[0].text === "Save Settings" && b.buttons[0].right === b.right && b.position === "static");
  const darkSections = [...new Set(keys.flatMap((k) => L.TABS.flatMap((t) => Object.entries(P[k].variants[t]).filter(([v]) => v.endsWith("dark")).flatMap(([, m]) => m.layout.sections.map((s) => s.bg)))))];
  const ret = saves.probes.retention.geometry;
  V.items["7-layout"] = { verdict: ovf.length === 0 && clip.length === 0 && darkBg.length === 0 && barOk ? "PASS" : "FAIL", pageOverflow: ovf, clippedInHeadersFootersBars: clip, darkSectionBackgrounds: darkSections, darkWhiteSections: darkBg, generalActionBar: bars.map((b) => ({ v: b.v, afterSection: b.afterSection, nextSection: b.nextSectionInDoc, right: b.right, sectionRight: b.prevSectionRight, buttonRight: b.buttons[0] && b.buttons[0].right, gapAbove: b.gapAbove, position: b.position })), firmTabStripScrollerItems: "664A firm tab strip overflows into its own horizontal scroller at 1024 (inScroller, not counted)", retentionTable1024: Object.fromEntries(Object.entries(ret).map(([k, g]) => [k, { scroller: `${g.scroller.clientWidth}/${g.scroller.scrollWidth}`, hiddenColumns: g.hiddenColumns, deleteVisible: g.rows.map((r) => r.deleteVisibleInScroller) }])) };
  // 8. console
  V.items["8-console"] = { verdict: cons.totals.allMessages === 0 ? "PASS" : "FAIL", totals: cons.totals };
  // K-102
  const steps = Object.values(roles).flatMap((r) => r.steps || []);
  const meas = steps.filter((x) => x.step === "create-dialog-measure");
  const creates = steps.filter((x) => x.step === "create");
  V.items["K-102-create-role-dialog"] = { verdict: meas.every((m) => m.titleInViewport && m.titleBottomInViewport && m.submit.inViewport && m.submitHit && m.cancel.inViewport && m.body.scrolls) && meas.some((m) => m.body.scrollTop > 0) && creates.length === 2 && creates.every((c) => c.submit === "click") ? "PASS" : "FAIL", measures: meas.map((m) => ({ viewport: m.viewport.join ? m.viewport.join("x") : m.viewport, theme: m.theme, at: m.at, dialog: m.dialog, titleTop: m.titleTop, create: m.submit && `${m.submit.top}–${m.submit.bottom}`, hit: m.submitHit, body: m.body && `${m.body.clientHeight}/${m.body.scrollHeight} scrollTop ${m.body.scrollTop}` })), creates: creates.map((c) => ({ role: c.role, viewport: c.viewport, submit: c.submit })), before: "986 px tall, Create off-screen at 1280×720 (792–828) and 1440×900 (882–918); dispatchEvent needed" };
  fs.writeFileSync(path.join(L.OUT, "verdicts.json"), JSON.stringify(V) + "\n");
  for (const [k, x] of Object.entries(V.items)) console.log("VERDICT", k, x.verdict);
}
