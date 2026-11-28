// D-126 client-ledgers "Non-zero balances only" keeps a client holding s86(4) money — OBS through
// the UI (real Chromium via the Playwright library; never the founder's Playwright-MCP Chrome).
// E2E mock-auth stack, fresh: app :3001, backend :8081, IDP :8090. As Alice (Bob only approves the
// placement and the payment — single-approval mode refuses self-approval):
//   seed. Trust account G (if none) and a section 86(4) investment account (Settings → Trust
//         accounting), then 3 clients through the UI (New Client → Start Onboarding → template
//         matter → Record Deposit):
//         "OBS Inv Holder"  R 30 000,00 deposit, then all of it placed in s86(4) from G (Place
//                           Investment), Bob approves → general R 0,00, invested R 30 000,00;
//         "OBS Inv Plain"   R 1 000,00 deposit (control, listed either way);
//         "OBS Inv Settled" R 300,00 deposit, R 300,00 payment Bob approves → R 0,00, nothing
//                           invested (control, the one the filter hides).
//   1. Client Ledgers, unfiltered: "3 clients found"; Holder's row: General balance R 0,00,
//      Invested (s86(4)) R 30 000,00.
//   2. Non-zero balances only ON: "2 clients found" — Plain and Holder; Settled gone. UI count ==
//      API totalElements.
//   3. Non-zero balances only OFF again: "3 clients found".
// Cross-checks: read-only GETs (client-ledgers, the holder's card). No SQL.
// Writes ../obs.json and ../*.png. Usage: node obs.cjs
const path = require("path");
const fs = require("fs");

const W = path.resolve(__dirname, "../../../..");
const { chromium, expect } = require(path.join(W, "frontend/node_modules/@playwright/test"));
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
const BASE = `${APP}/org/${ORG}`;
const LIST = `${BASE}/trust-accounting/client-ledgers`;
const OUT = path.resolve(__dirname, "..");
const FILE = path.join(OUT, "obs.json");
const TEMPLATE = /^Litigation \(Personal Injury \/ General\)/;
const RUN = Date.now().toString(36).slice(-4);
const BANK = "Investec";
const IA = { name: `OBS s86(4) Investments ${RUN}`, number: `6200${RUN}01` };

const USERS = {
  alice: { userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "org:owner" },
  bob: { userId: "user_e2e_bob", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "org:admin" },
};

const out = { at: new Date().toISOString(), run: RUN, pre: null, seeded: [], steps: [], console: [], checks: [], notes: [], deferred: [] };
const check = (name, ok, detail) => {
  out.checks.push({ name, ok: !!ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"} ${name}${detail !== undefined ? " — " + JSON.stringify(detail) : ""}`);
};
const save = () => fs.writeFileSync(FILE, JSON.stringify(out, null, 2));
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
// "R 1 010,00" / "-R 5,00" (en-ZA, NBSP group separators) → number
const money = (t) => {
  const neg = /[-−]/.test(t);
  const n = Number(t.replace(/[^\d,]/g, "").replace(",", "."));
  return neg ? -n : n;
};
const today = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Johannesburg" }).format(new Date());

async function token(user) {
  const r = await fetch(`${IDP}/token`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(USERS[user]) });
  return (await r.json()).access_token;
}
async function get(tok, p) {
  const r = await fetch(`${API}${p}`, { headers: { Authorization: `Bearer ${tok}` } });
  if (!r.ok) throw new Error(`GET ${p} -> ${r.status}`);
  return r.json();
}
async function session(browser, user) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: "Africa/Johannesburg", reducedMotion: "reduce" });
  await ctx.addCookies([{ name: "mock-auth-token", value: await token(user), domain: "localhost", path: "/", httpOnly: false, sameSite: "Lax" }]);
  const page = await ctx.newPage();
  page.on("console", (m) => { if (m.type() === "error") out.console.push(`${user}: ${m.text().slice(0, 300)}`); });
  page.on("pageerror", (e) => out.console.push(`${user}: ${String(e).slice(0, 300)}`));
  return page;
}
async function selectNative(select, label) {
  const option = select.locator("option", { hasText: label }).first();
  await expect(option).toBeAttached({ timeout: 15_000 });
  const value = await option.getAttribute("value");
  await select.selectOption(value);
  await expect(select).toHaveValue(value);
}
async function idFromTestId(locator, prefix) {
  const tid = await locator.getAttribute("data-testid");
  if (!tid || !tid.startsWith(prefix)) throw new Error(`expected data-testid ${prefix}<id>, got ${tid}`);
  return tid.slice(prefix.length);
}

// ── UI flows (as the e2e helpers and trust-balanced-month.spec.ts do them) ─────────────────────
async function ensureTrustAccount(page) {
  await page.goto(`${BASE}/trust-accounting`);
  await expect(page.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeVisible({ timeout: 15_000 });
  const add = page.getByRole("button", { name: "Add Account" });
  const record = page.getByRole("link", { name: "Record Transaction" });
  await expect(add.or(record).first()).toBeVisible({ timeout: 15_000 });
  if (await add.isVisible()) {
    await add.click();
    const d = page.getByRole("dialog", { name: "Add Trust Account" });
    await d.getByLabel("Account Name").fill("OBS Invested Trust Account");
    await d.getByLabel("Bank Name").fill("Standard Bank");
    await d.getByLabel("Branch Code").fill("051001");
    await d.getByLabel("Account Number").fill("0123456789");
    await d.getByRole("button", { name: "Create Account" }).click();
    await expect(d).toBeHidden({ timeout: 15_000 });
    out.notes.push("Created the trust account G (none existed on the fresh stack).");
  }
  await expect(record).toBeVisible({ timeout: 15_000 });
}
async function createInvestmentAccount(page, acct) {
  await page.goto(`${BASE}/settings/fees?tab=trust-accounting`);
  const section = page.getByTestId("trust-accounts-section");
  await expect(section).toBeVisible({ timeout: 30_000 });
  await page.getByTestId("add-trust-account-button").click();
  const dialog = page.getByTestId("create-trust-account-dialog");
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Account Name", { exact: true }).fill(acct.name);
  await dialog.getByLabel("Bank Name", { exact: true }).fill(BANK);
  await dialog.getByLabel("Branch Code", { exact: true }).fill("580105");
  await dialog.getByLabel("Account Number", { exact: true }).fill(acct.number);
  await dialog.getByTestId("trust-account-type-select").click();
  await page.getByRole("option", { name: "Investment", exact: true }).click();
  await dialog.getByTestId("trust-account-basis-select").click();
  await page.getByRole("option").filter({ hasText: "Section 86(4) — invested on a client's instruction" }).click();
  await dialog.getByLabel("Opened Date", { exact: true }).fill(today());
  const primary = dialog.getByRole("checkbox", { name: "Set as primary trust account" });
  if (await primary.isChecked()) await primary.click();
  await expect(primary).not.toBeChecked();
  await dialog.getByRole("button", { name: "Create Account" }).click();
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await page.reload();
  await expect(section).toContainText(acct.name, { timeout: 15_000 });
}
async function createClient(page, name) {
  await page.goto(`${BASE}/clients`);
  await page.getByRole("button", { name: "New Client" }).click();
  const d = page.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
  await expect(d).toBeVisible();
  await d.getByLabel("Name", { exact: true }).fill(name);
  await selectNative(d.getByLabel("Type", { exact: true }), "Individual");
  await d.getByLabel("Email", { exact: true }).fill(`${name.toLowerCase().replace(/[^a-z0-9]+/g, ".")}.${RUN}@obs.test`);
  await d.locator('input[name="addressLine1"]').fill("12 Rivonia Road");
  await d.locator('input[name="city"]').fill("Sandton");
  await d.locator('input[name="postalCode"]').fill("2196");
  await d.getByLabel(/^Country/).selectOption("ZA");
  await d.getByRole("button", { name: "Next" }).click();
  await expect(d.getByText("Step 2 of 2")).toBeVisible();
  await d.getByRole("button", { name: "Create Client" }).click();
  await expect(page.getByRole("heading", { level: 1, name })).toBeVisible({ timeout: 15_000 });
  await expect(page).toHaveURL(/\/clients\/[0-9a-f-]{36}/);
  const id = new URL(page.url()).pathname.match(/\/clients\/([0-9a-f-]{36})/)[1];
  const badge = page.getByTestId("lifecycle-badge");
  await expect(badge).toHaveText("Prospect");
  await page.getByRole("button", { name: "Start Onboarding" }).first().click();
  const confirm = page.getByRole("alertdialog", { name: "Start Onboarding" });
  await confirm.getByRole("button", { name: "Start Onboarding" }).click();
  await expect(confirm).toBeHidden({ timeout: 15_000 });
  await expect(badge).toHaveText("Onboarding", { timeout: 15_000 });
  return id;
}
async function createMatter(page, client, matter) {
  await page.goto(`${BASE}/matters`);
  await page.getByRole("button", { name: "New from Template" }).click();
  const d = page.getByRole("dialog", { name: /New from Template/ });
  await expect(d).toBeVisible();
  await d.getByRole("option", { name: TEMPLATE }).click();
  await d.getByRole("button", { name: "Next" }).click();
  await d.getByLabel(/Matter name/).fill(matter);
  await selectNative(d.getByLabel(/^Client/), client);
  await d.getByRole("button", { name: "Create Matter" }).click();
  await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 15_000 });
  await expect(page.getByRole("heading", { level: 1, name: matter })).toBeVisible({ timeout: 15_000 });
  return new URL(page.url()).pathname.match(/\/matters\/([0-9a-f-]{36})/)[1];
}
async function openTransactions(page, filters = {}) {
  await page.goto(`${BASE}/trust-accounting/transactions?${new URLSearchParams(filters)}`);
  await expect(page.getByRole("heading", { level: 1, name: "Transactions" })).toBeVisible({ timeout: 30_000 });
}
const txRow = (page, ref) => page.locator('[data-testid^="transaction-row-"]', { hasText: ref });
async function recordTx(page, kind, { client, matter, amount, reference }) {
  await openTransactions(page);
  await page.getByTestId("record-transaction-button").click();
  await page.getByRole("menuitem", { name: `Record ${kind}`, exact: true }).click();
  const d = page.getByRole("dialog", { name: `Record ${kind}` });
  await expect(d).toBeVisible();
  const key = kind.toLowerCase();
  await d.getByTestId(`trust-${key}-customer-trigger`).click();
  await page.getByRole("option", { name: new RegExp(`^${esc(client)}(\\s|$)`) }).click();
  const mt = d.getByTestId(`trust-${key}-matter-trigger`);
  await expect(mt).toBeEnabled({ timeout: 15_000 });
  await mt.click();
  await page.getByRole("option", { name: new RegExp(esc(matter)) }).click();
  await d.getByLabel("Amount", { exact: true }).fill(amount);
  await d.getByLabel("Reference", { exact: true }).fill(reference);
  await d.getByRole("button", { name: `Record ${kind}`, exact: true }).click();
  await expect(d).toBeHidden({ timeout: 20_000 });
  await openTransactions(page);
  const row = txRow(page, reference);
  await expect(row).toHaveCount(1, { timeout: 15_000 });
  await expect(row).toContainText(kind === "Deposit" ? "RECORDED" : "AWAITING APPROVAL");
  return idFromTestId(row, "transaction-row-");
}
async function bobApproves(bob, txId) {
  await openTransactions(bob, { dateFrom: today(), dateTo: today() });
  const row = bob.getByTestId(`transaction-row-${txId}`);
  await expect(row).toContainText("AWAITING APPROVAL", { timeout: 15_000 });
  await bob.getByTestId(`approval-actions-${txId}`).getByRole("button", { name: "Approve", exact: true }).click();
  await expect(bob.getByText("Transaction approved", { exact: true })).toBeVisible({ timeout: 20_000 });
  await openTransactions(bob, { dateFrom: today(), dateTo: today() });
  await expect(row).toContainText("APPROVED");
  await expect(row).not.toContainText("AWAITING");
}
async function placeInvestment(page, { client, clientId, matterId, principal }) {
  await page.goto(`${BASE}/trust-accounting/investments`);
  await expect(page.getByTestId("investments-page")).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: "Place Investment" }).first().click();
  const dialog = page.getByTestId("place-investment-dialog");
  await expect(dialog).toBeVisible();
  // Basis FIRST: changing it clears the account, client and matter (PlaceInvestmentDialog)
  await dialog.getByRole("radio", { name: "Section 86(4) — invested on a client's instruction" }).check();
  await dialog.getByTestId("investment-account-select").click();
  await page.getByRole("option", { name: new RegExp(`^${esc(IA.name)} \\(`) }).click();
  await dialog.getByLabel("Client ID", { exact: true }).fill(clientId);
  await dialog.getByLabel("Matter ID", { exact: true }).fill(matterId);
  await dialog.getByLabel("Institution", { exact: true }).fill(BANK);
  await dialog.getByLabel("Account Number", { exact: true }).fill(IA.number);
  await dialog.getByLabel("Principal", { exact: true }).fill(principal);
  await dialog.getByLabel("Interest Rate %", { exact: true }).fill("7.5");
  await dialog.getByLabel("Deposit Date", { exact: true }).fill(today());
  await dialog.getByRole("button", { name: "Place Investment", exact: true }).click();
  await expect(dialog).toBeHidden({ timeout: 30_000 });
  await page.reload();
  const invRow = page.locator('[data-testid^="investment-row-"]').filter({ hasText: client }).filter({ hasText: IA.name });
  await expect(invRow).toHaveCount(1, { timeout: 15_000 });
  await expect(invRow).toContainText("Awaiting approval");
  const investmentId = await idFromTestId(invRow, "investment-row-");
  await openTransactions(page, { dateFrom: today(), dateTo: today(), type: "INV_PLACE" });
  const row = page.locator('[data-testid^="transaction-row-"]').filter({ hasText: IA.name });
  await expect(row).toHaveCount(1, { timeout: 15_000 });
  await expect(row).toContainText("AWAITING APPROVAL");
  return { investmentId, txId: await idFromTestId(row, "transaction-row-") };
}

// ── The list page ─────────────────────────────────────────────────────────────────────────────
async function readList(page, label) {
  const pageEl = page.getByTestId("client-ledgers-page");
  await expect(pageEl).toBeVisible({ timeout: 15_000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  const desc = (await pageEl.getByText(/\d+ clients? found/).textContent()).trim();
  const table = pageEl.getByTestId("client-ledgers-table");
  const headers = (await table.locator("thead th").allTextContents()).map((h) => h.trim());
  const rows = pageEl.locator('[data-testid^="ledger-row-"]');
  const n = await rows.count();
  const list = [];
  for (let i = 0; i < n; i++) {
    const r = rows.nth(i);
    const cells = (await r.locator("td").allTextContents()).map((c) => c.trim());
    const byHeader = Object.fromEntries(headers.map((h, j) => [h, cells[j]]));
    const bal = byHeader["General balance"] ?? byHeader["Trust Balance"];
    list.push({
      id: (await r.getAttribute("data-testid")).replace("ledger-row-", ""),
      name: cells[0],
      balance: bal === undefined ? null : money(bal),
      invested: byHeader["Invested (s86(4))"] === undefined ? null : money(byHeader["Invested (s86(4))"]),
      cells: byHeader,
    });
  }
  const shot = `${String(out.steps.length + 1).padStart(2, "0")}-${label}.png`;
  await page.screenshot({ path: path.join(OUT, shot), fullPage: true });
  const step = { label, url: page.url().replace(APP, ""), desc, headers, rowCount: n, rows: list, shot };
  out.steps.push(step);
  save();
  return step;
}

(async () => {
  const tok = await token("alice");
  const browser = await chromium.launch();
  try {
    const page = await session(browser, "alice");
    const bob = await session(browser, "bob");

    // Pre-state (read-only)
    const before = await get(tok, "/api/trust-accounts");
    out.pre = { trustAccounts: before.map((a) => ({ id: a.id, name: a.accountName, type: a.accountType, isPrimary: a.isPrimary })) };
    for (const a of before) a.ledgers = (await get(tok, `/api/trust-accounts/${a.id}/client-ledgers?size=1`)).page.totalElements;
    out.pre.ledgerCounts = before.map((a) => a.ledgers);
    save();

    // Seed through the UI
    await ensureTrustAccount(page);
    await createInvestmentAccount(page, IA);
    const seed = [
      { key: "holder", name: `OBS Inv Holder ${RUN}`, amount: "30000.00" },
      { key: "plain", name: `OBS Inv Plain ${RUN}`, amount: "1000.00" },
      { key: "settled", name: `OBS Inv Settled ${RUN}`, amount: "300.00" },
    ];
    const ids = {};
    for (const [i, c] of seed.entries()) {
      const t0 = Date.now();
      const id = await createClient(page, c.name);
      const matter = `OBS Inv Matter ${i + 1} ${RUN}`;
      const matterId = await createMatter(page, c.name, matter);
      await recordTx(page, "Deposit", { client: c.name, matter, amount: c.amount, reference: `OBS-INV-DEP-${RUN}-${i + 1}` });
      if (c.key === "holder") {
        const placed = await placeInvestment(page, { client: c.name, clientId: id, matterId, principal: c.amount });
        await bobApproves(bob, placed.txId);
        out.investmentId = placed.investmentId;
      }
      if (c.key === "settled") {
        const payId = await recordTx(page, "Payment", { client: c.name, matter, amount: c.amount, reference: `OBS-INV-PAY-${RUN}-${i + 1}` });
        await bobApproves(bob, payId);
      }
      ids[c.key] = id;
      out.seeded.push({ key: c.key, id, name: c.name, matterId, deposit: c.amount, ms: Date.now() - t0 });
      console.log(`seeded ${c.name} (${Date.now() - t0} ms)`);
      save();
    }
    const accounts = await get(tok, "/api/trust-accounts");
    const g = accounts.find((a) => a.isPrimary) ?? accounts.find((a) => a.accountType === "GENERAL");
    out.accountId = g.id;
    const preCount = before.find((a) => a.id === g.id)?.ledgers ?? 0;
    check("pre-state: the list's account had no ledgers before the seed", preCount === 0, { preCount });

    const card = await get(tok, `/api/trust-accounts/${g.id}/client-ledgers/${ids.holder}`);
    out.holderCard = { balance: card.balance, investedBalance: card.investedBalance };
    check("seed: holder's general balance is R 0 and R 30 000 is invested (card, read-only GET)", Number(card.balance) === 0 && Number(card.investedBalance) === 30000, out.holderCard);

    // 1. Unfiltered
    await page.goto(LIST);
    const all = await readList(page, "unfiltered");
    const holderRow = all.rows.find((r) => r.id === ids.holder);
    check("1. unfiltered count: 3 clients found", all.desc === "3 clients found", all.desc);
    check("1. holder row: General balance R 0,00, Invested (s86(4)) R 30 000,00", holderRow && holderRow.balance === 0 && holderRow.invested === 30000, holderRow && holderRow.cells);
    check("1. all three seeded clients listed", [ids.holder, ids.plain, ids.settled].every((id) => all.rows.some((r) => r.id === id)), all.rows.map((r) => r.name));

    // 2. Non-zero balances only ON
    await Promise.all([page.waitForURL(/nonZeroOnly=true/, { timeout: 15_000 }), page.getByTestId("non-zero-filter").click()]);
    const nz = await readList(page, "nonzero-on");
    check("2. non-zero count: 2 clients found", nz.desc === "2 clients found", nz.desc);
    check("2. the holder (general R 0, s86(4) R 30 000) is listed", nz.rows.some((r) => r.id === ids.holder), nz.rows.map((r) => r.name));
    check("2. the plain client is listed", nz.rows.some((r) => r.id === ids.plain));
    check("2. the settled client (R 0, nothing invested) is hidden", !nz.rows.some((r) => r.id === ids.settled));
    const nzHolder = nz.rows.find((r) => r.id === ids.holder);
    check("2. holder row figures unchanged under the filter", nzHolder && nzHolder.balance === 0 && nzHolder.invested === 30000, nzHolder && nzHolder.cells);
    const apiNz = await get(tok, `/api/trust-accounts/${g.id}/client-ledgers?nonZeroOnly=true&size=20`);
    check("2. UI count == API totalElements (nonZeroOnly)", nz.desc === `${apiNz.page.totalElements} clients found`, apiNz.page);

    // 3. Non-zero balances only OFF again
    await Promise.all([page.waitForURL((u) => !/nonZeroOnly=true/.test(u.toString()), { timeout: 15_000 }), page.getByTestId("non-zero-filter").click()]);
    const off = await readList(page, "nonzero-off");
    check("3. filter off again: 3 clients found", off.desc === "3 clients found", off.desc);
    const apiAll = await get(tok, `/api/trust-accounts/${g.id}/client-ledgers?size=20`);
    check("3. UI count == API totalElements (unfiltered)", off.desc === `${apiAll.page.totalElements} clients found`, apiAll.page);

    check("console: no errors", out.console.length === 0, out.console);
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e).slice(0, 2000);
    console.error(out.error);
  } finally {
    out.deferred.push("A client taken on with ONLY an s86(4) investment (657A, no CLIENT row on the funding account): not driven through the UI here — it needs the take-on wizard's file upload on an account with no postings. Covered by ClientLedgerListInvestedIntegrationTest (unfiltered + filtered + row figures). DEFERRED, not PASS.");
    out.notes.push("Restore: no setting or pre-existing record was changed; only additive seed data (an investment account, 3 clients, their matters and postings) on a fresh throwaway stack. The investment is left placed so the screenshots stay reproducible; the stack teardown wipes it.");
    out.pass = !out.error && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    console.log(out.pass ? "OBS PASS" : "OBS FAIL");
    process.exit(out.pass ? 0 : 1);
  }
})();
