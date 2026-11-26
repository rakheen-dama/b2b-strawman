// 665B OBS after-fix (PR #1871 head a8720ea0c; read-only: URL changes, row expand, filters; nothing saved).
// Uses the main checkout's 661B helpers (login via /mock-login "Sign In", theme via localStorage
// `kazi-theme`, sidebar cookie, console watch) and its frontend/node_modules Playwright (headless
// Chromium). DPR 1, reduced motion, Africa/Johannesburg. Output: ../checks.json + ../*.png crops.
//  1. keyboard focus kept across Next / Next / Previous (activeElement + same-node marker)
//  2. collapse parity (page change collapses; same-page filter change keeps expansion)
//  3. Events h2 accessible name / textContent / "Events"→count gap (px)
//  4. regression sanity (page size, pager text, actor filter, filter resets page, zero state, TR, overflow)
//  5. console on plain loads (K-45 404s listed separately)
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");
const OUT = path.resolve(__dirname, "..");
const BASE = `${C.APP}/org/${C.ORG}`;
const TAB = `${BASE}/settings/firm?tab=audit-trail`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const UNUSED_UUID = "00000000-0000-4000-8000-000000000665";
const R = { capturedAt: new Date().toISOString(), focus: {}, collapse: {}, heading: {}, regression: { overflow: {} }, console: { plain: [], other: [], notFound: [] } };
const lab = { label: "" };
const LOADS = [];
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(400); };
const rel = (u) => u.replace(BASE, "");
const NAV = 'nav[aria-label="Audit log pagination"]';
const AE = () => {
  const a = document.activeElement;
  const nav = document.querySelector('nav[aria-label="Audit log pagination"]');
  return { tag: a && a.tagName, name: a && (a.getAttribute("aria-label") || a.innerText || "").trim(), ariaDisabled: a && a.getAttribute("aria-disabled"), disabled: a && a.disabled, connected: !!(a && a.isConnected), inPagerNav: !!(nav && a && nav.contains(a)), isBody: a === document.body, sameNodeAsMarked: !!(a && a.__obsMark), pager: nav && nav.querySelector("p") ? nav.querySelector("p").innerText.trim() : null, url: location.search };
};
const pagerText = (p) => p.locator(`${NAV} p`).innerText().then((t) => t.trim());
const detailsCount = (p) => p.locator('[data-testid^="audit-row-details-"]').count();

function wire(page) {
  C.watch(page, { push: (e) => (/^load\|/.test(e.label) ? R.console.plain : R.console.other).push(e) }, lab);
  page.on("response", (r) => { if (r.status() === 404) R.console.notFound.push({ label: lab.label, url: r.url().replace(C.APP, "") }); });
}
async function open(browser, S, w, h, theme) {
  const ctx = await C.newCtx(browser, S, { width: w, height: h, theme, extra: EXTRA });
  const p = await ctx.newPage();
  wire(p);
  return { ctx, p };
}
async function load(p, url, label) {
  lab.label = `load|${label}`;
  LOADS.push(label);
  await p.goto(url, { waitUntil: "load" });
  await settle(p);
  lab.label = `act|${label}`;
}

async function focusCheck(p, vp) {
  const T = [];
  await load(p, TAB, `focus-${vp}`);
  const nav = p.locator(NAV);
  const step = async (btnName, urlPred, pagerRe, label) => {
    const btn = nav.getByRole("button", { name: btnName, exact: true });
    await btn.focus();
    await btn.evaluate((b) => { for (const e of document.querySelectorAll("*")) delete e.__obsMark; b.__obsMark = 1; });
    const pre = await p.evaluate(AE);
    await Promise.all([p.waitForURL(urlPred, { timeout: 30000 }), p.keyboard.press("Enter")]);
    await p.waitForFunction((re) => new RegExp(re).test(document.querySelector('nav[aria-label="Audit log pagination"] p').innerText), pagerRe.source, { timeout: 30000 });
    const atCommit = await p.evaluate(AE);
    await settle(p);
    const settled = await p.evaluate(AE);
    T.push({ step: label, beforeEnter: pre, afterUrlAndPager: atCommit, afterSettle: settled });
  };
  await step("Next", /[?&]page=1(&|$)/, /^Page 2 of \d+$/, "Enter on Next (page 1→2)");
  await step("Next", /[?&]page=2(&|$)/, /^Page 3 of \d+$/, "Enter on Next (page 2→3)");
  await step("Previous", /[?&]page=1(&|$)/, /^Page 2 of \d+$/, "Enter on Previous (page 3→2)");
  const ok = T.every((s) => {
    const want = /Previous/.test(s.step) ? "Previous" : "Next";
    return [s.afterUrlAndPager, s.afterSettle].every((a) => !a.isBody && a.connected && a.inPagerNav && a.tag === "BUTTON" && a.name === want);
  });
  const sameNode = T.every((s) => s.afterSettle.sameNodeAsMarked);
  R.focus[vp] = { trace: T, pass: ok, sameNodeEveryStep: sameNode };
  if (vp === "1440-light") {
    const box = await nav.boundingBox();
    await p.screenshot({ path: path.join(OUT, "crop-pager-focus-1440-light.png"), clip: { x: Math.max(0, box.x - 8), y: box.y - 8, width: box.width + 16, height: box.height + 16 } });
  }
}

async function collapseCheck(p) {
  const X = {};
  await load(p, TAB, "collapse-1440-light");
  const nav = p.locator(NAV);
  const first = p.locator('[data-testid^="audit-row-"]').first();
  const firstId = await first.getAttribute("data-testid");
  const tog = first.getByRole("button", { name: "Expand details" });
  await tog.click();
  await p.waitForTimeout(300);
  X.page1Expanded = { rowTestId: firstId, details: await detailsCount(p), detailsVisible: await p.locator('[data-testid^="audit-row-details-"]').first().isVisible(), toggleExpanded: await first.getByRole("button", { name: /details/ }).getAttribute("aria-expanded") };
  await Promise.all([p.waitForURL(/[?&]page=1(&|$)/), nav.getByRole("button", { name: "Next", exact: true }).click()]);
  await settle(p);
  X.page2 = { pager: await pagerText(p), details: await detailsCount(p), expandedToggles: await p.locator('[data-testid^="audit-row-"] button[aria-expanded="true"]').count() };
  await Promise.all([p.waitForURL((u) => !/[?&]page=/.test(u.search)), nav.getByRole("button", { name: "Previous", exact: true }).click()]);
  await settle(p);
  const sameRow = p.locator(`[data-testid="${firstId}"]`);
  X.backOnPage1 = { pager: await pagerText(p), details: await detailsCount(p), rowPresent: await sameRow.count(), toggleExpanded: await sameRow.getByRole("button", { name: /details/ }).getAttribute("aria-expanded"), toggleLabel: await sameRow.getByRole("button", { name: /details/ }).getAttribute("aria-label") };
  // Same-page filter change: expand row 1 and toggle ITS severity (it stays the newest row of that severity).
  await sameRow.getByRole("button", { name: "Expand details" }).click();
  await p.waitForTimeout(300);
  const sev = await sameRow.locator('[data-testid="severity-pill"]').getAttribute("data-severity");
  X.sameRowSeverity = sev;
  X.beforeFilter = { details: await detailsCount(p) };
  await Promise.all([p.waitForURL(new RegExp(`severities=${sev}`)), p.getByTestId(`severity-toggle-${sev}`).click()]);
  await settle(p);
  X.afterFilter = { url: rel(p.url()), pager: await pagerText(p), rowStillPresent: await sameRow.count(), rowIndex: await p.evaluate((id) => [...document.querySelectorAll('[data-testid^="audit-row-"]')].filter((e) => e.tagName === "TR").findIndex((e) => e.dataset.testid === id), firstId), details: await detailsCount(p), detailsForRow: await p.locator(`[data-testid="${firstId.replace("audit-row-", "audit-row-details-")}"]`).count(), toggleExpanded: (await sameRow.count()) ? await sameRow.getByRole("button", { name: /details/ }).getAttribute("aria-expanded") : null };
  R.collapse = X;
  R.collapse.pass = X.page1Expanded.details === 1 && X.page1Expanded.detailsVisible && X.page2.details === 0 && X.page2.expandedToggles === 0 && X.backOnPage1.details === 0 && X.backOnPage1.toggleExpanded === "false";
  R.collapse.sameRowKeptExpandedAfterFilter = X.afterFilter.rowStillPresent === 1 && X.afterFilter.detailsForRow === 1 && X.afterFilter.toggleExpanded === "true";
}

async function headingCheck(p) {
  await load(p, TAB, "heading-1440-light");
  const H = {};
  H.byRoleSpace = await p.getByRole("heading", { level: 2, name: /^Events \d[\d\s,. ]* total$/ }).count();
  H.byRoleNoSpace = await p.getByRole("heading", { level: 2, name: /^Events\d/ }).count();
  const snap = await p.locator("#main-content").ariaSnapshot();
  H.ariaSnapshotLine = (snap.split("\n").find((l) => /heading "Events/.test(l)) || "").trim();
  const h = p.locator("#main-content h2", { hasText: /^Events/ });
  H.count = await h.count();
  Object.assign(H, await h.evaluate((el) => {
    const tn = [...el.childNodes].find((n) => n.nodeType === 3 && /Events/.test(n.textContent));
    const rg = document.createRange();
    const i = tn.textContent.indexOf("Events");
    rg.setStart(tn, i); rg.setEnd(tn, i + 6);
    const word = rg.getBoundingClientRect();
    const span = el.querySelector("span");
    const sr = span.getBoundingClientRect();
    const rs = document.createRange(); rs.selectNodeContents(span);
    const spanText = rs.getClientRects()[0];
    const cs = getComputedStyle(span);
    return { textContent: el.textContent, innerText: el.innerText, childNodes: [...el.childNodes].map((n) => (n.nodeType === 3 ? `#text ${JSON.stringify(n.textContent)}` : n.nodeType === 1 ? `<${n.tagName.toLowerCase()} class="${n.className}">` : `#node${n.nodeType}`)), eventsWordRight: +word.right.toFixed(2), spanBoxLeft: +sr.left.toFixed(2), spanGlyphLeft: +spanText.left.toFixed(2), spanMarginLeft: cs.marginLeft, gapWordToSpanBoxPx: +(sr.left - word.right).toFixed(2), gapWordToSpanGlyphsPx: +(spanText.left - word.right).toFixed(2), h2Font: getComputedStyle(el).font, spaceWidthPx: (() => { const c = document.createElement("canvas").getContext("2d"); c.font = getComputedStyle(el).font; return +c.measureText(" ").width.toFixed(2); })() };
  }));
  const b = await h.boundingBox();
  await p.screenshot({ path: path.join(OUT, "crop-events-heading-1440-light.png"), clip: { x: b.x - 8, y: b.y - 6, width: Math.min(b.width + 16, 420), height: b.height + 12 } });
  H.pass = H.byRoleSpace === 1 && H.byRoleNoSpace === 0 && H.count === 1;
  R.heading = H;
}

async function regression(p) {
  const G = {};
  await load(p, TAB, "regression-1440-light");
  const nav = p.locator(NAV);
  G.pageSize = await p.locator('tr[data-testid^="audit-row-"]').count();
  G.pager = await pagerText(p);
  G.prevDisabledOnPage1 = await nav.getByRole("button", { name: "Previous", exact: true }).isDisabled();
  G.firstRowTag = await p.locator('[data-testid^="audit-row-"]').first().evaluate((e) => e.tagName);
  await p.locator("#audit-filter-actor").click();
  await Promise.all([p.waitForURL(/actorId=/), p.getByRole("option", { name: /^Alice Owner/ }).click()]);
  await settle(p);
  G.actorFiltered = { url: rel(p.url()), pager: await pagerText(p) };
  await Promise.all([p.waitForURL(/actorId=.*page=1|page=1.*actorId=/), nav.getByRole("button", { name: "Next", exact: true }).click()]);
  await settle(p);
  G.actorPage2 = { url: rel(p.url()), pager: await pagerText(p), actors: await p.evaluate(() => [...new Set([...document.querySelectorAll('tr[data-testid^="audit-row-"]')].map((r) => (r.querySelector('[data-testid="actor-display"]') || r.children[4]).innerText.trim()))]) };
  await Promise.all([p.waitForURL((u) => /severities=INFO/.test(u.search) && !/[?&]page=/.test(u.search)), p.getByTestId("severity-toggle-INFO").click()]);
  await settle(p);
  G.filterChangeFromPage2 = { url: rel(p.url()), pager: await pagerText(p), pageParamRemoved: !/[?&]page=/.test(p.url()), actorKept: /actorId=/.test(p.url()) };
  await load(p, `${TAB}&entityId=${UNUSED_UUID}`, "zero-1440-light");
  G.zero = { pager: await pagerText(p), noEventsExact: await nav.getByText("No events", { exact: true }).count(), prevDisabled: await nav.getByRole("button", { name: "Previous", exact: true }).isDisabled(), nextDisabled: await nav.getByRole("button", { name: "Next", exact: true }).isDisabled() };
  G.pass = G.pageSize === 50 && /^Page 1 of \d+$/.test(G.pager) && G.prevDisabledOnPage1 && G.firstRowTag === "TR" && /actorId=/.test(G.actorPage2.url) && G.actorPage2.actors.length === 1 && G.filterChangeFromPage2.pageParamRemoved && G.zero.pager === "No events";
  Object.assign(R.regression, G);
}

const OVF = () => ({ scrollWidth: document.documentElement.scrollWidth, clientWidth: document.documentElement.clientWidth });
(async () => {
  const browser = await C.chromium.launch();
  const S = await C.loginState(browser, "alice");
  // 1440 light: focus, collapse, heading, regression
  {
    const { ctx, p } = await open(browser, S, 1440, 900, "light");
    await focusCheck(p, "1440-light");
    await collapseCheck(p);
    await headingCheck(p);
    await regression(p);
    await ctx.close();
  }
  // 1024 light: focus
  {
    const { ctx, p } = await open(browser, S, 1024, 768, "light");
    await focusCheck(p, "1024-light");
    await ctx.close();
  }
  // Overflow: 1024/1440 × light/dark, plus row 1 expanded at 1024 (light + dark) and 1440
  for (const [w, h] of [[1024, 768], [1440, 900]])
    for (const theme of ["light", "dark"]) {
      const { ctx, p } = await open(browser, S, w, h, theme);
      await load(p, TAB, `overflow-${w}-${theme}`);
      R.regression.overflow[`${w}-${theme}`] = await p.evaluate(OVF);
      await p.locator('[data-testid^="audit-row-"]').first().getByRole("button", { name: "Expand details" }).click();
      await p.waitForTimeout(400);
      R.regression.overflow[`${w}-${theme} row1-expanded`] = await p.evaluate(OVF);
      if (w === 1024 && theme === "dark") {
        const d = p.locator('[data-testid^="audit-row-details-"]').first();
        await d.scrollIntoViewIfNeeded();
        const b = await d.boundingBox();
        await p.screenshot({ path: path.join(OUT, "crop-row-expanded-1024-dark.png"), clip: { x: 0, y: Math.max(0, b.y - 60), width: 1024, height: Math.min(768 - Math.max(0, b.y - 60), b.height + 120) } });
      }
      // plain load of the other two tabs for console coverage
      for (const t of ["data-protection", "data-requests"]) await load(p, `${BASE}/settings/firm?tab=${t}`, `${t}-${w}-${theme}`);
      await ctx.close();
    }
  R.regression.overflowPass = Object.values(R.regression.overflow).every((o) => o.scrollWidth === o.clientWidth);
  await browser.close();
  const k45 = (e) => /Failed to load resource: the server responded with a status of 404/.test(e.text);
  R.console.summary = {
    plainLoads: LOADS.length,
    plainLoadLabels: LOADS,
    plainLabelsWithEntries: [...new Set(R.console.plain.map((e) => e.label))],
    plainErrorsNonK45: R.console.plain.filter((e) => !k45(e) && e.type !== "warning").length,
    plainWarnings: R.console.plain.filter((e) => e.type === "warning").length,
    hydration: [...R.console.plain, ...R.console.other].filter((e) => /hydrat/i.test(e.text)).length,
    k45_404ConsoleErrors: [...R.console.plain, ...R.console.other].filter(k45).length,
    other: R.console.other.filter((e) => !k45(e)),
    notFoundUrls: [...new Set(R.console.notFound.map((n) => n.url.replace(/[0-9a-f]{8}-[0-9a-f-]{27}/g, "{uuid}").replace(/\?.*$/, "")))],
  };
  fs.writeFileSync(path.join(OUT, "checks.json"), JSON.stringify(R) + "\n");
  console.log(JSON.stringify({ focus: Object.fromEntries(Object.entries(R.focus).map(([k, v]) => [k, { pass: v.pass, sameNode: v.sameNodeEveryStep }])), collapse: R.collapse, heading: R.heading, regression: R.regression, console: R.console.summary }, null, 1));
})().catch((e) => { console.error(e); process.exit(1); });
