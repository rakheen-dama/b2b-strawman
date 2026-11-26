// 663C K-26 final — in-browser PROTOTYPE of breadcrumb shrink rules (NEW). Runs against the served c4d1f7675 bundle and applies
// each candidate rule set as INLINE styles (flex-shrink / min-width) on the org link, the middle crumb wrappers and the last
// crumb wrapper, then measures every crumb. Arbitrary Tailwind classes are not in the built CSS, so inline styles stand in.
// Same pages, ids, viewport (1024×768), theme (localStorage `kazi-theme`), rail (cookie `sidebar_state`), reduced motion and
// timezone as ../../k26-recheck/scripts/01-capture.cjs (which stays unedited); login through 661B common.cjs (required unedited).
// Read-only: navigation only. No form input, no submits, no SQL, no REST writes. Output: ../prototype.json (one line).
const path = require("path");
const fs = require("fs");
const C = require("../../../../661B/walk/scripts/common.cjs");

const OUT = path.resolve(__dirname, "..");
const BASE = `${C.APP}/org/${C.ORG}`;
const EXTRA = { reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" };
const RECHECK = JSON.parse(fs.readFileSync(path.resolve(OUT, "../k26-recheck/measurements.json"), "utf8"));
const THREE = ["fee-note", "trust-transactions", "matter", "client-detail"];

// Candidate = inline styles per role. null = leave the c4d1f7675 classes alone. Iterations are recorded in order.
const CANDIDATES = {
  c0_baseline_c4d1f7675: null,
  c1_spec_naive: { org: { flexShrink: "10000", minWidth: "0px" }, mid: { flexShrink: "1", minWidth: "0px" }, last: { flexShrink: "1", minWidth: "0px" } },
  c2_bleed_1e3: { org: { flexShrink: "1000", minWidth: "1.5rem" }, mid: { flexShrink: "1", minWidth: "0px" }, last: { flexShrink: "1", minWidth: "3rem" } },
  c3_org0: { org: { flexShrink: "1000000", minWidth: "0px" }, mid: { flexShrink: "100", minWidth: "0px" }, last: { flexShrink: "1", minWidth: "2.75rem" } },
  c4_org24_m100_l44: { org: { flexShrink: "1000000", minWidth: "1.5rem" }, mid: { flexShrink: "100", minWidth: "0px" }, last: { flexShrink: "1", minWidth: "2.75rem" } },
  c5_org24_m100_l48: { org: { flexShrink: "1000000", minWidth: "1.5rem" }, mid: { flexShrink: "100", minWidth: "0px" }, last: { flexShrink: "1", minWidth: "3rem" } },
  c6_org24_m10_l48: { org: { flexShrink: "100000", minWidth: "1.5rem" }, mid: { flexShrink: "10", minWidth: "0px" }, last: { flexShrink: "1", minWidth: "3rem" } },
  c7_org32_m100_l48: { org: { flexShrink: "1000000", minWidth: "2rem" }, mid: { flexShrink: "100", minWidth: "0px" }, last: { flexShrink: "1", minWidth: "3rem" } },
  c8_org24_m1e4_l48: { org: { flexShrink: "100000000", minWidth: "1.5rem" }, mid: { flexShrink: "10000", minWidth: "0px" }, last: { flexShrink: "1", minWidth: "3rem" } },
};

const APPLY = (cand) => {
  const nav = document.querySelector('nav[aria-label="Breadcrumb"]');
  const kids = [...nav.children];
  for (const e of kids) e.removeAttribute("style");
  if (!cand) return;
  const set = (e, s) => Object.assign(e.style, s);
  set(kids[0], cand.org);
  kids.slice(1, -1).forEach((e) => set(e, cand.mid));
  set(kids[kids.length - 1], cand.last);
  void nav.offsetWidth;
};
const MEASURE = () => {
  const nav = document.querySelector('nav[aria-label="Breadcrumb"]');
  const r = (e) => e.getBoundingClientRect();
  const header = nav.closest("header");
  const kids = [...nav.children].filter((e) => getComputedStyle(e).display !== "none");
  const textW = (el) => { const rg = document.createRange(); rg.selectNodeContents(el); return +rg.getBoundingClientRect().width.toFixed(1); };
  const crumbs = kids.map((e, i) => {
    const label = i === 0 ? e : [...e.children].filter((c) => c.matches("a,span")).pop() || e;
    const b = r(e);
    return {
      text: label.textContent.trim().slice(0, 40), role: i === 0 ? "org" : i === kids.length - 1 ? "last" : "middle",
      w: +b.width.toFixed(1), labelW: +r(label).width.toFixed(1), naturalW: textW(label),
      truncated: label.scrollWidth > label.clientWidth, title: !!label.getAttribute("title"), right: +b.right.toFixed(1),
    };
  });
  const nb = r(nav);
  const ctrls = header ? [...header.querySelectorAll("button, a, input")].filter((e) => !nav.contains(e) && r(e).width > 0 && r(e).left >= nb.left) : [];
  const neighbourLeft = ctrls.length ? Math.round(Math.min(...ctrls.map((e) => r(e).left))) : null;
  const maxRight = Math.max(...crumbs.map((c) => c.right));
  return { navW: +nb.width.toFixed(1), navH: Math.round(nb.height), navRight: +nb.right.toFixed(1), neighbourLeft, maxRight, crumbs };
};

function verdict(key, pk, m) {
  const fails = [];
  const last = m.crumbs[m.crumbs.length - 1];
  const org = m.crumbs[0];
  const mids = m.crumbs.filter((c) => c.role === "middle");
  // 1. one line, nothing past the nav or under a header control
  if (m.navH !== 20) fails.push(`P1 nav ${m.navH}px`);
  if (m.maxRight > m.navRight + 1 || (m.neighbourLeft != null && m.maxRight > m.neighbourLeft)) fails.push(`P1 overflow ${m.maxRight} > nav ${m.navRight}`);
  // 2. 3-crumb pages: no middle truncated; last at least as wide as c4d1f7675
  if (THREE.includes(pk)) {
    for (const c of mids) if (c.truncated) fails.push(`P2 middle "${c.text}" truncated (${c.labelW}/${c.naturalW})`);
    const base = RECHECK.captures[key] && RECHECK.captures[key].crumbs.slice(-1)[0];
    if (base && last.labelW + 0.5 < base.labelW) fails.push(`P2 last ${last.labelW} < c4d1f7675 ${base.labelW}`);
  } else {
    // 3. long chains: last visible and readable; "Edit" whole
    if (last.labelW < 1) fails.push(`P3 last 0px`);
    if (pk === "template-edit" && last.truncated) fails.push(`P3 "Edit" truncated (${last.labelW}/${last.naturalW})`);
    if (last.labelW < 20) fails.push(`P3 last label ${last.labelW}px unreadable`);
  }
  for (const c of m.crumbs) if (c.truncated && !c.title) fails.push(`P3 "${c.text}" truncated without title`);
  // 4. org ≤ 160 with title; middle labels ≤ 160 with title
  if (org.w > 160.5 || !org.title) fails.push(`P4 org ${org.w}px title=${org.title}`);
  for (const c of mids) if (c.labelW > 160.5 || !c.title) fails.push(`P4 middle "${c.text}" ${c.labelW}px title=${c.title}`);
  return fails;
}

(async () => {
  const browser = await C.chromium.launch();
  const S = { alice: await C.loginState(browser, "alice"), carol: await C.loginState(browser, "carol") };
  const P = RECHECK.pages;
  const pages = [
    ["alice", "fee-note", `${BASE}/invoices/${P.feeNote.id}`],
    ["alice", "trust-transactions", `${BASE}/trust-accounting/transactions`],
    ["carol", "trust-transactions", `${BASE}/trust-accounting/transactions`],
    ["carol", "matter", `${C.APP}${P.carolMatter}`],
    ["alice", "template-edit", `${BASE}/settings/documents/templates/${P.template.id}/edit`],
    ["alice", "checklist-edit", `${C.APP}${P.checklist}/edit`],
    ["alice", "client-detail", `${C.APP}${P.aliceClient}`],
  ];
  const results = {};
  for (const [who, pk, url] of pages)
    for (const collapsed of [false, true])
      for (const theme of ["light", "dark"]) {
        const key = `${who}-${pk}-${collapsed ? "collapsed" : "expanded"}-${theme}`;
        const ctx = await C.newCtx(browser, S[who], { width: 1024, height: 768, theme, collapsed, extra: EXTRA });
        const p = await ctx.newPage();
        await p.goto(url, { waitUntil: "load" });
        await p.waitForLoadState("networkidle").catch(() => {});
        await p.waitForTimeout(400);
        results[key] = {};
        for (const [name, cand] of Object.entries(CANDIDATES)) {
          await p.evaluate(APPLY, cand);
          await p.waitForTimeout(50);
          const m = await p.evaluate(MEASURE);
          m.fails = verdict(key, pk, m);
          results[key][name] = m;
        }
        console.log(key, Object.entries(results[key]).map(([n, m]) => `${n.split("_")[0]}:${m.fails.length ? "F" : "P"}`).join(" "));
        await ctx.close();
      }
  await browser.close();
  const summary = {};
  for (const name of Object.keys(CANDIDATES)) {
    const fails = Object.entries(results).filter(([, r]) => r[name].fails.length).map(([k, r]) => `${k}: ${r[name].fails.join("; ")}`);
    summary[name] = { pass: Object.keys(results).length - fails.length, fail: fails.length, fails };
  }
  fs.writeFileSync(path.join(OUT, "prototype.json"), JSON.stringify({ capturedAt: new Date().toISOString(), stack: "e2e :3001 serving c4d1f7675 (inline-style prototype)", candidates: CANDIDATES, summary, results }) + "\n");
  for (const [n, s] of Object.entries(summary)) console.log(n, s.pass, "pass", s.fail, "fail", s.fails.slice(0, 6).join(" | "));
})();
