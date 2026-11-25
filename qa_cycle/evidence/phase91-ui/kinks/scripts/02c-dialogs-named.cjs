// 662A step 2c = 02b with one fix (02b kept as executed): the submit filter tested b.type, which is "submit" for every
// <button> without a type attribute, so 02b skipped nearly every opener. 02c tests the attribute instead.
// 662A step 2b (S7 supplement): dialogs opened by plain onClick state (no aria-haspopup) are missed by the
// harvest rule in 02-walk-routes.cjs. This pass clicks buttons in #main-content whose accessible name starts
// with an "opener" verb (New/Add/Create/Edit/Invite/Record/Log/Upload/Place/Set/Configure/Import/Filter/Schedule),
// never a submit button or a button inside a form that would submit, never a destructive name, never
// Approve/Send/Generate/Download/Pay/Link (those may act without a dialog). Open-only: wait ≤ 2.5 s for a
// dialog/sheet, viewport shot + dialogProbe, Escape. A click that navigates or opens a menu is recorded, not shot.
// Combos: Alice 1440 light/dark + 1024 light; Carol 1440 light/dark. Same RAW/S7 names as the harvest, so a
// dialog the harvest already captured is skipped. Time-boxed + resumable (marker per page × combo).
// Usage (worktree root): BUDGET_S=480 CONC=4 NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/kinks/scripts/02c-dialogs-named.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const ID = L.ids();
const CONC = +(process.env.CONC || 4);
const OPENER = /^(new|add|create|edit|invite|record|log|upload|place|set|configure|import|filter|schedule)\b/i;
const NEVER = /approve|send|generate|download|pay\b|link|save|submit|sign in|default|primary|enable|disable|mark|resend|retry|sync|refresh|run\b|start|finish|confirm|accept|verify|delete|remove|archive|anonymi|reverse|void|cancel|reject|disconnect|uninstall|deactivate|write off|close matter|reopen|discard|revoke|unlink|transfer lead|complete/i;
const MATTER_TABS = "details fields team overview documents correspondence clients tasks time budget financials activity rates generated requests comments court-dates adverse-parties trust disbursements statements audit".split(" ");
const CLIENT_TABS = "details fields tags overview matters documents generated invoices rates financials trust onboarding requests audit".split(" ");

function pagesFor(who, vp, theme) {
  const P = ID[who];
  const A = ID.alice;
  const pick = (k) => P[k] || A[k];
  const map = { "/clients/[id]": pick("client"), "/information-requests/[id]": pick("infoReq"), "/invoices/[id]": pick("invoice"), "/legal/disbursements/[id]": pick("disb"), "/matters/[id]": pick("matter"), "/reports/[reportSlug]": "timesheet", "/settings/documents/templates/[id]/edit": pick("docTpl"), "/settings/matters/checklists/[id]": pick("checklist"), "/settings/matters/request-templates/[id]": pick("reqTpl"), "/settings/matters/templates/[id]": pick("matterTpl"), "/trust-accounting/client-ledgers/[clientId]": pick("ledgerClient") };
  const out = [];
  for (const r of L.ROUTES) {
    if (L.FAMILY_HOSTS.has(r) || r === "/settings") continue;
    if (r.includes("[")) {
      if (!map[r]) continue;
      out.push({ route: r, url: r.replace(/\[[^\]]+\]/, map[r]), pageKey: L.slugOf(r) });
    } else out.push({ route: r, url: r, pageKey: L.slugOf(r) });
  }
  for (const [fam, tab] of L.TABS) out.push({ route: `/settings/${fam}?tab=${tab}`, url: `/settings/${fam}?tab=${tab}`, pageKey: L.slugOf(`/settings/${fam}?tab=${tab}`) });
  if (who === "alice" && theme === "light") for (const r of L.PA_ROUTES) out.push({ route: r, url: L.APP + r, pageKey: L.slugOf(r) });
  if (who === "alice" && vp === 1440 && theme === "light") {
    for (const t of MATTER_TABS) out.push({ route: `/matters/[id]?tab=${t}`, url: `/matters/${A.matter}?tab=${t}`, pageKey: "matters-id", group: "t1" });
    for (const t of CLIENT_TABS) out.push({ route: `/clients/[id]?tab=${t}`, url: `/clients/${A.client}?tab=${t}`, pageKey: "clients-id", group: "t1" });
  }
  return out;
}
const COMBOS = [
  { who: "alice", vp: 1440, theme: "light" },
  { who: "alice", vp: 1440, theme: "dark" },
  { who: "alice", vp: 1024, theme: "light" },
  { who: "carol", vp: 1440, theme: "light" },
  { who: "carol", vp: 1440, theme: "dark" },
];

(async () => {
  const browser = await L.chromium.launch();
  const tally = [];
  await L.pool(COMBOS, CONC, async ({ who, vp, theme }) => {
    const combo = `${who}-${vp}-${theme}`;
    const ctx = await L.ctxFor(browser, who, vp, theme);
    const page = await ctx.newPage();
    const sink = L.sinkFor(page);
    let shots = 0;
    let left = 0;
    for (const pg of pagesFor(who, vp, theme)) {
      const marker = path.join(L.RAW, "S7", `.named2-${L.slugOf(pg.route)}__${combo}`);
      if (fs.existsSync(marker)) continue;
      if (L.overBudget()) {
        left++;
        continue;
      }
      const goto = async () => {
        await page.goto(pg.url.startsWith("http") ? pg.url : L.BASE + pg.url, { waitUntil: "load", timeout: 90000 });
        await page.locator("#main-content h1").first().waitFor({ timeout: 15000 }).catch(() => {});
        await page.waitForTimeout(1500);
      };
      const done = [];
      try {
        await goto();
        const names = await page.evaluate(
          ({ o, n }) => {
            const OP = new RegExp(o, "i");
            const NV = new RegExp(n, "i");
            const seen = new Set();
            const res = [];
            for (const b of document.querySelectorAll('#main-content button, #main-content [role="button"]')) {
              const r = b.getBoundingClientRect();
              if (!r.width || !r.height || b.disabled || b.getAttribute("aria-haspopup")) continue;
              if (b.getAttribute("type") === "submit" || (b.closest("form") && b.getAttribute("type") !== "button")) continue; // 02c: b.type defaults to "submit" for a button without a type attribute (02b bug)
              const name = (b.getAttribute("aria-label") || b.innerText || "").trim().replace(/\s+/g, " ").slice(0, 60);
              if (!name || !OP.test(name) || NV.test(name) || seen.has(name)) continue;
              seen.add(name);
              res.push(name);
            }
            return res.slice(0, 10);
          },
          { o: OPENER.source, n: NEVER.source },
        );
        for (const name of names) {
          if (L.overBudget()) break;
          const base = L.shotBase("S7", `${pg.pageKey}__dlg-${L.fileSlug(name)}`, who, vp, theme);
          if (L.doneAlready(base)) continue;
          const rec = { set: "S7", via: "named", group: pg.group || L.groupOf(pg.route), route: pg.route, pageKey: pg.pageKey, trigger: name, persona: who, vp, theme, rawFile: L.rel(base + ".png"), capturedAt: new Date().toISOString() };
          sink.label = `S7/${pg.pageKey}__dlg-${L.fileSlug(name)}__${combo}`;
          const before = sink.msgs.length;
          const urlBefore = page.url();
          try {
            const btn = page.locator("#main-content").getByRole("button", { name, exact: true }).first();
            await btn.click({ timeout: 5000 });
            const dlg = page.locator('[role="dialog"]:visible, [role="alertdialog"]:visible').last();
            const opened = await dlg.waitFor({ timeout: 2500 }).then(() => true, () => false);
            if (!opened) {
              rec.attempts = 2;
              rec.skip = page.url() !== urlBefore ? `click navigated to ${page.url().replace(L.APP, "")}` : (await page.locator('[role="menu"]:visible').count()) ? "opened a menu, not a dialog" : "no dialog appeared (inline UI or file chooser)";
              await page.keyboard.press("Escape").catch(() => {});
              if (page.url() !== urlBefore) await goto();
            } else {
              await page.waitForTimeout(600);
              await page.screenshot({ path: base + ".png" });
              rec.dialog = await page.evaluate(L.dialogProbe);
              await page.keyboard.press("Escape");
              await page.waitForTimeout(500);
              let still = await page.locator('[role="dialog"]:visible, [role="alertdialog"]:visible').count();
              if (still) {
                await page.keyboard.press("Escape");
                await page.waitForTimeout(400);
                still = await page.locator('[role="dialog"]:visible, [role="alertdialog"]:visible').count();
              }
              rec.closedByEscape = !still;
              rec.focusAfterClose = await page.evaluate(() => {
                const a = document.activeElement;
                return { tag: a ? a.tagName.toLowerCase() : null, text: a ? (a.innerText || a.getAttribute("aria-label") || "").trim().slice(0, 40) : null };
              });
              if (still) await goto();
              shots++;
            }
          } catch (e) {
            rec.error = String(e).slice(0, 200);
            rec.attempts = 2;
            await page.keyboard.press("Escape").catch(() => {});
          }
          const mine = sink.msgs.slice(before);
          rec.console = { e: mine.filter((x) => x.type !== "warning").length, w: mine.filter((x) => x.type === "warning").length, h: mine.filter((x) => /hydrat/i.test(x.text)).length, msgs: mine.slice(0, 5) };
          fs.mkdirSync(path.dirname(base), { recursive: true });
          fs.writeFileSync(base + ".json", JSON.stringify(rec));
          done.push(name);
        }
        if (!L.overBudget()) fs.writeFileSync(marker, JSON.stringify(names));
        else left++;
      } catch (e) {
        fs.writeFileSync(marker, JSON.stringify({ error: String(e).slice(0, 200) }));
      }
    }
    await ctx.close();
    tally.push(left);
    console.log(`${combo}: +${shots} named dialogs, ${left} pages left, ${L.elapsed()}s`);
  });
  await browser.close();
  const left = tally.reduce((a, b) => a + b, 0);
  console.log(left ? `REMAINING ${left}` : "ALL DONE 02c-dialogs-named");
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
