// 665A OBS step 1: capture Settings › Firm's five 665A tabs (general, roles, compliance, features,
// subscription) for ONE persona on the running e2e mock-auth stack (:3001). Playwright library,
// headless Chromium, DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded
// (`sidebar_state=true`), theme via localStorage `kazi-theme`.
// Usage: PHASE=before node 01-capture.cjs <persona>
//   persona = alice | carol | carol-cr-holder | carol-cr-lacker  (carol-cr-* = Carol while she holds a
//   custom role assigned by 02-roles.cjs; the key only labels the run, login is Carol's cookie).
// Read-only apart from one click: Roles › "Capability Reference" (a Collapsible) for the sel-compat
// cell check and the expanded-reference shot. No form input, no submits.
// Per tab × 1024×768/1440×900 × light/dark: a viewport and a full-page shot (raw, /tmp), a compact
// probe; at 1440 light the full probe (headings, cards, tables, testids, buttons, controls); at 1024
// light the spec selectors (SEL-COMPAT). Output: $RAW/persona-<persona>.json + tiles (03/04 merge).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const KEY = process.argv[2];
if (!["alice", "carol", "carol-cr-holder", "carol-cr-lacker"].includes(KEY)) throw new Error("persona?");
const WHO = KEY.startsWith("carol") ? "carol" : "alice";
const VIEWPORTS = [[1024, 768], [1440, 900]];
const LABELS = { general: "General", roles: "Roles", compliance: "Compliance", features: "Features", subscription: "Subscription" };

const R = { persona: KEY, login: WHO, capturedAt: new Date().toISOString(), caps: null, tabs: {}, variants: {}, selCompat: {}, legacyRoutes: {}, capabilityReference: null };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
let pagesOpened = 0;

const FULL = () => {
  const main = document.querySelector("main#main-content");
  const vis = (e) => { const b = e.getBoundingClientRect(); const cs = getComputedStyle(e); return b.width > 0 && b.height > 0 && cs.visibility !== "hidden" && cs.display !== "none"; };
  const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
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
  const handCards = [...main.querySelectorAll("div.rounded-lg.border")].filter((e) => vis(e) && !e.closest('[role="tablist"]') && e.getAttribute("data-slot") !== "card");
  const cardHead = (c) => { const h = c.querySelector("h1,h2,h3,h4,legend,[data-slot=card-title]"); return h ? txt(h).slice(0, 60) : txt(c).slice(0, 40); };
  const testids = {};
  for (const e of main.querySelectorAll("[data-testid]")) testids[e.dataset.testid] = (testids[e.dataset.testid] || 0) + 1;
  const buttons = [...main.querySelectorAll('button,[role="button"],a[data-slot="button"]')].map((b) => ({ name: name(b).slice(0, 80), visible: vis(b), disabled: b.disabled || b.getAttribute("aria-disabled") === "true" || undefined, role: b.getAttribute("role") || undefined }));
  const controls = [...main.querySelectorAll('input,textarea,select,[role="combobox"],[role="switch"],[role="checkbox"]')].filter((e) => e.type !== "hidden" && !(e.tagName === "INPUT" && e.getAttribute("aria-hidden") === "true")).map((e) => ({ tag: e.tagName.toLowerCase(), type: e.getAttribute("type") || e.getAttribute("role") || undefined, id: e.id || undefined, testid: e.dataset.testid, label: labelFor(e), value: e.tagName === "BUTTON" ? (e.getAttribute("aria-checked") ?? txt(e)) : e.value, disabled: e.disabled || undefined, visible: vis(e) }));
  const tables = [...main.querySelectorAll("table")].map((t) => ({ dataSlot: t.getAttribute("data-slot"), visible: vis(t), headers: [...t.querySelectorAll("thead th")].map(txt), bodyRows: t.querySelectorAll("tbody tr").length, width: Math.round(t.getBoundingClientRect().width), containerWidth: Math.round(t.parentElement.getBoundingClientRect().width) }));
  return {
    title: document.title,
    tabBar: strip ? [...strip.querySelectorAll('[role="tab"]')].map((t) => `${txt(t)}:${t.getAttribute("aria-selected")}`) : null,
    notice: notice ? txt(notice) : null,
    mainCount: document.querySelectorAll("main#main-content").length,
    h1: h1s.map((h) => ({ text: txt(h), visible: vis(h), helpTip: [...h.querySelectorAll("button")].map(name) })),
    h1Count: h1s.length,
    headings,
    cards: { slotCard: slotCards.length, handRolled: handCards.length, slotCardHeads: slotCards.map(cardHead), handRolledHeads: handCards.map(cardHead) },
    tables,
    testids,
    buttons,
    controls,
    emptyStates: [...main.querySelectorAll('[data-slot="empty-state"],[data-testid*="empty"]')].map((e) => txt(e).slice(0, 100)),
    statusAlerts: [...main.querySelectorAll('[role="status"],[role="alert"]')].map((e) => txt(e).slice(0, 120)),
  };
};
const COMPACT = () => {
  const main = document.querySelector("main#main-content");
  const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const strip = main && main.querySelector('[role="tablist"][aria-label="Firm settings"]');
  const sel = strip && strip.querySelector('[role="tab"][aria-selected="true"]');
  const notice = main && [...main.querySelectorAll('p[role="status"]')].find((p) => /have access to/.test(p.textContent));
  const se = document.scrollingElement;
  const b = main ? main.getBoundingClientRect() : null;
  return {
    h1: main ? [...main.querySelectorAll("h1")].map(txt) : [],
    selectedTab: sel ? txt(sel) : null,
    tabCount: strip ? strip.querySelectorAll('[role="tab"]').length : 0,
    notice: notice ? txt(notice) : null,
    docHeight: se.scrollHeight,
    hOverflow: se.scrollWidth > se.clientWidth ? se.scrollWidth - se.clientWidth : 0,
    mainBox: b ? { left: Math.round(b.left), width: Math.round(b.width) } : null,
  };
};

async function selCompat(p, tab) {
  const c = async (loc) => loc.count();
  const out = {
    "general-settings.spec:21 heading h1 /^General/": await c(p.getByRole("heading", { level: 1, name: /^General/ })),
    "general-settings.spec:28 button 'Save Settings'": await c(p.getByRole("button", { name: "Save Settings" })),
    "general-settings.spec:62 heading h2 'Currency'": await c(p.getByRole("heading", { level: 2, name: "Currency" })),
    "general-settings.spec:54 label 'Brand Color' exact": await c(p.getByLabel("Brand Color", { exact: true })),
    "general-settings.spec:55 label 'Document Footer Text'": await c(p.getByLabel("Document Footer Text")),
    "day-00-firm-setup.spec:122 locator main": await c(p.locator("main")),
    "day-00-firm-setup.spec:123 text /ZAR/ (strict: needs 1)": await c(p.getByText(/ZAR/)),
    "day-00-firm-setup.spec:130 label 'Brand Color' (strict: needs 1)": await c(p.getByLabel("Brand Color")),
    "roles-capabilities.spec:21 heading h1 'Roles & Permissions'": await c(p.getByRole("heading", { level: 1, name: "Roles & Permissions" })),
    "roles-capabilities.spec:33 heading 'System Roles'": await c(p.getByRole("heading", { name: "System Roles" })),
    "roles-capabilities.spec:35 heading h3 'Owner' exact": await c(p.getByRole("heading", { level: 3, name: "Owner", exact: true })),
    "roles-capabilities.spec:35 heading h3 'Admin' exact": await c(p.getByRole("heading", { level: 3, name: "Admin", exact: true })),
    "roles-capabilities.spec:35 heading h3 'Member' exact": await c(p.getByRole("heading", { level: 3, name: "Member", exact: true })),
    "roles-capabilities.spec:41 text 'Built-in roles that cannot be modified or deleted.'": await c(p.getByText("Built-in roles that cannot be modified or deleted.")),
    "roles-capabilities.spec:46 button 'Capability Reference'": await c(p.getByRole("button", { name: "Capability Reference" })),
    "roles-capabilities.spec:49 cell 'Financial Visibility' exact (collapsed)": await c(p.getByRole("cell", { name: "Financial Visibility", exact: true })),
    "roles-capabilities.spec:57 button 'New Role'": await c(p.getByRole("button", { name: "New Role" })),
    "day-75-85-closure.spec:293 testid retention-card": await c(p.getByTestId("retention-card")),
    "day-75-85-closure.spec:316 testid retention-card-scheduled-deletion": await c(p.getByTestId("retention-card-scheduled-deletion")),
    "testid fica-review-intervals": await c(p.getByTestId("fica-review-intervals")),
    "testid fica-review-months-{low,medium,high}": await c(p.locator('[data-testid^="fica-review-months-"]')),
    "testid feature-card-*": await c(p.locator('[data-testid^="feature-card-"]')),
    "testid feature-status-*": await c(p.locator('[data-testid^="feature-status-"]')),
    "testid org-documents-section": await c(p.getByTestId("org-documents-section")),
    "main#main-content": await c(p.locator("main#main-content")),
    "h1 in #main-content": await c(p.locator("#main-content h1")),
    "tablist 'Firm settings' exact": await c(p.getByRole("tablist", { name: "Firm settings", exact: true })),
  };
  const capBtn = p.getByRole("button", { name: "Capability Reference" });
  if (tab === "roles" && (await capBtn.count()) === 1) {
    await capBtn.click();
    await p.waitForTimeout(400);
    out["roles-capabilities.spec:49 cell 'Financial Visibility' exact (after click)"] = await c(p.getByRole("cell", { name: "Financial Visibility", exact: true }));
    out["roles-capabilities.spec:49 cell visible (after click)"] = await p.getByRole("cell", { name: "Financial Visibility", exact: true }).first().isVisible();
  }
  return out;
}

async function legacyRoutes(browser, S) {
  const { ctx, p } = await page(browser, S, 1024, 768, "light");
  for (const r of ["/settings", "/settings/rates", "/settings/roles", "/settings/general", "/settings/compliance", "/settings/features", "/settings/billing"]) {
    lab.label = `${KEY}|legacy ${r}`;
    const resp = await p.goto(`${L.BASE}${r}`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    pagesOpened++;
    const body = await p.locator("body").innerText();
    R.legacyRoutes[r] = { status: resp ? resp.status() : null, finalUrl: p.url().replace(L.BASE, ""), somethingWentWrong: body.includes("Something went wrong"), deniedRegex: /don.t have access|permission/i.test(body), h1: await p.locator("#main-content h1").allInnerTexts() };
  }
  await ctx.close();
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
        const m = await p.evaluate(COMPACT);
        m.status = resp ? resp.status() : null;
        m.url = p.url().replace(L.BASE, "");
        (R.variants[tab] = R.variants[tab] || {})[v] = m;
        const gate = m.notice ? `DENIED → ${m.selectedTab}` : `tab ${m.selectedTab}`;
        await shot(p, `tab__${tab}-${KEY}-${v}-vp`, `${KEY} ?tab=${tab} ${w}×${h} ${theme} viewport — ${gate}; h1 ${JSON.stringify(m.h1)}`);
        await shot(p, `tab__${tab}-${KEY}-${v}-full`, `${KEY} ?tab=${tab} ${w}×${h} ${theme} full page (${m.docHeight}px)`, { fullPage: true });
        if (w === 1440 && theme === "light") {
          const f = await p.evaluate(FULL);
          f.requested = tab;
          f.inTabBar = !!(f.tabBar && f.tabBar.some((t) => t.startsWith(`${LABELS[tab]}:`)));
          f.gateOutcome = f.notice ? "forbidden-notice" : f.inTabBar ? "rendered" : "not-in-tab-bar";
          R.tabs[tab] = f;
          const capBtn = p.getByRole("button", { name: "Capability Reference" });
          if (tab === "roles" && (await capBtn.count()) === 1) {
            await capBtn.click();
            await p.waitForTimeout(400);
            R.capabilityReference = await p.evaluate(() => {
              const t = document.querySelector("#main-content table");
              const txt = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
              return t ? { dataSlot: t.getAttribute("data-slot"), headers: [...t.querySelectorAll("thead th")].map(txt), rows: [...t.querySelectorAll("tbody tr")].map((r) => [...r.children].map(txt).join(" | ")), width: Math.round(t.getBoundingClientRect().width), containerWidth: Math.round(t.parentElement.getBoundingClientRect().width), className: t.className } : null;
            });
            await shot(p, `tab__roles-${KEY}-1440-light-capref`, `${KEY} ?tab=roles 1440 light — Capability Reference expanded (full page)`, { fullPage: true });
          }
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
  for (const t of L.TABS) console.log(t, JSON.stringify({ gate: R.tabs[t] && R.tabs[t].gateOutcome, h1: R.tabs[t] && R.tabs[t].h1, notice: R.tabs[t] && R.tabs[t].notice }));
})();
