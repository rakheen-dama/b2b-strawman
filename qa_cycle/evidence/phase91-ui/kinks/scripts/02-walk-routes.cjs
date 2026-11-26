// 662A step 2 (task 662.2): S1 routes + S2 platform-admin + S3 task detail sheet + S4 matter/client detail
// tabs, with the S7 dialog harvest (open-only) right after each S1/S4 page shot. One worker per combo
// (persona × theme × viewport), CONC workers at a time. Read-only: nothing is submitted or saved.
// Time-boxed and resumable: exits after BUDGET_S seconds; re-run the same command until "ALL DONE".
// Usage (worktree root): BUDGET_S=480 CONC=4 NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/kinks/scripts/02-walk-routes.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ID = L.ids();
if (!ID) throw new Error("run 01-ids.cjs first");
const CONC = +(process.env.CONC || 4);
const MATTER_TABS = "details fields team overview documents correspondence clients tasks time budget financials activity rates generated requests comments court-dates adverse-parties trust disbursements statements audit".split(" ");
const CLIENT_TABS = "details fields tags overview matters documents generated invoices rates financials trust onboarding requests audit".split(" ");

// Dynamic segment → persona id (falls back to Alice's id, so a member sees the gated / not-found branch).
function dyn(who, route) {
  const P = ID[who];
  const A = ID.alice;
  const key = {
    "/clients/[id]": "client", "/compliance/requests/[id]": "dsr", "/information-requests/[id]": "infoReq", "/invoices/[id]": "invoice",
    "/invoices/billing-runs/[id]": "billingRun", "/legal/disbursements/[id]": "disb", "/matters/[id]": "matter", "/reports/[reportSlug]": "report",
    "/settings/documents/templates/[id]/edit": "docTpl", "/settings/matters/checklists/[id]": "checklist", "/settings/matters/checklists/[id]/edit": "checklistEdit",
    "/settings/matters/request-templates/[id]": "reqTpl", "/settings/matters/templates/[id]": "matterTpl", "/trust-accounting/client-ledgers/[clientId]": "ledgerClient",
    "/trust-accounting/reconciliation/lists/[listId]": "reconList",
  }[route];
  const v = (P && P[key]) || (A && A[key]);
  return v ? { id: v, own: !!(P && P[key]) } : null;
}
const tabPanelCheck = (tab) => async (page) => {
  const ok = await page.evaluate((tab) => {
    const p = document.querySelector('#main-content [role="tabpanel"][data-state="active"]');
    return p ? p.id.endsWith(`-content-${tab}`) : null;
  }, tab);
  return ok === false ? `tab "${tab}" not visible for this persona (fell back to another tab)` : null;
};

// Build the visit list for one combo.
function visits(who, vp, theme) {
  const v = [];
  const skipped = [];
  const full = true;
  for (const r of L.ROUTES) {
    if (L.FAMILY_HOSTS.has(r)) continue;
    if (r === "/settings") {
      if (vp === 1440 && theme === "light") v.push({ set: "S1", route: r, url: r });
      continue;
    }
    let url = r;
    if (r.includes("[")) {
      const d = dyn(who, r);
      if (!d) {
        skipped.push({ route: r, reason: "no id on the stack (read-only walk; see top-ups)" });
        continue;
      }
      url = r.replace(/\[[^\]]+\]/, d.id);
    }
    v.push({ set: "S1", route: r, url, dialogs: true });
  }
  for (const [fam, tab] of L.TABS) v.push({ set: "S1", route: `/settings/${fam}?tab=${tab}`, url: `/settings/${fam}?tab=${tab}`, dialogs: true });
  // Carol on the owner's matter (the not-found / not-on-team branch, W-8) — one extra tile per theme at 1440.
  if (who === "carol" && vp === 1440 && ID.alice.matter !== ID.carol.matter) v.push({ set: "S1", route: "/matters/[id]", name: "matters-id-owner-matter", url: `/matters/${ID.alice.matter}`, waitSel: "#main-content h1, #main-content h2" });
  // S2 platform-admin: light only; Alice 1024 + 1440, Carol 1440 (expected 404 tile).
  if (theme === "light" && (who === "alice" || vp === 1440)) for (const r of L.PA_ROUTES) v.push({ set: "S2", route: r, url: L.APP + r, dialogs: who === "alice" });
  // S3 task detail sheet (URL-driven): on the matter Tasks tab, all combos; on /my-work at 1440.
  const P = ID[who];
  if (P.matter && P.task) {
    v.push({ set: "S3", route: "/matters/[id]?tab=tasks&taskId=", name: "task-sheet-matter", url: `/matters/${P.matter}?tab=tasks&taskId=${P.task}`, waitSel: '[role="dialog"]', settle: 2200 });
    if (vp === 1440) v.push({ set: "S3", route: "/my-work?taskId=", name: "task-sheet-my-work", url: `/my-work?taskId=${P.task}`, waitSel: '[role="dialog"]', settle: 2200 });
  }
  // S4 detail tabs: 1440 × L/D × both personas + 1024 light Alice.
  if (vp === 1440 || (vp === 1024 && theme === "light" && who === "alice")) {
    const m = dyn(who, "/matters/[id]");
    for (const t of MATTER_TABS) v.push({ set: "S4", route: `/matters/[id]?tab=${t}`, name: `matters-id--${t}`, url: `/matters/${m.id}?tab=${t}`, check: tabPanelCheck(t), dialogs: true, pageKey: "matters-id", group: "t1" });
    const c = dyn(who, "/clients/[id]");
    if (c) for (const t of CLIENT_TABS) v.push({ set: "S4", route: `/clients/[id]?tab=${t}`, name: `clients-id--${t}`, url: `/clients/${c.id}?tab=${t}`, check: tabPanelCheck(t), dialogs: true, pageKey: "clients-id", group: "t1" });
  }
  return { v, skipped, full };
}

const COMBOS = [];
for (const who of ["alice", "carol"]) for (const theme of ["light", "dark"]) for (const vp of [1024, 1440]) COMBOS.push({ who, theme, vp });

(async () => {
  const browser = await L.chromium.launch();
  const skippedAll = {};
  const summary = [];
  await L.pool(COMBOS, CONC, async ({ who, theme, vp }) => {
    const combo = `${who}-${vp}-${theme}`;
    const { v, skipped } = visits(who, vp, theme);
    skippedAll[combo] = skipped;
    const todo = v.filter((x) => !L.doneAlready(L.shotBase(x.set, x.name || L.slugOf(x.route), who, vp, theme)) || x.dialogs);
    let ctx = await L.ctxFor(browser, who, vp, theme);
    let page = await ctx.newPage();
    let sink = L.sinkFor(page);
    const probeF = path.join(L.RAW, `probe-${combo}.json`);
    let shots = 0;
    let dlgs = 0;
    for (const x of todo) {
      if (L.overBudget()) break;
      const name = x.name || L.slugOf(x.route);
      const base = L.shotBase(x.set, name, who, vp, theme);
      const dlgDone = path.join(L.RAW, "S7", `.done-${name}__${combo}`);
      if (L.doneAlready(base) && (!x.dialogs || fs.existsSync(dlgDone))) continue;
      let rec;
      for (let attempt = 0; attempt < 2; attempt++) {
        try {
          rec = await L.capture(page, sink, { ...x, who, vp, theme });
          break;
        } catch (e) {
          if (!(e instanceof L.Relogin)) throw e;
          await ctx.close();
          await L.personaState(browser, who, true);
          ctx = await L.ctxFor(browser, who, vp, theme);
          page = await ctx.newPage();
          sink = L.sinkFor(page);
        }
      }
      if (rec && !rec.skipped) shots++;
      if (!fs.existsSync(probeF) && rec && rec.status) fs.writeFileSync(probeF, JSON.stringify({ combo, probeCaptured: await L.sinkProbe(page, sink), at: new Date().toISOString() }));
      if (x.dialogs && !(rec && rec.skip) && !L.overBudget()) {
        if (rec && rec.skipped) {
          // page shot existed from an earlier run: reload it before harvesting
          await page.goto(x.url.startsWith("http") ? x.url : L.BASE + x.url, { waitUntil: "load" });
          await page.locator(x.waitSel || "#main-content h1").first().waitFor({ timeout: 20000 }).catch(() => {});
          await page.waitForTimeout(1500);
        }
        const h = await L.harvestDialogs(page, sink, { pageKey: x.pageKey || name, foundOn: x.route, who, vp, theme, group: x.group || L.groupOf(x.route) });
        dlgs += h.out.length;
        if (!h.incomplete) {
          fs.mkdirSync(path.dirname(dlgDone), { recursive: true });
          fs.writeFileSync(dlgDone, JSON.stringify(h.out.map((d) => d.trigger)));
        }
      }
    }
    await ctx.close();
    const left = v.filter((x) => !L.doneAlready(L.shotBase(x.set, x.name || L.slugOf(x.route), who, vp, theme)) || (x.dialogs && !fs.existsSync(path.join(L.RAW, "S7", `.done-${x.name || L.slugOf(x.route)}__${combo}`)))).length;
    summary.push({ combo, visits: v.length, shots, dlgs, left });
    console.log(`${combo}: +${shots} shots, +${dlgs} dialogs, ${left} left, ${L.elapsed()}s`);
  });
  fs.writeFileSync(path.join(L.RAW, "skipped-02.json"), JSON.stringify(skippedAll));
  await browser.close();
  const left = summary.reduce((a, s) => a + s.left, 0);
  console.log(left ? `REMAINING ${left}` : "ALL DONE 02-walk-routes");
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
