// 664B step 1: capture (PHASE=before|after; output ../../$PHASE/, raw tiles /tmp/664B-$PHASE-raw/).
// e2e mock-auth stack :3001, Playwright library (headless chromium), Alice via /mock-login, Carol via
// the mock-auth-token cookie (661B helpers). Theme via localStorage `kazi-theme`, rail expanded,
// reduced motion, Africa/Johannesburg. No writes: navigation, hover, sort-header clicks (URL/local
// sort), pager clicks, and opening the Save View dialog (closed with Escape, never saved).
// Covers task 664.7: (1) other-creditors pagers, (2) F1/K-06 matters table widths + chevrons + sort
// shift, spot-check /clients and /reports/profitability, (3) K-11 saved-view Client column,
// (4) K-47 Save View dialog geometry, (5) aria-sort, (6) console.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const VIEW = "664B K-11 view"; // created by 00-seed.cjs
const OC = "/trust-accounting/other-creditors";
const M = { phase: L.PHASE, otherCreditors: {}, matters: {}, spotcheck: {}, k11: {}, k47: {} };
const PAGER = { phase: L.PHASE, states: {}, flows: {} };
const SEL = { phase: L.PHASE };
const sink = [];
const lab = { label: "" };
const pages = [];
const tiles = [];

async function open(browser, S, who, w, h, theme) {
  const ctx = await L.newCtx(browser, S[who], { width: w, height: h, theme, extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, sink, lab);
  return { ctx, p };
}
async function go(p, url, label) {
  lab.label = label;
  const resp = await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.mouse.move(1, 1);
  await p.waitForTimeout(700);
  pages.push(label);
  return resp ? resp.status() : null;
}
const rel = (p) => p.url().replace(L.BASE, "");
async function shot(p, key, caption, opt = {}) {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
}
async function elShot(loc, key, caption) {
  const f = path.join(L.RAW, `${key}.png`);
  if (!(await loc.count())) return tiles.push([key, null, caption + " (element absent)"]);
  await loc.first().screenshot({ path: f });
  tiles.push([key, f, caption]);
}

// ---------- in-page probes ----------
const TABLES = () => {
  const vis = (e) => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0; };
  const main = document.querySelector("#main-content");
  return [...document.querySelectorAll("#main-content table")].filter(vis).map((t) => {
    const cont = t.closest('[data-slot="table-container"]') || t.parentElement;
    const dt = t.closest('[data-slot="data-table"]');
    const tb = t.getBoundingClientRect();
    const ths = [...t.querySelectorAll("thead th")].map((h) => {
      const b = h.getBoundingClientRect();
      const svg = h.querySelector("svg");
      let icon = null;
      if (svg) {
        let op = 1;
        for (let e = svg; e && e !== h; e = e.parentElement) op *= parseFloat(getComputedStyle(e).opacity);
        const cs = getComputedStyle(svg);
        const sb = svg.getBoundingClientRect();
        icon = { cls: (svg.getAttribute("class") || "").replace(/\s+/g, " ").slice(0, 140), opacity: cs.opacity, effectiveOpacity: +op.toFixed(3), visibility: cs.visibility, display: cs.display, position: cs.position, w: Math.round(sb.width) };
      }
      const ctl = h.querySelector("a,button");
      return { label: h.innerText.trim().slice(0, 24), w: Math.round(b.width), left: Math.round(b.left), right: Math.round(b.right), ariaSort: h.getAttribute("aria-sort"), sortable: !!ctl, control: ctl ? ctl.tagName.toLowerCase() : null, icon };
    });
    return {
      testid: t.getAttribute("data-testid"),
      tableW: Math.round(tb.width), tableScrollW: t.scrollWidth, tableRight: Math.round(tb.right),
      containerClientW: cont.clientWidth, containerScrollW: cont.scrollWidth, containerOverflowX: getComputedStyle(cont).overflowX,
      overflowPx: cont.scrollWidth - cont.clientWidth, tableMinusContainer: Math.round(tb.width) - cont.clientWidth,
      dataTableW: dt ? Math.round(dt.getBoundingClientRect().width) : null, mainClientW: main ? main.clientWidth : null,
      docScrollW: document.scrollingElement.scrollWidth, innerW: innerWidth,
      rows: t.querySelectorAll("tbody tr").length, ths,
    };
  });
};
const SECTION = (testid) => {
  const s = document.querySelector(`[data-testid="${testid}"]`);
  if (!s) return { present: false };
  const leaf = [...s.querySelectorAll("*")].filter((e) => /^\s*Page \d+ of \d+\s*$/.test(e.textContent) && ![...e.children].some((c) => /Page \d+ of \d+/.test(c.textContent)));
  const ctl = [...s.querySelectorAll("a,button")].filter((e) => /previous|next/i.test(`${e.getAttribute("aria-label") || ""} ${e.textContent}`));
  const rows = [...s.querySelectorAll("tbody tr")];
  const txt = (r) => (r ? r.innerText.replace(/\s+/g, " ").trim().slice(0, 70) : null);
  return {
    present: true,
    pagerText: leaf.map((e) => e.textContent.trim()),
    controls: ctl.map((e) => ({ tag: e.tagName.toLowerCase(), text: e.textContent.trim(), ariaLabel: e.getAttribute("aria-label"), disabled: e.hasAttribute("disabled"), ariaDisabled: e.getAttribute("aria-disabled"), href: e.getAttribute("href") })),
    rows: rows.length, firstRow: txt(rows[0]), lastRow: txt(rows[rows.length - 1]),
  };
};
async function pagerState(p, key, url) {
  const status = await go(p, url, `pager|${key}`);
  const st = { requested: url, final: rel(p), status, fund: await p.evaluate(SECTION, "fund-ledger-section"), items: await p.evaluate(SECTION, "unidentified-deposits-section") };
  // Accessible names / disabled as Playwright's role engine sees them.
  for (const [k, tid] of [["fund", "fund-ledger-section"], ["items", "unidentified-deposits-section"]]) {
    const sec = p.getByTestId(tid);
    st[k].roles = [];
    for (const role of ["link", "button"]) {
      const loc = sec.getByRole(role, { name: /previous|next/i });
      for (let i = 0; i < (await loc.count()); i++) {
        const e = loc.nth(i);
        st[k].roles.push({ role, name: await e.evaluate((x) => x.getAttribute("aria-label") || x.textContent.trim()), disabled: await e.isDisabled() });
      }
    }
  }
  st.testids = await p.evaluate(() => Object.fromEntries(["other-creditors-page", "no-account-state", "fund-ledger-in-debit", "creditor-account-cards", "fund-ledger-section", "fund-ledger-table", "fund-ledger-row", "fund-ledger-empty", "fund-ledger-page-out-of-range", "unidentified-deposits-section", "unidentified-deposits-table", "unidentified-deposit-row", "unidentified-deposits-empty", "unidentified-deposits-page-out-of-range", "interest-memos-section"].map((t) => [t, document.querySelectorAll(`[data-testid="${t}"]`).length])));
  PAGER.states[key] = st;
  return st;
}
async function clickPager(p, tid, re) {
  const before = rel(p);
  const sec = p.getByTestId(tid);
  const loc = sec.getByRole("link", { name: re }).or(sec.getByRole("button", { name: re })).first();
  await loc.click();
  await p.waitForURL((u) => u.toString().replace(L.BASE, "") !== before, { timeout: 15000 }).catch(() => {});
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(800);
  return { from: before, to: rel(p), fund: (await p.evaluate(SECTION, "fund-ledger-section")).pagerText, items: (await p.evaluate(SECTION, "unidentified-deposits-section")).pagerText };
}

// ---------- 1. other-creditors ----------
async function otherCreditors(browser, S) {
  const { ctx, p } = await open(browser, S, "alice", 1024, 768, "light");
  for (const [k, u] of [["landing", OC], ["page=1", `${OC}?page=1`], ["page=2", `${OC}?page=2`], ["itemsPage=1", `${OC}?itemsPage=1`], ["itemsPage=2", `${OC}?itemsPage=2`], ["page=1&itemsPage=2", `${OC}?page=1&itemsPage=2`], ["page=9 (out of range)", `${OC}?page=9`], ["itemsPage=9 (out of range)", `${OC}?itemsPage=9`]])
    await pagerState(p, k, u);
  SEL.otherCreditors = PAGER.states.landing.testids;
  await go(p, OC, "pager|flow");
  PAGER.flows.fundNext = await clickPager(p, "fund-ledger-section", /next/i);
  PAGER.flows.itemsNextKeepsFund = await clickPager(p, "unidentified-deposits-section", /next/i);
  PAGER.flows.itemsNextAgain = await clickPager(p, "unidentified-deposits-section", /next/i);
  PAGER.flows.fundPrevKeepsItems = await clickPager(p, "fund-ledger-section", /previous/i);
  // Pager element crops at 1440 light: fund + items, first / middle / last page.
  await ctx.close();
  const c2 = await open(browser, S, "alice", 1440, 900, "light");
  for (const [k, u] of [["p0", OC], ["p1", `${OC}?page=1&itemsPage=1`], ["p2", `${OC}?page=2&itemsPage=2`]]) {
    await go(c2.p, u, `pager-crop|${k}`);
    for (const [s, tid] of [["fund", "fund-ledger-section"], ["items", "unidentified-deposits-section"]]) {
      // The pager = the parent of the "Page n of m" leaf; scrolled to the centre, clipped from the viewport.
      const r = await c2.p.evaluate((tid) => {
        const s = document.querySelector(`[data-testid="${tid}"]`);
        const leaf = s && [...s.querySelectorAll("*")].find((e) => /^\s*Page \d+ of \d+\s*$/.test(e.textContent) && ![...e.children].some((c) => /Page \d+ of \d+/.test(c.textContent)));
        if (!leaf) return null;
        leaf.parentElement.setAttribute("data-664b-pager", tid);
        leaf.parentElement.scrollIntoView({ block: "center", behavior: "instant" });
        return true;
      }, tid);
      await c2.p.waitForTimeout(400);
      const box = r && (await c2.p.evaluate((tid) => {
        const b = document.querySelector(`[data-664b-pager="${tid}"]`).getBoundingClientRect();
        const x = Math.max(0, b.left - 8), y = Math.max(0, b.top - 8);
        return { x, y, width: Math.min(innerWidth - x, b.width + 16), height: Math.min(innerHeight - y, b.height + 16) };
      }, tid));
      const key = `ocpager__${s}-${k}`;
      const cap = `${s === "fund" ? "Fund ledger" : "Unidentified deposits"} pager, ${u.replace(OC, "") || "(no params)"} — ${JSON.stringify((await c2.p.evaluate(SECTION, tid)).pagerText)}`;
      if (box) {
        await shot(c2.p, key, cap, { clip: box });
      } else tiles.push([key, null, cap + " (no pager)"]);
    }
  }
  await c2.ctx.close();
  for (const [w, h] of [[1024, 768], [1440, 900]])
    for (const theme of ["light", "dark"]) {
      const { ctx: c, p: q } = await open(browser, S, "alice", w, h, theme);
      await go(q, OC, `oc|alice|${w}|${theme}`);
      M.otherCreditors[`alice-${w}-${theme}`] = { tables: await q.evaluate(TABLES), fund: await q.evaluate(SECTION, "fund-ledger-section"), items: await q.evaluate(SECTION, "unidentified-deposits-section") };
      await shot(q, `oc__alice-${w}-${theme}`, `alice ${w}×${h} ${theme} other-creditors (full page)`, { fullPage: true });
      await c.close();
    }
  const c3 = await open(browser, S, "carol", 1024, 768, "light");
  await pagerState(c3.p, "carol landing", OC);
  await c3.ctx.close();
}

// ---------- 2. F1 / K-06 tables ----------
async function tablePass(p, key, opts = {}) {
  const rest = await p.evaluate(TABLES);
  const out = { url: rel(p), rest };
  const t0 = rest[opts.tableIndex || 0];
  if (!t0) return out;
  const idx = t0.ths.findIndex((h) => h.sortable && h.ariaSort === "none");
  if (idx < 0) return out;
  const th = p.locator("#main-content table").filter({ visible: true }).nth(opts.tableIndex || 0).locator("thead th").nth(idx);
  await th.locator("a,button").first().hover();
  await p.waitForTimeout(400);
  const hov = (await p.evaluate(TABLES))[opts.tableIndex || 0];
  out.hover = { column: t0.ths[idx].label, iconRest: t0.ths[idx].icon, iconHover: hov.ths[idx].icon, wRest: t0.ths[idx].w, wHover: hov.ths[idx].w };
  if (opts.theadCrops) {
    await elShot(p.locator("#main-content table thead").first(), `${key}-thead-hover`, `${key} thead, hovering "${t0.ths[idx].label}"`);
  }
  if (opts.sortClick) {
    const before = rel(p);
    await th.locator("a,button").first().click();
    await p.waitForURL((u) => u.toString().replace(L.BASE, "") !== before, { timeout: 8000 }).catch(() => {});
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.mouse.move(1, 1);
    await p.waitForTimeout(900);
    const after = (await p.evaluate(TABLES))[opts.tableIndex || 0];
    out.sort = {
      clicked: t0.ths[idx].label, urlBefore: before, urlAfter: rel(p),
      ariaSortAfter: after ? after.ths.map((h) => [h.label, h.ariaSort]) : null,
      tableW: [t0.tableW, after && after.tableW],
      widthDeltas: after ? t0.ths.map((h, i) => [h.label, h.w, after.ths[i] ? after.ths[i].w : null, after.ths[i] ? after.ths[i].w - h.w : null]).filter((r) => r[3] !== 0) : null,
    };
  }
  return out;
}
async function matters(browser, S) {
  for (const who of ["alice", "carol"])
    for (const [w, h] of [[1024, 768], [1440, 900]])
      for (const theme of ["light", "dark"]) {
        const { ctx, p } = await open(browser, S, who, w, h, theme);
        await go(p, "/matters", `matters|${who}|${w}|${theme}`);
        await shot(p, `mat__${who}-${w}-${theme}`, `${who} ${w}×${h} ${theme} /matters (rest)`);
        const crops = who === "alice" && w === 1024 && theme === "light";
        if (crops) await elShot(p.locator("#main-content table thead").first(), `mat__${who}-${w}-${theme}-thead-rest`, `alice 1024 light /matters thead at rest`);
        M.matters[`${who}-${w}-${theme}`] = await tablePass(p, `mat__${who}-${w}-${theme}`, { sortClick: true, theadCrops: crops });
        if (crops) await shot(p, `mat__${who}-${w}-${theme}-sorted`, `alice 1024 light /matters after clicking "${M.matters[`${who}-${w}-${theme}`].sort && M.matters[`${who}-${w}-${theme}`].sort.clicked}" (${rel(p)})`);
        if (who === "alice" && w === 1024 && theme === "light") SEL.matters = await selTables(p);
        await ctx.close();
      }
}
async function selTables(p) {
  return p.evaluate(() => {
    const n = (s) => document.querySelectorAll(s).length;
    return Object.fromEntries(['[data-testid="matters-table"]', '[data-testid="matter-row"]', '[data-testid="clients-table"]', '[data-testid="client-row"]', '[data-slot="data-table"]', '[data-slot="table-container"]', "th[aria-sort]", 'th[aria-sort="none"]', 'th[aria-sort="ascending"]', 'th[aria-sort="descending"]', "th svg.lucide-chevrons-up-down", '[data-testid="saved-view-selector"]'].map((s) => [s, { present: n(s) > 0 ? "y" : "n", count: n(s) }]));
  });
}
async function spotcheck(browser, S) {
  const { ctx, p } = await open(browser, S, "alice", 1024, 768, "light");
  await go(p, "/clients", "spot|clients");
  await shot(p, "spot__clients", "alice 1024 light /clients (All clients)");
  SEL.clients = await selTables(p);
  M.spotcheck.clients = await tablePass(p, "spot__clients", { sortClick: true });
  await go(p, "/reports/profitability", "spot|profitability");
  await shot(p, "spot__profitability", "alice 1024 light /reports/profitability", { fullPage: true });
  const n = (await p.evaluate(TABLES)).length;
  M.spotcheck.profitability = { tables: n, passes: [] };
  for (let i = 0; i < n; i++) {
    if (i > 0) await go(p, "/reports/profitability", `spot|profitability|table${i}`); // fresh local sort state per table
    const r = await tablePass(p, `spot__prof${i}`, { sortClick: true, tableIndex: i });
    r.rest = r.rest[i]; // this pass's table only
    M.spotcheck.profitability.passes.push(r);
  }
  if (!n) M.spotcheck.profitability.text = await p.evaluate(() => document.querySelector("#main-content").innerText.slice(0, 400));
  await ctx.close();
}

// ---------- 3. K-11 ----------
const CLIENT_COL = () => {
  const ths = [...document.querySelectorAll("#main-content table thead th")];
  const i = ths.findIndex((h) => /^client$/i.test(h.innerText.trim()));
  const links = i >= 0 ? [...document.querySelectorAll(`#main-content table tbody tr td:nth-child(${i + 1}) a`)] : [];
  const t = document.querySelector("#main-content table");
  const cont = t && (t.closest('[data-slot="table-container"]') || t.parentElement);
  return { url: location.pathname.replace(/^\/org\/[^/]+/, "") + location.search, columns: ths.map((h) => [h.innerText.trim().slice(0, 16), Math.round(h.getBoundingClientRect().width)]), clientTh: i >= 0 ? Math.round(ths[i].getBoundingClientRect().width) : null, linkWidths: links.map((a) => Math.round(a.getBoundingClientRect().width)), truncated: links.filter((a) => a.scrollWidth > a.clientWidth + 1).length, links: links.length, tableW: t ? Math.round(t.getBoundingClientRect().width) : null, containerW: cont ? cont.clientWidth : null };
};
async function k11(browser, S) {
  for (const [w, h] of [[1024, 768], [1440, 900]])
    for (const theme of ["light", "dark"]) {
      const { ctx, p } = await open(browser, S, "alice", w, h, theme);
      await go(p, "/clients", `k11|default|${w}|${theme}`);
      const def = await p.evaluate(CLIENT_COL);
      await shot(p, `k11__default-${w}-${theme}`, `alice ${w}×${h} ${theme} /clients "All clients" — Client th ${def.clientTh}px`);
      lab.label = `k11|saved|${w}|${theme}`;
      await p.locator('[data-testid="saved-view-selector"]').getByRole("tab", { name: VIEW, exact: true }).first().click();
      await p.waitForURL(/view=/, { timeout: 15000 }).catch(() => {});
      await p.waitForLoadState("networkidle").catch(() => {});
      await p.mouse.move(1, 1);
      await p.waitForTimeout(1500);
      const sv = await p.evaluate(CLIENT_COL);
      await shot(p, `k11__saved-${w}-${theme}`, `alice ${w}×${h} ${theme} /clients "${VIEW}" — Client th ${sv.clientTh}px, links ${sv.linkWidths.slice(0, 4).join("/")}px, ${sv.truncated}/${sv.links} truncated`);
      M.k11[`${w}-${theme}`] = { defaultView: def, savedView: sv };
      await ctx.close();
    }
  const a = M.k11["1024-light"].savedView;
  M.k11.verdict = a.clientTh != null && a.clientTh <= 40 ? `K-11 REPRODUCED (${L.PHASE}): saved-view Client th = ${a.clientTh}px at 1024 light` : `K-11 NOT reproduced (${L.PHASE}): saved-view Client th = ${a.clientTh}px at 1024 light`;
}

// ---------- 4. K-47 ----------
const DLG = () => {
  const d = [...document.querySelectorAll('[role="dialog"]')].pop();
  if (!d) return { open: false };
  const vh = innerHeight;
  const box = (e) => { if (!e) return null; const b = e.getBoundingClientRect(); return { top: Math.round(b.top), bottom: Math.round(b.bottom), height: Math.round(b.height), fullyInViewport: b.top >= 0 && b.bottom <= vh, partlyInViewport: b.bottom > 0 && b.top < vh }; };
  const btn = (re) => [...d.querySelectorAll("button")].find((b) => re.test((b.getAttribute("aria-label") || b.innerText || "").trim()));
  const title = d.querySelector('[data-slot="dialog-title"], h2');
  const cs = getComputedStyle(d);
  const scrollers = [d, ...d.querySelectorAll("*")].filter((e) => /(auto|scroll)/.test(getComputedStyle(e).overflowY) && e.scrollHeight > e.clientHeight + 1).map((e) => ({ tag: e.tagName.toLowerCase(), cls: (e.getAttribute("class") || "").slice(0, 80), scrollH: e.scrollHeight, clientH: e.clientHeight, scrollTop: e.scrollTop }));
  // A <select multiple> scrolling its own options is not the dialog body scrolling.
  return { open: true, viewport: [innerWidth, vh], dialog: { ...box(d), position: cs.position, transform: cs.transform, maxHeight: cs.maxHeight, overflowY: cs.overflowY, scrollH: d.scrollHeight, clientH: d.clientHeight }, title: { text: title && title.textContent.trim(), ...box(title) }, next: box(btn(/^next$/i)), close: box(btn(/^close$/i)), scrollers, bodyScrollable: scrollers.some((x) => x.tag !== "select"), docScroll: [document.scrollingElement.scrollHeight, innerHeight] };
};
async function k47(browser, S) {
  for (const route of ["/matters", "/clients"])
    for (const [w, h] of [[1024, 768], [1024, 900], [1440, 900]])
      for (const theme of ["light", "dark"]) {
        const key = `${route.slice(1)}-${w}x${h}-${theme}`;
        const { ctx, p } = await open(browser, S, "alice", w, h, theme);
        await go(p, route, `k47|${key}`);
        await p.locator("#main-content").getByRole("button", { name: "Save View" }).first().click();
        const dlg = p.getByRole("dialog");
        await dlg.waitFor();
        await p.waitForTimeout(700);
        const m = await p.evaluate(DLG);
        m.nextTrialClick = await dlg.getByRole("button", { name: "Next", exact: true }).click({ trial: true, timeout: 2500 }).then(() => "clickable", (e) => [...new Set(e.message.split("\n").map((l) => l.trim()).filter((l) => /outside|viewport|intercept|not visible|not stable|Timeout/i.test(l)))].slice(0, 4).join(" | "));
        await p.mouse.move(Math.round(w / 2), Math.round(h / 2));
        await p.mouse.wheel(0, 600);
        await p.waitForTimeout(500);
        const after = await p.evaluate(DLG);
        m.afterWheel = { dialogTop: after.dialog && after.dialog.top, titleTop: after.title && after.title.top, nextTop: after.next && after.next.top, scrollers: after.scrollers };
        await shot(p, `k47__${key}`, `${route} ${w}×${h} ${theme} — dialog top ${m.dialog && m.dialog.top} h ${m.dialog && m.dialog.height}; title ${m.title && m.title.fullyInViewport ? "in" : "OUT"}, Next ${m.next && m.next.fullyInViewport ? "in" : "OUT"}, Close ${m.close && m.close.fullyInViewport ? "in" : "OUT"}`);
        M.k47[key] = m;
        await p.keyboard.press("Escape");
        await ctx.close();
      }
}

(async () => {
  const browser = await L.chromium.launch();
  const S = { alice: await L.loginState(browser, "alice"), carol: await L.loginState(browser, "carol") };
  const step = async (name, f) => {
    try { await f(); console.log("OK", name); } catch (e) { console.log("FAIL", name, e.message.split("\n")[0]); (M.failures = M.failures || []).push({ step: name, error: e.message.split("\n")[0].slice(0, 300) }); }
  };
  await step("other-creditors", () => otherCreditors(browser, S));
  await step("matters", () => matters(browser, S));
  await step("spotcheck", () => spotcheck(browser, S));
  await step("k11", () => k11(browser, S));
  await step("k47", () => k47(browser, S));
  await browser.close();
  const stack = process.env.STACK || "e2e :3001 (see README)";
  const at = new Date().toISOString();
  fs.writeFileSync(path.join(L.RAW, "tiles.json"), JSON.stringify(tiles));
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify({ capturedAt: at, stack, ...M }) + "\n");
  fs.writeFileSync(path.join(L.OUT, "pager.json"), JSON.stringify({ capturedAt: at, stack, ...PAGER }) + "\n");
  fs.writeFileSync(path.join(L.OUT, "sel-compat.json"), JSON.stringify({ capturedAt: at, stack, note: "otherCreditors: testid → count on the landing URL (Alice 1024 light); matters/clients: selector → {present y/n, count} (Alice 1024 light)", ...SEL }) + "\n");
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify({ capturedAt: at, pagesOpened: pages.length, errors: sink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: sink.filter((c) => c.type === "warning").length, hydration: sink.filter((c) => /hydrat/i.test(c.text)).length, labelsWithMessages: [...new Set(sink.map((c) => c.label))], messages: sink }) + "\n");
  console.log("tiles", tiles.length, "pages", pages.length, "console", sink.length, "failures", (M.failures || []).length);
})();
