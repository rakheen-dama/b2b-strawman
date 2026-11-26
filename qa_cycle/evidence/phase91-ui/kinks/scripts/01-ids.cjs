// 662A step 1: resolve every dynamic route id for Alice (owner) and Carol (member) — API reads with each
// persona's own mock-IDP token, plus links harvested from the parent page as that persona. Read-only.
// Writes RAW/ids.json and the two storage states (RAW/state-{alice,carol}.json).
// Usage (worktree root): NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/kinks/scripts/01-ids.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const safe = async (f) => {
  try {
    return await f();
  } catch (e) {
    return null;
  }
};

(async () => {
  const browser = await L.chromium.launch();
  const out = { resolvedAt: new Date().toISOString() };
  for (const who of ["alice", "carol"]) {
    await L.personaState(browser, who, true);
    const token = await L.idpToken(who);
    const ctx = await L.ctxFor(browser, who, 1440, "light");
    const page = await ctx.newPage();
    const harvest = async (parent, re) => {
      await page.goto(L.BASE + parent, { waitUntil: "load" });
      await page.locator("#main-content h1").first().waitFor({ timeout: 20000 }).catch(() => {});
      await page.waitForTimeout(1500);
      const hrefs = await page.evaluate(() => [...document.querySelectorAll("a[href]")].map((a) => a.getAttribute("href")));
      const m = hrefs.map((h) => h && h.split(/[?#]/)[0].match(re)).find(Boolean);
      return m ? m[1] : null;
    };
    const R = (p) => new RegExp(`^/org/${L.ORG}${p}/(${L.UUID})$`);
    const matters = (await safe(async () => L.list(await L.api(token, "/api/matters")))) || [];
    const matter = matters.filter((p) => p.status === "ACTIVE" && p.clientName).sort((x, y) => (y.trustBalance || 0) - (x.trustBalance || 0))[0] || matters[0];
    const invoices = (await safe(async () => L.list(await L.api(token, "/api/invoices")))) || [];
    const checklists = (await safe(async () => L.list(await L.api(token, "/api/checklist-templates")))) || [];
    const nonPlatformChecklist = checklists.find((c) => c.source && !/platform/i.test(c.source)) || checklists.find((c) => c.platform === false || c.isPlatform === false);
    const id = {
      mattersVisible: matters.length,
      matter: matter && matter.id,
      matterName: matter && matter.name,
      invoice: (invoices.find((i) => i.status === "DRAFT") || invoices[0] || {}).id || null,
      infoReq: await safe(async () => L.list(await L.api(token, "/api/information-requests"))[0].id),
      disb: await safe(async () => L.list(await L.api(token, "/api/legal/disbursements"))[0].id),
      docTpl: await safe(async () => L.list(await L.api(token, "/api/templates"))[0].id),
      checklist: (checklists[0] || {}).id || null,
      checklistEdit: (nonPlatformChecklist || checklists[0] || {}).id || null,
      checklistEditIsPlatform: !nonPlatformChecklist,
      checklistSources: [...new Set(checklists.map((c) => c.source || String(c.platform)))],
      reqTpl: await safe(async () => L.list(await L.api(token, "/api/request-templates"))[0].id),
      billingRun: await safe(async () => L.list(await L.api(token, "/api/billing-runs"))[0].id),
      report: "timesheet",
    };
    id.task = matter ? await safe(async () => L.list(await L.api(token, `/api/matters/${matter.id}/tasks`))[0].id) : null;
    id.client = await harvest("/clients", R("/clients"));
    id.dsr = await harvest("/compliance/requests", R("/compliance/requests"));
    id.ledgerClient = await harvest("/trust-accounting/client-ledgers", R("/trust-accounting/client-ledgers"));
    id.reconList = await harvest("/trust-accounting/reconciliation", R("/trust-accounting/reconciliation/lists"));
    id.matterTpl = await harvest("/settings/matters?tab=templates", R("/settings/matters/templates"));
    id.billingRun = id.billingRun || (await harvest("/invoices/billing-runs", R("/invoices/billing-runs")));
    // Long transaction references (K-10 pattern) on the stack, if any.
    id.longRefs = await safe(async () => {
      const accts = L.list(await L.api(token, "/api/trust-accounts"));
      let max = 0;
      for (const a of accts) {
        const tx = L.list(await L.api(token, `/api/trust-accounts/${a.id}/transactions?size=200`));
        for (const t of tx) max = Math.max(max, (t.reference || "").length);
      }
      return { accounts: accts.length, maxReferenceLength: max };
    });
    out[who] = id;
    console.log(who, JSON.stringify(id));
    await ctx.close();
  }
  // Carol never sees Alice's owner-only matter unless she is on its team: record whether she can.
  out.carolOnAliceMatter = out.carol.matter === out.alice.matter;
  fs.writeFileSync(path.join(L.RAW, "ids.json"), JSON.stringify(out));
  await browser.close();
  console.log("ALL DONE ids");
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
