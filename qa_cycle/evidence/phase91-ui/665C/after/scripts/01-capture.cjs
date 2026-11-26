// 665C OBS step 1: capture Settings › Matters (8 tabs, the take-on wizard deep link and the sub-pages)
// for ONE persona on the running e2e mock-auth stack (:3001). Playwright library, headless Chromium,
// DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded (`sidebar_state=true`),
// theme via localStorage `kazi-theme`.
// Usage: PHASE=before node 01-capture.cjs <persona>
//   persona = alice | carol | carol-cr-holder | carol-cr-lacker  (carol-cr-* = Carol while she holds a
//   custom role assigned by 02-roles.cjs; the key only labels the run, login is Carol's cookie).
// Read-only: no clicks except the custom-fields inner tab "Clients" for SEL-COMPAT (a client-side
// tab switch, nothing saved). Per surface × 1024×768/1440×900 × light/dark: a full-page shot (raw,
// /tmp, never committed) and a compact probe (h1s, Matters tab bar, notice, main width, page overflow,
// every table's scroller visible vs scroll width, empty states); at 1440 light the full probe
// (headings, cards, tables, testids, buttons, links, controls, chips, empty states, status/alert);
// at 1024 light the spec selectors (SEL-COMPAT). Surfaces: the 8 `?tab=` ids, `?tab=take-on&batch=`
// (the mid-wizard batch from 03-data), and — for alice / carol only — the sub-pages. Then the
// legacy routes. Output: $RAW/persona-<persona>.json + tiles (05 merges).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const KEY = process.argv[2];
if (!["alice", "carol", "carol-cr-holder", "carol-cr-lacker"].includes(KEY)) throw new Error("persona?");
const WHO = KEY.startsWith("carol") ? "carol" : "alice";
const VIEWPORTS = [[1024, 768], [1440, 900]];

const R = { persona: KEY, login: WHO, capturedAt: new Date().toISOString(), caps: null, targets: null, surfaces: {}, variants: {}, selCompat: {}, legacyRoutes: {} };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
let pagesOpened = 0;

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
      dataSlot: t.getAttribute("data-slot"),
      headers: [...t.querySelectorAll("thead th")].map(window.__t),
      bodyRows: t.querySelectorAll("tbody tr").length,
      tableWidth: Math.round(tb.width),
      scroller: sc ? { tag: sc.tagName.toLowerCase(), dataSlot: sc.getAttribute("data-slot"), cls: (sc.className || "").toString().slice(0, 80), overflowX: getComputedStyle(sc).overflowX, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth } : null,
    };
  };
  window.__empties = (root) => [...root.querySelectorAll("p,td,h2,h3,div")].filter((e) => window.__v(e) && e.children.length <= 2 && /^(No |Nothing |There are no )/.test(window.__t(e)) && window.__t(e).length < 200).map((e) => {
    const box = e.closest('[data-slot="empty-state"]') || e.closest('[data-slot="card-content"]') || e.closest("td") || e.closest("div.rounded-lg") || e.parentElement;
    return { tag: e.tagName.toLowerCase(), text: window.__t(e).slice(0, 160), cls: (e.className || "").toString().slice(0, 60), height: Math.round(e.getBoundingClientRect().height), containerHeight: Math.round(box.getBoundingClientRect().height), containerSlot: box.getAttribute("data-slot") };
  });
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
  const strip = main.querySelector('[role="tablist"][aria-label="Matters settings"]');
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
  const buttons = dedupe([...main.querySelectorAll('button,[role="button"],a[data-slot="button"]')].filter((b) => !b.closest('[role="tablist"]')).map((b) => ({ name: name(b).replace(U, "{uuid}").slice(0, 80), visible: vis(b), disabled: b.disabled || b.getAttribute("aria-disabled") === "true" || undefined, role: b.getAttribute("role") || undefined })));
  const links = dedupe([...main.querySelectorAll("a[href]")].filter((a) => !a.closest('[role="tablist"]')).map((a) => ({ name: name(a).slice(0, 60), href: a.getAttribute("href").replace(U, "{uuid}"), testid: a.dataset.testid ? a.dataset.testid.replace(U, "{uuid}") : undefined })));
  const controls = [...main.querySelectorAll('input,textarea,select,[role="combobox"],[role="switch"],[role="checkbox"]')].filter((e) => e.type !== "hidden" && !(e.tagName === "INPUT" && e.getAttribute("aria-hidden") === "true")).map((e) => ({ tag: e.tagName.toLowerCase(), type: e.getAttribute("type") || e.getAttribute("role") || undefined, id: e.id || undefined, testid: e.dataset.testid, label: labelFor(e), placeholder: e.getAttribute("placeholder") || undefined, value: e.tagName === "BUTTON" ? (e.getAttribute("aria-checked") ?? txt(e).slice(0, 60)) : (e.type === "file" ? undefined : (e.value || "").slice(0, 80)), options: e.tagName === "SELECT" ? [...e.options].map((o) => `${o.value}${o.selected ? "*" : ""}`) : undefined, disabled: e.disabled || undefined, visible: vis(e) }));
  const tables = [...main.querySelectorAll("table")].map((t) => ({ visible: vis(t), firstRow: t.querySelector("tbody tr") ? [...t.querySelector("tbody tr").children].map((c) => txt(c).slice(0, 50)) : null, ...window.__tableMeasure(t) }));
  const chips = dedupe([...main.querySelectorAll('[data-slot="badge"]')].filter(vis).map((b) => ({ text: txt(b).slice(0, 40), variant: b.getAttribute("data-variant") || undefined })));
  const innerTabs = [...main.querySelectorAll('[role="tablist"]')].filter((t) => t !== strip).map((t) => ({ label: t.getAttribute("aria-label"), tabs: [...t.querySelectorAll('[role="tab"]')].map((x) => `${txt(x)}:${x.getAttribute("aria-selected")}`) }));
  const se = document.scrollingElement;
  return {
    title: document.title,
    url: location.pathname.replace(U, "{uuid}") + location.search.replace(U, "{uuid}"),
    tabBar: strip ? [...strip.querySelectorAll('[role="tab"]')].map((t) => `${txt(t)}:${t.getAttribute("aria-selected")}`) : null,
    notice: notice ? txt(notice) : null,
    mainCount: document.querySelectorAll("main#main-content").length,
    mainElCount: document.querySelectorAll("main").length,
    h1: h1s.map((h) => ({ text: txt(h), visible: vis(h) })),
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
    emptyStates: window.__empties(main),
    statusAlerts: [...main.querySelectorAll('[role="status"],[role="alert"]')].filter(vis).map((e) => txt(e).slice(0, 160)),
    docHeight: se.scrollHeight,
  };
};
const COMPACT = () => {
  const main = document.querySelector("main#main-content");
  const txt = window.__t;
  const strip = main && main.querySelector('[role="tablist"][aria-label="Matters settings"]');
  const sel = strip && strip.querySelector('[role="tab"][aria-selected="true"]');
  const notice = main && [...main.querySelectorAll('p[role="status"]')].find((p) => /have access to/.test(p.textContent));
  const se = document.scrollingElement;
  const b = main ? main.getBoundingClientRect() : null;
  // the content column: the tab panel when there is one, else main's first child
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
    notice: notice ? txt(notice) : null,
    docHeight: se.scrollHeight,
    hOverflow: se.scrollWidth > se.clientWidth ? se.scrollWidth - se.clientWidth : 0,
    mainBox: b ? { left: Math.round(b.left), width: Math.round(b.width) } : null,
    contentBox: pb ? { tag: pb && panel.tagName.toLowerCase(), role: panel.getAttribute("role"), left: Math.round(pb.left), width: Math.round(pb.width) } : null,
    tables: main ? [...main.querySelectorAll("table")].map(window.__tableMeasure) : [],
    emptyStates: main ? window.__empties(main) : [],
    somethingWentWrong: document.body.innerText.includes("Something went wrong"),
  };
};

// Spec selectors (counts; strict = the spec acts on it, so it must be exactly 1).
const TAKE_ON_SPEC_IDS = ["take-on-tab", "take-on-wizard", "take-on-new-upload", "take-on-start-clients", "take-on-start-matters", "take-on-start-trust-position", "take-on-start-debtor-balances", "take-on-batch-name", "take-on-file-input", "take-on-upload-submit", "take-on-cutover-date", "take-on-trust-account", "take-on-section", "take-on-upload-server-error", "take-on-mapping", "take-on-save-mapping", "take-on-date-format", "take-on-leftovers", "take-on-validation", "take-on-validate", "take-on-cards", "take-on-card-total", "take-on-card-valid", "take-on-card-error", "take-on-card-duplicate", "take-on-filter-status", "take-on-to-attestation", "take-on-batch-status", "take-on-step-upload", "take-on-step-validation", "take-on-step-signOff", "take-on-files", "take-on-files-next", "take-on-sheet-picker", "take-on-header-row", "take-on-apply-sheet", "take-on-commit", "take-on-results", "take-on-back", "take-on-header", "take-on-dataset-picker", "take-on-guidance", "take-on-go-live-card"];
async function selCompat(p, surface) {
  const c = async (loc) => loc.count();
  const out = {
    "main#main-content": await c(p.locator("main#main-content")),
    "main (strict locator('main'))": await c(p.locator("main")),
    "h1 in #main-content": await c(p.locator("#main-content h1")),
    "tablist 'Matters settings' exact": await c(p.getByRole("tablist", { name: "Matters settings", exact: true })),
    "body 'Something went wrong'": await c(p.getByText("Something went wrong")),
  };
  if (surface === "custom-fields") {
    out["custom-fields.spec:24 heading h1 /^Custom Fields/ (strict toBeVisible)"] = await c(p.getByRole("heading", { level: 1, name: /^Custom Fields/ }));
    out["custom-fields.spec:38 tab 'Clients' (strict click)"] = await c(p.getByRole("tab", { name: "Clients" }));
    out["custom-fields.spec:48 button 'Add Field' (strict click)"] = await c(p.getByRole("button", { name: "Add Field" }));
    out["inner tabs (non-family tablist)"] = await p.evaluate(() => [...document.querySelectorAll('#main-content [role="tablist"]')].filter((t) => t.getAttribute("aria-label") !== "Matters settings").map((t) => [...t.querySelectorAll('[role="tab"]')].map((x) => x.textContent.trim()).join("|")));
    if ((await p.getByRole("tab", { name: "Clients" }).count()) === 1) {
      await p.getByRole("tab", { name: "Clients" }).click();
      await p.waitForTimeout(400);
      out["custom-fields.spec:39 (after Clients) heading 'Field Definitions' (strict toBeVisible)"] = await c(p.getByRole("heading", { name: "Field Definitions" }));
      out["custom-fields.spec:40 (after Clients) testid field-row filter 'ID / Passport Number' (strict)"] = await c(p.getByTestId("field-row").filter({ hasText: "ID / Passport Number" }));
      out["custom-fields.spec:58-59 (after Clients) testid field-row (count)"] = await c(p.getByTestId("field-row"));
      out["(after Clients) heading 'Field Groups'"] = await c(p.getByRole("heading", { name: "Field Groups" }));
      out["(after Clients) button 'Add Group'"] = await c(p.getByRole("button", { name: "Add Group" }));
    }
  }
  if (surface === "tags") {
    out["tags.spec:23 heading h1 /^Tags/ (strict toBeVisible)"] = await c(p.getByRole("heading", { level: 1, name: /^Tags/ }));
    out["tags.spec:36/43 button 'Add Tag' (uses .first())"] = await c(p.getByRole("button", { name: "Add Tag" }));
    out["tags.spec:52 cell exact 'E2E Tag pitx' (an existing spec-made tag; strict)"] = await c(p.getByRole("cell", { name: "E2E Tag pitx", exact: true }));
    out["tags.spec:52 cell exact 'Applied Tag pitx'"] = await c(p.getByRole("cell", { name: "Applied Tag pitx", exact: true }));
    out["tags.spec:73 placeholder 'Search tags...' ON THIS PAGE (the spec uses it on the matter page's TagInput)"] = await c(p.getByPlaceholder("Search tags..."));
    out["button 'Edit E2E Tag pitx' (sr-only)"] = await c(p.getByRole("button", { name: "Edit E2E Tag pitx" }));
    out["button 'Delete E2E Tag pitx' (sr-only)"] = await c(p.getByRole("button", { name: "Delete E2E Tag pitx" }));
  }
  if (surface === "take-on" || surface === "take-on-batch") {
    for (const id of TAKE_ON_SPEC_IDS) out[`testid ${id}`] = await c(p.getByTestId(id));
    out["testid prefix take-on-row-status-"] = await c(p.locator('[data-testid^="take-on-row-status-"]'));
    out["testid prefix take-on-history-link-"] = await c(p.locator('[data-testid^="take-on-history-link-"]'));
    out["testid prefix take-on-go-live-item-"] = await c(p.locator('[data-testid^="take-on-go-live-item-"]'));
    out["testid prefix take-on-dataset-"] = await c(p.locator('[data-testid^="take-on-dataset-"]:not([data-testid="take-on-dataset-picker"])'));
    out["all take-on*/takeon* testids present"] = await p.evaluate(() => { const m = {}; for (const e of document.querySelectorAll('#main-content [data-testid^="take-on"],#main-content [data-testid^="takeon"]')) { const k = e.dataset.testid.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, "{uuid}"); m[k] = (m[k] || 0) + 1; } return m; });
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

function surfaces(T) {
  const s = L.TABS.map((t) => [t, `/settings/matters?tab=${t}`]);
  if (T.batch) s.push(["take-on-batch", `/settings/matters?tab=take-on&batch=${T.batch.id}`]);
  if (KEY === "alice" || KEY === "carol") {
    const sub = [
      ["templates-id", T.matterTemplate && `/settings/matters/templates/${T.matterTemplate.id}`],
      ["templates-new", "/settings/matters/templates/new"],
      ["checklists-new", "/settings/matters/checklists/new"],
      ["checklists-id-custom", T.checklistCustom && `/settings/matters/checklists/${T.checklistCustom.id}`],
      ["checklists-id-pack", T.checklistPack && `/settings/matters/checklists/${T.checklistPack.id}`],
      ["checklists-edit-custom", T.checklistCustom && `/settings/matters/checklists/${T.checklistCustom.id}/edit`],
      ["request-templates-new", "/settings/matters/request-templates/new"],
      ["request-templates-id-custom", T.requestTemplate && `/settings/matters/request-templates/${T.requestTemplate.id}`],
      ["request-templates-id-pack", T.requestTemplatePack && `/settings/matters/request-templates/${T.requestTemplatePack.id}`],
    ];
    for (const [k, u] of sub) if (u) s.push([k, u]);
  }
  return s;
}

async function legacyRoutes(browser, S, T) {
  const { ctx, p } = await page(browser, S, 1024, 768, "light");
  const routes = ["/settings/project-templates", "/settings/project-naming", "/settings/custom-fields", "/settings/tags", "/settings/checklists", "/settings/request-templates", "/settings/request-settings", "/settings/checklists/new", "/settings/request-templates/new"];
  if (T.checklistPack) routes.push(`/settings/matters/checklists/${T.checklistPack.id}/edit`);
  if (T.matterTemplate) routes.push(`/settings/project-templates/${T.matterTemplate.id}`);
  routes.push("/settings/matters?tab=take-on&batch=not-a-uuid", "/settings/matters?tab=take-on&batch=00000000-0000-4000-8000-000000000665");
  for (const r of routes) {
    lab.label = `${KEY}|legacy ${r}`;
    const resp = await p.goto(`${L.BASE}${r}`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    pagesOpened++;
    const body = await p.locator("body").innerText();
    const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
    R.legacyRoutes[r.replace(U, "{id}")] = { status: resp ? resp.status() : null, finalUrl: p.url().replace(L.BASE, "").replace(U, "{id}"), somethingWentWrong: body.includes("Something went wrong"), h1: await p.locator("#main-content h1").allInnerTexts(), statusTexts: (await p.locator('#main-content [role="status"]').allInnerTexts()).map((t) => t.slice(0, 120)) };
  }
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, WHO);
  const tok = await L.idpToken(WHO);
  const caps = await L.apiGet(tok, "/api/me/capabilities");
  R.caps = caps.json ? { role: caps.json.role, isAdmin: caps.json.isAdmin, isOwner: caps.json.isOwner, capabilities: caps.json.capabilities } : caps;
  const T = await L.targets();
  R.targets = Object.fromEntries(Object.entries(T).map(([k, v]) => [k, v && v.id ? { id: v.id, name: v.name, source: v.source, status: v.status } : v]));
  const SURF = surfaces(T);
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const { ctx, p } = await page(browser, S, w, h, theme);
      for (const [sk, url] of SURF) {
        const v = `${w}-${theme}`;
        lab.label = `${KEY}|${sk}|${v}`;
        const resp = await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
        await p.waitForLoadState("networkidle").catch(() => {});
        await p.waitForTimeout(500);
        pagesOpened++;
        await p.evaluate(HELPERS);
        const m = await p.evaluate(COMPACT);
        m.status = resp ? resp.status() : null;
        m.url = p.url().replace(L.BASE, "").replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, "{id}");
        (R.variants[sk] = R.variants[sk] || {})[v] = m;
        const gate = m.notice ? `DENIED → ${m.selectedTab}` : m.selectedTab ? `tab ${m.selectedTab}` : `→ ${m.url}`;
        await shot(p, `s__${sk}-${KEY}-${v}`, `${KEY} ${sk} ${w}×${h} ${theme} full page (${m.docHeight}px) — ${gate}; h1 ${JSON.stringify(m.h1)}`, { fullPage: true });
        if (w === 1440 && theme === "light") {
          const f = await p.evaluate(FULL);
          f.requested = url.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g, "{id}");
          if (L.TABS.includes(sk)) {
            f.inTabBar = !!(f.tabBar && f.tabBar.some((t) => t.startsWith(`${L.LABELS[sk]}:`)));
            f.gateOutcome = f.notice ? "forbidden-notice" : f.inTabBar ? "rendered" : "not-in-tab-bar";
          }
          R.surfaces[sk] = f;
        }
        if (w === 1024 && theme === "light") R.selCompat[sk] = await selCompat(p, sk);
      }
      await ctx.close();
    }
  await legacyRoutes(browser, S, T);
  await browser.close();
  R.console = { pagesOpened, errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, messages: consoleSink };
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, `persona-${KEY}.json`), JSON.stringify(R));
  console.log(KEY, "tiles", tiles.length, "pages", pagesOpened, "console", consoleSink.length, "caps", JSON.stringify(R.caps));
  for (const [sk] of SURF) { const f = R.surfaces[sk] || {}; const v = R.variants[sk]["1024-light"]; console.log(sk, JSON.stringify({ gate: f.gateOutcome, url: v.url, h1: v.h1, notice: v.notice, tabs: v.tabs.length, main: v.mainBox && v.mainBox.width, content: v.contentBox && v.contentBox.width, hOverflow: v.hOverflow, tables1024: v.tables.map((x) => x.scroller && `${x.scroller.clientWidth}/${x.scroller.scrollWidth}`), empties: v.emptyStates.map((e) => `${e.text.slice(0, 40)}:${e.containerHeight}`) })); }
  console.log("legacy", JSON.stringify(R.legacyRoutes));
})();
