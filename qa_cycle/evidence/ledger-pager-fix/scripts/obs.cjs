// F-667A-10 client-ledgers server paging — OBS through the UI (real Chromium via the Playwright
// library; never the founder's Playwright-MCP Chrome). E2E mock-auth stack, fresh: app :3001,
// backend :8081, IDP :8090. As Alice (Bob only approves the two payments — single-approval mode
// refuses self-approval):
//   seed. Trust account (if none), then 23 clients through the UI: New Client → Start Onboarding →
//         matter from the Litigation template → Record Deposit. 20 "OBS Pager Client NN"
//         (R 1 200,00 … R 1 010,00), "OBS Zebra Holdings" (R 5,00 — lowest, so page 2), and 2
//         "OBS Pager Settled NN" (R 300,00 deposit, then a R 300,00 payment Bob approves → R 0).
//   1. Client Ledgers: "23 clients found", "Page 1 of 2", 20 rows.
//   2. Next → page 2: "Page 2 of 2", the 3 remaining rows (Zebra + the 2 settled clients).
//      Pages 1 + 2 = the 23 seeded ids, no duplicate; Σ Trust Balance == GET total-balance.
//   3. Non-zero balances only: "21 clients found", page 2 holds Zebra only, no zero row.
//   4. Search "zebra" (from page 1): Zebra (page 2 unfiltered) is the one row, "1 client found".
//   5. Search "settled": 2 rows, both R 0,00. + non-zero → empty state "0 clients found".
//   6. Search "pager": "22 clients found", Page 1 of 2.
// Cross-checks: read-only GET /api/trust-accounts/{id}/client-ledgers and /total-balance. No SQL.
// Nothing pre-existing is changed: the stack is fresh (pre-state recorded in obs.json); the seed
// adds clients, matters and trust postings only, which the stack teardown wipes.
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

const USERS = {
  alice: { userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "org:owner" },
  bob: { userId: "user_e2e_bob", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "org:admin" },
};

const SEED = [
  ...Array.from({ length: 20 }, (_, i) => ({
    name: `OBS Pager Client ${String(i + 1).padStart(2, "0")}`,
    amount: String(1000 + (20 - i) * 10) + ".00",
    settle: false,
  })),
  { name: "OBS Zebra Holdings", amount: "5.00", settle: false },
  { name: "OBS Pager Settled 01", amount: "300.00", settle: true },
  { name: "OBS Pager Settled 02", amount: "300.00", settle: true },
];

const out = { at: new Date().toISOString(), run: RUN, pre: null, seeded: [], steps: [], console: [], checks: [], notes: [] };
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
const r2 = (n) => Math.round(n * 100) / 100;

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

// ── UI flows (as the e2e helpers do them) ─────────────────────────────────────────────────────
async function ensureTrustAccount(page) {
  await page.goto(`${BASE}/trust-accounting`);
  await expect(page.getByRole("heading", { level: 1, name: "Trust Accounting" })).toBeVisible({ timeout: 15_000 });
  const add = page.getByRole("button", { name: "Add Account" });
  const record = page.getByRole("link", { name: "Record Transaction" });
  await expect(add.or(record).first()).toBeVisible({ timeout: 15_000 });
  if (await add.isVisible()) {
    await add.click();
    const d = page.getByRole("dialog", { name: "Add Trust Account" });
    await d.getByLabel("Account Name").fill("OBS Pager Trust Account");
    await d.getByLabel("Bank Name").fill("Standard Bank");
    await d.getByLabel("Branch Code").fill("051001");
    await d.getByLabel("Account Number").fill("0123456789");
    await d.getByRole("button", { name: "Create Account" }).click();
    await expect(d).toBeHidden({ timeout: 15_000 });
    out.notes.push("Created the trust account (none existed on the fresh stack).");
  }
  await expect(record).toBeVisible({ timeout: 15_000 });
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
  // PROSPECT → ONBOARDING (a prospect cannot have a matter)
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
}
async function openTransactions(page) {
  await page.goto(`${BASE}/trust-accounting/transactions`);
  await expect(page.getByRole("heading", { level: 1, name: "Transactions" })).toBeVisible({ timeout: 15_000 });
}
const txRow = (page, ref) => page.locator("main tbody tr", { hasText: ref });
async function recordTx(page, kind, { client, matter, amount, reference }) {
  await openTransactions(page);
  await page.getByTestId("record-transaction-button").click();
  await page.getByRole("menuitem", { name: `Record ${kind}` }).click();
  const d = page.getByRole("dialog", { name: `Record ${kind}` });
  await expect(d).toBeVisible();
  const key = kind.toLowerCase();
  await d.getByTestId(`trust-${key}-customer-trigger`).click();
  await page.getByRole("option", { name: new RegExp(`^${esc(client)}(\\s|$)`) }).click();
  await d.getByTestId(`trust-${key}-matter-trigger`).click();
  await page.getByRole("option", { name: new RegExp(esc(matter)) }).click();
  await d.getByLabel("Amount").fill(amount);
  await d.getByLabel("Reference").fill(reference);
  await d.getByLabel(/Description/).fill(`OBS ${kind.toLowerCase()} ${client}`);
  await d.getByRole("button", { name: `Record ${kind}` }).click();
  await expect(d).toBeHidden({ timeout: 15_000 });
  await expect(txRow(page, reference)).toContainText(kind === "Deposit" ? "RECORDED" : /AWAITING/, { timeout: 15_000 });
}
async function approve(bobPage, reference) {
  await openTransactions(bobPage);
  const row = txRow(bobPage, reference);
  await row.getByTestId("approve-button").click();
  await expect(row).toContainText("APPROVED", { timeout: 15_000 });
}

// ── The list page ─────────────────────────────────────────────────────────────────────────────
async function readList(page, label) {
  const pageEl = page.getByTestId("client-ledgers-page");
  await expect(pageEl).toBeVisible({ timeout: 15_000 });
  await page.waitForLoadState("networkidle").catch(() => {});
  const desc = (await pageEl.getByText(/\d+ clients? found/).textContent()).trim();
  const pager = (await pageEl.getByText(/^Page \d+ of \d+$/).count()) > 0 ? (await pageEl.getByText(/^Page \d+ of \d+$/).textContent()).trim() : null;
  const rows = pageEl.locator('[data-testid^="ledger-row-"]');
  const n = await rows.count();
  const list = [];
  for (let i = 0; i < n; i++) {
    const r = rows.nth(i);
    const tid = await r.getAttribute("data-testid");
    const cells = r.locator("td");
    list.push({ id: tid.replace("ledger-row-", ""), name: (await cells.nth(0).textContent()).trim(), balance: money(await cells.nth(1).textContent()) });
  }
  const empty = await page.getByTestId("empty-state").count();
  const shot = `${String(out.steps.length + 1).padStart(2, "0")}-${label}.png`;
  await page.screenshot({ path: path.join(OUT, shot), fullPage: true });
  const step = { label, url: page.url().replace(APP, ""), desc, pager, rowCount: n, rows: list, emptyState: empty > 0, shot };
  out.steps.push(step);
  save();
  return step;
}
async function search(page, term) {
  const box = page.getByTestId("client-search");
  await box.fill(term);
  await Promise.all([page.waitForURL(/search=/, { timeout: 15_000 }), page.getByRole("button", { name: "Search", exact: true }).click()]);
}

(async () => {
  const alicePage = { tok: await token("alice") };
  const browser = await chromium.launch();
  try {
    const page = await session(browser, "alice");
    const bob = await session(browser, "bob");

    // Pre-state (read-only) — the fresh stack's trust accounts and their ledger counts
    const accountsBefore = await get(alicePage.tok, "/api/trust-accounts");
    out.pre = { trustAccounts: accountsBefore.map((a) => ({ id: a.id, name: a.accountName, isPrimary: a.isPrimary })) };
    for (const a of accountsBefore) {
      const l = await get(alicePage.tok, `/api/trust-accounts/${a.id}/client-ledgers?size=1`);
      a.ledgers = l.page.totalElements;
    }
    out.pre.ledgerCounts = accountsBefore.map((a) => a.ledgers);
    save();

    // Seed through the UI
    await ensureTrustAccount(page);
    for (const [i, c] of SEED.entries()) {
      const t0 = Date.now();
      const id = await createClient(page, c.name);
      const matter = `OBS Pager Matter ${String(i + 1).padStart(2, "0")} ${RUN}`;
      await createMatter(page, c.name, matter);
      await recordTx(page, "Deposit", { client: c.name, matter, amount: c.amount, reference: `OBS-DEP-${RUN}-${i + 1}` });
      if (c.settle) {
        const ref = `OBS-PAY-${RUN}-${i + 1}`;
        await recordTx(page, "Payment", { client: c.name, matter, amount: c.amount, reference: ref });
        await approve(bob, ref);
      }
      out.seeded.push({ id, name: c.name, deposit: c.amount, settled: c.settle, ms: Date.now() - t0 });
      console.log(`seeded ${i + 1}/${SEED.length} ${c.name} (${Date.now() - t0} ms)`);
      save();
    }
    const accounts = await get(alicePage.tok, "/api/trust-accounts");
    const primary = accounts.find((a) => a.isPrimary) ?? accounts[0];
    out.accountId = primary.id;
    const seededIds = new Set(out.seeded.map((s) => s.id));
    const zebra = out.seeded.find((s) => s.name === "OBS Zebra Holdings").id;
    const settledIds = out.seeded.filter((s) => s.settled).map((s) => s.id);
    const preCount = accountsBefore.find((a) => a.id === primary.id)?.ledgers ?? 0;
    const expectAll = preCount + SEED.length;
    check("pre-state: the list's account had no ledgers before the seed", preCount === 0, { preCount });

    // 1. Page 1
    await page.goto(LIST);
    const p1 = await readList(page, "page1");
    check("1. count is every ledger", p1.desc === `${expectAll} clients found`, p1.desc);
    check("1. pager reads Page 1 of 2", p1.pager === "Page 1 of 2", p1.pager);
    check("1. 20 rows on page 1", p1.rowCount === 20, p1.rowCount);

    // 2. Page 2 via Next
    await Promise.all([page.waitForURL(/page=1/, { timeout: 15_000 }), page.getByRole("link", { name: "Next", exact: true }).click()]);
    const p2 = await readList(page, "page2");
    check("2. pager reads Page 2 of 2", p2.pager === "Page 2 of 2", p2.pager);
    check("2. the remaining 3 ledgers are on page 2", p2.rowCount === 3, p2.rows.map((r) => r.name));
    check("2. Zebra and both settled clients are on page 2", [zebra, ...settledIds].every((id) => p2.rows.some((r) => r.id === id)));
    const union = [...p1.rows, ...p2.rows].map((r) => r.id);
    check("2. pages 1+2 = the seeded set, no duplicate", union.length === new Set(union).size && union.length === seededIds.size && union.every((id) => seededIds.has(id)), { union: union.length, distinct: new Set(union).size });
    const uiSum = r2([...p1.rows, ...p2.rows].reduce((s, r) => s + r.balance, 0));
    const total = await get(alicePage.tok, `/api/trust-accounts/${primary.id}/total-balance`);
    out.totalBalance = total;
    const apiTotal = Number(total.totalBalance ?? total.balance ?? total.total);
    const seedTotal = r2(SEED.filter((s) => !s.settle).reduce((s, c) => s + Number(c.amount), 0));
    check("2. Σ Trust Balance over both pages == total-balance == seeded deposits less payments", uiSum === r2(apiTotal) && uiSum === seedTotal, { uiSum, apiTotal, seedTotal });

    // 3. Non-zero balances only (from page 2: the toggle drops page)
    await Promise.all([page.waitForURL(/nonZeroOnly=true/, { timeout: 15_000 }), page.getByTestId("non-zero-filter").click()]);
    const nz1 = await readList(page, "nonzero-page1");
    check("3. non-zero count excludes the 2 settled clients", nz1.desc === `${expectAll - 2} clients found`, nz1.desc);
    check("3. non-zero pager Page 1 of 2", nz1.pager === "Page 1 of 2", nz1.pager);
    await Promise.all([page.waitForURL(/page=1/, { timeout: 15_000 }), page.getByRole("link", { name: "Next", exact: true }).click()]);
    const nz2 = await readList(page, "nonzero-page2");
    check("3. non-zero page 2 holds Zebra only", nz2.rowCount === 1 && nz2.rows[0].id === zebra, nz2.rows.map((r) => r.name));
    check("3. no zero row on either non-zero page", [...nz1.rows, ...nz2.rows].every((r) => r.balance !== 0));
    check("3. Σ non-zero balances == total-balance", r2([...nz1.rows, ...nz2.rows].reduce((s, r) => s + r.balance, 0)) === r2(apiTotal));
    const apiNz = await get(alicePage.tok, `/api/trust-accounts/${primary.id}/client-ledgers?nonZeroOnly=true&size=20`);
    check("3. UI count == API totalElements (nonZeroOnly)", nz1.desc === `${apiNz.page.totalElements} clients found`, apiNz.page);

    // 4. Search "zebra" from the unfiltered page 1
    await page.goto(LIST);
    await search(page, "zebra");
    const sz = await readList(page, "search-zebra");
    check("4. search finds Zebra (page 2 unfiltered) on page 1", sz.rowCount === 1 && sz.rows[0].id === zebra, sz.rows.map((r) => r.name));
    check("4. '1 client found', no pager", sz.desc === "1 client found" && sz.pager === null, { desc: sz.desc, pager: sz.pager });

    // 5. Search "settled", then + non-zero
    await page.goto(LIST);
    await search(page, "settled");
    const ss = await readList(page, "search-settled");
    check("5. search 'settled' → the 2 settled clients, both R 0,00", ss.rowCount === 2 && ss.rows.every((r) => settledIds.includes(r.id) && r.balance === 0) && ss.desc === "2 clients found", ss.rows);
    await Promise.all([page.waitForURL(/nonZeroOnly=true/, { timeout: 15_000 }), page.getByTestId("non-zero-filter").click()]);
    const ssnz = await readList(page, "search-settled-nonzero");
    check("5. 'settled' + non-zero → 0 found, empty state", ssnz.rowCount === 0 && ssnz.emptyState && ssnz.desc === "0 clients found", { desc: ssnz.desc, empty: ssnz.emptyState });

    // 6. Search "pager" (22 of 23)
    await page.goto(LIST);
    await search(page, "pager");
    const sp = await readList(page, "search-pager");
    check("6. search 'pager' → 22 found, Page 1 of 2", sp.desc === "22 clients found" && sp.pager === "Page 1 of 2", { desc: sp.desc, pager: sp.pager });
    const apiSp = await get(alicePage.tok, `/api/trust-accounts/${primary.id}/client-ledgers?search=pager&size=20`);
    check("6. UI count == API totalElements (search)", apiSp.page.totalElements === 22, apiSp.page);

    check("console: no errors", out.console.length === 0, out.console);
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e).slice(0, 2000);
    console.error(out.error);
  } finally {
    out.notes.push("Restore: no setting or pre-existing record was changed; only additive seed data on a fresh throwaway stack.");
    out.pass = !out.error && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    console.log(out.pass ? "OBS PASS" : "OBS FAIL");
    process.exit(out.pass ? 0 : 1);
  }
})();
