// 661B light-scheme audit, run in the page (page.evaluate(lightAudit)). Adapted from Phase 90's
// pageAudit() (phase90-ui/matrix/scripts/common-kc.cjs): effective WCAG 2.x ratio of every visible
// text owner against its alpha-blended ancestor background (colours normalised through a 1×1 canvas),
// plus icon-only controls (non-text, 3:1) and form fields whose border and fill both vanish.
//
// Attribution (the BLOCKS-661B bar is "the scheme itself made a page illegible"): every element is
// measured twice in one synchronous pass on the SAME nodes —
//   A = as rendered (firm light scheme on);
//   B = counterfactual: the `data-app-shell` attribute removed from the shell wrapper, so
//       :root:not(.dark):has([data-app-shell="firm"]) and the light-inset rules stop matching and
//       the page computes today's pre-scheme light tokens (dark rail, #f4f6f8 canvas, teal-600
//       accent, slate-950 primary). The attribute is restored before returning; transitions are
//       disabled for the measurement so computed colours are final values.
// Nothing is screenshotted in state B.
function lightAudit() {
  const cv = document.createElement("canvas");
  cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const cache = new Map();
  const rgba = (c) => {
    if (cache.has(c)) return cache.get(c);
    cx.clearRect(0, 0, 1, 1);
    cx.fillStyle = "rgba(0,0,0,0)";
    cx.fillStyle = c;
    cx.fillRect(0, 0, 1, 1);
    const d = cx.getImageData(0, 0, 1, 1).data;
    const r = [d[0], d[1], d[2], d[3] / 255];
    cache.set(c, r);
    return r;
  };
  const blend = (top, under) => {
    const a = top[3];
    return [top[0] * a + under[0] * (1 - a), top[1] * a + under[1] * (1 - a), top[2] * a + under[2] * (1 - a), 1];
  };
  const lum = (c) => {
    const f = (v) => {
      v /= 255;
      return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]);
  };
  const ratio = (a, b) => {
    const x = lum(a);
    const y = lum(b);
    return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
  };
  const hex = (c) => "#" + c.slice(0, 3).map((v) => Math.round(v).toString(16).padStart(2, "0")).join("");
  const desc = (el) => {
    const cls = (typeof el.className === "string" ? el.className : el.getAttribute("class") || "").slice(0, 140);
    return { tag: el.tagName.toLowerCase(), cls, testid: el.closest("[data-testid]")?.getAttribute("data-testid") || null, slot: el.closest("[data-slot]")?.getAttribute("data-slot") || null, inRail: !!el.closest('[data-slot="sidebar"],[data-sidebar="sidebar"]') };
  };
  const visible = (el) => {
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    const cs = getComputedStyle(el);
    if (cs.visibility === "hidden" || cs.display === "none") return false;
    for (let n = el; n && n.nodeType === 1; n = n.parentElement) if (parseFloat(getComputedStyle(n).opacity) < 0.05) return false;
    return true;
  };
  const opacityOf = (el) => {
    let op = 1;
    for (let n = el; n && n.nodeType === 1; n = n.parentElement) op *= parseFloat(getComputedStyle(n).opacity);
    return op;
  };

  // Collect the nodes once.
  const texts = [];
  const icons = [];
  const fields = [];
  for (const el of document.body.querySelectorAll("*")) {
    if (el.closest("svg, script, style, noscript, nextjs-portal")) continue;
    const own = [...el.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim().length > 0);
    if (own && visible(el)) texts.push(el);
  }
  for (const el of document.body.querySelectorAll("button, a, [role=button], [role=tab], [role=menuitem]")) {
    if (el.closest("nextjs-portal")) continue;
    if ((el.innerText || "").trim().length > 0) continue;
    const svg = el.querySelector("svg");
    if (svg && visible(el) && visible(svg)) icons.push([el, svg]);
  }
  for (const el of document.body.querySelectorAll("input:not([type=hidden]):not([type=checkbox]):not([type=radio]), textarea, select, [role=combobox], button[role=checkbox], [data-slot=select-trigger]"))
    if (visible(el)) fields.push(el);

  const measure = () => {
    const memo = new WeakMap();
    const bgOf = (el) => {
      if (!el || el.nodeType !== 1) return [255, 255, 255, 1];
      if (memo.has(el)) return memo.get(el);
      const own = rgba(getComputedStyle(el).backgroundColor);
      const r = own[3] >= 0.999 ? own : own[3] > 0 ? blend(own, bgOf(el.parentElement)) : bgOf(el.parentElement);
      memo.set(el, r);
      return r;
    };
    const t = texts.map((el) => {
      const cs = getComputedStyle(el);
      const fg = rgba(cs.color);
      const bg = bgOf(el);
      const eff = blend([fg[0], fg[1], fg[2], fg[3] * opacityOf(el)], bg);
      return { r: ratio(eff, bg), fg: hex(eff), bg: hex(bg) };
    });
    const ic = icons.map(([el, svg]) => {
      const fg = rgba(getComputedStyle(svg).color);
      const bg = bgOf(el);
      const eff = blend([fg[0], fg[1], fg[2], fg[3] * opacityOf(svg)], bg);
      return { r: ratio(eff, bg), fg: hex(eff), bg: hex(bg) };
    });
    const fi = fields.map((el) => {
      const cs = getComputedStyle(el);
      const parentBg = bgOf(el.parentElement);
      const ownBg = bgOf(el);
      const bw = parseFloat(cs.borderTopWidth) + parseFloat(cs.borderBottomWidth);
      const bc = blend(rgba(cs.borderTopColor), ownBg);
      return { border: bw > 0 ? ratio(bc, parentBg) : 1, fill: ratio(ownBg, parentBg), bc: hex(bc), pbg: hex(parentBg) };
    });
    return { t, ic, fi };
  };

  const noTrans = document.createElement("style");
  noTrans.textContent = "*,*::before,*::after{transition:none!important;animation:none!important}";
  document.head.appendChild(noTrans);
  const shells = [...document.querySelectorAll("[data-app-shell]")];
  const shellVals = shells.map((e) => e.getAttribute("data-app-shell"));
  const firmLightA = document.documentElement.matches(':root:not(.dark):has([data-app-shell="firm"])');
  const A = measure();
  shells.forEach((e) => e.removeAttribute("data-app-shell"));
  const firmLightB = document.documentElement.matches(':root:not(.dark):has([data-app-shell="firm"])');
  const B = measure();
  shells.forEach((e, i) => e.setAttribute("data-app-shell", shellVals[i]));
  noTrans.remove();

  const lowText = [];
  texts.forEach((el, i) => {
    const a = A.t[i];
    const b = B.t[i];
    const cs = getComputedStyle(el);
    const px = parseFloat(cs.fontSize);
    const bold = parseInt(cs.fontWeight, 10) >= 700;
    const floor = px >= 24 || (bold && px >= 18.66) ? 3 : 4.5;
    if (a.r < floor || b.r < floor) {
      const disabled = !!el.closest("[disabled], [aria-disabled='true'], [data-disabled]");
      lowText.push({ text: el.textContent.trim().replace(/\s+/g, " ").slice(0, 50), floor, ratio: +a.r.toFixed(2), fg: a.fg, bg: a.bg, preScheme: +b.r.toFixed(2), preFg: b.fg, preBg: b.bg, delta: +(a.r - b.r).toFixed(2), disabled, ...desc(el) });
    }
  });
  lowText.sort((x, y) => x.ratio - y.ratio);
  const lowIcons = [];
  icons.forEach(([el], i) => {
    const a = A.ic[i];
    const b = B.ic[i];
    if (a.r < 3 || b.r < 3) lowIcons.push({ label: el.getAttribute("aria-label") || el.getAttribute("title") || null, ratio: +a.r.toFixed(2), fg: a.fg, bg: a.bg, preScheme: +b.r.toFixed(2), disabled: !!el.closest("[disabled], [aria-disabled='true'], [data-disabled]"), ...desc(el) });
  });
  const faint = [];
  fields.forEach((el, i) => {
    const a = A.fi[i];
    const b = B.fi[i];
    const fa = a.border < 1.3 && a.fill < 1.15;
    const fb = b.border < 1.3 && b.fill < 1.15;
    if (fa || fb) faint.push({ name: el.getAttribute("name") || el.getAttribute("aria-label") || el.getAttribute("placeholder") || el.id || null, border: +a.border.toFixed(2), fill: +a.fill.toFixed(2), preBorder: +b.border.toFixed(2), preFill: +b.fill.toFixed(2), faintNow: fa, faintPre: fb, ...desc(el) });
  });
  // Scheme-attributable: below its floor now but at or above it pre-scheme.
  const schemeText = lowText.filter((x) => x.ratio < x.floor && x.preScheme >= x.floor);
  const schemeIllegible = lowText.filter((x) => x.ratio < 3 && x.preScheme >= 3 && !x.disabled);
  const schemeIcons = lowIcons.filter((x) => x.ratio < 3 && x.preScheme >= 3);
  const schemeFaint = faint.filter((x) => x.faintNow && !x.faintPre);
  const vw = document.documentElement.clientWidth;
  return {
    firmLightA,
    firmLightB,
    shellAttr: shells.length,
    textCount: texts.length,
    iconCount: icons.length,
    fieldCount: fields.length,
    lowTextCount: lowText.filter((x) => x.ratio < x.floor).length,
    lowTextPreCount: lowText.filter((x) => x.preScheme < x.floor).length,
    minRatio: texts.length ? +Math.min(...A.t.map((x) => x.r)).toFixed(2) : null,
    schemeTextCount: schemeText.length,
    schemeIllegibleCount: schemeIllegible.length,
    schemeIconCount: schemeIcons.length,
    schemeFaintCount: schemeFaint.length,
    schemeText: schemeText.slice(0, 15),
    schemeIcons: schemeIcons.slice(0, 10),
    schemeFaint: schemeFaint.slice(0, 10),
    lowText: lowText.slice(0, 20),
    lowIcons: lowIcons.slice(0, 10),
    faint: faint.slice(0, 10),
    docOverflow: document.documentElement.scrollWidth > vw + 1 ? document.documentElement.scrollWidth - vw : 0,
  };
}
module.exports = { lightAudit };
