// 665D OBS step 1: capture Settings › Fees & Trust (the 6 `?tab=` ids) for ONE persona on the running
// e2e mock-auth stack (:3001). Playwright library, headless Chromium, DPR 1, reduced motion, timezone
// Africa/Johannesburg, app rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`.
// Usage: PHASE=before node 01-capture.cjs <persona> [suffix]
//   persona = alice | carol | carol-cr-holder | carol-cr-lacker  (carol-cr-* = Carol while she holds a
//   custom role assigned by 02-roles.cjs; the key only labels the run, login is Carol's cookie).
// Read-only: no clicks except the Rates inner tab "Cost Rates" for SEL-COMPAT (a client-side tab
// switch, nothing saved). Per tab × 1024×768/1440×900 × light/dark: a full-page shot (raw, /tmp,
// never committed) and a compact probe (h1s, the Fees tab bar, the settings-rail Fees links, notice,
// main / content width, document h-overflow, every table's scroller visible vs scroll width, empty
// states with an outerHTML hash); at 1440 light the full probe (headings, cards, tables, testids,
// buttons, links, controls, chips, empty states, status/alert); at 1024 light the spec selectors
// (SEL-COMPAT). Output: $RAW/persona-<persona>.json + tiles (05 merges).
const path = require("path");
const fs = require("fs");
const crypto = require("crypto");
const L = require("./lib.cjs");

const KEY = process.argv[2];
if (!["alice", "carol", "carol-cr-holder", "carol-cr-lacker"].includes(KEY)) throw new Error("persona?");
const WHO = KEY.startsWith("carol") ? "carol" : "alice";
const VIEWPORTS = [[1024, 768], [1440, 900]];
const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;

const R = { persona: KEY, login: WHO, capturedAt: new Date().toISOString(), caps: null, surfaces: {}, variants: {}, selCompat: {} };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
let pagesOpened = 0;

const HELPERS = (TABLIST) => {
  window.__TABLIST = TABLIST;
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
      dataSlot: t.getAttribute("data-slot"),
      visible: window.__v(t),
      headers: [...t.querySelectorAll("thead th")].map(window.__t),
      bodyRows: t.querySelectorAll("tbody tr").length,
      tableWidth: Math.round(tb.width),
      scroller: sc ? { tag: sc.tagName.toLowerCase(), dataSlot: sc.getAttribute("data-slot"), cls: (sc.className || "").toString().slice(0, 80), overflowX: getComputedStyle(sc).overflowX, position: getComputedStyle(sc).position, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth } : null,
    };
  };
  window.__empties = (root) => [...root.querySelectorAll("p,td,h2,h3,div")].filter((e) => window.__v(e) && e.children.length <= 2 && /^(No |Nothing |There are no )/.test(window.__t(e)) && window.__t(e).length < 200).map((e) => {
    const box = e.closest('[data-slot="empty-state"]') || e.closest('[data-slot="card-content"]') || e.closest("td") || e.closest("div.rounded-lg") || e.parentElement;
    return { tag: e.tagName.toLowerCase(), text: window.__t(e).slice(0, 160), cls: (e.className || "").toString().slice(0, 60), height: Math.round(e.getBoundingClientRect().height), containerHeight: Math.round(box.getBoundingClientRect().height), containerSlot: box.getAttribute("data-slot"), outerHTML: box.outerHTML.slice(0, 4000) };
  });
  window.__rail = () => {
    const nav = document.querySelector('nav[aria-label="Settings navigation"]');
    if (!nav) return null;
    return [...nav.querySelectorAll('a[href*="/settings/fees"]')].map((a) => `${window.__t(a)}${a.getAttribute("aria-current") ? "*" : ""}`);
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
  const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
  const strip = main.querySelector(`[role="tablist"][aria-label="${window.__TABLIST}"]`);
  const notice = [...main.querySelectorAll('p[role="status"]')].find((p) => /have access to/.test(p.textContent));
  const headings = [...main.querySelectorAll("h1,h2,h3,h4,h5,h6,[role=heading]")].filter(vis).map((h) => ({ level: h.getAttribute("aria-level") ? +h.getAttribute("aria-level") : +h.tagName[1], tag: h.tagName.toLowerCase(), text: txt(h).slice(0, 100) }));
  const h1s = [...main.querySelectorAll("h1")];
  const slotCards = [...main.querySelectorAll('[data-slot="card"]')].filter(vis);
  const handCards = [...main.querySelectorAll("div.rounded-lg.border")].filter((e) => vis(e) && !e.closest('[role="tablist"]') && e.getAttribute("data-slot") !== "card" && !e.closest('[data-slot="card"]'));
  const cardHead = (c) => { const h = c.querySelector("h1,h2,h3,h4,legend,[data-slot=card-title]"); return h ? txt(h).slice(0, 60) : txt(c).slice(0, 40); };
  const testids = {};
  for (const e of main.querySelectorAll("[data-testid]")) {
    const k = e.dataset.testid.replace(U, "{uuid}");
    testids[k] = (testids[k] || 0) + 1;
  }
  const dedupe = (arr) => { const m = new Map(); for (const x of arr) { const k = JSON.stringify(x); m.set(k, (m.get(k) || 0) + 1); } return [...m].map(([k, n]) => ({ ...JSON.parse(k), n })); };
  const buttons = dedupe([...main.querySelectorAll('button,[role="button"],a[data-slot="button"]')].filter((b) => !b.closest(`[role="tablist"][aria-label="${window.__TABLIST}"]`)).map((b) => ({ name: name(b).replace(U, "{uuid}").slice(0, 80), visible: vis(b), disabled: b.disabled || b.getAttribute("aria-disabled") === "true" || undefined, role: b.getAttribute("role") || undefined })));
  const links = dedupe([...main.querySelectorAll("a[href]")].filter((a) => !a.closest('[role="tablist"]')).map((a) => ({ name: name(a).slice(0, 60), href: a.getAttribute("href").replace(U, "{uuid}"), testid: a.dataset.testid ? a.dataset.testid.replace(U, "{uuid}") : undefined })));
  const controls = [...main.querySelectorAll('input,textarea,select,[role="combobox"],[role="switch"],[role="checkbox"]')].filter((e) => e.type !== "hidden" && !(e.tagName === "INPUT" && e.getAttribute("aria-hidden") === "true")).map((e) => ({ tag: e.tagName.toLowerCase(), type: e.getAttribute("type") || e.getAttribute("role") || undefined, id: e.id || undefined, testid: e.dataset.testid, label: labelFor(e), placeholder: e.getAttribute("placeholder") || undefined, value: e.tagName === "BUTTON" ? (e.getAttribute("aria-checked") ?? txt(e).slice(0, 60)) : (e.type === "file" ? undefined : (e.value || "").slice(0, 80)), options: e.tagName === "SELECT" ? [...e.options].map((o) => `${o.value}${o.selected ? "*" : ""}`) : undefined, disabled: e.disabled || undefined, visible: vis(e) }));
  const tables = [...main.querySelectorAll("table")].map((t) => ({ firstRow: t.querySelector("tbody tr") ? [...t.querySelector("tbody tr").children].map((c) => txt(c).slice(0, 50)) : null, ...window.__tableMeasure(t) }));
  const chips = dedupe([...main.querySelectorAll('[data-slot="badge"]')].filter(vis).map((b) => ({ text: txt(b).slice(0, 40), variant: b.getAttribute("data-variant") || undefined })));
  const innerTabs = [...main.querySelectorAll('[role="tablist"]')].filter((t) => t !== strip).map((t) => ({ label: t.getAttribute("aria-label"), tabs: [...t.querySelectorAll('[role="tab"]')].map((x) => `${txt(x)}:${x.getAttribute("aria-selected")}`) }));
  const msgs = [...main.querySelectorAll("p")].filter((p) => vis(p) && /text-(green|red)-600/.test(p.className)).map((p) => ({ text: txt(p).slice(0, 120), cls: p.className, role: p.getAttribute("role") }));
  return {
    title: document.title,
    url: location.pathname.replace(U, "{uuid}") + location.search.replace(U, "{uuid}"),
    tabBar: strip ? [...strip.querySelectorAll('[role="tab"]')].map((t) => `${txt(t)}:${t.getAttribute("aria-selected")}`) : null,
    rail: window.__rail(),
    notice: notice ? txt(notice) : null,
    mainCount: document.querySelectorAll("main#main-content").length,
    mainElCount: document.querySelectorAll("main").length,
    h1: h1s.map((h) => ({ text: txt(h), visible: vis(h), cls: h.className })),
    h1Count: h1s.length,
    headings,
    innerTabs,
    cards: { slotCard: slotCards.length, handRolled: handCards.length, slotCardHeads: slotCards.map(cardHead), handRolledHeads: handCards.map(cardHead) },
    tables,
    testids,
    buttons,
    links,
    controls,
    chips,
    inlineMessages: msgs,
    emptyStates: window.__empties(main).map(({ outerHTML, ...e }) => e),
    statusAlerts: [...main.querySelectorAll('[role="status"],[role="alert"]')].filter(vis).map((e) => txt(e).slice(0, 160)),
    docHeight: document.scrollingElement.scrollHeight,
  };
};
const COMPACT = () => {
  const main = document.querySelector("main#main-content");
  const txt = window.__t;
  const strip = main && main.querySelector(`[role="tablist"][aria-label="${window.__TABLIST}"]`);
  const sel = strip && strip.querySelector('[role="tab"][aria-selected="true"]');
  const notice = main && [...main.querySelectorAll('p[role="status"]')].find((p) => /have access to/.test(p.textContent));
  const se = document.scrollingElement;
  const b = main ? main.getBoundingClientRect() : null;
  const panel = main && (main.querySelector('[role="tabpanel"]') || main.firstElementChild);
  const pb = panel ? panel.getBoundingClientRect() : null;
  return {
    h1: main ? [...main.querySelectorAll("h1")].map(txt) : [],
    h1Count: main ? main.querySelectorAll("h1").length : 0,
    mainCount: document.querySelectorAll("main#main-content").length,
    mainElCount: document.querySelectorAll("main").length,
    selectedTab: sel ? txt(sel) : null,
    tabs: strip ? [...strip.querySelectorAll('[role="tab"]')].map(txt) : [],
    tabStrip: strip ? { clientWidth: strip.clientWidth, scrollWidth: strip.scrollWidth } : null,
    rail: window.__rail(),
    notice: notice ? txt(notice) : null,
    docHeight: se.scrollHeight,
    hOverflow: se.scrollWidth > se.clientWidth ? se.scrollWidth - se.clientWidth : 0,
    mainBox: b ? { left: Math.round(b.left), width: Math.round(b.width) } : null,
    contentBox: pb ? { tag: panel.tagName.toLowerCase(), role: panel.getAttribute("role"), left: Math.round(pb.left), width: Math.round(pb.width) } : null,
    tables: main ? [...main.querySelectorAll("table")].map(window.__tableMeasure) : [],
    emptyStates: main ? window.__empties(main) : [],
    somethingWentWrong: document.body.innerText.includes("Something went wrong"),
  };
};

// Spec selectors (counts; strict = the spec acts on it, so it must be exactly 1).
async function selCompat(p, surface) {
  const c = (loc) => loc.count();
  const main = p.locator("#main-content");
  const out = {
    "main#main-content": await c(p.locator("main#main-content")),
    "main (strict locator('main'))": await c(p.locator("main")),
    "h1 in #main-content": await c(p.locator("#main-content h1")),
    [`tablist '${L.TABLIST}' exact`]: await c(p.getByRole("tablist", { name: L.TABLIST, exact: true })),
    "body 'Something went wrong'": await c(p.getByText("Something went wrong")),
    "status notice 'You don't have access to'": await c(p.getByRole("status").filter({ hasText: "You don't have access to" })),
  };
  if (surface === "tariffs") {
    out["rate-cards.spec:14/26 #main-content heading h1 /^Rates & Currency/ (strict toBeVisible)"] = await c(main.getByRole("heading", { level: 1, name: /^Rates & Currency/ }));
    out["day-00:58 page heading h1 'Rates & Currency' (substring; strict toBeVisible)"] = await c(p.getByRole("heading", { level: 1, name: "Rates & Currency" }));
    out["day-90:392/448 page heading h1 /Rates & Currency/ (strict toBeVisible)"] = await c(p.getByRole("heading", { level: 1, name: /Rates & Currency/ }));
    out["rate-cards.spec:19 #main-content contains 'Alice Owner'"] = (await main.innerText()).includes("Alice Owner") ? 1 : 0;
    out["rate-cards.spec:39 / day-90:397 text 'You do not have permission to manage rates and currency settings.'"] = await c(p.getByText(/You do not have permission to manage rates and currency settings\./));
    out["day-90:397 exact full denial text (getByText substring)"] = await c(p.getByText("You do not have permission to manage rates and currency settings. Only admins and owners can access this page."));
    out["day-90:401 buttons /^(Add|Edit) (billing )?rate for/ (toHaveCount 0 for members)"] = await c(p.getByRole("button", { name: /^(Add|Edit) (billing )?rate for/ }));
    out["day-90:451/476 testid billing-rate-owner (strict toBeVisible)"] = await c(p.getByTestId("billing-rate-owner"));
    out["day-00:73 testids billing-rate-{owner,admin,member}"] = [await c(p.getByTestId("billing-rate-owner")), await c(p.getByTestId("billing-rate-admin")), await c(p.getByTestId("billing-rate-member"))].join("/");
    out["day-00:145 tab 'Billing Rates' (strict toBeVisible)"] = await c(p.getByRole("tab", { name: "Billing Rates" }));
    out["day-00:146/164 tab 'Cost Rates' (strict toBeVisible / click)"] = await c(p.getByRole("tab", { name: "Cost Rates" }));
    out["day-00:74 button 'Add rate for Carol Member' in billing-rate-member"] = await c(p.getByTestId("billing-rate-member").getByRole("button", { name: "Add rate for Carol Member" }));
    out["heading 'Cost Rates' BEFORE the click"] = await c(p.getByRole("heading", { name: "Cost Rates" }));
    if ((await p.getByRole("tab", { name: "Cost Rates" }).count()) === 1) {
      await p.getByRole("tab", { name: "Cost Rates" }).click();
      await p.waitForTimeout(400);
      out["day-00:165 (after Cost Rates click) heading 'Cost Rates' (strict toBeVisible)"] = await c(p.getByRole("heading", { name: "Cost Rates" }));
      out["(after Cost Rates click) testids cost-rate-{owner,admin,member}"] = [await c(p.getByTestId("cost-rate-owner")), await c(p.getByTestId("cost-rate-admin")), await c(p.getByTestId("cost-rate-member"))].join("/");
      out["(after Cost Rates click) cost table measure"] = await p.evaluate(() => [...document.querySelectorAll("#main-content table")].filter(window.__v).map(window.__tableMeasure));
    }
  }
  if (surface === "tax") {
    out["tax-settings.spec:24 page heading h1 'Tax Settings' (strict toBeVisible)"] = await c(p.getByRole("heading", { level: 1, name: "Tax Settings" }));
    const vat = p.getByRole("row").filter({ has: p.getByRole("cell", { name: "VAT — Standard", exact: true }) });
    out["tax-settings.spec:37-39 row filter cell exact 'VAT — Standard'"] = await c(vat);
    out["tax-settings.spec:40 that row contains '15.00%'"] = (await vat.count()) === 1 && (await vat.innerText()).includes("15.00%") ? 1 : 0;
    out["tax-settings.spec:41/47 button 'Add Tax Rate' (strict)"] = await c(p.getByRole("button", { name: "Add Tax Rate" }));
    out["tax-settings.spec:57 row filter cell exact 'E2E Rate v0n1' (an existing spec-made rate)"] = await c(p.getByRole("row").filter({ has: p.getByRole("cell", { name: "E2E Rate v0n1", exact: true }) }));
    out["day-00:174 testid tax-rate-row (.first() toBeVisible; count)"] = await c(p.getByTestId("tax-rate-row"));
    const v2 = p.getByTestId("tax-rate-row").filter({ has: p.getByTestId("tax-rate-name").getByText("VAT — Standard", { exact: true }) });
    out["day-00:183-186 tax-rate-row filter tax-rate-name 'VAT — Standard' (toHaveCount 1)"] = await c(v2);
    out["day-00:187 its tax-rate-value text"] = (await v2.count()) === 1 ? await v2.getByTestId("tax-rate-value").innerText() : null;
    out["day-00:188 its tax-rate-default text"] = (await v2.count()) === 1 ? await v2.getByTestId("tax-rate-default").innerText() : null;
    out["heading 'Tax Configuration'"] = await c(p.getByRole("heading", { name: "Tax Configuration" }));
    out["heading 'Tax Rates'"] = await c(p.getByRole("heading", { name: "Tax Rates", exact: true }));
    out["button 'Save Settings'"] = await c(p.getByRole("button", { name: "Save Settings" }));
  }
  if (surface === "trust-accounting") {
    out["day-90:483 testid trust-settings-page (toBeVisible; Carol toHaveCount 0 at :414)"] = await c(p.getByTestId("trust-settings-page"));
    out["day-00:250 / day-90:484 testid trust-accounts-section"] = await c(p.getByTestId("trust-accounts-section"));
    out["day-00:252 trust-accounts-section getByText exact 'Mathebula & Partners Trust Account'"] = await c(p.getByTestId("trust-accounts-section").getByText("Mathebula & Partners Trust Account", { exact: true }));
    out["day-90:409 status filter 'You don't have access to Trust accounting — showing Rates instead.'"] = await c(p.getByRole("status").filter({ hasText: "You don't have access to Trust accounting — showing Rates instead." }));
    out["day-90:415 button 'Add Account'"] = await c(p.getByRole("button", { name: "Add Account" }));
    for (const id of ["approval-settings-section", "reminder-settings-section", "trust-account-section", "trust-account-basis", "section-86-6-settings-advisory"]) out[`testid ${id}`] = await c(p.getByTestId(id));
  }
  for (const [s, h] of [["time-recording", "Time Tracking"], ["fee-runs", "Batch Billing"], ["collections", "Collections"]])
    if (surface === s) {
      out[`heading h1 '${h}'`] = await c(p.getByRole("heading", { level: 1, name: h }));
      out["button 'Save Settings'"] = await c(p.getByRole("button", { name: "Save Settings" }));
    }
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
const hashEmpties = (arr) => arr.map(({ outerHTML, ...e }) => ({ ...e, outerHTMLsha1: crypto.createHash("sha1").update(outerHTML.replace(U, "{uuid}")).digest("hex").slice(0, 12), outerHTMLlen: outerHTML.length }));

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, WHO);
  const tok = await L.idpToken(WHO);
  const caps = await L.apiGet(tok, "/api/me/capabilities");
  R.caps = caps.json ? { role: caps.json.role, isAdmin: caps.json.isAdmin, isOwner: caps.json.isOwner, capabilities: caps.json.capabilities } : caps;
  const SURF = L.TABS.map((t) => [t, `/settings/fees?tab=${t}`]);
  SURF.push(["no-tab", "/settings/fees"]);
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const { ctx, p } = await page(browser, S, w, h, theme);
      for (const [sk, url] of SURF) {
        if (sk === "no-tab" && !(w === 1024 && theme === "light")) continue;
        const v = `${w}-${theme}`;
        lab.label = `${KEY}|${sk}|${v}`;
        const resp = await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
        await p.waitForLoadState("networkidle").catch(() => {});
        await p.waitForTimeout(500);
        pagesOpened++;
        await p.evaluate(HELPERS, L.TABLIST);
        const m = await p.evaluate(COMPACT);
        m.emptyStates = hashEmpties(m.emptyStates);
        m.status = resp ? resp.status() : null;
        m.url = p.url().replace(L.BASE, "").replace(U, "{id}");
        m.title = await p.title();
        (R.variants[sk] = R.variants[sk] || {})[v] = m;
        const gate = m.notice ? `DENIED → ${m.selectedTab}` : m.selectedTab ? `tab ${m.selectedTab}` : `→ ${m.url}`;
        await shot(p, `s__${sk}-${KEY}-${v}`, `${KEY} ${sk} ${w}×${h} ${theme} full page (${m.docHeight}px) — ${gate}; h1 ${JSON.stringify(m.h1)}; docHOverflow ${m.hOverflow}`, { fullPage: true });
        if (w === 1440 && theme === "light") {
          const f = await p.evaluate(FULL);
          f.requested = url;
          if (L.TABS.includes(sk)) {
            f.inTabBar = !!(f.tabBar && f.tabBar.some((t) => t.startsWith(`${L.LABELS[sk]}:`)));
            f.gateOutcome = f.notice ? "forbidden-notice" : f.inTabBar ? "rendered" : "not-in-tab-bar";
          }
          R.surfaces[sk] = f;
        }
        if (w === 1024 && theme === "light" && sk !== "no-tab") R.selCompat[sk] = await selCompat(p, sk);
      }
      await ctx.close();
    }
  await browser.close();
  R.console = { pagesOpened, errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, messages: consoleSink };
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, `persona-${KEY}.json`), JSON.stringify(R));
  console.log(KEY, "tiles", tiles.length, "pages", pagesOpened, "console", consoleSink.length, "caps", JSON.stringify(R.caps));
  for (const [sk] of SURF) { const f = R.surfaces[sk] || {}; const v = R.variants[sk]["1024-light"]; console.log(sk, JSON.stringify({ gate: f.gateOutcome, url: v.url, h1: v.h1, notice: v.notice, tabs: v.tabs.join("|"), rail: v.rail && v.rail.join("|"), main: v.mainBox && v.mainBox.width, content: v.contentBox && v.contentBox.width, hOverflow: v.hOverflow, dark1024: R.variants[sk]["1024-dark"] && R.variants[sk]["1024-dark"].hOverflow, tables1024: v.tables.map((x) => x.scroller && `${x.scroller.clientWidth}/${x.scroller.scrollWidth}`), empties: v.emptyStates.map((e) => `${e.text.slice(0, 40)}:${e.containerHeight}`) })); }
})();
