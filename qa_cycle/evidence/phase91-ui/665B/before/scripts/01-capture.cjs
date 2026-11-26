// 665B OBS step 1: capture Settings › Firm's three 665B tabs (audit-trail, data-protection,
// data-requests) for ONE persona on the running e2e mock-auth stack (:3001). Playwright library,
// headless Chromium, DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded
// (`sidebar_state=true`), theme via localStorage `kazi-theme`.
// Usage: PHASE=before node 01-capture.cjs <persona>
//   persona = alice | carol | carol-cr-holder | carol-cr-lacker  (carol-cr-* = Carol while she holds a
//   custom role assigned by 02-roles.cjs; the key only labels the run, login is Carol's cookie).
// Read-only: no clicks, no input. Per tab × 1024×768/1440×900 × light/dark: a full-page shot (raw,
// outside the repo) and a compact probe (h1, tab bar, notice, page overflow, every table's scroller
// visible vs scroll width); at 1440 light the full probe (headings, cards, tables, testids, buttons,
// links, controls, chips, empty states, pager); at 1024 light the spec selectors (SEL-COMPAT).
// Then the legacy routes. Output: $RAW/persona-<persona>.json + tiles (05 merges).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const KEY = process.argv[2];
if (!["alice", "carol", "carol-cr-holder", "carol-cr-lacker"].includes(KEY)) throw new Error("persona?");
const WHO = KEY.startsWith("carol") ? "carol" : "alice";
const VIEWPORTS = [[1024, 768], [1440, 900]];

const R = { persona: KEY, login: WHO, capturedAt: new Date().toISOString(), caps: null, tabs: {}, variants: {}, selCompat: {}, legacyRoutes: {} };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
let pagesOpened = 0;

// Shared in-page helpers, installed per probe call.
const HELPERS = () => {
  window.__v = (e) => { const b = e.getBoundingClientRect(); const cs = getComputedStyle(e); return b.width > 0 && b.height > 0 && cs.visibility !== "hidden" && cs.display !== "none"; };
  window.__t = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  window.__scroller = (t) => {
    for (let e = t.parentElement; e && e !== document.body; e = e.parentElement) {
      const cs = getComputedStyle(e);
      if (/(auto|scroll)/.test(cs.overflowX) || /inline-size/.test(cs.contain || "") || /inline-size/.test(cs.containerType || "")) return e;
    }
    return null;
  };
  window.__tableMeasure = (t) => {
    const sc = window.__scroller(t);
    const tb = t.getBoundingClientRect();
    return {
      headers: [...t.querySelectorAll("thead th")].map(window.__t),
      tableWidth: Math.round(tb.width),
      scroller: sc ? { tag: sc.tagName.toLowerCase(), dataSlot: sc.getAttribute("data-slot"), cls: (sc.className || "").toString().slice(0, 80), overflowX: getComputedStyle(sc).overflowX, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth } : null,
    };
  };
};

const FULL = () => {
  const main = document.querySelector("main#main-content");
  const vis = window.__v, txt = window.__t;
  const name = (e) => e.getAttribute("aria-label") || (e.getAttribute("aria-labelledby") ? e.getAttribute("aria-labelledby").split(/\s+/).map((id) => document.getElementById(id)).filter(Boolean).map(txt).join(" ") : "") || txt(e) || e.getAttribute("title") || "";
  const labelFor = (e) => {
    if (e.getAttribute("aria-label")) return e.getAttribute("aria-label");
    if (e.id) { const l = document.querySelector(`label[for="${CSS.escape(e.id)}"]`); if (l) return txt(l); }
    const wrap = e.closest("label"); if (wrap) return txt(wrap).slice(0, 80);
    return null;
  };
  if (!main) return { error: "no main#main-content" };
  const strip = main.querySelector('[role="tablist"][aria-label="Firm settings"]');
  const notice = [...main.querySelectorAll('p[role="status"]')].find((p) => /have access to/.test(p.textContent));
  const headings = [...main.querySelectorAll("h1,h2,h3,h4,h5,h6,[role=heading]")].filter(vis).map((h) => ({ level: h.getAttribute("aria-level") ? +h.getAttribute("aria-level") : +h.tagName[1], tag: h.tagName.toLowerCase(), text: txt(h) }));
  const h1s = [...main.querySelectorAll("h1")];
  const slotCards = [...main.querySelectorAll('[data-slot="card"]')].filter(vis);
  const handCards = [...main.querySelectorAll("div.rounded-lg.border")].filter((e) => vis(e) && !e.closest('[role="tablist"]') && e.getAttribute("data-slot") !== "card" && !e.closest('[data-slot="card"]'));
  const cardHead = (c) => { const h = c.querySelector("h1,h2,h3,h4,legend,[data-slot=card-title]"); return h ? txt(h).slice(0, 60) : txt(c).slice(0, 40); };
  const testids = {};
  for (const e of main.querySelectorAll("[data-testid]")) {
    const k = e.dataset.testid.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, "{uuid}");
    testids[k] = (testids[k] || 0) + 1;
  }
  const dedupe = (arr) => { const m = new Map(); for (const x of arr) { const k = JSON.stringify(x); m.set(k, (m.get(k) || 0) + 1); } return [...m].map(([k, n]) => ({ ...JSON.parse(k), n })); };
  const buttons = dedupe([...main.querySelectorAll('button,[role="button"],a[data-slot="button"]')].map((b) => ({ name: name(b).slice(0, 80), visible: vis(b), disabled: b.disabled || b.getAttribute("aria-disabled") === "true" || undefined, role: b.getAttribute("role") || undefined })));
  const links = dedupe([...main.querySelectorAll("a[href]")].filter((a) => !a.closest('[role="tablist"]')).map((a) => ({ name: name(a).replace(/\b[0-9a-f]{8}\b/g, "{id8}").slice(0, 60), href: a.getAttribute("href").replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, "{uuid}"), testid: a.dataset.testid, entityType: a.dataset.entityType })));
  const controls = [...main.querySelectorAll('input,textarea,select,[role="combobox"],[role="switch"],[role="checkbox"]')].filter((e) => e.type !== "hidden" && !(e.tagName === "INPUT" && e.getAttribute("aria-hidden") === "true")).map((e) => ({ tag: e.tagName.toLowerCase(), type: e.getAttribute("type") || e.getAttribute("role") || undefined, id: e.id || undefined, testid: e.dataset.testid, label: labelFor(e), value: e.tagName === "BUTTON" ? (e.getAttribute("aria-checked") ?? txt(e)) : e.value, options: e.tagName === "SELECT" ? [...e.options].map((o) => `${o.value}${o.selected ? "*" : ""}`) : undefined, disabled: e.disabled || undefined, visible: vis(e) }));
  const tables = [...main.querySelectorAll("table")].map((t) => ({ dataSlot: t.getAttribute("data-slot"), visible: vis(t), bodyRows: t.querySelectorAll("tbody tr").length, firstRow: t.querySelector("tbody tr") ? [...t.querySelector("tbody tr").children].map((c) => txt(c).slice(0, 50)) : null, ...window.__tableMeasure(t) }));
  const chips = dedupe([...main.querySelectorAll('[data-slot="badge"],[data-testid="severity-pill"]')].filter(vis).map((b) => ({ kind: b.dataset.testid || "badge", text: txt(b), severity: b.dataset.severity })));
  const emptyCandidates = [...main.querySelectorAll("p,td")].filter((e) => vis(e) && /^(No |The audit log is empty)/.test(txt(e)));
  const emptyStates = emptyCandidates.map((e) => { const b = (e.closest("td") || e).getBoundingClientRect(); const box = e.closest('[data-slot="card-content"]') || e.closest("div.rounded-lg") || e.parentElement; return { tag: e.tagName.toLowerCase(), text: txt(e).slice(0, 160), height: Math.round(b.height), containerHeight: Math.round(box.getBoundingClientRect().height) }; });
  const nav = main.querySelector('nav[aria-label="Audit log pagination"]');
  const pager = nav ? { label: nav.getAttribute("aria-label"), text: txt(nav), buttons: [...nav.querySelectorAll("button,a")].map((b) => ({ tag: b.tagName.toLowerCase(), name: name(b), disabled: !!b.disabled })) } : null;
  const se = document.scrollingElement;
  return {
    title: document.title,
    url: location.pathname + location.search,
    tabBar: strip ? [...strip.querySelectorAll('[role="tab"]')].map((t) => `${txt(t)}:${t.getAttribute("aria-selected")}`) : null,
    notice: notice ? txt(notice) : null,
    mainCount: document.querySelectorAll("main#main-content").length,
    h1: h1s.map((h) => ({ text: txt(h), visible: vis(h) })),
    h1Count: h1s.length,
    headings,
    cards: { slotCard: slotCards.length, handRolled: handCards.length, slotCardHeads: slotCards.map(cardHead), handRolledHeads: handCards.map(cardHead) },
    tables,
    testids,
    buttons,
    links,
    controls,
    chips,
    emptyStates,
    pager,
    statusAlerts: [...main.querySelectorAll('[role="status"],[role="alert"]')].map((e) => txt(e).slice(0, 160)),
    docHeight: se.scrollHeight,
  };
};
const COMPACT = () => {
  const main = document.querySelector("main#main-content");
  const txt = window.__t;
  const strip = main && main.querySelector('[role="tablist"][aria-label="Firm settings"]');
  const sel = strip && strip.querySelector('[role="tab"][aria-selected="true"]');
  const notice = main && [...main.querySelectorAll('p[role="status"]')].find((p) => /have access to/.test(p.textContent));
  const se = document.scrollingElement;
  const b = main ? main.getBoundingClientRect() : null;
  const nav = main && main.querySelector('nav[aria-label="Audit log pagination"]');
  return {
    h1: main ? [...main.querySelectorAll("h1")].map(txt) : [],
    h1Count: main ? main.querySelectorAll("h1").length : 0,
    mainCount: document.querySelectorAll("main#main-content").length,
    selectedTab: sel ? txt(sel) : null,
    tabs: strip ? [...strip.querySelectorAll('[role="tab"]')].map(txt) : [],
    notice: notice ? txt(notice) : null,
    docHeight: se.scrollHeight,
    hOverflow: se.scrollWidth > se.clientWidth ? se.scrollWidth - se.clientWidth : 0,
    mainBox: b ? { left: Math.round(b.left), width: Math.round(b.width) } : null,
    tables: main ? [...main.querySelectorAll("table")].map(window.__tableMeasure) : [],
    pagerText: nav ? txt(nav) : null,
  };
};

async function selCompat(p, tab) {
  const c = async (loc) => loc.count();
  const ROW = '[data-testid^="audit-row-"]:not([data-testid^="audit-row-toggle-"]):not([data-testid^="audit-row-details-"])';
  const out = {
    "main#main-content": await c(p.locator("main#main-content")),
    "h1 in #main-content": await c(p.locator("#main-content h1")),
    "tablist 'Firm settings' exact": await c(p.getByRole("tablist", { name: "Firm settings", exact: true })),
    "body 'Something went wrong'": await c(p.getByText("Something went wrong")),
    // data-protection-settings.spec.ts
    "dp-settings.spec:27 heading h1 'Data Protection' exact": await c(p.getByRole("heading", { level: 1, name: "Data Protection", exact: true })),
    "dp-settings.spec:37 heading h1 'DSAR Requests'": await c(p.getByRole("heading", { level: 1, name: "DSAR Requests" })),
    "dp-settings.spec:57 combobox filter /Select a jurisdiction|South Africa/ (strict click)": await c(p.getByRole("combobox").filter({ hasText: /Select a jurisdiction|South Africa/ })),
    "dp-settings.spec:65 button 'Generate PAIA Manual' (strict)": await c(p.getByRole("button", { name: "Generate PAIA Manual" })),
    "dp-settings.spec:77 label 'Retention days for Client' (strict fill)": await c(p.getByLabel("Retention days for Client")),
    "dp-settings.spec:78 button 'Save Client policy' (strict)": await c(p.getByRole("button", { name: "Save Client policy" })),
    "dp-settings.spec:96 heading 'Retention Policies' (strict)": await c(p.getByRole("heading", { name: "Retention Policies" })),
    "dp-settings.spec:97 heading 'Processing Register' (strict)": await c(p.getByRole("heading", { name: "Processing Register" })),
    "dp-settings.spec:115 locator #officer-name": await c(p.locator("#officer-name")),
    "dp-settings.spec:116 locator #officer-email": await c(p.locator("#officer-email")),
    "dp-settings.spec:122 button 'Save' exact (strict)": await c(p.getByRole("button", { name: "Save", exact: true })),
    "dp-settings.spec:124 text 'Saved successfully.' (before save)": await c(p.getByText("Saved successfully.")),
    "dp-settings.spec:156 button 'Add Activity'": await c(p.getByRole("button", { name: "Add Activity" })),
    "dp-settings.spec:168 text /PAIA manual generated:/ (before generate)": await c(p.getByText(/PAIA manual generated:/)),
    "dp-settings.spec:174 tab 'Data requests' (strict click)": await c(p.getByRole("tab", { name: "Data requests" })),
    "dp-settings.spec:178 button 'Log New Request' (strict)": await c(p.getByRole("button", { name: "Log New Request" })),
    "dp-settings.spec:197 row filter hasText 'DSAR Client 5qp3' (seeded by an earlier run)": await c(p.getByRole("row").filter({ hasText: "DSAR Client 5qp3" })),
    // audit-log-smoke.spec.ts / admin-audit-lifecycle.spec.ts (both go via /settings/audit-log)
    "audit-smoke.spec:26 locator h1 filter /Audit log/i (strict)": await c(p.locator("h1").filter({ hasText: /Audit log/i })),
    "audit-smoke.spec:31 testid audit-preset-select (strict)": await c(p.getByTestId("audit-preset-select")),
    "audit-smoke.spec:45 event-row locator count": await c(p.locator(ROW)),
    "audit-smoke.spec:53 body text /No audit events/i": await c(p.getByText(/No audit events/i)),
    // day-75-85-closure.spec.ts:78 / day-30-45-soa-writeoff.spec.ts:404
    "day-75-85:78 / day-30-45:404 locator #audit-filter-event-type (strict click)": await c(p.locator("#audit-filter-event-type")),
    "day-75-85:89 row locator :not(details) (uses .first())": await c(p.locator('[data-testid^="audit-row-"]:not([data-testid^="audit-row-details-"])')),
    "day-30-45:412 locator [data-testid^=audit-row-] (uses .first(); includes toggles)": await c(p.locator('[data-testid^="audit-row-"]')),
    "day-30-45:414 first row › button 'Expand details' (strict)": await c(p.locator('[data-testid^="audit-row-"]').first().getByRole("button", { name: "Expand details" })),
    "day-30-45:415 [data-testid^=audit-row-details-] (before expand)": await c(p.locator('[data-testid^="audit-row-details-"]')),
    // matter-closure-audit-tab.spec.ts targets /matters/{id} (closure history), not this tab;
    // its severity-pill selector is recorded here only because the settings table also uses it.
    "matter-closure-audit-tab.spec:93 [data-testid=severity-pill][data-severity=CRITICAL] (informational)": await c(p.locator('[data-testid="severity-pill"][data-severity="CRITICAL"]')),
    "testid severity-pill": await c(p.getByTestId("severity-pill")),
    "testid entity-cell-link": await c(p.getByTestId("entity-cell-link")),
    "testid entity-cell-literal": await c(p.getByTestId("entity-cell-literal")),
    "testid entity-cell-empty": await c(p.getByTestId("entity-cell-empty")),
    "testid actor-display": await c(p.getByTestId("actor-display")),
    "testid export-dropdown-trigger": await c(p.getByTestId("export-dropdown-trigger")),
    "testid audit-filter-row": await c(p.getByTestId("audit-filter-row")),
    "testid severity-toggle-*": await c(p.locator('[data-testid^="severity-toggle-"]')),
    "nav 'Audit log pagination'": await c(p.getByRole("navigation", { name: "Audit log pagination" })),
    "button 'Previous' exact": await c(p.getByRole("button", { name: "Previous", exact: true })),
    "button 'Next' exact": await c(p.getByRole("button", { name: "Next", exact: true })),
  };
  return out;
}

async function page(browser, S, w, h, theme) {
  const ctx = await L.newCtx(browser, S, { width: w, height: h, theme, extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  return { ctx, p };
}
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};

async function legacyRoutes(browser, S) {
  const { ctx, p } = await page(browser, S, 1024, 768, "light");
  for (const r of ["/settings/audit-log", "/settings/data-protection", "/settings/data-protection/requests"]) {
    lab.label = `${KEY}|legacy ${r}`;
    const resp = await p.goto(`${L.BASE}${r}`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    pagesOpened++;
    const body = await p.locator("body").innerText();
    R.legacyRoutes[r] = { status: resp ? resp.status() : null, finalUrl: p.url().replace(L.BASE, ""), somethingWentWrong: body.includes("Something went wrong"), h1: await p.locator("#main-content h1").allInnerTexts(), notice: (await p.locator('#main-content p[role="status"]').allInnerTexts()).filter((t) => /have access/.test(t)), auditH1Strict: await p.locator("h1").filter({ hasText: /Audit log/i }).count() };
  }
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, WHO);
  const tok = await L.idpToken(WHO);
  const caps = await L.apiGet(tok, "/api/me/capabilities");
  R.caps = caps.json ? { role: caps.json.role, isAdmin: caps.json.isAdmin, isOwner: caps.json.isOwner, capabilities: caps.json.capabilities } : caps;
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const { ctx, p } = await page(browser, S, w, h, theme);
      for (const tab of L.TABS) {
        const v = `${w}-${theme}`;
        lab.label = `${KEY}|${tab}|${v}`;
        const resp = await p.goto(`${L.BASE}/settings/firm?tab=${tab}`, { waitUntil: "load" });
        await p.waitForLoadState("networkidle").catch(() => {});
        await p.waitForTimeout(500);
        pagesOpened++;
        await p.evaluate(HELPERS);
        const m = await p.evaluate(COMPACT);
        m.status = resp ? resp.status() : null;
        m.url = p.url().replace(L.BASE, "");
        (R.variants[tab] = R.variants[tab] || {})[v] = m;
        const gate = m.notice ? `DENIED → ${m.selectedTab}` : `tab ${m.selectedTab}`;
        await shot(p, `tab__${tab}-${KEY}-${v}-full`, `${KEY} ?tab=${tab} ${w}×${h} ${theme} full page (${m.docHeight}px) — ${gate}; h1 ${JSON.stringify(m.h1)}`, { fullPage: true });
        if (w === 1440 && theme === "light") {
          const f = await p.evaluate(FULL);
          f.requested = tab;
          f.inTabBar = !!(f.tabBar && f.tabBar.some((t) => t.startsWith(`${L.LABELS[tab]}:`)));
          f.gateOutcome = f.notice ? "forbidden-notice" : f.inTabBar ? "rendered" : "not-in-tab-bar";
          R.tabs[tab] = f;
        }
        if (w === 1024 && theme === "light") R.selCompat[tab] = await selCompat(p, tab);
      }
      await ctx.close();
    }
  await legacyRoutes(browser, S);
  await browser.close();
  R.console = { pagesOpened, errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, messages: consoleSink };
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, `persona-${KEY}.json`), JSON.stringify(R));
  console.log(KEY, "tiles", tiles.length, "pages", pagesOpened, "console", consoleSink.length, "caps", JSON.stringify(R.caps));
  for (const t of L.TABS) console.log(t, JSON.stringify({ gate: R.tabs[t] && R.tabs[t].gateOutcome, h1: R.tabs[t] && R.tabs[t].h1, notice: R.tabs[t] && R.tabs[t].notice, tabBar: R.tabs[t] && R.tabs[t].tabBar, tables1024: R.variants[t]["1024-light"].tables.map((x) => x.scroller && `${x.scroller.clientWidth}/${x.scroller.scrollWidth}`) }));
  console.log("legacy", JSON.stringify(R.legacyRoutes));
})();
