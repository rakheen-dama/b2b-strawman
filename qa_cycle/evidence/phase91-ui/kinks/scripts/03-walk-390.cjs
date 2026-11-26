// 662A step 3 (task 662.2, 390 px): S5 shell — /dashboard, /matters, /matters/{own id}: closed, mobile Sheet
// open ("Toggle menu"), Sheet scrolled to the bottom (K-05: are Fees/Trust/Firm below the fold?);
// S6 settings — every ?tab= of the 5 families, /settings (redirect) and the 11 sub-pages, with the
// family-switch probe (K-02: any control on the page that reaches another family without the Sheet?).
// 390×844 × light/dark × Alice/Carol. Read-only. Time-boxed + resumable (re-run until "ALL DONE").
// Usage (worktree root): BUDGET_S=480 CONC=4 NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/kinks/scripts/03-walk-390.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ID = L.ids();
const CONC = +(process.env.CONC || 4);
const SHEET = '[data-mobile="true"][data-sidebar="sidebar"]';
const openSheet = async (page) => {
  await page.getByRole("button", { name: "Toggle menu" }).first().click();
  await page.locator(SHEET).waitFor({ state: "visible", timeout: 10000 });
  await page.waitForTimeout(700);
};
const sheetInfo = (page) =>
  page.evaluate((sel) => {
    const s = document.querySelector(sel);
    const c = s && (s.querySelector('[data-sidebar="content"]') || s);
    const groups = s ? [...s.querySelectorAll('[data-sidebar="group-label"], [data-slot="sidebar-group-label"], button[aria-expanded]')].map((g) => ({ text: g.innerText.trim().slice(0, 20), bottom: Math.round(g.getBoundingClientRect().bottom), expanded: g.getAttribute("aria-expanded") })) : [];
    return s ? { sheetW: Math.round(s.getBoundingClientRect().width), scrollTop: c.scrollTop, scrollH: c.scrollHeight, clientH: c.clientHeight, innerH: innerHeight, groups: groups.slice(0, 12) } : null;
  }, SHEET);
// K-02 probe: controls in #main-content (Sheet closed) that link to ANOTHER settings family; plus the settings rail.
const familyProbe = (fam) => (page) =>
  page.evaluate((fam) => {
    const FAMS = ["firm", "matters", "documents", "fees", "connections"];
    const main = document.querySelector("#main-content");
    const vis = (e) => {
      const r = e.getBoundingClientRect();
      return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== "hidden";
    };
    const links = [...main.querySelectorAll("a[href]")].filter(vis).map((a) => a.getAttribute("href")).filter((h) => {
      const m = h.match(/\/settings\/(\w+)/);
      return m && FAMS.includes(m[1]) && m[1] !== fam;
    });
    const nav = document.querySelector('nav[aria-label="Settings navigation"]');
    const tabs = [...main.querySelectorAll('[role="tab"]')].filter(vis);
    const tablist = main.querySelector('[role="tablist"]');
    return {
      family: fam,
      otherFamilyLinks: [...new Set(links)].slice(0, 10),
      otherFamilyControls: [...main.querySelectorAll("select, [role=combobox], button")].filter(vis).filter((b) => /firm|matters|documents|fees|connections/i.test(b.innerText || b.getAttribute("aria-label") || "") && /famil|settings/i.test((b.getAttribute("aria-label") || "") + (b.closest("[aria-label]")?.getAttribute("aria-label") || ""))).map((b) => (b.innerText || b.getAttribute("aria-label")).trim().slice(0, 30)),
      settingsRail: nav ? { visible: vis(nav), w: Math.round(nav.getBoundingClientRect().width) } : null,
      tabsVisible: tabs.length,
      tablist: tablist ? { clientW: tablist.clientWidth, scrollW: tablist.scrollWidth, overflowX: getComputedStyle(tablist).overflowX } : null,
    };
  }, fam);

function visits(who) {
  const v = [];
  const P = ID[who];
  for (const [r, url] of [["/dashboard", "/dashboard"], ["/matters", "/matters"], ["/matters/[id]", `/matters/${P.matter}`]]) {
    v.push({ set: "S5", route: r, url, name: `${L.slugOf(r)}--closed`, group: "t1" });
    v.push({ set: "S5", route: r, url, name: `${L.slugOf(r)}--sheet`, group: "t1", viewportOnly: true, sheetProbe: true, noAudit: true, before: async (page) => (await openSheet(page), sheetInfo(page)) });
    v.push({
      set: "S5", route: r, url, name: `${L.slugOf(r)}--sheet-bottom`, group: "t1", viewportOnly: true, sheetProbe: true, noAudit: true,
      before: async (page) => {
        await openSheet(page);
        await page.evaluate((sel) => {
          const s = document.querySelector(sel);
          const c = s.querySelector('[data-sidebar="content"]') || s;
          c.scrollTop = c.scrollHeight;
        }, SHEET);
        await page.waitForTimeout(400);
        return sheetInfo(page);
      },
    });
  }
  v.push({ set: "S6", route: "/settings", url: "/settings" });
  const firstTab = {};
  for (const [fam, tab] of L.TABS) {
    const isFirst = !firstTab[fam];
    firstTab[fam] = firstTab[fam] || tab;
    v.push({ set: "S6", route: `/settings/${fam}?tab=${tab}`, url: `/settings/${fam}?tab=${tab}`, after: familyProbe(fam), familyDefault: isFirst });
  }
  const A = ID.alice;
  const pick = (k) => P[k] || A[k];
  const map = { "/settings/documents/templates/[id]/edit": pick("docTpl"), "/settings/matters/checklists/[id]": pick("checklist"), "/settings/matters/checklists/[id]/edit": pick("checklistEdit"), "/settings/matters/request-templates/[id]": pick("reqTpl"), "/settings/matters/templates/[id]": pick("matterTpl") };
  for (const r of L.ROUTES.filter(L.isSub)) {
    const url = r.includes("[") ? (map[r] ? r.replace(/\[[^\]]+\]/, map[r]) : null) : r;
    if (url) v.push({ set: "S6", route: r, url, after: familyProbe((r.match(/^\/settings\/(\w+)/) || [])[1]) });
  }
  return v;
}

const COMBOS = [];
for (const who of ["alice", "carol"]) for (const theme of ["light", "dark"]) COMBOS.push({ who, theme, vp: 390 });

(async () => {
  const browser = await L.chromium.launch();
  const tally = [];
  await L.pool(COMBOS, CONC, async ({ who, theme, vp }) => {
    const combo = `${who}-${vp}-${theme}`;
    const v = visits(who);
    let ctx = await L.ctxFor(browser, who, vp, theme);
    let page = await ctx.newPage();
    let sink = L.sinkFor(page);
    let shots = 0;
    const probeF = path.join(L.RAW, `probe-${combo}.json`);
    for (const x of v) {
      if (L.overBudget()) break;
      const name = x.name || L.slugOf(x.route);
      if (L.doneAlready(L.shotBase(x.set, name, who, vp, theme))) continue;
      try {
        const rec = await L.capture(page, sink, { ...x, who, vp, theme });
        if (!rec.skipped) shots++;
        if (!fs.existsSync(probeF) && rec.status) fs.writeFileSync(probeF, JSON.stringify({ combo, probeCaptured: await L.sinkProbe(page, sink), at: new Date().toISOString() }));
      } catch (e) {
        if (!(e instanceof L.Relogin)) throw e;
        await ctx.close();
        await L.personaState(browser, who, true);
        ctx = await L.ctxFor(browser, who, vp, theme);
        page = await ctx.newPage();
        sink = L.sinkFor(page);
      }
    }
    await ctx.close();
    const left = v.filter((x) => !L.doneAlready(L.shotBase(x.set, x.name || L.slugOf(x.route), who, vp, theme))).length;
    tally.push(left);
    console.log(`${combo}: +${shots} shots, ${left} left, ${L.elapsed()}s`);
  });
  await browser.close();
  const left = tally.reduce((a, b) => a + b, 0);
  console.log(left ? `REMAINING ${left}` : "ALL DONE 03-walk-390");
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
