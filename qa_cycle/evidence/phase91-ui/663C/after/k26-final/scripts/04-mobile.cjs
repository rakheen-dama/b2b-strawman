// 663C K-26 final — acceptance point 5 (NEW): below `sm` only the last crumb shows. 390×844, Alice/Carol, light + dark, on the
// served build. Same login (661B common.cjs, required unedited), theme, reduced motion and timezone as 03-capture.cjs; page ids
// come from ../../k26-recheck/measurements.json (same stack data; 03 re-discovers the same ids). Read-only: navigation only.
// Output: ../$K26_MOBILE_OUT (default mobile.json; run once on the c4d1f7675 bundle as mobile-c4d1f7675.json), one line.
const path = require("path");
const fs = require("fs");
const C = require("../../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const P = JSON.parse(fs.readFileSync(path.resolve(OUT, "../k26-recheck/measurements.json"), "utf8")).pages;
const NAME = process.env.K26_MOBILE_OUT || "mobile.json";

const MEASURE = () => {
  const nav = document.querySelector('nav[aria-label="Breadcrumb"]');
  if (!nav) return { error: "no breadcrumb nav" };
  const r = (e) => e.getBoundingClientRect();
  const kids = [...nav.children];
  const shown = kids.filter((e) => getComputedStyle(e).display !== "none");
  const last = kids[kids.length - 1];
  const label = [...last.children].filter((c) => c.matches("a,span")).pop();
  const chevron = last.querySelector("svg");
  return {
    viewport: [window.innerWidth, window.innerHeight],
    crumbs: kids.length,
    shown: shown.length,
    shownIsLast: shown.length === 1 && shown[0] === last,
    lastText: label.textContent.trim().slice(0, 40),
    lastLabelW: +r(label).width.toFixed(1),
    lastTruncated: label.scrollWidth > label.clientWidth,
    lastChevronDisplay: chevron ? getComputedStyle(chevron).display : null,
    navH: Math.round(r(nav).height),
    navRight: +r(nav).right.toFixed(1),
  };
};

(async () => {
  const browser = await C.chromium.launch();
  const S = { alice: await C.loginState(browser, "alice"), carol: await C.loginState(browser, "carol") };
  const pages = [
    ["alice", "fee-note", `${BASE}/invoices/${P.feeNote.id}`],
    ["carol", "matter", `${C.APP}${P.carolMatter}`],
    ["alice", "template-edit", `${BASE}/settings/documents/templates/${P.template.id}/edit`],
    ["alice", "checklist-edit", `${C.APP}${P.checklist}/edit`],
  ];
  const out = {};
  for (const [who, pk, url] of pages)
    for (const theme of ["light", "dark"]) {
      const key = `${who}-${pk}-390-${theme}`;
      const ctx = await C.newCtx(browser, S[who], { width: 390, height: 844, theme, extra: EXTRA });
      const p = await ctx.newPage();
      await p.goto(url, { waitUntil: "load" });
      await p.waitForLoadState("networkidle").catch(() => {});
      await p.waitForTimeout(400);
      const m = await p.evaluate(MEASURE);
      m.pass = !m.error && m.shownIsLast && m.lastChevronDisplay === "none" && m.navH === 20 && m.navRight <= 390;
      out[key] = m;
      console.log(key, JSON.stringify(m));
      await ctx.close();
    }
  await browser.close();
  const vals = Object.values(out);
  fs.writeFileSync(path.join(OUT, NAME), JSON.stringify({ capturedAt: new Date().toISOString(), captures: out, summary: { captures: vals.length, pass: vals.filter((m) => m.pass).length } }) + "\n");
})();
