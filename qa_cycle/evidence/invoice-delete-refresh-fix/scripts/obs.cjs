// Delete/refresh stale-VAT fix — OBS through the UI (real Chromium via the Playwright library;
// never the founder's Playwright-MCP Chrome). E2E mock-auth stack: app :3001, backend :8081,
// IDP :8090. As Alice:
//   pre. Generate a draft fee note from a client's unbilled time; add "OBS None line" (R100, None)
//        and "OBS VAT line" (R200, VAT); delete the generated time lines. Fixture asserted: exactly
//        those two lines, the VAT line carries the rate, VAT > 0, per-line tax mode. Then the org's
//        default tax rate is switched off (so a tariff line comes back untaxed) and the draft opened.
//   1. Delete the VAT line → WITHOUT a reload: VAT 0, Total == Subtotal, DOM == API, and the header
//      Tax Amount field shows 0.
//   2. Add a tariff line (Add Tariff Items dialog; its success handler refreshes the note) → still
//      without a reload: the tariff line is untaxed, the header field shows 0 (not the old VAT).
//   3. Save Changes → VAT stays 0; reload → still 0, DOM == API.
// The original default tax rate is recorded and restored in `finally` (the restore flag is set
// before the mutation). Totals: page DOM and read-only GET /api/invoices/{id}. No SQL.
// Writes ../obs.json and ../*.png; process.exitCode reflects allChecksPass. Usage: node obs.cjs
const path = require("path");
const fs = require("fs");
const { execFileSync } = require("child_process");

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
const TARIFF_ITEM = "Instructions to sue or defend";

const out = {
  at: new Date().toISOString(),
  gitHead: execFileSync("git", ["rev-parse", "HEAD"], { cwd: W }).toString().trim(),
  source: null,
  invoiceId: null,
  vatRate: null,
  originalDefaultTaxRate: null,
  steps: [],
  console: [],
  checks: [],
  notes: [],
};
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
  lines: (inv.lines ?? []).map((l) => ({
    id: l.id,
    description: l.description.slice(0, 80),
    lineType: l.lineType,
    amount: l.amount,
    taxRateId: l.taxRateId ?? null,
    taxRateName: l.taxRateName ?? null,
    taxAmount: l.taxAmount ?? null,
  })),
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
  let restoreDefaultTo = null; // the rate to make default again in `finally`

  const setDefault = async (r, on) => {
    await page.goto(`${BASE}/settings/fees?tab=tax`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await main.getByRole("button", { name: `Edit ${r.name}`, exact: true }).click();
    const dlg = page.getByRole("dialog");
    const sw = dlg.locator("#edit-tax-rate-default");
    const isOn = (await sw.getAttribute("aria-checked")) === "true";
    if (isOn !== on) await sw.click();
    await dlg.getByRole("button", { name: "Save", exact: true }).click();
    await dlg.waitFor({ state: "hidden", timeout: 20000 });
    for (let i = 0; i < 20; i++) {
      const now = (await get(tok, "/api/tax-rates")).find((t) => t.id === r.id);
      if (now.isDefault === on) return;
      await page.waitForTimeout(500);
    }
    throw new Error(`default flag of ${r.name} did not become ${on}`);
  };

  try {
    await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
    await page.getByRole("button", { name: "Sign In", exact: true }).click();
    await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });

    const rates = await get(tok, "/api/tax-rates");
    const originalDefault = rates.find((t) => t.isDefault) ?? null;
    out.originalDefaultTaxRate = originalDefault ? { id: originalDefault.id, name: originalDefault.name, rate: originalDefault.rate } : null;
    const vatRate = rates.find((t) => t.active && !t.isExempt && t.rate > 0 && t.isDefault) ?? rates.find((t) => t.active && !t.isExempt && t.rate > 0);
    if (!vatRate) throw new Error("no active VAT rate");
    out.vatRate = { id: vatRate.id, name: vatRate.name, rate: vatRate.rate };

    // --- pre: a draft from unbilled time ---------------------------------------------------------
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
    const fetchInv = () => get(tok, `/api/invoices/${invoiceId}`);

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
      const field = main.locator("#invoice-tax-amount");
      dom.headerTaxField = (await field.count()) > 0 ? await field.inputValue() : null;
      dom.lineRows = await main.getByRole("row").count();
      return { dom, block };
    };
    // reload=false reads the page as the last action left it (no navigation) — the no-reload check.
    const snap = async (name, shot, { reload = true } = {}) => {
      if (reload) await open();
      const { dom, block } = await domTotals();
      await page.screenshot({ path: path.join(OUT, `${shot}.png`), fullPage: true });
      await block.screenshot({ path: path.join(OUT, `${shot}-totals.png`) });
      const api = apiTotals(await fetchInv());
      const step = { name, reloaded: reload, url: page.url(), at: new Date().toISOString(), dom, api };
      out.steps.push(step);
      save();
      return step;
    };
    const domMatchesApi = (s) => s.dom.parsed.subtotal === s.api.subtotal && s.dom.parsed.tax === s.api.taxAmount && s.dom.parsed.total === s.api.total;
    const settle = async () => {
      await page.waitForLoadState("networkidle").catch(() => {});
      await page.waitForTimeout(1200);
    };
    // Wait (no navigation) until the page's totals equal the server's, up to ~10s.
    const waitDomEqualsApi = async () => {
      for (let i = 0; i < 20; i++) {
        const api = apiTotals(await fetchInv());
        const { dom } = await domTotals();
        if (dom.parsed.subtotal === api.subtotal && dom.parsed.tax === api.taxAmount && dom.parsed.total === api.total) return true;
        await page.waitForTimeout(500);
      }
      return false;
    };
    const addLine = async (description, amount, optionName) => {
      await main.getByRole("button", { name: "Add Line", exact: true }).click();
      const form = main.locator("div.rounded-lg").filter({ has: page.getByRole("heading", { name: "Add Line Item", exact: true }) });
      await form.getByPlaceholder("Line item description").fill(description);
      const nums = form.locator('input[type="number"]');
      await nums.nth(0).fill("1");
      await nums.nth(1).fill(String(amount));
      await form.getByRole("combobox").click();
      await page.getByRole("option", { name: optionName, exact: true }).click();
      await form.getByRole("button", { name: "Add", exact: true }).click();
      await form.waitFor({ state: "hidden", timeout: 20000 });
      await settle();
    };
    const deleteLine = async (description) => {
      const n = (await fetchInv()).lines.length;
      await main.getByRole("button", { name: `Delete ${description}`, exact: true }).first().click();
      for (let i = 0; i < 30; i++) {
        if ((await fetchInv()).lines.length < n) break;
        await page.waitForTimeout(300);
      }
      await settle();
    };

    // --- pre: fixture = one VAT line + one "None" line ---------------------------------------------
    await open();
    await addLine(NONE_LINE, 100, "None");
    await addLine(VAT_LINE, 200, `${vatRate.name} (${vatRate.rate}%)`);
    for (let i = 0; i < 40; i++) {
      const other = (await fetchInv()).lines.find((l) => l.description !== NONE_LINE && l.description !== VAT_LINE);
      if (!other) break;
      await deleteLine(other.description);
    }
    // Untaxed tariff lines need no default rate; flag the restore before switching it off.
    if (originalDefault) {
      restoreDefaultTo = originalDefault;
      await setDefault(originalDefault, false);
      out.notes.push(`pre: default flag of ${originalDefault.name} switched off (restored in finally)`);
    }
    const s0 = await snap("0-fixture: one VAT line + one None line", "00-fixture");
    const vatL = s0.api.lines.find((l) => l.description === VAT_LINE);
    const noneL = s0.api.lines.find((l) => l.description === NONE_LINE);
    const expectedVat = r2((200 * vatRate.rate) / 100);
    check("pre: exactly the two OBS lines", s0.api.lines.length === 2 && vatL && noneL, s0.api.lines);
    check("pre: the VAT line carries the rate and its VAT", vatL && vatL.taxRateId === vatRate.id && vatL.taxAmount === expectedVat, vatL);
    check("pre: the None line carries no rate", noneL && noneL.taxRateId === null && noneL.taxAmount === null, noneL);
    check("pre: starting VAT positive, per-line mode, total == subtotal + VAT", s0.api.taxAmount === expectedVat && s0.api.taxAmount > 0 && s0.api.hasPerLineTax && s0.api.total === r2(s0.api.subtotal + s0.api.taxAmount), s0.api);
    check("pre: header Tax Amount field hidden (per-line mode)", s0.dom.headerTaxField === null, s0.dom.headerTaxField);
    check("pre: no default tax rate now", !(await get(tok, "/api/tax-rates")).some((t) => t.isDefault), null);

    // --- 1. delete the VAT line ------------------------------------------------------------------------
    const urlBefore = page.url();
    await deleteLine(VAT_LINE);
    const synced1 = await waitDomEqualsApi();
    const s1 = await snap("1-vat-line-deleted (no reload)", "01-vat-line-deleted-live", { reload: false });
    check("step 1: server — per-line off, VAT 0, Total == Subtotal == 100", !s1.api.hasPerLineTax && s1.api.taxAmount === 0 && s1.api.total === s1.api.subtotal && s1.api.subtotal === 100, s1.api);
    check("step 1: no reload — DOM totals updated to the server's (VAT 0, Total 100)", synced1 && domMatchesApi(s1) && s1.dom.parsed.tax === 0 && s1.dom.parsed.total === 100, s1.dom.parsed);
    check("step 1: no reload — header Tax Amount field shows 0", s1.dom.headerTaxField !== null && Number(s1.dom.headerTaxField) === 0, s1.dom.headerTaxField);
    check("step 1: no navigation happened", page.url() === urlBefore, { urlBefore, now: page.url() });

    // --- 2. add a tariff line (dialog success → page refresh) -----------------------------------------
    await main.getByRole("button", { name: "Add Tariff Items", exact: true }).click();
    const tariff = page.getByTestId("tariff-line-dialog");
    await tariff.waitFor({ state: "visible", timeout: 20000 });
    await tariff.getByTestId("tariff-item-search").fill(TARIFF_ITEM);
    await tariff.locator('[data-testid^="tariff-item-"]').filter({ hasText: TARIFF_ITEM }).last().click();
    await tariff.getByTestId("add-to-invoice-btn").click();
    await tariff.waitFor({ state: "hidden", timeout: 20000 });
    await settle();
    const synced2 = await waitDomEqualsApi();
    const s2 = await snap("2-tariff-line-added (no reload)", "02-tariff-line-added-live", { reload: false });
    const tariffL = s2.api.lines.find((l) => l.lineType === "TARIFF");
    out.notes.push(`step 2: tariff line ${tariffL ? `${tariffL.description} amount ${tariffL.amount} taxRate ${tariffL.taxRateName}` : "missing"}`);
    check("step 2: tariff line added, untaxed", tariffL && tariffL.taxRateId === null && tariffL.taxAmount === null, tariffL ?? null);
    check("step 2: server — header-tax mode, VAT 0", !s2.api.hasPerLineTax && s2.api.taxAmount === 0 && s2.api.total === s2.api.subtotal, s2.api);
    check("step 2: no reload — DOM == API, VAT 0", synced2 && domMatchesApi(s2) && s2.dom.parsed.tax === 0, s2.dom.parsed);
    check(`step 2: no reload — header Tax Amount shows 0, not the old VAT (${expectedVat})`, s2.dom.headerTaxField !== null && Number(s2.dom.headerTaxField) === 0, s2.dom.headerTaxField);
    check("step 2: no navigation happened", page.url() === urlBefore, { urlBefore, now: page.url() });

    // --- 3. Save Changes, then reload ------------------------------------------------------------------
    const saveBtn = main.getByRole("button", { name: "Save Changes", exact: true });
    await saveBtn.click();
    await settle();
    for (let i = 0; i < 20 && !(await saveBtn.isEnabled()); i++) await page.waitForTimeout(300);
    const s3live = await snap("3-save-changes (no reload)", "03-after-save-live", { reload: false });
    check("step 3: after Save Changes — server VAT stays 0, Total == Subtotal", s3live.api.taxAmount === 0 && s3live.api.total === s3live.api.subtotal && !s3live.api.hasPerLineTax, s3live.api);
    check("step 3: after Save Changes — DOM == API", domMatchesApi(s3live), s3live.dom.parsed);
    const s3 = await snap("3-reloaded", "03-reloaded");
    check("step 3: reloaded — VAT 0, DOM == API, header field 0", s3.api.taxAmount === 0 && domMatchesApi(s3) && Number(s3.dom.headerTaxField) === 0, { dom: s3.dom.parsed, field: s3.dom.headerTaxField, api: s3.api });
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
    await page.screenshot({ path: path.join(OUT, "zz-error.png"), fullPage: true }).catch(() => {});
  } finally {
    if (restoreDefaultTo) {
      try {
        await setDefault(restoreDefaultTo, true);
        out.notes.push(`post: default flag of ${restoreDefaultTo.name} restored to on`);
      } catch (e) {
        out.restoreError = String(e);
      }
    }
    try {
      const after = (await get(tok, "/api/tax-rates")).find((t) => t.isDefault) ?? null;
      out.defaultAfterRestore = after ? { id: after.id, name: after.name } : null;
      check("post: default tax rate restored to the original", (after?.id ?? null) === (out.originalDefaultTaxRate?.id ?? null), out.defaultAfterRestore);
    } catch (e) {
      out.restoreError = out.restoreError ?? String(e);
    }
    out.allChecksPass = !out.error && !out.restoreError && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    process.exitCode = out.allChecksPass ? 0 : 1;
    console.log(
      JSON.stringify(
        {
          allChecksPass: out.allChecksPass,
          gitHead: out.gitHead,
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
