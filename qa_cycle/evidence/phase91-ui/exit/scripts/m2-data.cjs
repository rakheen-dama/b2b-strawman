// 670A M2 data created THROUGH THE UI (Alice, 1440 light), for fee-note states absent on stack A:
//  A. SENT overdue: stack A has 3 SENT fee notes, all due 2026-12-27 (none overdue). 666B recipe
//     ($EV/666B/before/README.md "Data created"): a DRAFT's form Due Date ← 2026-11-01 → Save Changes
//     → Approve → Send fee note (→ "Send Anyway" if the send-override dialog opens).
//     Draft used: 39452bc6-… ("Quarter hour", R 345,00 incl. R 45,00 VAT). KNOWN: F-666B-B1 (Save
//     Changes on the draft form zeroes per-line VAT) — totals are read before/after with read-only
//     GETs and recorded, so the observation is not mistaken for a new defect.
//  B. Fee run: stack A has 0 billing runs (the default suite's billing-runs.spec.ts only opens the
//     wizard). Fee runs › New → Period From 2026-11-01, Period To 2026-11-30, Notes → Next (creates
//     the run; the wizard is left at step 2, nothing generated, approved or sent).
// Results merged into ../ids.json → m2.invoices.SENT_OVERDUE, m2.billingRunId, m2.created[].
// Usage: node m2-data.cjs [A|B|AB]   (default AB)
const fs = require("fs");
const path = require("path");
const L = require("./lib.cjs");
const DRAFT = "39452bc6-5c5f-4984-9984-921bee60a542";
const RAWD = path.join(L.RAW, "m2-data");
fs.mkdirSync(RAWD, { recursive: true });
const which = process.argv[2] || "AB";

(async () => {
  const browser = await L.chromium.launch();
  const state = await L.loginState(browser, "alice");
  const ctx = await L.ctxFor(browser, state, "1440-light");
  const p = await ctx.newPage();
  const native = [];
  p.on("dialog", (d) => { native.push({ type: d.type(), message: d.message() }); d.accept(); });
  const ta = await L.idpToken("alice");
  const created = [];
  const shot = async (n) => p.screenshot({ path: path.join(RAWD, `${n}.png`) });
  const main = p.locator("#main-content");
  const out = {};
  if (which.includes("A")) {
    const before = await L.api(ta, `/api/invoices/${DRAFT}`);
    await p.goto(`${L.BASE}/invoices/${DRAFT}`, { waitUntil: "load" });
    await L.settle(p);
    await shot("A0-draft");
    await p.locator("#invoice-due-date").fill("2026-11-01");
    await main.getByRole("button", { name: "Save Changes" }).click();
    await p.waitForTimeout(3000);
    await shot("A1-saved");
    const afterSave = await L.api(ta, `/api/invoices/${DRAFT}`);
    await main.getByRole("button", { name: "Approve", exact: true }).click();
    await main.getByRole("button", { name: "Send fee note", exact: true }).waitFor({ timeout: 30000 });
    await shot("A2-approved");
    await main.getByRole("button", { name: "Send fee note", exact: true }).click();
    const r = await Promise.race([
      p.getByTestId("send-override-dialog").waitFor({ timeout: 30000 }).then(() => "override"),
      main.getByRole("button", { name: "Record Payment" }).waitFor({ timeout: 30000 }).then(() => "sent"),
    ]);
    let overrideText = null;
    if (r === "override") {
      overrideText = (await p.getByTestId("send-override-dialog").innerText()).replace(/\s+/g, " ").slice(0, 400);
      await shot("A3-override");
      await p.getByTestId("send-override-dialog").getByRole("button", { name: "Send Anyway" }).click();
      await main.getByRole("button", { name: "Record Payment" }).waitFor({ timeout: 30000 });
    }
    await L.settle(p);
    await shot("A4-sent");
    const after = await L.api(ta, `/api/invoices/${DRAFT}`);
    const pick = (x) => ({ status: x.status, invoiceNumber: x.invoiceNumber, dueDate: x.dueDate, subtotal: x.subtotal, taxAmount: x.taxAmount, total: x.total, lineTax: (x.lines || []).map((l) => l.taxAmount) });
    out.A = { id: DRAFT, before: pick(before), afterSave: pick(afterSave), after: pick(after), sendPath: r, overrideText };
    created.push({ what: "fee note DRAFT → SENT overdue", id: DRAFT, number: after.invoiceNumber, via: "UI: /invoices/<id> draft form Due Date 2026-11-01 → Save Changes → Approve → Send fee note" + (r === "override" ? " → Send Anyway" : ""), vat: `before save tax ${before.taxAmount} / total ${before.total}; after save tax ${afterSave.taxAmount} / total ${afterSave.total} (F-666B-B1 if zeroed)`, at: new Date().toISOString() });
    console.log("A", JSON.stringify(out.A));
  }
  if (which.includes("B")) {
    await p.goto(`${L.BASE}/invoices/billing-runs/new`, { waitUntil: "load" });
    await L.settle(p);
    await p.locator("#periodFrom").fill("2026-11-01");
    await p.locator("#periodTo").fill("2026-11-30");
    await p.locator("#notes").fill("670A exit gate fee run (observation data)");
    await shot("B0-configure");
    await main.getByRole("button", { name: "Next", exact: true }).click();
    await p.waitForTimeout(5000);
    await L.settle(p);
    await shot("B1-after-next");
    const br = L.list(await L.api(ta, "/api/billing-runs?size=20"));
    const run = br.find((x) => (x.notes || "").startsWith("670A exit gate")) || br[0];
    out.B = { id: run ? run.id : null, status: run ? run.status : null, url: p.url().replace(L.BASE, ""), h2: await p.locator("#main-content h2").first().textContent().catch(() => null) };
    if (run) created.push({ what: "fee run (wizard step 1 only)", id: run.id, status: run.status, via: "UI: Fee runs › New fee run → Period 2026-11-01…2026-11-30, Notes → Next", at: new Date().toISOString() });
    console.log("B", JSON.stringify(out.B));
  }
  await ctx.close();
  await browser.close();
  const f = path.join(L.EXIT, "ids.json");
  const cur = JSON.parse(fs.readFileSync(f, "utf8"));
  cur.m2 = cur.m2 || {};
  cur.m2.created = (cur.m2.created || []).concat(created);
  if (out.A) cur.m2.invoices.SENT_OVERDUE = { id: DRAFT, number: out.A.after.invoiceNumber, dueDate: out.A.after.dueDate, total: out.A.after.total, data: out.A };
  if (out.B && out.B.id) cur.m2.billingRunId = out.B.id;
  cur.m2.nativeDialogs = native;
  fs.writeFileSync(f, JSON.stringify(cur) + "\n");
  console.log("DONE", created.length, "created");
})().catch((e) => { console.error("DATA FAIL", e); process.exit(1); });
