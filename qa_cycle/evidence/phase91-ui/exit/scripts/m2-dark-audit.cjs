// 670A 670.6 in-page dark audit. Copy of Phase 90 `pageAudit()` (qa_cycle/evidence/phase90-ui/matrix/
// scripts/common-kc.cjs, unedited there) adapted for the 670.6 rubric (brief M-5):
//  * every visible text owner: colour alpha-composited over its ancestor background on a 1×1 sRGB
//    canvas (S7 gotcha 14), WCAG ratio; thresholds: 4.5:1 normal text, 3:1 large text (≥ 24 px, or
//    ≥ 18.66 px and weight ≥ 700). < 3:1 (any size) = "illegible" candidate; 3:1 … AA = "degraded".
//    Disabled controls are recorded separately (excluded, as Phase 90).
//  * light surfaces (composited luminance ≥ 0.45, area ≥ 1500 px²) on the dark page; plus small light
//    discs/chips (area 256–1500 px², luminance ≥ 0.6) for K-58-type avatars.
//  * faint form fields (Phase 90 rule: border < 1.3:1 against the surround AND fill < 1.15:1).
//  * destructive ghost/icon buttons (class contains "text-destructive"): the computed colour that the
//    icon inherits (currentColor) vs. its background, plus hue/saturation, for F-665C-14 / F-665D-12.
//  * accent-filled controls (computed background teal: hue 160–190°, saturation ≥ 0.6) — text ratio,
//    for K-21 / F-666A-9.
//  * card borders ([data-slot=card]) ratio against the page surround (K-13, informational).
// Every item carries its viewport box so a defect crop can be cut from the raw tile.
function darkAudit() {
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
  const bodyBg = () => { const b = rgba(getComputedStyle(document.body).backgroundColor); return b[3] > 0 ? b : dark ? [9, 9, 11, 1] : [255, 255, 255, 1]; };
  const bgOf = (el) => {
    if (!el || el.nodeType !== 1) return bodyBg();
    if (memo.has(el)) return memo.get(el);
    const own = rgba(getComputedStyle(el).backgroundColor);
    const r = own[3] >= 0.999 ? own : own[3] > 0 ? blend(own, bgOf(el.parentElement)) : bgOf(el.parentElement);
    memo.set(el, r); return r;
  };
  const lum = (c) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]); };
  const ratio = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
  const hex = (c) => "#" + c.slice(0, 3).map((v) => Math.round(v).toString(16).padStart(2, "0")).join("");
  const hsl = (c) => { const [r, g, b] = c.slice(0, 3).map((v) => v / 255); const mx = Math.max(r, g, b), mn = Math.min(r, g, b); const l = (mx + mn) / 2; if (mx === mn) return [0, 0, l]; const d = mx - mn; const s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn); let h = mx === r ? (g - b) / d + (g < b ? 6 : 0) : mx === g ? (b - r) / d + 2 : (r - g) / d + 4; return [Math.round(h * 60), +s.toFixed(2), +l.toFixed(2)]; };
  const box = (el) => { const r = el.getBoundingClientRect(); return [Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height)]; };
  const desc = (el) => {
    const cls = (typeof el.className === "string" ? el.className : el.getAttribute("class") || "").slice(0, 200);
    const tid = el.closest("[data-testid]")?.getAttribute("data-testid") || null;
    const slot = el.closest("[data-slot]")?.getAttribute("data-slot") || null;
    const zone = el.closest("main#main-content") ? "main" : el.closest('[data-slot="sidebar"],[data-sidebar]') ? "sidebar" : el.closest("header") ? "header" : el.closest('[role="dialog"]') ? "dialog" : "other";
    return { tag: el.tagName.toLowerCase(), cls, testid: tid, slot, zone, box: box(el) };
  };
  const visible = (el) => {
    const r = el.getBoundingClientRect(); if (r.width < 2 || r.height < 2) return false;
    const cs = getComputedStyle(el); if (cs.visibility === "hidden" || cs.display === "none") return false;
    for (let n = el; n && n.nodeType === 1; n = n.parentElement) { const s = getComputedStyle(n); if (parseFloat(s.opacity) < 0.05) return false; }
    return true;
  };
  const isDisabled = (el) => !!el.closest("[disabled], [aria-disabled='true'], [data-disabled]");
  const fails = []; const disabledLow = []; let textCount = 0;
  for (const el of document.body.querySelectorAll("*")) {
    if (el.closest("svg, script, style, noscript, nextjs-portal")) continue;
    const own = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim().length > 0);
    if (!own || !visible(el)) continue;
    textCount++;
    const cs = getComputedStyle(el);
    const fg = rgba(cs.color); const bg = bgOf(el);
    let op = 1; for (let n = el; n && n.nodeType === 1; n = n.parentElement) op *= parseFloat(getComputedStyle(n).opacity);
    const eff = blend([fg[0], fg[1], fg[2], fg[3] * op], bg);
    const r = ratio(eff, bg);
    const px = parseFloat(cs.fontSize); const wt = parseInt(cs.fontWeight, 10) || 400;
    const large = px >= 24 || (px >= 18.66 && wt >= 700);
    const need = large ? 3 : 4.5;
    if (r < need) {
      const it = { text: el.textContent.trim().replace(/\s+/g, " ").slice(0, 50), ratio: +r.toFixed(2), need, px, fg: hex(eff), bg: hex(bg), sev: r < 3 ? "illegible" : "degraded", ...desc(el) };
      (isDisabled(el) ? disabledLow : fails).push(it);
    }
  }
  fails.sort((a, b) => a.ratio - b.ratio);
  const lightSurfaces = []; const lightDiscs = [];
  if (dark) {
    for (const el of document.body.querySelectorAll("*")) {
      if (el.closest("svg, nextjs-portal")) continue;
      const own = rgba(getComputedStyle(el).backgroundColor); if (own[3] < 0.05) continue;
      const r = el.getBoundingClientRect(); const area = r.width * r.height; if (area < 256 || !visible(el)) continue;
      const b = bgOf(el); const L = lum(b);
      if (area >= 1500 && L >= 0.45) lightSurfaces.push({ bg: hex(b), lum: +L.toFixed(2), w: Math.round(r.width), h: Math.round(r.height), text: (el.innerText || "").trim().replace(/\s+/g, " ").slice(0, 50), ...desc(el) });
      else if (area < 1500 && L >= 0.6 && !el.closest("button, a, [role=button]") && /rounded-full/.test(String(el.className))) lightDiscs.push({ bg: hex(b), lum: +L.toFixed(2), w: Math.round(r.width), h: Math.round(r.height), text: (el.innerText || "").trim().slice(0, 12), ...desc(el) });
    }
    lightSurfaces.sort((a, b) => b.w * b.h - a.w * a.h);
  }
  const faintFields = []; let fieldCount = 0;
  for (const el of document.body.querySelectorAll("input:not([type=hidden]):not([type=checkbox]):not([type=radio]), textarea, select, [role=combobox], button[role=checkbox], [data-slot=select-trigger]")) {
    if (!visible(el)) continue; fieldCount++;
    const cs = getComputedStyle(el); const parentBg = bgOf(el.parentElement); const ownBg = bgOf(el);
    const bw = parseFloat(cs.borderTopWidth) + parseFloat(cs.borderBottomWidth);
    const bc = blend(rgba(cs.borderTopColor), ownBg);
    const borderR = bw > 0 ? ratio(bc, parentBg) : 1; const fillR = ratio(ownBg, parentBg);
    if (borderR < 1.3 && fillR < 1.15) faintFields.push({ borderRatio: +borderR.toFixed(2), fillRatio: +fillR.toFixed(2), border: hex(bc), parentBg: hex(parentBg), name: el.getAttribute("name") || el.getAttribute("aria-label") || el.getAttribute("placeholder") || el.id || null, ...desc(el) });
  }
  const destructive = [];
  for (const el of document.querySelectorAll("button, a")) {
    const c = typeof el.className === "string" ? el.className : "";
    if (!/(^|\s)(hover:)?text-destructive/.test(c) || !visible(el)) continue;
    const col = blend(rgba(getComputedStyle(el).color), bgOf(el)); const bg = bgOf(el); const h = hsl(col);
    destructive.push({ label: el.getAttribute("aria-label") || el.textContent.trim().slice(0, 30), color: hex(col), bg: hex(bg), ratio: +ratio(col, bg).toFixed(2), hsl: h, reddish: h[1] >= 0.35 && (h[0] <= 20 || h[0] >= 340), disabled: isDisabled(el), ...desc(el) });
  }
  const accent = [];
  for (const el of document.querySelectorAll("button, a, [role=button], [data-slot=badge], span")) {
    if (!visible(el)) continue;
    const own = rgba(getComputedStyle(el).backgroundColor); if (own[3] < 0.9) continue;
    const h = hsl(own); if (!(h[0] >= 160 && h[0] <= 190 && h[1] >= 0.6 && h[2] >= 0.25)) continue;
    const t = (el.innerText || "").trim(); if (!t) continue;
    const col = blend(rgba(getComputedStyle(el).color), own);
    accent.push({ text: t.replace(/\s+/g, " ").slice(0, 30), color: hex(col), bg: hex(own), ratio: +ratio(col, own).toFixed(2), disabled: isDisabled(el), ...desc(el) });
  }
  const cards = [];
  for (const el of [...document.querySelectorAll('main [data-slot="card"]')].slice(0, 3)) {
    if (!visible(el)) continue; const cs = getComputedStyle(el);
    const bc = blend(rgba(cs.borderTopColor), bgOf(el.parentElement));
    cards.push({ border: hex(bc), surround: hex(bgOf(el.parentElement)), ratio: +ratio(bc, bgOf(el.parentElement)).toFixed(2), fill: hex(bgOf(el)) });
  }
  const vw = document.documentElement.clientWidth;
  return {
    dark, textCount, failCount: fails.length, illegibleCount: fails.filter((f) => f.sev === "illegible").length, fails: fails.slice(0, 40),
    disabledLowCount: disabledLow.length, lightSurfaceCount: lightSurfaces.length, lightSurfaces: lightSurfaces.slice(0, 10), lightDiscCount: lightDiscs.length, lightDiscs: lightDiscs.slice(0, 8),
    fieldCount, faintFieldCount: faintFields.length, faintFields: faintFields.slice(0, 10), destructive: destructive.slice(0, 10), accent: accent.slice(0, 10), cards,
    docOverflow: document.documentElement.scrollWidth > vw + 1 ? document.documentElement.scrollWidth - vw : 0,
  };
}
module.exports = { darkAudit };
