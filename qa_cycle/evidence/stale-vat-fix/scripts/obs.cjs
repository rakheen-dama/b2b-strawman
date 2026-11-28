// Stale-VAT fix — OBS through the UI (real Chromium via the Playwright library; never the
// founder's Playwright-MCP Chrome). E2E mock-auth stack: app :3001, backend :8081, IDP :8090.
// As Alice:
//   pre. Settings › Fees › Tax: switch the default VAT rate's "default" off, so new lines can be
//        untaxed (with a default rate, "None" on a new line still gets the default — see PR).
//   pre. Generate a draft fee note from a client's unbilled time — its time lines are untaxed.
//   1. Add a line R 200,00 with the VAT rate chosen explicitly → record totals (per-line VAT).
//   2a. Edit that line, choose "None", Save → reload. Record (the edit form's "None" is not sent
//       as a clear — a separate bug, recorded, not fixed here).
//   2b. Delete that line (the last taxed line) → reload: VAT gone, total == subtotal, header
//       Tax Amount field shows 0.
//   3. Change only the Due Date → Save Changes → reload: still no phantom VAT.
//   4. Add a line R 200,00 with VAT again → VAT == 15% of it, total == subtotal + VAT.
//   post. Switch the VAT rate's default back on.
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
const VAT_LINE = "OBS VAT line";
const VAT_LINE_2 = "OBS VAT line again";

const out = { at: new Date().toISOString(), source: null, invoiceId: null, taxRate: null, steps: [], console: [], checks: [], notes: [] };
const check = (name, ok, detail) => out.checks.push({ name, ok: !!ok, detail });
const save = () => fs.writeFileSync(FILE, JSON.stringify(out, null, 2));
const r2 = (n) => Math.round(n * 100) / 100;

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
  dueDate: inv.dueDate ?? null,
  hasPerLineTax: inv.hasPerLineTax,
  subtotal: inv.subtotal,
  taxAmount: inv.taxAmount,
  total: inv.total,
  lineSum: r2((inv.lines ?? []).reduce((s, l) => s + (l.amount ?? 0), 0)),
  lineTaxSum: r2((inv.lines ?? []).reduce((s, l) => s + (l.taxAmount ?? 0), 0)),
  lines: (inv.lines ?? []).map((l) => ({ description: l.description.slice(0, 60), amount: l.amount, taxRateName: l.taxRateName ?? null, taxAmount: l.taxAmount ?? null })),
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
  let defaultSwitchedOff = false;

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

    // --- pre: the default VAT rate, switched off as default -------------------------------------
    const rates = await get(tok, "/api/tax-rates");
    rate = rates.find((t) => t.isDefault && t.active && !t.isExempt && t.rate > 0) ?? rates.find((t) => t.active && !t.isExempt && t.rate > 0);
    if (!rate) throw new Error("no active VAT rate");
    out.taxRate = { id: rate.id, name: rate.name, rate: rate.rate, wasDefault: rate.isDefault };
    if (rate.isDefault) {
      await setDefault(false);
      defaultSwitchedOff = true;
    }
    const otherDefault = (await get(tok, "/api/tax-rates")).find((t) => t.isDefault && t.active);
    check("pre: no default tax rate while the draft is built", !otherDefault, otherDefault ?? null);

    // --- pre: a draft from unbilled time (untaxed time lines) -----------------------------------
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
      return { rows, block };
    };
    const snap = async (name, shot) => {
      await open();
      const { rows, block } = await domTotals();
      await page.screenshot({ path: path.join(OUT, `${shot}.png`), fullPage: true });
      await block.screenshot({ path: path.join(OUT, `${shot}-totals.png`) });
      const api = apiTotals(await get(tok, `/api/invoices/${invoiceId}`));
      const dom = Object.fromEntries(rows.filter((r) => r.length === 2));
      const headerTaxField = main.locator("#invoice-tax-amount");
      dom.headerTaxField = (await headerTaxField.count()) > 0 ? await headerTaxField.inputValue() : null;
      const step = { name, at: new Date().toISOString(), dom, api };
      out.steps.push(step);
      save();
      return step;
    };
    const waitForInvoiceCall = () =>
      page.waitForResponse((r) => r.request().method() === "POST" && r.url().includes(`/invoices/${invoiceId}`), { timeout: 30000 }).catch(() => null);
    const pickTax = async (form, optionName) => {
      await form.getByRole("combobox").click();
      await page.getByRole("option", { name: optionName, exact: true }).click();
    };
    const rateOption = `${rate.name} (${rate.rate}%)`;
    const addLine = async (description, amount, optionName) => {
      await main.getByRole("button", { name: "Add Line", exact: true }).click();
      const form = main.locator("div.rounded-lg").filter({ has: page.getByRole("heading", { name: "Add Line Item", exact: true }) });
      await form.getByPlaceholder("Line item description").fill(description);
      const nums = form.locator('input[type="number"]');
      await nums.nth(0).fill("1");
      await nums.nth(1).fill(String(amount));
      await pickTax(form, optionName);
      const call = waitForInvoiceCall();
      await form.getByRole("button", { name: "Add", exact: true }).click();
      await call;
      await form.waitFor({ state: "hidden", timeout: 20000 });
      await page.waitForTimeout(600);
    };

    const s0 = await snap("0-draft-untaxed-time-lines", "00-draft-untaxed");
    check("pre: generated draft has no per-line tax", !s0.api.hasPerLineTax && s0.api.taxAmount === 0, s0.api);
    const base = s0.api.subtotal;

    // --- 1. a VAT line, rate chosen explicitly ------------------------------------------------
    await addLine(VAT_LINE, 200, rateOption);
    const s1 = await snap("1-vat-line-added", "01-vat-line-added");
    const vat = r2((200 * rate.rate) / 100);
    check("step 1: per-line VAT on", s1.api.hasPerLineTax, s1.api);
    check("step 1: VAT == 15% of the VAT line", s1.api.taxAmount === vat, { vat, api: s1.api.taxAmount });
    check("step 1: total == subtotal + VAT", s1.api.total === r2(base + 200 + vat), s1.api);

    // --- 2a. edit that line, "None", Save -----------------------------------------------------
    await main.getByRole("button", { name: `Edit ${VAT_LINE}`, exact: true }).click();
    const editForm = main.locator("div.rounded-lg").filter({ has: page.getByRole("heading", { name: "Edit Line Item", exact: true }) });
    await pickTax(editForm, "None");
    const editCall = waitForInvoiceCall();
    await editForm.getByRole("button", { name: "Save", exact: true }).click();
    await editCall;
    await editForm.waitFor({ state: "hidden", timeout: 20000 });
    await page.waitForTimeout(600);
    const s2a = await snap("2a-edit-line-none-saved", "02a-edit-line-none");
    const vatLine = s2a.api.lines.find((l) => l.description === VAT_LINE);
    out.notes.push(
      vatLine?.taxRateName
        ? `step 2a: the edit form's "None" did not clear the line's rate (line still ${vatLine.taxRateName}, VAT ${s2a.api.taxAmount}) — separate bug, see PR "Found, not fixed"`
        : `step 2a: "None" cleared the line's rate`
    );
    if (!vatLine?.taxRateName) {
      check("step 2a: VAT gone after clearing the rate", s2a.api.taxAmount === 0 && s2a.api.total === s2a.api.subtotal, s2a.api);
    }

    // --- 2b. delete the last taxed line ---------------------------------------------------------
    if (vatLine?.taxRateName) {
      await main.getByRole("button", { name: `Delete ${VAT_LINE}`, exact: true }).click();
      for (let i = 0; i < 30; i++) {
        const inv = await get(tok, `/api/invoices/${invoiceId}`);
        if (!inv.lines.some((l) => l.description === VAT_LINE)) break;
        await page.waitForTimeout(500);
      }
      const s2b = await snap("2b-last-taxed-line-deleted", "02b-last-taxed-line-deleted");
      check("step 2b: per-line VAT off", !s2b.api.hasPerLineTax, s2b.api);
      check("step 2b: VAT 0", s2b.api.taxAmount === 0, s2b.api);
      check("step 2b: total == subtotal", s2b.api.total === s2b.api.subtotal && s2b.api.subtotal === base, s2b.api);
      check("step 2b: header Tax Amount field shows 0", Number(s2b.dom.headerTaxField) === 0, s2b.dom);
    }

    // --- 3. due date only ---------------------------------------------------------------------
    const due = new Date(Date.now() + 45 * 864e5).toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" });
    await main.locator("#invoice-due-date").fill(due);
    const saveCall = waitForInvoiceCall();
    await main.getByRole("button", { name: "Save Changes", exact: true }).click();
    await saveCall;
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(800);
    const s3 = await snap("3-after-due-date-save", "03-after-due-date-save");
    check("step 3: due date saved", s3.api.dueDate === due, { due, api: s3.api.dueDate });
    check("step 3: still VAT 0, total == subtotal", s3.api.taxAmount === 0 && s3.api.total === s3.api.subtotal, s3.api);
    check("step 3: DOM Total == DOM Subtotal", s3.dom.Total === s3.dom.Subtotal, s3.dom);

    // --- 4. VAT line again ----------------------------------------------------------------------
    await addLine(VAT_LINE_2, 200, rateOption);
    const s4 = await snap("4-vat-line-added-again", "04-vat-line-again");
    check("step 4: per-line VAT on", s4.api.hasPerLineTax, s4.api);
    check("step 4: VAT == 15% of the new line only", s4.api.taxAmount === vat && s4.api.taxAmount === s4.api.lineTaxSum, s4.api);
    check("step 4: total == subtotal + VAT", s4.api.total === r2(s4.api.subtotal + vat), s4.api);
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
    await page.screenshot({ path: path.join(OUT, "zz-error.png"), fullPage: true }).catch(() => {});
  } finally {
    if (defaultSwitchedOff) {
      try {
        await setDefault(true);
        out.notes.push(`post: ${rate.name} is the default tax rate again`);
      } catch (e) {
        out.restoreError = String(e);
      }
    }
    out.allChecksPass = !out.error && !out.restoreError && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    console.log(
      JSON.stringify(
        { allChecksPass: out.allChecksPass, error: out.error ?? null, notes: out.notes, steps: out.steps.map((s) => ({ name: s.name, sub: s.api.subtotal, tax: s.api.taxAmount, total: s.api.total, domTotal: s.dom.Total, headerTaxField: s.dom.headerTaxField })) },
        null,
        2
      )
    );
  }
})();
