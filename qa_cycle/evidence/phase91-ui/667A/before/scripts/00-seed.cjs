// 667A step 0: seed — RUN ONCE (PHASE=before). Idempotent: every row is found again by its
// reference, every UI step checks state first, so a re-run changes nothing. It always writes
// before/seed.json (the ids 01-capture reads), whatever PHASE is.
// No SQL. Writes, in this order:
//  (a) REST PUT /api/trust-accounts/{primary} {requireDualApproval: true} as Alice (MANAGE_TRUST) —
//      the approval settings on Settings › Fees › Trust accounting are read-only (no UI path, see
//      found.md), so the app's own API is the only way. Threshold left null = every approval is dual.
//  (b) REST deposits as Alice (POST …/transactions/deposit, the RecordDepositDialog's call):
//      22 "667A-H-01..22" on Khumalo Holdings (client-history page 2), two 150-char references
//      (K-10: one with spaces, one unbroken token) on Khumalo, "667A-ACME-DEP" on Acme Corp.
//  (c) REST payments as Alice (POST …/transactions/payment → AWAITING_APPROVAL): "667A-ACME-PAY"
//      (Acme to a zero balance) and "667A-REV-PAY" (Khumalo; the reversal target) each approved by
//      Alice then Bob through POST /api/trust-transactions/{id}/approve (dual: 1st + 2nd);
//      "667A-AWAIT-0" (Ndlovu, no approval) and "667A-AWAIT-1" (Ndlovu).
//  (d) Browser (Alice, 1440×900 light): the FIRST approval of 667A-AWAIT-1 by clicking its Approve
//      button on /trust-accounting/transactions; the reversal of 667A-REV-PAY through its Reverse
//      button + dialog. Screenshots + DOM state before / dialog / right after (no reload) / reload.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const log = [];
const say = (...a) => { const s = a.join(" "); log.push(s); console.log(s); };
const ref150 = (s) => { if (s.length !== 150) throw new Error(`ref length ${s.length}`); return s; };
// 150 characters each (asserted).
const K10_SPACED = ref150("667A-K10 spaced EFT proceeds sale of Erf 4471 Rondebosch per offer to purchase dated 2026-11-20 held in trust pending registration transfer at the dee");
const K10_TOKEN = ref150("667A-K10-TOKEN-" + "X".repeat(135));

async function main() {
  const res = { at: new Date().toISOString(), phase: L.PHASE, steps: [] };
  const ta = await L.idpToken("alice");
  const tb = await L.idpToken("bob");
  const accounts = L.list((await L.apiGet(ta, "/api/trust-accounts")).json);
  const primary = accounts.filter((a) => a.accountType !== "INVESTMENT").find((a) => a.isPrimary);
  res.account = { id: primary.id, name: primary.accountName, lockedThrough: primary.lockedThrough, requireDualApprovalBefore: primary.requireDualApproval, thresholdBefore: primary.paymentApprovalThreshold };
  const clients = L.list((await L.apiGet(ta, "/api/clients?size=200")).json);
  const matters = L.list((await L.apiGet(ta, "/api/matters?size=200")).json);
  const cl = (name) => clients.find((c) => c.name === name);
  const mt = (cid) => matters.find((m) => (m.clientId || m.customerId) === cid);
  const K = cl("Khumalo Holdings"), N = cl("Ndlovu Family Trust"), A = cl("Acme Corp");
  res.clients = { khumalo: K.id, ndlovu: N.id, acme: A.id };
  res.matters = { khumalo: mt(K.id).id, ndlovu: mt(N.id).id, acme: mt(A.id).id };
  const members = await L.api(ta, "/api/members");
  res.members = Object.fromEntries(members.map((m) => [m.name.split(" ")[0].toLowerCase(), m.id]));
  const before = await L.allTransactions(ta, primary.id);
  res.txCountBefore = before.length;
  say("account", primary.id, "dual", primary.requireDualApproval, "tx before", before.length);

  // (a) dual approval
  if (!primary.requireDualApproval) {
    const u = await L.apiWrite(ta, "PUT", `/api/trust-accounts/${primary.id}`, { requireDualApproval: true });
    res.steps.push(`PUT requireDualApproval=true → ${u.requireDualApproval}, threshold ${u.paymentApprovalThreshold}`);
  } else res.steps.push("dual approval already on");

  const byRef = async () => new Map((await L.allTransactions(ta, primary.id)).map((t) => [t.reference, t]));
  let have = await byRef();
  const post = async (kind, ref, clientId, matterId, amount, date, description) => {
    if (have.has(ref)) return have.get(ref);
    const t = await L.apiWrite(ta, "POST", `/api/trust-accounts/${primary.id}/transactions/${kind}`, { clientId, matterId, amount, reference: ref, description: description || null, transactionDate: date });
    res.steps.push(`${kind} ${ref.slice(0, 40)} ${amount} ${date} → ${t.status}`);
    return t;
  };
  // (b) deposits
  for (let i = 1; i <= 22; i++) await post("deposit", `667A-H-${String(i).padStart(2, "0")}`, K.id, res.matters.khumalo, 100 + i, `2026-11-${String(1 + i).padStart(2, "0")}`, "667A seed — client history page 2");
  await post("deposit", K10_SPACED, K.id, res.matters.khumalo, 250, "2026-11-25", "667A seed — K-10 spaced 150-char reference");
  await post("deposit", K10_TOKEN, K.id, res.matters.khumalo, 260, "2026-11-25", "667A seed — K-10 unbroken 150-char reference");
  await post("deposit", "667A-ACME-DEP", A.id, res.matters.acme, 2000, "2026-11-24", "667A seed — Acme deposit");
  // (c) payments
  await post("payment", "667A-ACME-PAY", A.id, res.matters.acme, 2000, "2026-11-24", "667A seed — Acme to zero (nonZeroOnly)");
  await post("payment", "667A-REV-PAY", K.id, res.matters.khumalo, 750, "2026-11-24", "667A seed — reversal target");
  await post("payment", "667A-AWAIT-0", N.id, res.matters.ndlovu, 1500, "2026-11-26", "667A seed — awaiting, no approval");
  await post("payment", "667A-AWAIT-1", N.id, res.matters.ndlovu, 2500, "2026-11-26", "667A seed — awaiting, first approval by Alice (UI)");
  have = await byRef();
  for (const ref of ["667A-ACME-PAY", "667A-REV-PAY"]) {
    let t = have.get(ref);
    if (t.status === "AWAITING_APPROVAL" && !t.approvedBy) { t = await L.apiWrite(ta, "POST", `/api/trust-transactions/${t.id}/approve`); res.steps.push(`${ref} approve Alice → ${t.status}`); }
    if (t.status === "AWAITING_APPROVAL" && t.approvedBy) { t = await L.apiWrite(tb, "POST", `/api/trust-transactions/${t.id}/approve`); res.steps.push(`${ref} approve Bob → ${t.status}`); }
  }
  have = await byRef();

  // (d) UI: first approval of AWAIT-1, reversal of REV-PAY
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const page = await ctx.newPage();
  const cons = [];
  const lbl = { label: "seed" };
  L.watch(page, cons, lbl);
  const url = `${L.TA}/transactions`;
  const rowState = (id) => page.evaluate((id) => {
    const r = document.querySelector(`[data-testid="transaction-row-${id}"]`);
    if (!r) return null;
    return { text: r.innerText.replace(/\s+/g, " ").trim(), buttons: [...r.querySelectorAll("button")].map((b) => `${b.innerText.trim()}${b.disabled ? " [disabled]" : ""}${b.title ? ` (title: ${b.title})` : ""}`), testids: [...r.querySelectorAll("[data-testid]")].map((e) => e.dataset.testid.replace(id, "<id>")) };
  }, id);
  const shot = async (name, loc) => { const f = path.join(L.RAW, `seed-${name}.png`); await (loc || page).screenshot({ path: f, ...(loc ? {} : { fullPage: false }) }); return f; };
  const toasts = () => page.evaluate(() => [...document.querySelectorAll("[data-sonner-toast]")].map((t) => t.innerText.replace(/\s+/g, " ").trim()));
  const ui = (res.ui = {});

  const aw1 = have.get("667A-AWAIT-1");
  if (aw1.status === "AWAITING_APPROVAL" && !aw1.approvedBy) {
    lbl.label = "seed approve AWAIT-1";
    await page.goto(`${url}?status=AWAITING_APPROVAL`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    const row = page.getByTestId(`transaction-row-${aw1.id}`);
    await row.scrollIntoViewIfNeeded();
    ui.approve = { before: await rowState(aw1.id) };
    await shot("approve-1-before", row);
    await row.getByTestId("approve-button").click();
    await page.waitForSelector("[data-sonner-toast]", { timeout: 20000 }).catch(() => {});
    await page.waitForTimeout(1500);
    ui.approve.toasts = await toasts();
    ui.approve.afterNoReload = await rowState(aw1.id);
    await shot("approve-2-after-noreload");
    await page.reload({ waitUntil: "load" });
    await page.waitForTimeout(800);
    ui.approve.afterReload = await rowState(aw1.id);
    await shot("approve-3-after-reload", page.getByTestId(`transaction-row-${aw1.id}`));
    res.steps.push(`UI first approval of 667A-AWAIT-1 by Alice; toasts ${JSON.stringify(ui.approve.toasts)}`);
  } else res.steps.push(`667A-AWAIT-1 already ${aw1.status} approvedBy ${aw1.approvedBy}`);

  const rp = have.get("667A-REV-PAY");
  if (rp.status === "APPROVED") {
    lbl.label = "seed reverse REV-PAY";
    await page.goto(url, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    const row = page.getByTestId(`transaction-row-${rp.id}`);
    await row.scrollIntoViewIfNeeded();
    ui.reversal = { before: await rowState(rp.id), totalBefore: await page.locator("text=/\\d+ transactions? found/").first().innerText() };
    await shot("reverse-1-before", row);
    await row.getByTestId("reverse-button").click();
    const dlg = page.getByRole("dialog");
    await dlg.waitFor();
    await page.waitForTimeout(400);
    ui.reversal.dialog = await dlg.evaluate((d) => ({ text: d.innerText.replace(/\s+/g, " ").trim(), testids: [...d.querySelectorAll("[data-testid]")].map((e) => e.dataset.testid), dateMin: d.querySelector('[data-testid="reversal-date-input"]')?.min }));
    await page.getByTestId("reversal-reason-input").fill("667A OBS reversal — seeded payment reversed through the UI");
    await shot("reverse-2-dialog");
    await page.getByTestId("confirm-reverse-button").click();
    await dlg.waitFor({ state: "hidden", timeout: 30000 });
    await page.waitForTimeout(2000);
    ui.reversal.toasts = await toasts();
    ui.reversal.afterNoReload = await rowState(rp.id);
    ui.reversal.totalAfterNoReload = await page.locator("text=/\\d+ transactions? found/").first().innerText();
    await shot("reverse-3-after-noreload");
    await page.reload({ waitUntil: "load" });
    await page.waitForTimeout(800);
    ui.reversal.afterReload = await rowState(rp.id);
    ui.reversal.totalAfterReload = await page.locator("text=/\\d+ transactions? found/").first().innerText();
    await shot("reverse-4-after-reload");
    res.steps.push(`UI reversal of 667A-REV-PAY by Alice; toasts ${JSON.stringify(ui.reversal.toasts)}`);
  } else res.steps.push(`667A-REV-PAY already ${rp.status}`);
  ui.console = cons;
  await browser.close();

  // final ids
  const fin = await L.allTransactions(ta, primary.id);
  const f = (ref) => fin.find((t) => t.reference === ref);
  const rev = fin.find((t) => t.transactionType === "REVERSAL" && t.reversalOf === f("667A-REV-PAY").id) || fin.find((t) => t.transactionType === "REVERSAL" && /667A-REV-PAY/.test(t.reference));
  res.ids = {
    k10Spaced: f(K10_SPACED).id, k10Token: f(K10_TOKEN).id, acmeDep: f("667A-ACME-DEP").id, acmePay: f("667A-ACME-PAY").id,
    revPay: f("667A-REV-PAY").id, reversalRow: rev && rev.id, reversalRef: rev && rev.reference, await0: f("667A-AWAIT-0").id, await1: f("667A-AWAIT-1").id,
    historyDeposits: Array.from({ length: 22 }, (_, i) => f(`667A-H-${String(i + 1).padStart(2, "0")}`).id),
    // another eligible row whose Reverse dialog 01-capture opens and cancels (non-destructive)
    reverseProbe: f("667A-H-22").id,
  };
  res.refs = { k10Spaced: K10_SPACED, k10Token: K10_TOKEN };
  res.final = { txCount: fin.length, states: Object.fromEntries(["667A-ACME-PAY", "667A-REV-PAY", "667A-AWAIT-0", "667A-AWAIT-1"].map((r) => [r, { status: f(r).status, approvedBy: f(r).approvedBy, secondApprovedBy: f(r).secondApprovedBy }])), reversal: rev && { id: rev.id, ref: rev.reference, status: rev.status, date: rev.transactionDate, amount: rev.amount } };
  const acct = (await L.apiGet(ta, `/api/trust-accounts/${primary.id}`)).json;
  res.account.requireDualApprovalAfter = acct.requireDualApproval;
  res.account.thresholdAfter = acct.paymentApprovalThreshold;
  res.ledgers = L.list((await L.apiGet(ta, `/api/trust-accounts/${primary.id}/client-ledgers?size=100`)).json).map((l) => ({ clientId: l.clientId, clientName: l.clientName, balance: l.balance, investedBalance: l.investedBalance }));
  res.log = log;
  fs.writeFileSync(L.SEED_FILE, JSON.stringify(res));
  console.log(JSON.stringify({ ids: res.ids, final: res.final, ledgers: res.ledgers, account: res.account }, null, 1));
}
main().catch((e) => { console.error("SEED FAIL", e); process.exit(1); });
