// 644B OBS helpers — Keycloak dev stack (frontend :3000 → gateway :8443 → Keycloak :8180).
// Node @playwright/test chromium (headless). Playwright MCP's Chrome profile is held by the user's
// live session ("Browser is already in use"), so this is the accepted library-script fallback
// (memory env_playwright_mcp_profile_lock_obs; orchestrator ruling R4).
const path = require("path");
const fs = require("fs");
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.resolve(__dirname, "../../../../../frontend")] }));

const BASE = "http://localhost:3000";
const SLUG = "mathebula-partners";
const ORG = `${BASE}/org/${SLUG}`;
const MATRIX = path.resolve(__dirname, "..");
const USERS = {
  thandi: ["thandi@mathebula-test.local", "SecureP@ss1"],
  bob: ["bob@mathebula-test.local", "SecureP@ss2"],
  carol: ["carol@mathebula-test.local", "SecureP@ss3"],
};

// One Keycloak login per persona; theme and viewport are switched in place afterwards.
async function session(browser, who, { theme = "light", viewport = { width: 1440, height: 900 }, consoleSink } = {}) {
  const ctx = await browser.newContext({ viewport, colorScheme: theme, reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" });
  await ctx.addInitScript((t) => { try { if (!localStorage.getItem("kazi-theme")) localStorage.setItem("kazi-theme", t); } catch {} }, theme);
  const page = await ctx.newPage();
  const state = { who, theme, width: viewport.width, label: "login" };
  if (consoleSink) {
    page.on("console", (m) => {
      const t = m.text();
      if (m.type() === "error" || m.type() === "warning" || /hydrat/i.test(t))
        consoleSink.push({ label: state.label, who, theme: state.theme, width: state.width, url: page.url().replace(BASE, ""), type: m.type(), text: t.slice(0, 400) });
    });
    page.on("pageerror", (e) => consoleSink.push({ label: state.label, who, theme: state.theme, width: state.width, url: page.url().replace(BASE, ""), type: "pageerror", text: String(e).slice(0, 400) }));
  }
  const [email, password] = USERS[who];
  await page.goto(`${ORG}/dashboard`, { waitUntil: "load" });
  await page.waitForURL(/\/realms\//, { timeout: 30000 });
  await page.locator("#username").fill(email);
  if (await page.locator("#password").isVisible()) {
    await page.locator("#password").fill(password);
  } else {
    await page.locator('input[name="login"], button[type="submit"]').first().click();
    await page.locator("#password").waitFor({ timeout: 15000 });
    await page.locator("#password").fill(password);
  }
  await page.locator('input[name="login"], button[type="submit"]').first().click();
  await page.waitForURL((u) => !u.pathname.includes("/realms/"), { timeout: 60000 });
  await page.locator("h1").first().waitFor({ timeout: 120000 });
  return { ctx, page, state };
}

async function setTheme(s, theme) {
  await s.page.evaluate((t) => localStorage.setItem("kazi-theme", t), theme);
  await s.page.emulateMedia({ colorScheme: theme, reducedMotion: "reduce" });
  s.state.theme = theme;
}
async function setWidth(s, width) {
  await s.page.setViewportSize(width === 1024 ? { width: 1024, height: 768 } : { width: 1440, height: 900 });
  s.state.width = width;
}
async function open(s, url, waitSel, label) {
  if (label) s.state.label = label;
  const resp = await s.page.goto(url, { waitUntil: "load", timeout: 180000 });
  await s.page.locator("h1").first().waitFor({ timeout: 120000 }).catch(() => {});
  if (waitSel) await s.page.locator(waitSel).first().waitFor({ timeout: 60000 }).catch(() => {});
  // Park the pointer top-left: after the Keycloak submit click it rests mid-viewport and pins a
  // chart tooltip open (seen in the first stack-proof dry run).
  await s.page.mouse.move(1, 1);
  await s.page.waitForTimeout(3000);
  return resp ? resp.status() : null;
}

// In-page audit: effective-contrast of every visible text node owner (WCAG ratio against the
// alpha-blended ancestor background), light surfaces on a dark page, near-invisible form-field
// borders, and horizontal overflow. Colours are normalised through a 1×1 canvas (Chromium reports
// Tailwind v4 colours as oklch()/lab()).
function pageAudit() {
  const cv = document.createElement("canvas"); cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const cache = new Map();
  const rgba = (c) => {
    if (cache.has(c)) return cache.get(c);
    cx.clearRect(0, 0, 1, 1); cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1);
    const d = cx.getImageData(0, 0, 1, 1).data; const r = [d[0], d[1], d[2], d[3] / 255]; cache.set(c, r); return r;
  };
  const dark = document.documentElement.classList.contains("dark");
  const blend = (top, under) => { const a = top[3]; return [top[0] * a + under[0] * (1 - a), top[1] * a + under[1] * (1 - a), top[2] * a + under[2] * (1 - a), 1]; };
  const memo = new WeakMap();
  const bgOf = (el) => {
    if (!el || el.nodeType !== 1) return dark ? [9, 9, 11, 1] : [255, 255, 255, 1];
    if (memo.has(el)) return memo.get(el);
    const own = rgba(getComputedStyle(el).backgroundColor);
    const r = own[3] >= 0.999 ? own : own[3] > 0 ? blend(own, bgOf(el.parentElement)) : bgOf(el.parentElement);
    memo.set(el, r); return r;
  };
  const lum = (c) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]); };
  const ratio = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
  const hex = (c) => "#" + c.slice(0, 3).map((v) => Math.round(v).toString(16).padStart(2, "0")).join("");
  const desc = (el) => {
    const cls = (typeof el.className === "string" ? el.className : el.getAttribute("class") || "").slice(0, 160);
    const tid = el.closest("[data-testid]")?.getAttribute("data-testid") || null;
    const slot = el.closest("[data-slot]")?.getAttribute("data-slot") || null;
    return { tag: el.tagName.toLowerCase(), cls, testid: tid, slot };
  };
  const visible = (el) => {
    const r = el.getBoundingClientRect(); if (r.width < 2 || r.height < 2) return false;
    const cs = getComputedStyle(el); if (cs.visibility === "hidden" || cs.display === "none") return false;
    for (let n = el; n && n.nodeType === 1; n = n.parentElement) { const s = getComputedStyle(n); if (parseFloat(s.opacity) < 0.05) return false; if (n.getAttribute("aria-hidden") === "true" && n.tagName !== "svg") { /* still painted */ } }
    return true;
  };
  const lowText = []; let textCount = 0;
  for (const el of document.body.querySelectorAll("*")) {
    if (el.closest("svg, script, style, noscript, nextjs-portal")) continue;
    const own = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim().length > 0);
    if (!own || !visible(el)) continue;
    textCount++;
    const cs = getComputedStyle(el);
    let fg = rgba(cs.color); const bg = bgOf(el);
    let op = 1; for (let n = el; n && n.nodeType === 1; n = n.parentElement) op *= parseFloat(getComputedStyle(n).opacity);
    const fgA = [fg[0], fg[1], fg[2], fg[3] * op]; const eff = blend(fgA, bg);
    const r = ratio(eff, bg);
    if (r < 3) {
      const disabled = !!el.closest("[disabled], [aria-disabled='true'], [data-disabled]");
      lowText.push({ text: el.textContent.trim().replace(/\s+/g, " ").slice(0, 50), ratio: +r.toFixed(2), fg: hex(eff), bg: hex(bg), disabled, ...desc(el) });
    }
  }
  lowText.sort((a, b) => a.ratio - b.ratio);
  // Light surfaces on a dark page (white card / panel / row in dark mode).
  const lightSurfaces = [];
  if (dark) {
    for (const el of document.body.querySelectorAll("*")) {
      if (el.closest("svg, nextjs-portal")) continue;
      const own = rgba(getComputedStyle(el).backgroundColor); if (own[3] < 0.05) continue;
      const r = el.getBoundingClientRect(); const area = r.width * r.height; if (area < 1500 || !visible(el)) continue;
      const b = bgOf(el); const L = lum(b); if (L < 0.45) continue;
      lightSurfaces.push({ bg: hex(b), lum: +L.toFixed(2), w: Math.round(r.width), h: Math.round(r.height), text: (el.innerText || "").trim().replace(/\s+/g, " ").slice(0, 50), ...desc(el) });
    }
    lightSurfaces.sort((a, b) => b.w * b.h - a.w * a.h);
  }
  // Form fields whose border and fill both vanish into the surrounding surface.
  const faintFields = []; let fieldCount = 0;
  for (const el of document.body.querySelectorAll("input:not([type=hidden]):not([type=checkbox]):not([type=radio]), textarea, select, [role=combobox], button[role=checkbox], [data-slot=select-trigger]")) {
    if (!visible(el)) continue; fieldCount++;
    const cs = getComputedStyle(el); const parentBg = bgOf(el.parentElement); const ownBg = bgOf(el);
    const bw = parseFloat(cs.borderTopWidth) + parseFloat(cs.borderBottomWidth);
    const bc = blend(rgba(cs.borderTopColor), ownBg);
    const borderR = bw > 0 ? ratio(bc, parentBg) : 1; const fillR = ratio(ownBg, parentBg);
    if (borderR < 1.3 && fillR < 1.15) faintFields.push({ borderRatio: +borderR.toFixed(2), fillRatio: +fillR.toFixed(2), border: hex(bc), parentBg: hex(parentBg), name: el.getAttribute("name") || el.getAttribute("aria-label") || el.getAttribute("placeholder") || el.id || null, ...desc(el) });
  }
  // Horizontal overflow: document-level, and text that sits beyond the right viewport edge
  // without a scrolling/clipping ancestor (= clipped content).
  const vw = document.documentElement.clientWidth;
  const scrollers = [];
  for (const el of document.body.querySelectorAll("*")) {
    const cs = getComputedStyle(el);
    if ((cs.overflowX === "auto" || cs.overflowX === "scroll") && el.scrollWidth > el.clientWidth + 2 && el.clientWidth > 50)
      scrollers.push({ sw: el.scrollWidth, cw: el.clientWidth, ...desc(el) });
  }
  const offscreen = [];
  for (const el of document.body.querySelectorAll("*")) {
    const own = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim().length > 0);
    if (!own) continue; const r = el.getBoundingClientRect(); if (r.width < 2 || r.right <= vw + 1) continue;
    let clipped = false; for (let n = el.parentElement; n && n !== document.body; n = n.parentElement) { const ox = getComputedStyle(n).overflowX; if (ox !== "visible") { clipped = true; break; } }
    if (!clipped && visible(el)) offscreen.push({ text: el.textContent.trim().slice(0, 40), right: Math.round(r.right), ...desc(el) });
  }
  return {
    dark, textCount, lowTextCount: lowText.length, lowText: lowText.slice(0, 25), lightSurfaces: lightSurfaces.slice(0, 12), lightSurfaceCount: lightSurfaces.length,
    fieldCount, faintFields: faintFields.slice(0, 12), faintFieldCount: faintFields.length,
    docOverflow: document.documentElement.scrollWidth > vw + 1 ? document.documentElement.scrollWidth - vw : 0, scrollers: scrollers.slice(0, 8), offscreen: offscreen.slice(0, 8),
  };
}

// Screenshot + facts that prove what was rendered (theme really applied, no error overlay, audit).
async function shot(s, dir, file, { fullPage = false, audit = true } = {}) {
  await s.page.screenshot({ path: path.join(dir, file), fullPage });
  const facts = await s.page.evaluate(() => ({
    url: location.pathname + location.search,
    h1: document.querySelector("h1")?.textContent?.trim() ?? null,
    dark: document.documentElement.classList.contains("dark"),
    htmlClass: document.documentElement.className,
    stored: (() => { try { return localStorage.getItem("kazi-theme"); } catch { return null; } })(),
    bodyBg: getComputedStyle(document.body).backgroundColor,
    errorOverlay: !!document.querySelector("nextjs-portal") && !!document.querySelector("nextjs-portal")?.shadowRoot?.querySelector("[data-nextjs-dialog], [data-nextjs-toast-errors]"),
    notFound: /could not be found|404|Something went wrong/i.test(document.body.innerText.slice(0, 3000)),
  }));
  const a = audit ? await s.page.evaluate(pageAudit) : null;
  return { file, ...facts, audit: a };
}
function writeJson(dir, file, obj) { fs.writeFileSync(path.join(dir, file), JSON.stringify(obj)); } // compact: merge-hook 20k-line limit
module.exports = { chromium, BASE, SLUG, ORG, MATRIX, USERS, session, setTheme, setWidth, open, shot, pageAudit, writeJson };
