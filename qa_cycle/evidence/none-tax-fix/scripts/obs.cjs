// "None" tax fix — OBS through the UI (real Chromium via the Playwright library; never the
// founder's Playwright-MCP Chrome). E2E mock-auth stack: app :3001, backend :8081, IDP :8090.
// As Alice, with the org's VAT rate as the DEFAULT rate (the case the bug needs):
//   pre. Generate a draft fee note from a client's unbilled time (its time lines get default VAT).
//   1. Add a line R 100,00 choosing "None" → the line has no tax; VAT unchanged; totals match.
//   2. Add a line R 200,00 choosing VAT → VAT +15% of it. Edit that line to "None", Save → the
//      VAT row and Total drop back WITHOUT a reload; then reload → still correct.
//   2c. Edit every remaining taxed line to "None" (the note leaves per-line mode) → without a
//      reload: VAT 0, Total == Subtotal, the header Tax Amount field shows 0 (not a stale value);
//      Save Changes without a reload → still VAT 0.
//   3. Approve → approved totals: VAT 0, Total == Subtotal.
// If VAT is not the default rate, it is made default first and restored in `finally`.
// Totals: the page's totals block (DOM) and read-only GET /api/invoices/{id}. No SQL.
// Writes ../obs.json and ../*.png. Usage: node obs.cjs
const path = require("path");
const fs = require("fs");

const W = path.resolve(__dirname, "../../../..");
const { chromium } = require(path.join(W, "frontend/node_modules/@playwright/test"));
const APP = "http://localhost:3001";
const API = "http://localhost:8081";
const IDP = "http://localhost:8090";
const ORG = "e2e-test-org";
const BASE = `${APP}/org/${ORG}`;
const OUT = path.resolve(__dirname, "..");
const FILE = path.join(OUT, "obs.json");
const NONE_LINE = "OBS None line";
const VAT_LINE = "OBS VAT line";

const out = { at: new Date().toISOString(), source: null, invoiceId: null, taxRate: null, steps: [], console: [], checks: [], notes: [] };
const check = (name, ok, detail) => out.checks.push({ name, ok: !!ok, detail });
const save = () => fs.writeFileSync(FILE, JSON.stringify(out, null, 2));
const r2 = (n) => Math.round(n * 100) / 100;
const money = (s) => (s == null ? null : Number(String(s).replace(/[^\d,.-]/g, "").replace(/\./g, "").replace(",", ".")));

async function token() {
  const r = await fetch(`${IDP}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: ORG, orgRole: "owner" }),
  });
  return (await r.json()).access_token;
}
async function get(tok, p) {
  const r = await fetch(`${API}${p}`, { headers: { Authorization: `Bearer ${tok}` } });
  if (!r.ok) throw new Error(`GET ${p} -> ${r.status}`);
  return r.json();
}
const apiTotals = (inv) => ({
  status: inv.status,
  hasPerLineTax: inv.hasPerLineTax,
  taxInclusive: inv.taxInclusive,
  subtotal: inv.subtotal,
  taxAmount: inv.taxAmount,
  total: inv.total,
  lineSum: r2((inv.lines ?? []).reduce((s, l) => s + (l.amount ?? 0), 0)),
  lineTaxSum: r2((inv.lines ?? []).reduce((s, l) => s + (l.taxAmount ?? 0), 0)),
  lines: (inv.lines ?? []).map((l) => ({ id: l.id, description: l.description.slice(0, 80), amount: l.amount, taxRateName: l.taxRateName ?? null, taxAmount: l.taxAmount ?? null })),
  taxBreakdown: (inv.taxBreakdown ?? []).map((b) => ({ rate: `${b.rateName} (${b.ratePercent}%)`, taxAmount: b.taxAmount })),
});

(async () => {
  const tok = await token();
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: "Africa/Johannesburg", reducedMotion: "reduce" });
  const page = await ctx.newPage();
  page.on("console", (m) => {
    if (m.type() === "error") out.console.push(m.text().slice(0, 300));
  });
  page.on("pageerror", (e) => out.console.push(String(e).slice(0, 300)));
  const main = page.locator("#main-content");
  let rate = null;
  let restoreDefaultOff = false;

  const setDefault = async (on) => {
    await page.goto(`${BASE}/settings/fees?tab=tax`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await main.getByRole("button", { name: `Edit ${rate.name}`, exact: true }).click();
    const dlg = page.getByRole("dialog");
    const sw = dlg.locator("#edit-tax-rate-default");
    const isOn = (await sw.getAttribute("aria-checked")) === "true";
    if (isOn !== on) await sw.click();
    await dlg.getByRole("button", { name: "Save", exact: true }).click();
    await dlg.waitFor({ state: "hidden", timeout: 20000 });
    for (let i = 0; i < 20; i++) {
      const now = (await get(tok, "/api/tax-rates")).find((t) => t.id === rate.id);
      if (now.isDefault === on) return;
      await page.waitForTimeout(500);
    }
    throw new Error(`default flag did not become ${on}`);
  };

  try {
    await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
    await page.getByRole("button", { name: "Sign In", exact: true }).click();
    await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });

    // --- pre: VAT is the org's default rate ---------------------------------------------------
    const rates = await get(tok, "/api/tax-rates");
    rate = rates.find((t) => t.isDefault && t.active && !t.isExempt && t.rate > 0) ?? rates.find((t) => t.active && !t.isExempt && t.rate > 0);
    if (!rate) throw new Error("no active VAT rate");
    out.taxRate = { id: rate.id, name: rate.name, rate: rate.rate, wasDefault: rate.isDefault };
    if (!rate.isDefault) {
      restoreDefaultOff = true; // set before the mutation
      await setDefault(true);
    }
    const def = (await get(tok, "/api/tax-rates")).find((t) => t.isDefault && t.active);
    check("pre: VAT is the default tax rate", def && def.id === rate.id, def ?? null);

    // --- pre: a draft from unbilled time -------------------------------------------------------
    let invoiceId = null;
    const clients = await get(tok, "/api/clients");
    let client = null;
    for (const c of clients) {
      const u = await get(tok, `/api/clients/${c.id}/unbilled-time`).catch(() => null);
      const zar = u?.matters?.flatMap((m) => m.entries).filter((e) => e.billingRateCurrency === "ZAR") ?? [];
      if (zar.length > 0) {
        client = { id: c.id, name: c.name, unbilledZarEntries: zar.length };
        break;
      }
    }
    if (!client) throw new Error("no client with unbilled ZAR time");
    out.source = { generatedFrom: client };
    const before = new Set((await get(tok, `/api/invoices?clientId=${client.id}`)).map((i) => i.id));
    await page.goto(`${BASE}/clients/${client.id}?tab=invoices`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await main.getByRole("button", { name: "New fee note", exact: true }).click();
    const gen = page.getByRole("dialog");
    await gen.getByRole("button", { name: "Fetch Unbilled Time", exact: true }).click();
    await gen.getByRole("button", { name: "Validate & Create Draft", exact: true }).click();
    await gen.getByRole("button", { name: /^Create Draft/ }).click();
    await gen.waitFor({ state: "hidden", timeout: 30000 });
    for (let i = 0; i < 20 && !invoiceId; i++) {
      const now = await get(tok, `/api/invoices?clientId=${client.id}&status=DRAFT`);
      invoiceId = now.map((x) => x.id).find((id) => !before.has(id)) ?? null;
      if (!invoiceId) await page.waitForTimeout(500);
    }
    if (!invoiceId) throw new Error("generated draft not found");
    out.invoiceId = invoiceId;

    const open = async () => {
      await page.goto(`${BASE}/invoices/${invoiceId}`, { waitUntil: "load" });
      await page.waitForLoadState("networkidle").catch(() => {});
      await main.getByText("Subtotal", { exact: true }).first().waitFor({ timeout: 20000 });
      await page.waitForTimeout(400);
    };
    const domTotals = async () => {
      const block = main.locator("div.max-w-xs").filter({ has: page.getByText("Subtotal", { exact: true }) }).last();
      await block.scrollIntoViewIfNeeded();
      const rows = await block.locator(":scope > div").evaluateAll((els) =>
        els.map((e) => Array.from(e.children).map((c) => c.textContent.trim().replace(/ /g, " ")))
      );
      const pairs = rows.filter((r) => r.length === 2);
      const dom = Object.fromEntries(pairs);
      const taxRows = pairs.filter(([k]) => !["Subtotal", "Total", "Amount paid", "Balance due"].includes(k));
      dom.parsed = { subtotal: money(dom.Subtotal), tax: r2(taxRows.reduce((s, [, v]) => s + money(v), 0)), total: money(dom.Total), taxRows: taxRows.map(([k]) => k) };
      const headerTaxField = main.locator("#invoice-tax-amount");
      dom.headerTaxField = (await headerTaxField.count()) > 0 ? await headerTaxField.inputValue() : null;
      return { dom, block };
    };
    // reload=false reads the page as the save left it (no navigation) — the no-reload check.
    const snap = async (name, shot, { reload = true } = {}) => {
      if (reload) await open();
      const { dom, block } = await domTotals();
      await page.screenshot({ path: path.join(OUT, `${shot}.png`), fullPage: true });
      await block.screenshot({ path: path.join(OUT, `${shot}-totals.png`) });
      const api = apiTotals(await get(tok, `/api/invoices/${invoiceId}`));
      const step = { name, reloaded: reload, at: new Date().toISOString(), dom, api };
      out.steps.push(step);
      save();
      return step;
    };
    const domMatchesApi = (s) => s.dom.parsed.subtotal === s.api.subtotal && s.dom.parsed.tax === s.api.taxAmount && s.dom.parsed.total === s.api.total;
    const waitForInvoiceCall = () =>
      page.waitForResponse((r) => r.request().method() === "POST" && r.url().includes(`/invoices/${invoiceId}`), { timeout: 30000 }).catch(() => null);
    const pickTax = async (form, optionName) => {
      await form.getByRole("combobox").click();
      await page.getByRole("option", { name: optionName, exact: true }).click();
    };
    const rateOption = `${rate.name} (${rate.rate}%)`;
    const settle = async () => {
      await page.waitForLoadState("networkidle").catch(() => {});
      await page.waitForTimeout(1200);
    };
    const addLine = async (description, amount, optionName) => {
      await main.getByRole("button", { name: "Add Line", exact: true }).click();
      const form = main.locator("div.rounded-lg").filter({ has: page.getByRole("heading", { name: "Add Line Item", exact: true }) });
      await form.getByPlaceholder("Line item description").fill(description);
      const nums = form.locator('input[type="number"]');
      await nums.nth(0).fill("1");
      await nums.nth(1).fill(String(amount));
      out.notes.push(`add "${description}": tax select pre-selected "${await form.getByRole("combobox").textContent()}"`);
      await pickTax(form, optionName);
      const call = waitForInvoiceCall();
      await form.getByRole("button", { name: "Add", exact: true }).click();
      await call;
      await form.waitFor({ state: "hidden", timeout: 20000 });
      await settle();
    };
    const editLineToNone = async (description) => {
      await main.getByRole("button", { name: `Edit ${description}`, exact: true }).first().click();
      const form = main.locator("div.rounded-lg").filter({ has: page.getByRole("heading", { name: "Edit Line Item", exact: true }) });
      await pickTax(form, "None");
      const call = waitForInvoiceCall();
      await form.getByRole("button", { name: "Save", exact: true }).click();
      await call;
      await form.waitFor({ state: "hidden", timeout: 20000 });
      await settle();
    };

    const s0 = await snap("0-draft-from-time-default-vat", "00-draft");
    check("pre: draft totals consistent (total == subtotal + VAT)", s0.api.total === r2(s0.api.subtotal + s0.api.taxAmount) && !s0.api.taxInclusive, s0.api);

    // --- 1. add a line with "None" ---------------------------------------------------------------
    await addLine(NONE_LINE, 100, "None");
    const s1live = await snap("1-none-line-added (no reload)", "01-none-line-added-live", { reload: false });
    const s1 = await snap("1-none-line-added (reloaded)", "01-none-line-added");
    const noneLine = s1.api.lines.find((l) => l.description === NONE_LINE);
    check("step 1: the None line carries no tax rate and no tax", noneLine && noneLine.taxRateName === null && noneLine.taxAmount === null, noneLine);
    check("step 1: VAT unchanged by the None line", s1.api.taxAmount === s0.api.taxAmount && s1.api.taxAmount === s1.api.lineTaxSum, { before: s0.api.taxAmount, after: s1.api.taxAmount });
    check("step 1: subtotal +100, total == subtotal + VAT", s1.api.subtotal === r2(s0.api.subtotal + 100) && s1.api.total === r2(s1.api.subtotal + s1.api.taxAmount), s1.api);
    check("step 1: DOM totals == API (no reload)", domMatchesApi(s1live), s1live.dom.parsed);
    check("step 1: DOM totals == API (reloaded)", domMatchesApi(s1), s1.dom.parsed);

    // --- 2. add a VAT line, then edit it to "None" -----------------------------------------------
    await addLine(VAT_LINE, 200, rateOption);
    const s2a = await snap("2a-vat-line-added", "02a-vat-line-added");
    const vat = r2((200 * rate.rate) / 100);
    check("step 2a: VAT +15% of the VAT line", s2a.api.taxAmount === r2(s1.api.taxAmount + vat), { vat, api: s2a.api.taxAmount });
    await editLineToNone(VAT_LINE);
    const s2live = await snap("2b-vat-line-edited-to-none (no reload)", "02b-vat-line-none-live", { reload: false });
    check("step 2b: no reload — DOM VAT and Total dropped back", s2live.dom.parsed.tax === s1.api.taxAmount && s2live.dom.parsed.total === r2(s2live.api.subtotal + s1.api.taxAmount), s2live.dom.parsed);
    check("step 2b: no reload — DOM == API", domMatchesApi(s2live), { dom: s2live.dom.parsed, api: s2live.api });
    const s2 = await snap("2b-vat-line-edited-to-none (reloaded)", "02b-vat-line-none");
    const editedLine = s2.api.lines.find((l) => l.description === VAT_LINE);
    check("step 2b: the edited line has no rate and no tax", editedLine && editedLine.taxRateName === null && editedLine.taxAmount === null, editedLine);
    check("step 2b: reloaded — VAT back to before the VAT line, totals match", s2.api.taxAmount === s1.api.taxAmount && s2.api.total === r2(s2.api.subtotal + s2.api.taxAmount) && domMatchesApi(s2), s2.api);

    // --- 2c. edit the remaining taxed lines to "None" (leave per-line mode) ----------------------
    for (let i = 0; i < 30; i++) {
      const taxed = (await get(tok, `/api/invoices/${invoiceId}`)).lines.find((l) => l.taxRateId != null);
      if (!taxed) break;
      await editLineToNone(taxed.description);
    }
    const s2clive = await snap("2c-all-lines-none (no reload)", "02c-all-lines-none-live", { reload: false });
    check("step 2c: no reload — per-line tax off, VAT 0, Total == Subtotal", !s2clive.api.hasPerLineTax && s2clive.api.taxAmount === 0 && s2clive.dom.parsed.tax === 0 && s2clive.dom.parsed.total === s2clive.dom.parsed.subtotal, { dom: s2clive.dom.parsed, api: s2clive.api });
    check("step 2c: no reload — header Tax Amount field shows 0 (not stale)", s2clive.dom.headerTaxField !== null && Number(s2clive.dom.headerTaxField) === 0, s2clive.dom.headerTaxField);
    const saveCall = waitForInvoiceCall();
    await main.getByRole("button", { name: "Save Changes", exact: true }).click();
    await saveCall;
    await settle();
    const s2csaved = await snap("2c-save-changes-without-reload (then reloaded)", "02c-after-save-changes");
    check("step 2c: Save Changes without a reload keeps VAT 0", s2csaved.api.taxAmount === 0 && s2csaved.api.total === s2csaved.api.subtotal && domMatchesApi(s2csaved), s2csaved.api);

    // --- 3. approve -------------------------------------------------------------------------------
    await main.getByRole("button", { name: "Approve", exact: true }).click();
    for (let i = 0; i < 30; i++) {
      if ((await get(tok, `/api/invoices/${invoiceId}`)).status === "APPROVED") break;
      await page.waitForTimeout(500);
    }
    const s3 = await snap("3-approved", "03-approved");
    check("step 3: approved", s3.api.status === "APPROVED", s3.api.status);
    check("step 3: approved totals — VAT 0, Total == Subtotal, == pre-approval", s3.api.taxAmount === 0 && s3.api.total === s3.api.subtotal && s3.api.subtotal === s2csaved.api.subtotal, s3.api);
    check("step 3: DOM totals == API", domMatchesApi(s3), s3.dom.parsed);
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
    await page.screenshot({ path: path.join(OUT, "zz-error.png"), fullPage: true }).catch(() => {});
  } finally {
    if (restoreDefaultOff) {
      try {
        await setDefault(false);
        out.notes.push(`post: ${rate.name} default flag restored to off`);
      } catch (e) {
        out.restoreError = String(e);
      }
    }
    out.allChecksPass = !out.error && !out.restoreError && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    console.log(
      JSON.stringify(
        {
          allChecksPass: out.allChecksPass,
          error: out.error ?? null,
          failed: out.checks.filter((c) => !c.ok).map((c) => c.name),
          checks: `${out.checks.filter((c) => c.ok).length}/${out.checks.length}`,
          notes: out.notes,
          steps: out.steps.map((s) => ({ name: s.name, sub: s.api.subtotal, tax: s.api.taxAmount, total: s.api.total, dom: s.dom.parsed, headerTaxField: s.dom.headerTaxField })),
        },
        null,
        2
      )
    );
  }
})();
