// 665E OBS step 1: capture Settings › Documents (the 4 `?tab=` ids + the Email inner "Delivery Log"
// tab + the template sub-pages) for ONE persona on the running e2e mock-auth stack (:3001).
// Playwright library, headless Chromium, DPR 1, reduced motion, timezone Africa/Johannesburg, app
// rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`.
// Usage: PHASE=before node 01-capture.cjs <persona>     persona = alice | carol
// Read-only: no clicks except the Email inner tab "Delivery Log" (a client-side tab switch). Per
// surface × 1024×768/1440×900 × light/dark: a full-page shot (raw, /tmp, never committed) and a
// compact probe (h1s, the Documents tab bar, the settings-rail Documents links, notice, main /
// content width, document h-overflow, every table's scroller visible vs scroll width + per-column
// cut, empty states with an outerHTML hash, a text-contrast probe); at 1440 light the full probe
// (headings, cards, tables, testids, buttons, links, controls, chips, status/alert); at 1024 light the
// spec selectors (SEL-COMPAT). Output: $RAW/persona-<persona>.json + tiles (05 merges).
const path = require("path");
const fs = require("fs");
const crypto = require("crypto");
const L = require("./lib.cjs");

const KEY = process.argv[2];
if (!["alice", "carol"].includes(KEY)) throw new Error("persona?");
const WHO = KEY;
const VIEWPORTS = [[1024, 768], [1440, 900]];
const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
// The platform template opened in the editor (read-only for everyone: source PLATFORM).
const EDIT_TEMPLATE = "Engagement Letter — General";

const R = { persona: KEY, login: WHO, capturedAt: new Date().toISOString(), caps: null, editTemplate: null, surfaces: {}, variants: {}, selCompat: {} };
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
      if (/(auto|scroll|hidden|clip)/.test(cs.overflowX) || /inline-size/.test(cs.contain || "") || /inline-size/.test(cs.containerType || "")) return e;
    }
    return null;
  };
  window.__tableMeasure = (t) => {
    const sc = window.__scroller(t);
    const tb = t.getBoundingClientRect();
    const sr = sc ? sc.getBoundingClientRect() : null;
    const visRight = sr ? sr.left + sc.clientWidth : innerWidth;
    const cols = [...t.querySelectorAll("thead th")].filter(window.__v).map((th) => { const r = th.getBoundingClientRect(); return { h: window.__t(th) || "(blank)", w: Math.round(r.width), cutPx: Math.max(0, Math.round(r.right - visRight)), hidden: r.left >= visRight }; });
    const cellClip = [...t.querySelectorAll("tbody td")].filter((td) => window.__v(td) && td.scrollWidth > td.clientWidth + 1).length;
    return {
      dataSlot: t.getAttribute("data-slot"),
      visible: window.__v(t),
      headers: [...t.querySelectorAll("thead th")].map(window.__t),
      bodyRows: t.querySelectorAll("tbody tr").length,
      tableWidth: Math.round(tb.width),
      cols,
      cellsWithInternalClip: cellClip,
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
    return [...nav.querySelectorAll('a[href*="/settings/documents"]')].filter(window.__v).map((a) => `${window.__t(a)}${a.getAttribute("aria-current") ? "*" : ""}`);
  };
  // WCAG contrast of each visible text element's own text (direct text nodes) against its composited
  // background (the ancestor background chain down from the first opaque one). Returns < 4.5 only.
  const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (c, base) => { cx.globalCompositeOperation = "copy"; cx.fillStyle = base; cx.fillRect(0, 0, 1, 1); cx.globalCompositeOperation = "source-over"; cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return `rgb(${d[0]},${d[1]},${d[2]})`; };
  const trip = (s) => s.match(/\d+/g).slice(0, 3).map(Number);
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const isClear = (c) => { cx.globalCompositeOperation = "copy"; cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); return cx.getImageData(0, 0, 1, 1).data[3] === 0; };
  const alphaOf = (c) => { cx.globalCompositeOperation = "copy"; cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); return cx.getImageData(0, 0, 1, 1).data[3]; };
  window.__bg = (e) => { const chain = []; for (let x = e; x; x = x.parentElement) { const b = getComputedStyle(x).backgroundColor; if (!isClear(b)) { chain.push(b); if (alphaOf(b) === 255) break; } } let base = "rgb(255,255,255)"; for (const b of chain.reverse()) base = px(b, base); return base; };
  window.__contrast = (root) => {
    const out = [];
    for (const e of root.querySelectorAll("*")) {
      if (!window.__v(e)) continue;
      const own = [...e.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent).join("").replace(/\s+/g, " ").trim();
      if (!own || own.length < 2) continue;
      const cs = getComputedStyle(e);
      if (cs.opacity === "0") continue;
      const bg = window.__bg(e);
      const fg = px(cs.color, bg);
      const [l1, l2] = [lum(trip(fg)), lum(trip(bg))].sort((a, b) => b - a);
      const ratio = +((l1 + 0.05) / (l2 + 0.05)).toFixed(2);
      const large = parseFloat(cs.fontSize) >= 24 || (parseFloat(cs.fontSize) >= 18.66 && +cs.fontWeight >= 700);
      if (ratio < (large ? 3 : 4.5)) out.push({ text: own.slice(0, 60), tag: e.tagName.toLowerCase(), cls: (e.className || "").toString().slice(0, 90), fg, bg, ratio, fontPx: parseFloat(cs.fontSize), disabled: !!e.closest("button:disabled,[aria-disabled=true],[data-disabled]") });
    }
    const m = new Map(); for (const x of out) { const k = `${x.text}|${x.cls}`; if (!m.has(k)) m.set(k, x); }
    return [...m.values()].sort((a, b) => a.ratio - b.ratio).slice(0, 25);
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
  const handCards = [...main.querySelectorAll("div.rounded-lg.border, div.rounded-md.border")].filter((e) => vis(e) && !e.closest('[role="tablist"]') && e.getAttribute("data-slot") !== "card" && !e.closest('[data-slot="card"]'));
  const cardHead = (c) => { const h = c.querySelector("h1,h2,h3,h4,legend,[data-slot=card-title]"); return h ? txt(h).slice(0, 60) : txt(c).slice(0, 40); };
  const testids = {};
  for (const e of main.querySelectorAll("[data-testid]")) {
    const k = e.dataset.testid.replace(U, "{uuid}");
    testids[k] = (testids[k] || 0) + 1;
  }
  const dedupe = (arr) => { const m = new Map(); for (const x of arr) { const k = JSON.stringify(x); m.set(k, (m.get(k) || 0) + 1); } return [...m].map(([k, n]) => ({ ...JSON.parse(k), n })); };
  const buttons = dedupe([...main.querySelectorAll('button,[role="button"],a[data-slot="button"]')].filter((b) => !b.closest(`[role="tablist"][aria-label="${window.__TABLIST}"]`)).map((b) => ({ name: name(b).replace(U, "{uuid}").slice(0, 80), visible: vis(b), disabled: b.disabled || b.getAttribute("aria-disabled") === "true" || undefined, role: b.getAttribute("role") || undefined })));
  const links = dedupe([...main.querySelectorAll("a[href]")].filter((a) => !a.closest('[role="tablist"]')).map((a) => ({ name: name(a).slice(0, 60), href: a.getAttribute("href").replace(U, "{uuid}"), testid: a.dataset.testid ? a.dataset.testid.replace(U, "{uuid}") : undefined }))).slice(0, 40);
  const controls = [...main.querySelectorAll('input,textarea,select,[role="combobox"],[role="switch"],[role="checkbox"],[contenteditable]')].filter((e) => e.type !== "hidden" && !(e.tagName === "INPUT" && e.getAttribute("aria-hidden") === "true")).map((e) => ({ tag: e.tagName.toLowerCase(), type: e.getAttribute("type") || e.getAttribute("role") || (e.hasAttribute("contenteditable") ? `contenteditable=${e.getAttribute("contenteditable")}` : undefined), id: e.id || undefined, testid: e.dataset.testid, label: labelFor(e), placeholder: e.getAttribute("placeholder") || undefined, value: e.hasAttribute("contenteditable") ? txt(e).slice(0, 40) : e.tagName === "BUTTON" ? (e.getAttribute("aria-checked") ?? txt(e).slice(0, 60)) : (e.type === "file" ? undefined : (e.value || "").slice(0, 80)), options: e.tagName === "SELECT" ? [...e.options].map((o) => `${o.value}${o.selected ? "*" : ""}`) : undefined, disabled: e.disabled || undefined, visible: vis(e) }));
  const tables = [...main.querySelectorAll("table")].map((t) => ({ firstRow: t.querySelector("tbody tr") ? [...t.querySelector("tbody tr").children].map((c) => txt(c).slice(0, 50)) : null, ...window.__tableMeasure(t) }));
  const chips = dedupe([...main.querySelectorAll('[data-slot="badge"]')].filter(vis).map((b) => ({ text: txt(b).slice(0, 40), variant: b.getAttribute("data-variant") || undefined })));
  const innerTabs = [...main.querySelectorAll('[role="tablist"]')].filter((t) => t !== strip).map((t) => ({ label: t.getAttribute("aria-label"), tabs: [...t.querySelectorAll('[role="tab"]')].map((x) => `${txt(x)}:${x.getAttribute("aria-selected")}`) }));
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
    h1OutsideMain: [...document.querySelectorAll("h1")].filter((h) => !main.contains(h)).map(txt),
    headings,
    innerTabs,
    cards: { slotCard: slotCards.length, handRolled: handCards.length, slotCardHeads: slotCards.map(cardHead).slice(0, 40), handRolledHeads: handCards.map(cardHead).slice(0, 40) },
    tables,
    testids,
    buttons,
    links,
    controls,
    chips,
    proseMirror: [...main.querySelectorAll(".ProseMirror")].map((e) => e.getAttribute("contenteditable")),
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
  const panel = main && (main.querySelector('[role="tabpanel"]') || main.querySelector(".min-w-0.flex-1 > *:last-child") || main.firstElementChild);
  const pb = panel ? panel.getBoundingClientRect() : null;
  const wide = main ? [...main.querySelectorAll("*")].filter((e) => window.__v(e) && e.getBoundingClientRect().right > b.right + 1 && !window.__scroller(e)).slice(0, 5).map((e) => ({ tag: e.tagName.toLowerCase(), cls: (e.className || "").toString().slice(0, 60), right: Math.round(e.getBoundingClientRect().right), text: txt(e).slice(0, 40) })) : [];
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
    mainBox: b ? { left: Math.round(b.left), width: Math.round(b.width), right: Math.round(b.right) } : null,
    contentBox: pb ? { tag: panel.tagName.toLowerCase(), role: panel.getAttribute("role"), left: Math.round(pb.left), width: Math.round(pb.width) } : null,
    beyondMainRight: wide,
    tables: main ? [...main.querySelectorAll("table")].filter(window.__v).map(window.__tableMeasure) : [],
    emptyStates: main ? window.__empties(main) : [],
    lowContrast: main ? window.__contrast(main) : [],
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
    "h1 page-wide": await c(p.locator("h1")),
    [`tablist '${L.TABLIST}' exact`]: await c(p.getByRole("tablist", { name: L.TABLIST, exact: true })),
    "packs.spec:130 body 'Something went wrong'": await c(p.getByText("Something went wrong")),
    "status notice 'You don't have access to'": await c(p.getByRole("status").filter({ hasText: "You don't have access to" })),
  };
  if (surface === "templates" || surface === "legacy-redirect" || surface === "no-tab") {
    out["packs.spec:127 locator('h1').filter({hasText:'Templates'}) (strict toBeVisible)"] = await c(p.locator("h1").filter({ hasText: "Templates" }));
    out["template-management.spec:51 heading h1 /^Templates/ (strict toBeVisible)"] = await c(p.getByRole("heading", { level: 1, name: /^Templates/ }));
    out["template-management.spec:79 heading h2 'Engagement Letter' (strict toBeVisible)"] = await c(p.getByRole("heading", { level: 2, name: "Engagement Letter" }));
    const row = p.getByTestId("template-list-item").filter({ has: p.getByRole("link", { name: "Engagement Letter — General", exact: true }) });
    out["template-management.spec:57-60/80-81 template-list-item filter link exact 'Engagement Letter — General' (toHaveCount 1)"] = await c(row);
    out["template-management.spec:82/83 that row contains 'Platform' / 'Active'"] = (await row.count()) === 1 ? `${(await row.innerText()).includes("Platform")}/${(await row.innerText()).includes("Active")}` : null;
    out["template-management.spec:125 that row getByRole('button') count (.last() = actions trigger)"] = (await row.count()) === 1 ? await c(row.getByRole("button")) : null;
    out["template-management.spec:84/90 link 'New Template' (strict toBeVisible / click)"] = await c(p.getByRole("link", { name: "New Template" }));
    out["template-management.spec:85 testid format-filter (strict toBeVisible)"] = await c(p.getByTestId("format-filter"));
    out["packs.spec:132 link exact 'Power of Attorney' (strict toBeVisible)"] = await c(p.getByRole("link", { name: "Power of Attorney", exact: true }));
    out["testid template-list-item (rows)"] = await c(p.getByTestId("template-list-item"));
    out["button 'Upload Word Template'"] = await c(p.getByRole("button", { name: "Upload Word Template" }));
    out["heading h2 'Branding'"] = await c(p.getByRole("heading", { level: 2, name: "Branding" }));
    out["button 'Save Branding'"] = await c(p.getByRole("button", { name: "Save Branding" }));
    out["#brand-color / #footer-text"] = `${await c(p.locator("#brand-color"))}/${await c(p.locator("#footer-text"))}`;
    out["img 'Organization logo' (logo delete X shown only with a logo)"] = await c(p.getByRole("img", { name: "Organization logo" }));
  }
  if (surface === "clauses") {
    out["heading h1 'Clause Library'"] = await c(p.getByRole("heading", { level: 1, name: "Clause Library" }));
    out["placeholder 'Search clauses...'"] = await c(p.getByPlaceholder("Search clauses..."));
    out["combobox 'Filter by category'"] = await c(p.getByRole("combobox", { name: "Filter by category" }));
    out["button 'New Clause'"] = await c(p.getByRole("button", { name: "New Clause" }));
    out["testid clause-category"] = await c(p.getByTestId("clause-category"));
    out["testid clause-row"] = await c(p.getByTestId("clause-row"));
    out["button 'Clause actions'"] = await c(p.getByRole("button", { name: "Clause actions" }));
    out["button 'Expand clause'"] = await c(p.getByRole("button", { name: "Expand clause" }));
  }
  if (surface === "acceptance") {
    out["heading h1 'Document Acceptance'"] = await c(p.getByRole("heading", { level: 1, name: "Document Acceptance" }));
    out["#acceptance-expiry-days"] = await c(p.locator("#acceptance-expiry-days"));
    out["button 'Save Settings'"] = await c(p.getByRole("button", { name: "Save Settings" }));
    out["text 'You do not have permission to manage acceptance settings.'"] = await c(p.getByText(/You do not have permission to manage acceptance settings/));
  }
  if (surface === "email" || surface === "email-delivery-log") {
    out["heading h1 'Email'"] = await c(p.getByRole("heading", { level: 1, name: "Email", exact: true }));
    out["tab 'Overview' / 'Delivery Log'"] = `${await c(p.getByRole("tab", { name: "Overview" }))}/${await c(p.getByRole("tab", { name: "Delivery Log" }))}`;
    out["heading 'Delivery Statistics'"] = await c(p.getByRole("heading", { name: "Delivery Statistics" }));
    out["textbox 'From date' / 'To date'"] = `${await c(p.getByLabel("From date"))}/${await c(p.getByLabel("To date"))}`;
    out["button 'Previous' / 'Next'"] = `${await c(p.getByRole("button", { name: "Previous" }))}/${await c(p.getByRole("button", { name: "Next" }))}`;
    out["table rows"] = await c(main.locator("table tbody tr"));
    out["text 'You do not have permission to manage email settings.'"] = await c(p.getByText(/You do not have permission to manage email settings/));
  }
  if (surface === "tpl-new") {
    out["template-management.spec:92 heading h1 'New Template' (strict toBeVisible)"] = await c(p.getByRole("heading", { level: 1, name: "New Template" }));
    out["template-management.spec:97 getByLabel('Category', exact) (value)"] = (await c(p.getByLabel("Category", { exact: true }))) + ((await c(p.getByLabel("Category", { exact: true }))) === 1 ? ` value=${await p.getByLabel("Category", { exact: true }).inputValue()}` : "");
    out["template-management.spec:98 getByLabel('Primary Entity Type') (value)"] = (await c(p.getByLabel("Primary Entity Type"))) + ((await c(p.getByLabel("Primary Entity Type"))) === 1 ? ` value=${await p.getByLabel("Primary Entity Type").inputValue()}` : "");
    out["template-management.spec:102 getByLabel('Name', exact)"] = await c(p.getByLabel("Name", { exact: true }));
    out["template-management.spec:105 button 'Create Template'"] = await c(p.getByRole("button", { name: "Create Template" }));
    out["link 'Templates' (back)"] = await c(main.getByRole("link", { name: "Templates", exact: true }));
  }
  if (surface === "tpl-edit-platform") {
    out[`editor h1 (TemplateEditorClient.tsx:205, readOnly only) named '${EDIT_TEMPLATE}'`] = await c(p.getByRole("heading", { level: 1, name: EDIT_TEMPLATE }));
    out["template-management.spec:112/135/150 getByLabel('Template name') (editable only)"] = await c(p.getByLabel("Template name"));
    out["template-management.spec:136/154 button 'Save' exact (editable only)"] = await c(p.getByRole("button", { name: "Save", exact: true }));
    out["template-management.spec:152 .ProseMirror first contenteditable"] = (await c(p.locator(".ProseMirror"))) ? await p.locator(".ProseMirror").first().getAttribute("contenteditable") : 0;
    out["button 'Preview with data'"] = await c(p.getByRole("button", { name: "Preview with data" }));
    out["testid field-pack-status"] = await c(p.getByTestId("field-pack-status"));
    out["link 'Templates' (back)"] = await c(main.getByRole("link", { name: "Templates", exact: true }));
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
  const tpl = L.list((await L.apiGet(await L.idpToken("alice"), "/api/templates")).json).find((t) => t.name === EDIT_TEMPLATE);
  R.editTemplate = { id: tpl.id, name: tpl.name, source: tpl.source, format: tpl.format };
  const SURF = L.TABS.map((t) => [t, `/settings/documents?tab=${t}`]);
  SURF.splice(4, 0, ["email-delivery-log", "/settings/documents?tab=email"]);
  SURF.push(["tpl-new", "/settings/documents/templates/new"], ["tpl-edit-platform", `/settings/documents/templates/${tpl.id}/edit`], ["no-tab", "/settings/documents"], ["legacy-redirect", "/settings/templates"]);
  const ONLY1024L = ["no-tab", "legacy-redirect"];
  for (const [w, h] of VIEWPORTS)
    for (const theme of ["light", "dark"]) {
      const { ctx, p } = await page(browser, S, w, h, theme);
      for (const [sk, url] of SURF) {
        if (ONLY1024L.includes(sk) && !(w === 1024 && theme === "light")) continue;
        const v = `${w}-${theme}`;
        lab.label = `${KEY}|${sk}|${v}`;
        const resp = await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
        await p.waitForLoadState("networkidle").catch(() => {});
        await p.waitForTimeout(600);
        pagesOpened++;
        if (sk === "email-delivery-log") {
          const t = p.getByRole("tab", { name: "Delivery Log" });
          if ((await t.count()) === 1) { await t.click(); await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800); }
        }
        if (sk === "email") await p.getByText("Loading stats...").waitFor({ state: "hidden", timeout: 10000 }).catch(() => {});
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
        if (w === 1024 && theme === "light") R.selCompat[sk] = await selCompat(p, sk);
      }
      await ctx.close();
    }
  await browser.close();
  R.console = { pagesOpened, errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, messages: consoleSink };
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, `persona-${KEY}.json`), JSON.stringify(R));
  console.log(KEY, "tiles", tiles.length, "pages", pagesOpened, "console", consoleSink.length, "caps", JSON.stringify(R.caps));
  for (const [sk] of SURF) { const f = R.surfaces[sk] || {}; const v = R.variants[sk]["1024-light"]; console.log(sk, JSON.stringify({ gate: f.gateOutcome, url: v.url, h1: v.h1, notice: v.notice, tabs: v.tabs.join("|"), rail: v.rail && v.rail.join("|"), main: v.mainBox && v.mainBox.width, content: v.contentBox && v.contentBox.width, hOverflow: v.hOverflow, dark1024: R.variants[sk]["1024-dark"] && R.variants[sk]["1024-dark"].hOverflow, beyond: v.beyondMainRight.length, tables1024: v.tables.map((x) => `${x.scroller ? x.scroller.clientWidth + "/" + x.scroller.scrollWidth : "-"} cut:${x.cols.filter((c) => c.cutPx).map((c) => c.h + "=" + c.cutPx).join(",")}`), lowC: v.lowContrast.slice(0, 3).map((x) => `${x.text}:${x.ratio}`), lowCdark: R.variants[sk]["1024-dark"] ? R.variants[sk]["1024-dark"].lowContrast.slice(0, 3).map((x) => `${x.text}:${x.ratio}`) : null })); }
  const cByLabel = {}; for (const m of consoleSink) cByLabel[m.label.split("|")[1]] = (cByLabel[m.label.split("|")[1]] || 0) + 1;
  console.log("console by surface", JSON.stringify(cByLabel));
})();
