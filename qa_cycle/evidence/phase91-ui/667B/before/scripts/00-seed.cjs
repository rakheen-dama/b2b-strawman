// 667B step 0: seed — RUN ONCE (PHASE=before). Idempotent: the statement is found again by its file
// name and every draft by its period end, so a re-run changes nothing. Always writes before/seed.json
// (the ids 01-capture reads), whatever PHASE is.
// No SQL. The REST calls the reconciliation wizard makes, as Alice (MANAGE_TRUST):
//  (a) step 2 "Upload": POST /api/trust-accounts/{primary}/bank-statements (multipart `file`, the
//      uploadBankStatement() call) — one CSV, "667B-open-statement-2026-11.csv", two lines dated
//      2–3 Nov 2026 (nothing in the books matches them). It stays an open (not reconciled) statement:
//      the "Statements Not Yet Reconciled" table and its Discard dialog need one.
//  (b) step 3 → "Review Matches": POST /api/trust-accounts/{primary}/reconciliations {periodEnd,
//      bankStatementId} (get-or-create a DRAFT) then POST /api/trust-reconciliations/{id}/calculate
//      {expectedBankStatementId} — for 22 period ends, 5..26 Nov 2026 (the period end is the firm's
//      choice; the statement fits each: its last line is 3 Nov and no other statement has a line
//      after it). With the existing COMPLETED 31 Oct reconciliation that is 23 rows = 2 pages of 20.
//  (c) Record Deposit (POST …/transactions/deposit, the RecordDepositDialog call): ONE deposit
//      "667B-AFTER-LOCK", R 500, Khumalo Holdings, dated 28 Oct 2026 — inside the month whose Rule
//      54.15 list is locked. A backdated entry is "recorded and shown against the list, never
//      refused" (TrustIntegrityService BACKDATED_AFTER_LOCK); it is what puts a row in the monthly
//      list's "Entered after this list was locked" table (lists/[listId]/page.tsx:185), which is
//      otherwise empty on this stack.
// Nothing is auto-matched, completed or discarded.
const fs = require("fs");
const L = require("./lib.cjs");

const FILE = "667B-open-statement-2026-11.csv";
const CSV = [
  "Standard Bank Trust Account - Acc 0123456789",
  "Date,Description,Reference,Amount,Balance",
  "2026-11-02,Opening Balance,,0.00,103130.00",
  "2026-11-02,ACB CREDIT 667B OBS,667B-OBS-1,250.00,103380.00",
  "2026-11-03,MONTHLY SERVICE FEE 667B,,-115.00,103265.00",
].join("\n") + "\n";
const PERIOD_ENDS = Array.from({ length: 22 }, (_, i) => `2026-11-${String(5 + i).padStart(2, "0")}`);

async function main() {
  // a re-run keeps the first run's record of the data before the seed and its steps (runs[])
  const prev = fs.existsSync(L.SEED_FILE) ? JSON.parse(fs.readFileSync(L.SEED_FILE, "utf8")) : null;
  const res = { at: new Date().toISOString(), phase: L.PHASE, steps: [] };
  const ta = await L.idpToken("alice");
  const accounts = L.list((await L.apiGet(ta, "/api/trust-accounts")).json);
  const primary = accounts.filter((a) => a.accountType !== "INVESTMENT").find((a) => a.isPrimary);
  res.accounts = accounts.map((a) => ({ id: a.id, name: a.accountName, type: a.accountType, basis: a.investmentBasis, primary: a.isPrimary, lockedThrough: a.lockedThrough, requireDualApproval: a.requireDualApproval }));
  res.account = { id: primary.id, name: primary.accountName };
  const recons = async () => L.list((await L.apiGet(ta, `/api/trust-accounts/${primary.id}/reconciliations`)).json);
  const stmts = async () => L.list((await L.apiGet(ta, `/api/trust-accounts/${primary.id}/bank-statements`)).json);
  const invs = async () => L.list((await L.apiGet(ta, `/api/trust-accounts/${primary.id}/investments?page=0&size=100&sort=depositDate,desc`)).json);
  const brief = (r) => ({ id: r.id, periodEnd: r.periodEnd, status: r.status, isBalanced: r.isBalanced, monthlyListId: r.monthlyListId, bankStatementId: r.bankStatementId, bankBalance: r.bankBalance, cashbookBalance: r.cashbookBalance, clientLedgerTotal: r.clientLedgerTotal });
  res.dataBefore = {
    reconciliations: (await recons()).map(brief),
    statements: (await stmts()).map((s) => ({ id: s.id, fileName: s.fileName, status: s.status, periodStart: s.periodStart, periodEnd: s.periodEnd, lineCount: s.lineCount })),
    investments: (await invs()).map((i) => ({ id: i.id, client: i.clientName, basis: i.investmentBasis, status: i.status, principal: i.principal, balance: i.balance, awaitingApproval: i.awaitingApproval })),
  };
  console.log("before", JSON.stringify(res.dataBefore).slice(0, 800));

  // (a) the open statement
  let st = (await stmts()).find((s) => s.fileName === FILE);
  if (!st) {
    st = await L.apiUpload(ta, `/api/trust-accounts/${primary.id}/bank-statements`, FILE, CSV);
    res.steps.push(`imported ${FILE} → ${st.id} ${st.status} ${st.periodStart}..${st.periodEnd} lines ${st.lineCount}`);
  } else res.steps.push(`statement ${FILE} already ${st.id} ${st.status}`);
  res.csv = { fileName: FILE, text: CSV };

  // (b) the drafts
  const have = new Map((await recons()).map((r) => [r.periodEnd, r]));
  for (const pe of PERIOD_ENDS) {
    if (have.has(pe)) { res.steps.push(`draft ${pe} already ${have.get(pe).id}`); continue; }
    const r = await L.apiWrite(ta, "POST", `/api/trust-accounts/${primary.id}/reconciliations`, { periodEnd: pe, bankStatementId: st.id });
    const c = await L.apiWrite(ta, "POST", `/api/trust-reconciliations/${r.id}/calculate`, { expectedBankStatementId: st.id });
    res.steps.push(`draft ${pe} → ${r.id} ${c.status} balanced ${c.isBalanced}`);
  }

  // (c) the backdated deposit (after the lock)
  const txs = L.list((await L.apiGet(ta, `/api/trust-accounts/${primary.id}/transactions?size=500&sort=transactionDate,desc`)).json);
  let dep = txs.find((t) => t.reference === "667B-AFTER-LOCK");
  if (!dep) {
    const clients = L.list((await L.apiGet(ta, "/api/clients?size=200")).json);
    const matters = L.list((await L.apiGet(ta, "/api/matters?size=200")).json);
    const K = clients.find((x) => x.name === "Khumalo Holdings");
    const KM = matters.find((m) => (m.clientId || m.customerId) === K.id);
    dep = await L.apiWrite(ta, "POST", `/api/trust-accounts/${primary.id}/transactions/deposit`, { clientId: K.id, matterId: KM.id, amount: 500, reference: "667B-AFTER-LOCK", description: "667B seed — backdated into the locked October list", transactionDate: "2026-10-28" });
    res.steps.push(`deposit 667B-AFTER-LOCK 500 2026-10-28 Khumalo → ${dep.id} ${dep.status}`);
  } else res.steps.push(`deposit 667B-AFTER-LOCK already ${dep.id} ${dep.status}`);
  res.afterLockDeposit = { id: dep.id, status: dep.status, date: dep.transactionDate, amount: dep.amount };

  const fin = await recons();
  const completed = fin.filter((r) => r.status === "COMPLETED");
  res.final = { reconciliations: fin.map(brief), statements: (await stmts()).map((s) => ({ id: s.id, fileName: s.fileName, status: s.status })) };
  res.ids = {
    accountId: primary.id,
    statementId: st.id,
    completedId: completed[0] && completed[0].id,
    monthlyListId: completed[0] && completed[0].monthlyListId,
    afterLockDepositId: dep.id,
    draftIds: fin.filter((r) => r.status === "DRAFT").map((r) => r.id),
    investments: res.dataBefore.investments.map((i) => i.id),
    investmentAccounts: accounts.filter((a) => a.accountType === "INVESTMENT").map((a) => a.id),
  };
  res.counts = { reconciliations: fin.length, pages: Math.ceil(fin.length / 20) };
  if (prev) { res.dataBefore = prev.dataBefore; res.runs = [...(prev.runs || [{ at: prev.at, steps: prev.steps }]), { at: res.at, steps: res.steps }]; }
  else res.runs = [{ at: res.at, steps: res.steps }];
  fs.writeFileSync(L.SEED_FILE, JSON.stringify(res));
  console.log(JSON.stringify({ ids: res.ids, counts: res.counts, steps: res.steps.slice(0, 4) }, null, 1));
}
main().catch((e) => { console.error("SEED FAIL", e); process.exit(1); });
