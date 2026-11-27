// 669A step 1: capture. Reads the ids from before/seed.json (00-seed.cjs, run once). Re-run UNEDITED
// with PHASE=after for the OBS-after.
// Read-only on the data EXCEPT one recorded write per run: the "run a check" interaction submits ONE
// conflict check (Alice 1440 light, name "Pillay Holdings", New Matter) — the result panel exists only
// after a submit. It runs after every page load of both personas, so all page loads of one run see the
// same History count (before: the stack's 8; each replay adds 1). Everything else: dialogs/menus opened
// and Escaped, filters/searches typed (client state only), nothing saved.
// Personas through /mock-login: alice (owner), carol (member). Variants 1024×768 / 1440×900 × light /
// dark (theme via localStorage `kazi-theme`, rail expanded, DPR 1, reduced motion, Africa/Johannesburg).
// The conflict result is re-shot at 1024 and in dark by resizing the viewport and switching the <html>
// class (`.dark`, the class next-themes sets) on the same page — re-submitting would write again.
// Raw PNGs → /tmp/669A-$PHASE-raw/ (never committed); JSON → $RAW/capture.json (02-assemble.cjs).
// Usage: PHASE=before|after node 01-capture.cjs   (NO_WRITE=1 skips the conflict-check submit — dry runs only)
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const { probe } = require("./probe.cjs");

const S = L.readSeed();
const ID = S.ids;
const B = L.BASE;
const V4 = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const L1 = ["1440-light"];
const AC = { alice: V4, carol: V4 };
// key, url, {persona: variants}, settle action
const TARGETS = [
  ["compliance", `${B}/compliance`, AC],
  ["compliance-tab-param", `${B}/compliance?tab=ai-audit`, { alice: L1 }],
  ["requests", `${B}/compliance/requests`, AC],
  ["requests-RECEIVED", `${B}/compliance/requests?status=RECEIVED`, { alice: L1 }],
  ["requests-IN_PROGRESS", `${B}/compliance/requests?status=IN_PROGRESS`, { alice: ["1440-light", "1024-dark"] }],
  ["requests-COMPLETED", `${B}/compliance/requests?status=COMPLETED`, { alice: L1 }],
  ["requests-REJECTED", `${B}/compliance/requests?status=REJECTED`, { alice: L1 }],
  ["request-detail", `${B}/compliance/requests/${ID.dsarId}`, AC],
  ["conflict-check", `${B}/conflict-check`, AC],
  ["conflict-prefill-client", `${B}/conflict-check?customerId=${ID.conflictPrefillClientId}&checkType=NEW_MATTER`, { alice: L1, carol: L1 }],
  ["conflict-prefill-explicit", `${B}/conflict-check?checkedName=Thabo%20Mokoena&checkedIdNumber=8001015009087&checkType=PERIODIC_REVIEW`, { alice: L1 }],
  ["conflict-prefill-badtype", `${B}/conflict-check?checkedName=Road%20Accident%20Fund&checkType=BOGUS`, { alice: L1 }],
  ["adverse-parties", `${B}/legal/adverse-parties`, AC],
  ["disbursements", `${B}/legal/disbursements`, AC],
  ["disbursement-detail", `${B}/legal/disbursements/${ID.disbursementTrust}`, AC],
  ["disbursement-detail-office", `${B}/legal/disbursements/${ID.disbursementActiveMatter}`, { alice: L1 }],
  ["matter-disbursements-tab", `${B}/matters/${ID.disbursementActiveMatterMatterId}?tab=disbursements`, { alice: L1 }, "project-disbursements-tab"],
  ["matter-disbursements-tab-empty", `${B}/matters/${ID.statementsEmptyMatterId}?tab=disbursements`, { alice: L1 }, "project-disbursements-tab"],
  ["tariffs", `${B}/legal/tariffs`, AC],
  ["information-request", `${B}/information-requests/${ID.informationRequestNullReminder}`, AC],
  ["information-request-2", `${B}/information-requests/${ID.informationRequestWithReminder}`, { alice: L1 }],
  ["statements-empty", `${B}/matters/${ID.statementsEmptyMatterId}?tab=statements`, { alice: L1, carol: L1 }, "project-statements-tab"],
  ["checklist-empty", `${B}/clients/${ID.checklistEmptyClientId}?tab=onboarding`, { alice: L1, carol: L1 }],
];
// Carol is not on the seed's statements-empty matter: her own first ACTIVE matter (by name) with 0
// statements is resolved from the API at start (recorded in capture.json › carolStatementsMatter).
async function carolMatter() {
  const t = await L.idpToken("carol");
  const ms = L.list((await L.apiGet(t, "/api/matters?size=200")).json).filter((m) => m.status === "ACTIVE").sort((a, b) => a.name.localeCompare(b.name));
  for (const m of ms) if (L.list((await L.apiGet(t, `/api/matters/${m.id}/statements?page=0&size=20`)).json).length === 0) return { id: m.id, name: m.name, carolMatters: ms.length };
  return null;
}

const cons = [];
const out = { phase: L.PHASE, at: new Date().toISOString(), seedIds: ID, caps: {}, pages: {}, crops: {}, interactions: {}, writes: [] };
const rel = (page) => { const u = new URL(page.url()); return u.pathname.replace(`/org/${L.ORG}`, "") + u.search; };
async function settle(page, action) {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.locator("#main-content h1").first().waitFor({ timeout: 15000 }).catch(() => {});
  if (action) {
    await page.getByTestId(action).first().waitFor({ timeout: 15000 }).catch(() => {});
    await page.getByText(/^Loading/).first().waitFor({ state: "hidden", timeout: 15000 }).catch(() => {});
  }
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(700);
}
async function crop(page, name, sel) {
  try {
    const loc = page.locator(sel).first();
    if (!(await loc.count())) { out.crops[name] = { missing: true, sel }; return null; }
    const f = path.join(L.RAW, `crop-${name}.png`);
    await loc.scrollIntoViewIfNeeded();
    await loc.screenshot({ path: f });
    out.crops[name] = { file: f, sel };
    return f;
  } catch (e) { out.crops[name] = { error: e.message.split("\n")[0], sel }; return null; }
}
const CROPS = {
  "information-request": [["k37-ir-header", '#main-content h1 >> xpath=ancestor::div[contains(@class,"justify-between")][1]'], ["k67-k85-ir-meta", 'xpath=//*[normalize-space(text())="Reminder Interval"]/ancestor::div[contains(@class,"grid")][1]']],
  disbursements: [["k66-k79-disbursement-list", '[data-testid="disbursement-list"]']],
  "disbursement-detail": [["k80-disbursement-detail-header", 'xpath=//*[@id="main-content"]//h1/ancestor::div[contains(normalize-space(.),"Incurred")][1]']],
  tariffs: [["k81-tariff-schedule-row", '[data-testid^="schedule-row-"]']],
  "request-detail": [["k82-dsar-header", 'xpath=//*[starts-with(normalize-space(.),"Requested:")]/ancestor::div[.//h1][1]'], ["k68-dsar-timeline", 'xpath=//h2[normalize-space(.)="Status Timeline"]/..']],
  "conflict-check": [["k60-conflict-form", '[data-testid="conflict-check-form"]']],
};

async function capture(browser, who, state) {
  const t = await L.idpToken(who);
  const cap = (await L.apiGet(t, "/api/me/capabilities")).json;
  out.caps[who] = cap && { role: cap.role, isAdmin: cap.isAdmin, isOwner: cap.isOwner, capabilities: cap.capabilities };
  const variants = [...new Set(TARGETS.flatMap(([, , p]) => p[who] || []))];
  for (const v of variants) {
    const [w, theme] = v.split("-");
    const ctx = await L.newCtx(browser, state, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
    const page = await ctx.newPage();
    const lbl = { label: "" };
    L.watch(page, cons, lbl);
    for (const [key, url, per, action] of TARGETS) {
      if (!(per[who] || []).includes(v)) continue;
      lbl.label = `${who} ${key} ${v}`;
      const resp = await page.goto(url, { waitUntil: "load" });
      await settle(page, action);
      const m = await page.evaluate(probe);
      m.httpStatus = resp ? resp.status() : null;
      m.finalUrl = rel(page);
      (out.pages[key] = out.pages[key] || {})[`${who}/${v}`] = m;
      await page.screenshot({ path: path.join(L.RAW, `${key}--${who}--${v}.png`), fullPage: true });
      if (who === "alice" && v === "1440-light") for (const [n, sel] of CROPS[key] || []) await crop(page, n, sel);
    }
    await ctx.close();
  }
}

async function interact(browser, state, who, v, withWrite) {
  const [w, theme] = v.split("-");
  const ctx = await L.newCtx(browser, state, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
  const page = await ctx.newPage();
  const lbl = { label: `${who} interactions ${v}` };
  L.watch(page, cons, lbl);
  const I = (out.interactions[`${who}/${v}`] = {});
  const go = async (u, action) => { await page.goto(u, { waitUntil: "load" }); await settle(page, action); };
  const waitUrlChange = async (from, ms = 10000) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (rel(page) !== from) break; await page.waitForTimeout(150); } await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(600); return rel(page); };
  const quiet = async () => { await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(1200); await page.waitForLoadState("networkidle").catch(() => {}); };
  const safe = async (k, fn) => { try { I[k] = await fn(); } catch (e) { I[k] = { ...(I[k] || {}), error: e.message.split("\n")[0] }; } };
  const shot = async (name, full = true) => { const f = path.join(L.RAW, `int-${name}--${who}--${v}.png`); await page.screenshot({ path: f, fullPage: full }); return f; };
  const P = async () => { const m = await page.evaluate(probe); const { testids, ...rest } = m; return rest; };
  const main = page.locator("#main-content");
  const isAlice = who === "alice";

  // Compliance tabs: Overview → AI Audit → Overview (Radix tabs; does the URL change?)
  if (isAlice) await safe("complianceTabs", async () => {
    await go(`${B}/compliance`);
    const r = { start: rel(page), tabsAtStart: (await P()).tabs };
    let from = rel(page);
    await main.getByRole("tab", { name: "AI Audit" }).click();
    r.toAiAudit = await waitUrlChange(from, 2500);
    const m = await P();
    r.aiAudit = { tabs: m.tabs, empties: m.empties, headings: m.headings, buttons: m.buttons, lowContrast: m.lowContrast, bodyHead: m.bodyHead, tables: m.tables.length };
    r.file = await shot("compliance-ai-audit");
    from = rel(page);
    await main.getByRole("tab", { name: "Overview" }).click();
    r.backToOverview = await waitUrlChange(from, 2500);
    return r;
  });
  // Data requests: the status pills (Links) — URL, rows, empty text and the selected-pill style
  if (isAlice) await safe("requestPills", async () => {
    await go(`${B}/compliance/requests`);
    const r = { start: rel(page), steps: [] };
    for (const name of ["Received", "In Progress", "Completed", "Rejected", "All"]) {
      const from = rel(page);
      await main.getByRole("link", { name, exact: true }).click();
      const url = await waitUrlChange(from);
      const m = await P();
      const pills = await main.locator("a").evaluateAll((as) => as.filter((a) => /status=|compliance\/requests$/.test(a.getAttribute("href") || "")).map((a) => ({ text: a.innerText.trim(), current: a.getAttribute("aria-current"), selectedStyle: /(^|\s)bg-slate-900(\s|$)/.test(a.className) })));
      r.steps.push({ click: name, url, rows: m.sel.mainTbodyTr, empties: m.empties.map((e) => `${e.variant || e.tag}: ${e.text} (h ${e.h})`), countPill: (m.bodyHead.match(/^Data Requests (\d+)/) || [])[1] || null, pills });
    }
    return r;
  });
  // Conflict check: prefill from searchParams, then (Alice 1440 light only) ONE submit
  await safe("conflictPrefill", async () => {
    const r = {};
    for (const [k, u] of [["client", `${B}/conflict-check?customerId=${ID.conflictPrefillClientId}&checkType=NEW_MATTER`], ["explicit", `${B}/conflict-check?checkedName=Thabo%20Mokoena&checkedIdNumber=8001015009087&checkType=PERIODIC_REVIEW`]]) {
      await go(u);
      const m = await P();
      r[k] = { url: rel(page), inputs: m.inputs.map((i) => `${i.name}=${i.value} (w ${i.w})`) };
    }
    return r;
  });
  if (withWrite && process.env.NO_WRITE !== "1") await safe("conflictRun", async () => {
    await go(`${B}/conflict-check?checkedName=Pillay%20Holdings&checkType=NEW_MATTER`);
    const r = { start: rel(page), inputs: (await P()).inputs.map((i) => `${i.name}=${i.value}`) };
    await main.getByRole("button", { name: "Run Conflict Check" }).click();
    await page.getByText(/Conflict Found|Potential Conflict|No Conflict/).first().waitFor({ timeout: 20000 }).catch(() => {});
    await quiet();
    r.afterSubmit = rel(page);
    out.writes.push({ at: new Date().toISOString(), what: "conflict check submitted through the UI (Run Conflict Check): checkedName 'Pillay Holdings', checkType NEW_MATTER", url: r.start });
    const shots = {};
    const probes = {};
    for (const [vw, vh, th] of [[1440, 900, "light"], [1024, 768, "light"], [1024, 768, "dark"], [1440, 900, "dark"]]) {
      await page.setViewportSize({ width: vw, height: vh });
      await page.evaluate((t) => { const h = document.documentElement; h.classList.remove("light", "dark"); h.classList.add(t); h.style.colorScheme = t; }, th);
      await page.waitForTimeout(500);
      const m = await P();
      const k = `${vw}-${th}`;
      probes[k] = { bodyHead: m.bodyHead, headings: m.headings, badges: m.badges.map((b) => `${b.text} ${b.ratio}`), lowContrast: m.lowContrast, links: m.links.filter((l) => !/conflict-check/.test(l.href)).map((l) => `${l.text} ${l.href} ${l.fg} ${l.ratio}`), tables: m.tables.map((t) => ({ width: t.width, scroller: t.scroller, container: t.container, rows: t.rowCount })), inputs: m.inputs.map((i) => `${i.name} w${i.w}`), docHOverflowPx: m.docHOverflowPx, empties: m.empties };
      shots[k] = await shot(`conflict-result-${k}`);
    }
    await page.evaluate(() => { const h = document.documentElement; h.classList.remove("dark"); h.classList.add("light"); h.style.colorScheme = "light"; });
    await page.setViewportSize({ width: 1440, height: 900 });
    r.result = probes;
    r.files = shots;
    return r;
  });
  // Conflict history tab, result filter, Resolve dialog (opened, Escaped)
  await safe("conflictHistory", async () => {
    await go(`${B}/conflict-check`);
    const r = {};
    const tab = main.getByRole("tab", { name: /^History/ });
    r.tabLabel = (await tab.innerText()).trim();
    let from = rel(page);
    await tab.click();
    await page.getByTestId("conflict-check-history").waitFor({ timeout: 15000 });
    await quiet();
    r.url = await waitUrlChange(from, 2000);
    let m = await P();
    r.history = { tables: m.tables.map((t) => ({ width: t.width, tableScrollWidth: t.tableScrollWidth, scroller: t.scroller, container: t.container, columnsInView: t.columnsInView, rows: t.rowCount, firstRows: t.rows.slice(0, 3).map((x) => x.cells.join(" | ")) })), empties: m.empties, inputs: m.inputs.map((i) => `${i.name}=${i.value}`), docHOverflowPx: m.docHOverflowPx, lowContrast: m.lowContrast };
    r.file = await shot("conflict-history");
    const sel = page.getByTestId("conflict-check-history").locator("select").first();
    await sel.selectOption("CONFLICT_FOUND");
    await quiet();
    m = await P();
    r.filterConflictFound = { url: rel(page), rows: m.sel.mainTbodyTr, empties: m.empties.map((e) => e.text) };
    await sel.selectOption("");
    await quiet();
    const resolve = main.getByRole("button", { name: "Resolve" }).first();
    if (isAlice && v === "1440-light" && (await resolve.count())) {
      await resolve.click();
      const dlg = page.getByRole("dialog").first();
      await dlg.waitFor({ timeout: 10000 });
      r.resolveDialog = { title: (await dlg.getByRole("heading").first().innerText().catch(() => "")).trim(), file: await shot("conflict-resolve-dialog", false) };
      await page.keyboard.press("Escape");
      await dlg.waitFor({ state: "hidden", timeout: 5000 }).catch(() => {});
      r.resolveDialog.closedByEscape = (await page.getByRole("dialog").count()) === 0;
    }
    return r;
  });
  // Adverse parties: a search with no match (the registry's empty state) and one with a match
  await safe("adverseSearch", async () => {
    await go(`${B}/legal/adverse-parties`);
    const r = {};
    const box = main.getByPlaceholder("Search parties...");
    for (const [k, q] of [["none", "zzz-669A-no-match"], ["pillay", "Pillay"]]) {
      await box.fill(q);
      await quiet();
      const m = await P();
      r[k] = { q, url: rel(page), rows: m.sel.mainTbodyTr, adversePartyTable: m.sel.adversePartyTable, empties: m.empties, tables: m.tables.map((t) => ({ width: t.width, scroller: t.scroller, container: t.container })) };
      r[`${k}File`] = await shot(`adverse-search-${k}`);
      if (!isAlice || v !== "1440-light") break;
    }
    return r;
  });
  // Disbursements: Matter filter → unbilled strip; a From date past every row → the list's empty
  await safe("disbursementFilters", async () => {
    await go(`${B}/legal/disbursements`);
    const r = {};
    let from = rel(page);
    if (isAlice) {
      await main.getByLabel("Matter filter").selectOption(ID.disbursementActiveMatterMatterId);
      await quiet();
      r.matterFilterUrl = await waitUrlChange(from, 2000);
      const m = await P();
      r.matterFilter = { rows: m.sel.disbursementRows, bodyHead: m.bodyHead.slice(0, 300), empties: m.empties, unbilledStrip: await page.locator('[data-testid*="unbilled"]').evaluateAll((es) => es.map((e) => `${e.dataset.testid}: ${e.innerText.replace(/\s+/g, " ").trim().slice(0, 160)}`)) };
      r.matterFilterFile = await shot("disbursements-matter-filter");
      await main.getByLabel("Matter filter").selectOption("");
      await quiet();
    }
    from = rel(page);
    await main.getByLabel("From date").fill("2030-01-01");
    await quiet();
    r.dateFilterUrl = await waitUrlChange(from, 1500);
    const m2 = await P();
    r.dateFilterEmpty = { rows: m2.sel.disbursementRows, empties: m2.empties, headingContainsDisbursements: m2.sel.headingContainsDisbursements, countLine: (m2.bodyHead.match(/\d+ disbursements?[^A-Z]*/) || [])[0] || null };
    r.dateFilterFile = await shot("disbursements-date-empty");
    return r;
  });
  // Tariffs: expand the schedule → item browser; search with matches and with none
  await safe("tariffBrowse", async () => {
    await go(`${B}/legal/tariffs`);
    const r = {};
    let from = rel(page);
    await page.getByTestId(`schedule-row-${ID.tariffScheduleId}`).click();
    await page.getByTestId("tariff-item-search").waitFor({ timeout: 15000 }).catch(() => {});
    await quiet();
    r.expandUrl = await waitUrlChange(from, 1500);
    let m = await P();
    r.expanded = { tariffItemSearch: m.sel.tariffItemSearch, sections: Object.keys((await page.evaluate(() => Object.fromEntries([...document.querySelectorAll('[data-testid^="section-header-"]')].map((e) => [e.dataset.testid, e.innerText.replace(/\s+/g, " ").trim()]))))).length, items: await page.locator('[data-testid^="tariff-item-"]:not([data-testid="tariff-item-search"]):not([data-testid="tariff-item-browser"])').count(), emptyBadges: m.emptyBadges, badges: m.badges.slice(0, 8).map((b) => `${b.text || "(empty)"} w${b.w} ${b.ratio}`), tables: m.tables.map((t) => ({ width: t.width, scroller: t.scroller, container: t.container, rows: t.rowCount })), isoDates: m.isoDates, docHOverflowPx: m.docHOverflowPx };
    r.expandedFile = await shot("tariffs-expanded");
    for (const [k, q] of [["summons", "summons"], ["none", "zzz-669A-no-match"]]) {
      await page.getByTestId("tariff-item-search").fill(q);
      await quiet();
      m = await P();
      r[k] = { q, url: rel(page), items: await page.locator('[data-testid^="tariff-item-"]:not([data-testid="tariff-item-search"]):not([data-testid="tariff-item-browser"])').count(), empties: m.empties };
      if (k === "none" || (isAlice && v === "1440-light")) r[`${k}File`] = await shot(`tariffs-search-${k}`);
    }
    return r;
  });
  await ctx.close();
}

(async () => {
  const cm = await carolMatter();
  out.carolStatementsMatter = cm;
  if (cm) TARGETS.push(["statements-empty-carol-matter", `${B}/matters/${cm.id}?tab=statements`, { carol: L1 }, "project-statements-tab"]);
  const browser = await L.chromium.launch();
  const states = {};
  for (const who of ["alice", "carol"]) {
    states[who] = await L.loginState(browser, who);
    await capture(browser, who, states[who]);
    console.log("captured pages", who);
  }
  await interact(browser, states.alice, "alice", "1440-light", true);
  await interact(browser, states.alice, "alice", "1024-dark", false);
  await interact(browser, states.carol, "carol", "1440-light", false);
  console.log("interactions done");
  out.api = {};
  for (const who of ["alice", "carol"]) {
    const t = await L.idpToken(who);
    const dr = await L.apiGet(t, "/api/data-requests");
    const d = await L.apiGet(t, `/api/data-requests/${ID.dsarId}`);
    const cc = await L.apiGet(t, "/api/conflict-checks?size=100");
    const ir = await L.apiGet(t, `/api/information-requests/${ID.informationRequestNullReminder}`);
    out.api[who] = {
      dataRequests: { status: dr.status, count: dr.json ? L.list(dr.json).length : null },
      dsar: { status: d.status, requestedAt: d.json && d.json.requestedAt, deadline: d.json && d.json.deadline, createdAt: d.json && d.json.createdAt, requestStatus: d.json && d.json.status },
      conflictChecks: { status: cc.status, total: cc.json && cc.json.page ? cc.json.page.totalElements : null },
      informationRequest: { status: ir.status, reminderIntervalDays: ir.json && ir.json.reminderIntervalDays },
    };
  }
  out.console = cons;
  await browser.close();
  fs.writeFileSync(path.join(L.RAW, "capture.json"), JSON.stringify(out));
  console.log("pages", Object.values(out.pages).reduce((n, x) => n + Object.keys(x).length, 0), "console", cons.length, "writes", out.writes.length);
  console.log("interactions", JSON.stringify(out.interactions).slice(0, 3000));
})().catch((e) => { console.error("CAPTURE FAIL", e); process.exit(1); });
