// 662A kinks walk — shared helpers. Re-uses the committed 661B helpers by require (never copied):
// ../../661B/walk/scripts/common.cjs (login, contexts, theme/rail, sheet) and audit.cjs (lightAudit).
// Adds: route/tab inventory, triage-group map, the per-capture probe (overflow/truncation, narrow th,
// empty-state sizing, dark slabs, rail fold), a resumable capture() writing RAW/<set>/<slug>__<who>-<w>-<theme>.{png,json},
// the dialog harvest (open-only, Escape, never click inside), a time budget, and a 662A contact sheet.
// Raw output lives out of git in <worktree>/.tmp/662A-walk-raw (.gitignore:128 ".tmp/").
const path = require("path");
const fs = require("fs");
const C = require("../../661B/walk/scripts/common.cjs");
const { lightAudit } = require("../../661B/walk/scripts/audit.cjs");

const { W, APP, ORG } = C;
const RAW = process.env.RAW || path.join(W, ".tmp/662A-walk-raw");
const EV = path.join(W, "qa_cycle/evidence/phase91-ui/kinks");
const BASE = `${APP}/org/${ORG}`;
const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
const T0 = Date.now();
const BUDGET_S = +(process.env.BUDGET_S || 480);
const overBudget = () => (Date.now() - T0) / 1000 > BUDGET_S;
const elapsed = () => Math.round((Date.now() - T0) / 1000);
const VP = { 390: { width: 390, height: 844 }, 768: { width: 768, height: 1024 }, 1024: { width: 1024, height: 768 }, 1440: { width: 1440, height: 900 } };
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const SHOT_CLIP = 3200; // raw page clip (661B rule); sheets show the first 2 700 px
fs.mkdirSync(RAW, { recursive: true });

// ---- Route inventory (as walk-light.cjs: file system + settings-nav.ts by indentation).
const ROOT = path.join(W, "frontend/app/(app)/org/[slug]");
function pages(dir, pre = "") {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.isDirectory()) out.push(...pages(path.join(dir, e.name), `${pre}/${e.name}`));
    else if (e.name === "page.tsx") out.push(pre || "/");
  }
  return out;
}
const ROUTES = pages(ROOT)
  .map((r) => r.replace(/\/\([^)]+\)/g, ""))
  .sort();
const TABS = [];
{
  let fam = null;
  for (const line of fs.readFileSync(path.join(W, "frontend/lib/settings-nav.ts"), "utf8").split("\n")) {
    const f = line.match(/^ {4}id: "([\w-]+)",/);
    if (f) fam = f[1];
    const t = line.match(/^ {8}id: "([\w-]+)",/);
    if (t && fam) TABS.push([fam, t[1]]);
  }
}
const FAMILY_HOSTS = new Set(["/settings/firm", "/settings/matters", "/settings/documents", "/settings/fees", "/settings/connections"]);
const isSub = (r) => r.startsWith("/settings/") && !FAMILY_HOSTS.has(r);
const PA_ROUTES = ["/platform-admin/access-requests", "/platform-admin/billing", "/platform-admin/demo"];

// ---- Triage group per route pattern (brief § Triage groups).
function groupOf(route) {
  const r = route.split("?")[0];
  if (r.startsWith("/platform-admin")) return "t3";
  if (r.startsWith("/settings")) {
    const m = route.match(/^\/settings\/(\w+)/);
    const fam = m ? m[1] : "firm";
    return fam === "firm" || fam === "matters" || r === "/settings" ? "t4" : "t5";
  }
  if (/^\/(dashboard|matters|clients|conflict-check|team|legal\/adverse-parties)/.test(r)) return "t1";
  if (/^\/(trust-accounting|my-work|diary|notifications)/.test(r)) return "t3";
  return "t2";
}
// Slug: 661B rule on the route PATTERN (so owner and member share a slug): "/" stripped, ?tab= → --, [x] → x.
const slugOf = (route) =>
  route
    .replace(/^\//, "")
    .replace(/\?tab=/, "--")
    .replace(/\[([^\]]+)\]/g, "$1")
    .replace(/[/?=&]+/g, "-") || "root";
const fileSlug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 48) || "x";

// ---- Persona storage state (re-used across runs; refreshed when a page lands on /mock-login).
async function personaState(browser, who, force = false) {
  const f = path.join(RAW, `state-${who}.json`);
  if (!force && fs.existsSync(f)) return JSON.parse(fs.readFileSync(f, "utf8"));
  const st = await C.loginState(browser, who);
  fs.writeFileSync(f, JSON.stringify(st));
  return st;
}
async function ctxFor(browser, who, vp, theme, collapsed = false) {
  const st = await personaState(browser, who);
  return C.newCtx(browser, st, { ...VP[vp], theme, collapsed, extra: EXTRA });
}
// Console sink per page: errors, warnings, hydration text, page errors; label set by the caller.
function sinkFor(page) {
  const s = { label: "", msgs: [] };
  page.on("console", (m) => {
    if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(m.text())) s.msgs.push({ label: s.label, type: m.type(), text: m.text().slice(0, 300) });
  });
  page.on("pageerror", (e) => s.msgs.push({ label: s.label, type: "pageerror", text: String(e).slice(0, 300) }));
  return s;
}
async function sinkProbe(page, s) {
  const before = s.msgs.length;
  s.label = "probe";
  await page.evaluate(() => console.warn("662A console-sink probe"));
  await page.waitForTimeout(300);
  const ok = s.msgs.slice(before).some((m) => m.text.includes("662A console-sink probe"));
  s.msgs.splice(before); // the probe itself is not a finding
  return ok;
}

// ---- In-page probe (page.evaluate(pageProbe, opts)): § Per-capture probe of the brief.
function pageProbe(opts) {
  const o = opts || {};
  const main = document.querySelector("#main-content") || document.body;
  const vw = document.documentElement.clientWidth;
  const vh = window.innerHeight;
  const cut = (s, n = 60) => (s || "").trim().replace(/\s+/g, " ").slice(0, n);
  const d = (el) => {
    const r = el.getBoundingClientRect();
    const cls = (typeof el.className === "string" ? el.className : el.getAttribute("class") || "").slice(0, 90);
    return { tag: el.tagName.toLowerCase(), testid: el.getAttribute("data-testid") || el.closest("[data-testid]")?.getAttribute("data-testid") || null, slot: el.getAttribute("data-slot") || null, cls, text: cut(el.innerText || el.textContent, 50), x: Math.round(r.left), w: Math.round(r.width), right: Math.round(r.right) };
  };
  const vis = (el) => {
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) return false;
    const cs = getComputedStyle(el);
    return cs.visibility !== "hidden" && cs.display !== "none";
  };
  const clipping = (cs) => /hidden|clip|auto|scroll/.test(cs.overflowX);
  const clipped = [];
  const scrollers = [];
  const pastCard = [];
  const pastViewport = [];
  const narrowTh = [];
  const all = [...main.querySelectorAll("*")];
  for (const el of all) {
    if (el.closest("svg")) continue;
    if (!vis(el)) continue;
    const r0 = el.getBoundingClientRect();
    if (r0.width <= 2 || r0.height <= 2) continue; // sr-only (1×1) text is not a visible clip
    const cs = getComputedStyle(el);
    if (el.scrollWidth > el.clientWidth + 1 && el.clientWidth > 0) {
      const rec = { ...d(el), scrollW: el.scrollWidth, clientW: el.clientWidth };
      if (/auto|scroll/.test(cs.overflowX)) scrollers.push(rec);
      else if (/hidden|clip/.test(cs.overflowX) || cs.textOverflow === "ellipsis") clipped.push(rec);
    }
    const r = el.getBoundingClientRect();
    const hasText = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim());
    if (!hasText && !/^(td|th|button|a|input|select|textarea|img)$/i.test(el.tagName)) continue;
    // ancestors up to the card/viewport: a clipping/scrolling ancestor means the overflow is contained
    let contained = false;
    let card = null;
    for (let n = el.parentElement; n && n !== document.body; n = n.parentElement) {
      if (!card && n.getAttribute("data-slot") === "card") card = n;
      if (!card && clipping(getComputedStyle(n))) {
        contained = true;
        break;
      }
      if (card) break;
    }
    if (!contained && card) {
      const cr = card.getBoundingClientRect();
      if (r.right > cr.right + 1 && r.width > 0) pastCard.push({ ...d(el), cardRight: Math.round(cr.right), by: Math.round(r.right - cr.right) });
    }
    if (r.right > vw + 1) {
      let inScroll = false;
      for (let n = el.parentElement; n && n !== document.body; n = n.parentElement) if (clipping(getComputedStyle(n))) { inScroll = true; break; }
      if (!inScroll) pastViewport.push({ ...d(el), by: Math.round(r.right - vw) });
    }
  }
  for (const th of main.querySelectorAll("th")) {
    if (!vis(th)) continue;
    const r = th.getBoundingClientRect();
    if (r.width < 40 && cut(th.innerText).length) narrowTh.push({ ...d(th), links: [...th.closest("table").querySelectorAll("tbody tr td:nth-child(" + (th.cellIndex + 1) + ") a")].slice(0, 6).map((a) => Math.round(a.getBoundingClientRect().width)) });
  }
  const empties = [...document.querySelectorAll('[data-slot="empty-state"]')].filter(vis).map((e) => {
    const r = e.getBoundingClientRect();
    return { variant: e.getAttribute("data-variant"), h: Math.round(r.height), w: Math.round(r.width), text: cut(e.innerText, 60), inCard: !!e.closest('[data-slot="card"]'), inSection: !!e.closest("section"), inSheet: !!e.closest('[data-slot="sheet-content"]'), inTabpanel: !!e.closest('[role="tabpanel"]'), inDialog: !!e.closest('[role="dialog"]') };
  });
  // Dark slabs: own opaque background with luminance > 0.5 on a dark page (Phase 90 D-2 pattern).
  const slabs = [];
  if (document.documentElement.classList.contains("dark")) {
    const cv = document.createElement("canvas");
    cv.width = cv.height = 1;
    const cx = cv.getContext("2d", { willReadFrequently: true });
    const rgba = (c) => {
      cx.clearRect(0, 0, 1, 1);
      cx.fillStyle = "rgba(0,0,0,0)";
      cx.fillStyle = c;
      cx.fillRect(0, 0, 1, 1);
      const x = cx.getImageData(0, 0, 1, 1).data;
      return [x[0], x[1], x[2], x[3] / 255];
    };
    const L = (c) => {
      const f = (v) => ((v /= 255) <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
      return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]);
    };
    for (const el of document.body.querySelectorAll("*")) {
      if (el.closest("svg, img, video, canvas, nextjs-portal")) continue;
      const r = el.getBoundingClientRect();
      if (r.width < 24 || r.height < 24 || !vis(el)) continue;
      const bg = rgba(getComputedStyle(el).backgroundColor);
      if (bg[3] >= 0.9 && L(bg) > 0.5) slabs.push({ ...d(el), h: Math.round(r.height), bg: "#" + bg.slice(0, 3).map((v) => v.toString(16).padStart(2, "0")).join("") });
      if (slabs.length >= 30) break;
    }
  }
  // Rail fold (K-05): rail menu buttons below the bottom of the rail's visible area.
  const rail = document.querySelector(o.sheet ? '[data-mobile="true"][data-sidebar="sidebar"]' : '[data-slot="sidebar-container"] [data-sidebar="sidebar"], [data-sidebar="sidebar"]:not([data-mobile="true"])');
  let fold = null;
  if (rail && vis(rail)) {
    const content = rail.querySelector('[data-sidebar="content"]') || rail;
    const cb = Math.min(content.getBoundingClientRect().bottom, vh);
    const items = [...rail.querySelectorAll('[data-sidebar="menu-button"]')].filter(vis);
    const below = items.filter((b) => b.getBoundingClientRect().bottom > cb + 1);
    const last = items[items.length - 1];
    fold = { items: items.length, contentBottom: Math.round(cb), lastBottom: last ? Math.round(last.getBoundingClientRect().bottom) : null, lastLabel: last ? cut(last.innerText || last.getAttribute("aria-label"), 30) : null, hidden: below.map((b) => cut(b.innerText || b.getAttribute("aria-label"), 30)), contentScrollH: content.scrollHeight, contentClientH: content.clientHeight };
  }
  const top = (a, n = 10) => a.slice(0, n);
  return {
    url: location.pathname.replace(/^\/org\/[^/]+/, "") + location.search,
    h1: [...document.querySelectorAll("#main-content h1")].map((e) => cut(e.textContent)),
    mains: document.querySelectorAll("main#main-content").length,
    selectedTab: [...document.querySelectorAll('#main-content [role="tab"][aria-selected="true"]')].map((t) => cut(t.textContent, 40)).join(" | ") || null,
    dark: document.documentElement.classList.contains("dark"),
    docHeight: document.documentElement.scrollHeight,
    docOverflowX: document.documentElement.scrollWidth > vw + 1 ? document.documentElement.scrollWidth - vw : 0,
    errorText: /Something went wrong|could not be found|Application error|Unhandled Runtime Error|page could not be found/i.test(main.innerText.slice(0, 3000)) ? cut(main.innerText, 200) : null,
    overflow: { clippedN: clipped.length, scrollN: scrollers.length, pastCardN: pastCard.length, pastViewportN: pastViewport.length, narrowThN: narrowTh.length, clipped: top(clipped), scroll: top(scrollers), pastCard: top(pastCard), pastViewport: top(pastViewport), narrowTh: top(narrowTh) },
    empty: empties,
    darkSlabsN: slabs.length,
    darkSlabs: top(slabs),
    fold,
  };
}
// lightAudit trimmed to what the triage needs (lowText ≤ 10 etc.).
async function audit(page) {
  const a = await page.evaluate(lightAudit);
  return { firmLightA: a.firmLightA, textCount: a.textCount, lowTextCount: a.lowTextCount, lowTextPreCount: a.lowTextPreCount, minRatio: a.minRatio, schemeTextCount: a.schemeTextCount, lowText: a.lowText.slice(0, 10), lowIcons: a.lowIcons.slice(0, 10), faint: a.faint.slice(0, 10), sameColour: a.lowText.filter((x) => x.ratio < 1.2).slice(0, 10), docOverflow: a.docOverflow };
}

// ---- Resumable capture: RAW/<set>/<name>__<who>-<w>-<theme>.{png,json}. Skips when the PNG and JSON
// exist, or when the JSON records a final skip / two failed attempts.
const shotBase = (set, name, who, vp, theme) => path.join(RAW, set, `${name}__${who}-${vp}-${theme}`);
function doneAlready(base) {
  if (fs.existsSync(base + ".json")) {
    const j = JSON.parse(fs.readFileSync(base + ".json", "utf8"));
    if (j.skip || (j.attempts || 0) >= 2 || (fs.existsSync(base + ".png") && !j.error)) return true;
  }
  return false;
}
class Relogin extends Error {}
async function capture(page, sink, spec) {
  const { set, route, who, vp, theme } = spec;
  const name = spec.name || slugOf(route);
  const base = shotBase(set, name, who, vp, theme);
  fs.mkdirSync(path.dirname(base), { recursive: true });
  if (doneAlready(base)) return { skipped: true };
  const prev = fs.existsSync(base + ".json") ? JSON.parse(fs.readFileSync(base + ".json", "utf8")) : {};
  const rec = { set, group: spec.group || groupOf(route), route, name, persona: who, vp, theme, requested: spec.url, rawFile: C.rel(base + ".png"), attempts: (prev.attempts || 0) + 1, capturedAt: new Date().toISOString() };
  sink.label = `${set}/${name}__${who}-${vp}-${theme}`;
  const before = sink.msgs.length;
  try {
    const target = spec.url.startsWith("http") ? spec.url : BASE + spec.url;
    const resp = await page.goto(target, { waitUntil: "load", timeout: 90000 });
    rec.status = resp ? resp.status() : null;
    if (/\/mock-login|\/sign-in/.test(page.url())) throw new Relogin("landed on login");
    await page.locator(spec.waitSel || "#main-content h1").first().waitFor({ timeout: spec.waitTimeout || 20000 }).catch(() => (rec.waitMiss = spec.waitSel || "#main-content h1"));
    await page.mouse.move(1, 1);
    await page.waitForTimeout(spec.settle || 1800);
    if (spec.before) rec.before = await spec.before(page);
    if (spec.check) {
      const skip = await spec.check(page);
      if (skip) {
        rec.skip = skip;
        fs.writeFileSync(base + ".json", JSON.stringify(rec));
        return rec;
      }
    }
    const h = await page.evaluate(() => document.documentElement.scrollHeight);
    const width = VP[vp].width;
    if (spec.viewportOnly) await page.screenshot({ path: base + ".png" });
    else await page.screenshot({ path: base + ".png", fullPage: true, clip: { x: 0, y: 0, width, height: Math.min(h, SHOT_CLIP) } });
    Object.assign(rec, await page.evaluate(pageProbe, { sheet: !!spec.sheetProbe }));
    if (!spec.noAudit) rec.audit = await audit(page).catch((e) => ({ error: String(e).slice(0, 120) }));
    if (spec.after) rec.after = await spec.after(page);
    delete rec.error;
  } catch (e) {
    if (e instanceof Relogin) throw e;
    rec.error = String(e).slice(0, 300);
  }
  await page.waitForTimeout(150);
  const mine = sink.msgs.slice(before);
  rec.console = { e: mine.filter((m) => m.type !== "warning").length, w: mine.filter((m) => m.type === "warning").length, h: mine.filter((m) => /hydrat/i.test(m.text)).length, msgs: mine.slice(0, 8) };
  fs.writeFileSync(base + ".json", JSON.stringify(rec));
  return rec;
}

// ---- Dialog harvest (brief § Dialog harvesting rule): triggers inside #main-content with
// aria-haspopup="dialog", not destructive by name; click → wait ≤ 3 s → viewport shot → probe → Escape.
const DESTRUCTIVE = /delete|remove|archive|anonymi|reverse|void|cancel|reject|disconnect|uninstall|deactivate|write off|close matter|reopen|discard|revoke|unlink|transfer lead/i;
const NOT_DIALOG = /^help\b|^help:|toggle|command k|pick a date|^more info/i;
async function listTriggers(page, scope) {
  return page.evaluate((scope) => {
    const root = document.querySelector(scope) || document.querySelector("#main-content");
    if (!root) return [];
    const out = [];
    let i = 0;
    for (const b of root.querySelectorAll('[aria-haspopup="dialog"]')) {
      const r = b.getBoundingClientRect();
      const cs = getComputedStyle(b);
      b.setAttribute("data-662a-trg", String(i));
      const name = (b.getAttribute("aria-label") || b.innerText || b.getAttribute("title") || "").trim().replace(/\s+/g, " ").slice(0, 60);
      out.push({ i, name, visible: r.width > 0 && r.height > 0 && cs.visibility !== "hidden", disabled: b.disabled || b.getAttribute("aria-disabled") === "true" });
      i++;
    }
    return out;
  }, scope);
}
function dialogProbe() {
  const dl = [...document.querySelectorAll('[role="dialog"],[role="alertdialog"]')].filter((e) => e.getBoundingClientRect().width > 0).pop();
  if (!dl) return null;
  const r = dl.getBoundingClientRect();
  const t = dl.querySelector('[data-slot$="title"]') || dl.querySelector("h1,h2,h3");
  const a = document.activeElement;
  const btns = [...dl.querySelectorAll("button")].filter((b) => b.getBoundingClientRect().width > 0).map((b) => {
    const br = b.getBoundingClientRect();
    return { text: (b.innerText || b.getAttribute("aria-label") || "").trim().slice(0, 30), offscreen: br.bottom > innerHeight || br.top < 0 };
  });
  return {
    role: dl.getAttribute("role"), slot: dl.getAttribute("data-slot"), title: t ? t.textContent.trim().slice(0, 80) : null,
    rect: { x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height), bottom: Math.round(r.bottom) },
    fitsViewport: r.top >= 0 && r.bottom <= innerHeight + 1 && r.right <= innerWidth + 1, innerScroll: dl.scrollHeight > dl.clientHeight + 1, overflowX: dl.scrollWidth > dl.clientWidth + 1,
    offscreenButtons: btns.filter((b) => b.offscreen).map((b) => b.text), buttons: btns.slice(0, 12).map((b) => b.text),
    focus: a ? { tag: a.tagName.toLowerCase(), text: (a.innerText || a.getAttribute("aria-label") || a.getAttribute("name") || a.id || "").trim().slice(0, 40), inDialog: dl.contains(a) } : null,
    empties: [...dl.querySelectorAll('[data-slot="empty-state"]')].map((e) => ({ variant: e.getAttribute("data-variant"), h: Math.round(e.getBoundingClientRect().height) })),
    dark: document.documentElement.classList.contains("dark"),
  };
}
async function harvestDialogs(page, sink, { pageKey, foundOn, who, vp, theme, group, max = 15, scope = "#main-content" }) {
  const out = [];
  const trig = (await listTriggers(page, scope)).filter((t) => t.visible && !t.disabled && t.name);
  const seen = new Set();
  for (const t of trig) {
    if (overBudget()) return { out, incomplete: true };
    if (DESTRUCTIVE.test(t.name) || NOT_DIALOG.test(t.name) || seen.has(t.name)) continue;
    seen.add(t.name);
    if (seen.size > max) break;
    const base = shotBase("S7", `${pageKey}__dlg-${fileSlug(t.name)}`, who, vp, theme);
    fs.mkdirSync(path.dirname(base), { recursive: true });
    if (doneAlready(base)) continue;
    const rec = { set: "S7", group, route: foundOn, pageKey, trigger: t.name, persona: who, vp, theme, rawFile: C.rel(base + ".png"), capturedAt: new Date().toISOString() };
    sink.label = `S7/${pageKey}__dlg-${fileSlug(t.name)}__${who}-${vp}-${theme}`;
    const before = sink.msgs.length;
    try {
      const all = await listTriggers(page, scope); // re-mark (DOM may have re-rendered)
      const m = all.find((x) => x.name === t.name && x.visible);
      if (!m) throw new Error("trigger gone");
      await page.locator(`[data-662a-trg="${m.i}"]`).click({ timeout: 5000 });
      await page.locator('[role="dialog"]:visible, [role="alertdialog"]:visible').last().waitFor({ timeout: 3000 });
      await page.waitForTimeout(600);
      await page.screenshot({ path: base + ".png" });
      rec.dialog = await page.evaluate(dialogProbe);
      await page.keyboard.press("Escape");
      await page.waitForTimeout(500);
      let still = await page.locator('[role="dialog"]:visible, [role="alertdialog"]:visible').count();
      if (still) {
        await page.keyboard.press("Escape");
        await page.waitForTimeout(500);
        still = await page.locator('[role="dialog"]:visible, [role="alertdialog"]:visible').count();
      }
      rec.closedByEscape = !still;
      rec.focusAfterClose = await page.evaluate((i) => {
        const a = document.activeElement;
        return { isTrigger: !!a && a.getAttribute("data-662a-trg") === String(i), tag: a ? a.tagName.toLowerCase() : null, text: a ? (a.innerText || a.getAttribute("aria-label") || "").trim().slice(0, 40) : null };
      }, m.i);
      if (still) {
        await page.reload({ waitUntil: "load" });
        await page.waitForTimeout(1500);
      }
    } catch (e) {
      rec.error = String(e).slice(0, 200);
      rec.attempts = 2; // a dialog that did not open is recorded, not retried
      await page.keyboard.press("Escape").catch(() => {});
    }
    const mine = sink.msgs.slice(before);
    rec.console = { e: mine.filter((x) => x.type !== "warning").length, w: mine.filter((x) => x.type === "warning").length, h: mine.filter((x) => /hydrat/i.test(x.text)).length, msgs: mine.slice(0, 5) };
    fs.writeFileSync(base + ".json", JSON.stringify(rec));
    out.push(rec);
  }
  return { out };
}

// ---- 662A contact sheet (Chromium-rendered HTML grid, as common.cjs sheet()): rows of tiles, each tile
// scaled to tileW, page clipped at 2 700 px, a dashed red line at the viewport fold, a caption per tile
// and "not captured: <reason>" cells. rows = [{ label, tiles: [{ cap, file, vpW, vpH, tileW, miss }] }].
const esc = (s) => String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;");
async function sheet662(browser, outFile, title, rows, { clipH = 2700 } = {}) {
  const tile = (t) => {
    const k = t.tileW / t.vpW;
    if (!t.file || !fs.existsSync(t.file)) return `<figure style="width:${t.tileW}px"><figcaption>${esc(t.cap)}</figcaption><div class="miss">not captured: ${esc(t.miss || "no file")}</div></figure>`;
    const b64 = fs.readFileSync(t.file).toString("base64");
    return `<figure style="width:${t.tileW}px"><figcaption>${esc(t.cap)}</figcaption><div class="frame" style="max-height:${Math.round(clipH * k)}px"><img src="data:image/png;base64,${b64}" style="width:${t.tileW}px"><div class="fold" style="top:${Math.round(t.vpH * k)}px"></div></div></figure>`;
  };
  const html = `<html><body style="margin:0;background:#777;font:12px/1.25 -apple-system,Helvetica,sans-serif"><h2 style="margin:6px 8px;color:#fff;font-size:15px">${esc(title)}</h2>
  ${rows.map((r) => `<div class="row"><div class="lab">${esc(r.label)}</div>${r.tiles.map(tile).join("")}</div>`).join("")}
  <style>.row{display:flex;gap:6px;padding:4px 8px;align-items:flex-start}.lab{writing-mode:vertical-rl;transform:rotate(180deg);color:#fff;font-weight:700;font-size:13px}
  figure{margin:0;background:#fff}figcaption{padding:3px 5px;font-weight:600;overflow-wrap:anywhere;font-size:11px}
  .frame{position:relative;overflow:hidden}.frame img{display:block}.fold{position:absolute;left:0;right:0;border-top:2px dashed #e00}
  .miss{padding:30px 8px;color:#a00;font-weight:600}</style></body></html>`;
  const p = await browser.newPage({ viewport: { width: 800, height: 600 } });
  await p.setContent(html, { waitUntil: "load" });
  fs.mkdirSync(path.dirname(outFile), { recursive: true });
  await p.screenshot({ path: outFile, fullPage: true });
  await p.close();
}

// ---- Worker pool: run async jobs with a concurrency limit.
async function pool(items, n, fn) {
  const q = items.slice();
  const run = async () => {
    while (q.length) await fn(q.shift());
  };
  await Promise.all(Array.from({ length: Math.min(n, items.length) }, run));
}
const readJson = (f, d = null) => (fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, "utf8")) : d);
const ids = () => readJson(path.join(RAW, "ids.json"));

module.exports = { ...C, C, lightAudit, RAW, EV, BASE, UUID, BUDGET_S, overBudget, elapsed, VP, EXTRA, ROUTES, TABS, FAMILY_HOSTS, isSub, PA_ROUTES, groupOf, slugOf, fileSlug, personaState, ctxFor, sinkFor, sinkProbe, pageProbe, audit, shotBase, doneAlready, Relogin, capture, listTriggers, dialogProbe, harvestDialogs, DESTRUCTIVE, sheet662, pool, readJson, ids };
