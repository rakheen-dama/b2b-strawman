// 664B step 0: seed (idempotent — a no-op once seeded, so the PHASE=after run changes nothing).
// (a) Other-creditors pager data, through the REST API as Alice (owner, MANAGE_TRUST; her mock-IDP
//     token): one bank statement CSV on the primary trust account (generic CSV parser) with 60 credit
//     lines "664B seed interest NNN" and 60 "664B seed unknown deposit NNN" (2026-10-01..2026-11-25),
//     then each line recorded through POST /api/bank-statement-lines/{id}/record as FUND_INTEREST /
//     UNIDENTIFIED_DEPOSIT — the app's own "record a line from the bank statement" path. Page size is
//     25 for both lists (other-creditors/page.tsx FUND_LEDGER_PAGE_SIZE / UNIDENTIFIED_PAGE_SIZE), so
//     60 rows = 3 pages each (a middle page with both links live).
// (b) K-11 saved view "664B K-11 view" on /clients through the Save View dialog in the browser
//     (Alice 1440×900 light; All clients, default columns = explicit columns, not shared). Next/Save
//     are off-screen (K-47), so — as 662A's kinks/scripts/04-checks.cjs:303 — they are dispatchEvent'ed.
// Discovery reads are GET only. Writes: the statement import, the line records, the saved view.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const TAG = "664B seed";
const VIEW = "664B K-11 view";
const N = 60;
const log = [];
const say = (...a) => { const s = a.join(" "); log.push(s); console.log(s); };

async function trustSeed() {
  const t = await L.idpToken("alice");
  const accounts = L.list(await L.api(t, "/api/trust-accounts"));
  const primary = accounts.find((a) => a.isPrimary) || accounts[0];
  say("primary trust account", primary.id, primary.accountName);
  const creditors = await L.api(t, `/api/trust-accounts/${primary.id}/creditor-accounts`);
  const fund = creditors.find((c) => c.accountType === "FUND_INTEREST_AND_CHARGES");
  const count = async () => ({
    fund: (await L.api(t, `/api/trust-creditor-accounts/${fund.id}/transactions?page=0&size=1`)).page.totalElements,
    items: (await L.api(t, `/api/trust-accounts/${primary.id}/unidentified-deposits?page=0&size=1`)).page.totalElements,
  });
  const before = await count();
  say("before: fund ledger rows", before.fund, "unidentified items", before.items);

  // Our statement (by file name), else import it.
  const statements = await L.api(t, `/api/trust-accounts/${primary.id}/bank-statements`);
  let st = statements.find((s) => (s.fileName || "").startsWith("664B-seed"));
  if (!st) {
    if (before.fund >= 26 && before.items >= 26) return { primary: primary.id, before, after: before, skipped: "already ≥ 2 pages each, no 664B statement needed" };
    const rows = ["Date,Description,Amount,Balance"];
    let bal = 0;
    const start = Date.UTC(2026, 9, 1); // 2026-10-01
    for (let i = 0; i < N * 2; i++) {
      const d = new Date(start + Math.floor((i * 55) / (N * 2)) * 86400000); // spread to 2026-11-25
      const dd = `${String(d.getUTCDate()).padStart(2, "0")}/${String(d.getUTCMonth() + 1).padStart(2, "0")}/${d.getUTCFullYear()}`;
      const k = Math.floor(i / 2) + 1;
      const fundLine = i % 2 === 0;
      const amt = fundLine ? 10 + k / 100 : 500 + k;
      bal += amt;
      rows.push(`${dd},${TAG} ${fundLine ? "interest" : "unknown deposit"} ${String(k).padStart(3, "0")},${amt.toFixed(2)},${bal.toFixed(2)}`);
    }
    const fd = new FormData();
    fd.append("file", new Blob([rows.join("\n") + "\n"], { type: "text/csv" }), "664B-seed-statement.csv");
    st = await L.apiWrite(t, "POST", `/api/trust-accounts/${primary.id}/bank-statements`, fd);
    say("imported statement", st.id, st.periodStart, "→", st.periodEnd, "lines", st.lineCount);
  } else say("statement exists", st.id);
  const full = await L.api(t, `/api/bank-statements/${st.id}`);
  let recorded = 0;
  const errors = [];
  for (const line of full.lines) {
    if (line.matchStatus !== "UNMATCHED") continue;
    const m = /664B seed (interest|unknown deposit)/.exec(line.description || "");
    if (!m) continue;
    const classification = m[1] === "interest" ? "FUND_INTEREST" : "UNIDENTIFIED_DEPOSIT";
    try {
      await L.apiWrite(t, "POST", `/api/bank-statement-lines/${line.id}/record`, { classification, notes: `${TAG} (664B observe-first pager data)` });
      recorded++;
    } catch (e) {
      errors.push(`${line.lineNumber} ${e.message.slice(0, 200)}`);
    }
  }
  const after = await count();
  say("recorded", recorded, "lines; errors", errors.length, "; after: fund", after.fund, "items", after.items);
  return { primary: primary.id, fund: fund.id, statement: st.id, statementLines: full.lines.length, recordedNow: recorded, errors, before, after };
}

async function savedViewSeed() {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const page = await ctx.newPage();
  await page.goto(`${L.BASE}/clients`, { waitUntil: "load" });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(800);
  const tabs = () => page.evaluate(() => [...document.querySelectorAll('[data-testid="saved-view-selector"] [role="tab"]')].map((t) => t.innerText.trim()));
  const out = { tabsBefore: await tabs() };
  if (out.tabsBefore.includes(VIEW)) {
    out.result = "view already existed";
  } else {
    await page.locator("#main-content").getByRole("button", { name: "Save View" }).click();
    const dlg = page.getByRole("dialog");
    await dlg.waitFor();
    await page.waitForTimeout(500);
    const next = () => dlg.getByRole("button", { name: "Next" }).dispatchEvent("click");
    await next();
    await page.waitForTimeout(400);
    out.step2Columns = await dlg.evaluate((d) => [...d.querySelectorAll('input[type="checkbox"]')].map((c) => `${(c.closest("label") || c.parentElement).textContent.trim().slice(0, 30)}:${c.checked}`));
    await next();
    await page.waitForTimeout(400);
    await dlg.locator("#view-name").fill(VIEW);
    await dlg.getByRole("button", { name: "Save View" }).dispatchEvent("click");
    await dlg.waitFor({ state: "hidden", timeout: 30000 });
    await page.waitForTimeout(1000);
    await page.reload({ waitUntil: "load" });
    await page.waitForTimeout(800);
    out.result = "created through the Save View dialog (Next/Save via dispatchEvent — off-screen, K-47)";
  }
  out.tabsAfter = await tabs();
  say("saved view:", out.result, "tabs", JSON.stringify(out.tabsAfter));
  await browser.close();
  return out;
}

(async () => {
  const res = { phase: L.PHASE, at: new Date().toISOString() };
  try { res.trust = await trustSeed(); } catch (e) { res.trustError = e.message; say("TRUST FAIL", e.message); }
  try { res.savedView = await savedViewSeed(); } catch (e) { res.savedViewError = e.message.split("\n")[0]; say("VIEW FAIL", res.savedViewError); }
  fs.writeFileSync(path.join(L.OUT, "seed.json"), JSON.stringify(res));
  console.log(JSON.stringify(res));
})();
