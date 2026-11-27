// F-666B-B1 fix — OBS through the UI (real Chromium via the Playwright library; never the
// founder's Playwright-MCP Chrome). E2E mock-auth stack: app :3001, backend :8081, IDP :8090.
// As Alice:
//   0. Generate a draft fee note from a client's unbilled time (client › Fee notes › New fee note
//      › Fetch Unbilled Time › Validate & Create Draft › Create Draft) — its lines carry the
//      default VAT rate. Falls back to an existing per-line-tax DRAFT only when no client has
//      unbilled ZAR time. Records the opening totals (DOM + GET).
//   1. Change only the Due Date → Save Changes → reload: VAT row, tax and total unchanged.
//   2. Change Notes + PO number → Save Changes → reload: still unchanged.
//   3. Approve → reload: status APPROVED, total equals the opening total.
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

const out = { at: new Date().toISOString(), source: null, invoiceId: null, steps: [], console: [], checks: [] };
const check = (name, ok, detail) => out.checks.push({ name, ok: !!ok, detail });
const save = () => fs.writeFileSync(FILE, JSON.stringify(out, null, 2));

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
  invoiceNumber: inv.invoiceNumber ?? null,
  dueDate: inv.dueDate ?? null,
  notes: inv.notes ?? null,
  poNumber: inv.poNumber ?? null,
  hasPerLineTax: inv.hasPerLineTax,
  subtotal: inv.subtotal,
  taxAmount: inv.taxAmount,
  total: inv.total,
  lineTaxSum: Math.round((inv.lines ?? []).reduce((s, l) => s + (l.taxAmount ?? 0), 0) * 100) / 100,
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

  try {
    await page.goto(`${APP}/mock-login`, { waitUntil: "load" });
    await page.getByRole("button", { name: "Sign In", exact: true }).click();
    await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
    const main = page.locator("#main-content");

    // --- 0. a draft with per-line VAT -------------------------------------------------------
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
    if (client) {
      out.source = { generatedFrom: client };
      const before = new Set((await get(tok, `/api/invoices?clientId=${client.id}`)).map((i) => i.id));
      await page.goto(`${BASE}/clients/${client.id}?tab=invoices`, { waitUntil: "load" });
      await page.waitForLoadState("networkidle").catch(() => {});
      await main.getByRole("button", { name: "New fee note", exact: true }).click();
      const dlg = page.getByRole("dialog");
      await dlg.getByRole("button", { name: "Fetch Unbilled Time", exact: true }).click();
      await dlg.getByRole("button", { name: "Validate & Create Draft", exact: true }).click();
      await dlg.getByRole("button", { name: /^Create Draft/ }).click();
      await dlg.waitFor({ state: "hidden", timeout: 30000 });
      for (let i = 0; i < 20 && !invoiceId; i++) {
        const now = await get(tok, `/api/invoices?clientId=${client.id}&status=DRAFT`);
        invoiceId = now.map((x) => x.id).find((id) => !before.has(id)) ?? null;
        if (!invoiceId) await page.waitForTimeout(500);
      }
      if (!invoiceId) throw new Error("generated draft not found");
    } else {
      const drafts = (await get(tok, "/api/invoices?status=DRAFT")).filter((i) => i.hasPerLineTax && i.taxAmount > 0);
      if (drafts.length === 0) throw new Error("no client with unbilled ZAR time and no per-line-tax draft");
      invoiceId = drafts[0].id;
      out.source = { existingDraft: drafts[0].id };
    }
    out.invoiceId = invoiceId;

    const open = async () => {
      await page.goto(`${BASE}/invoices/${invoiceId}`, { waitUntil: "load" });
      await page.waitForLoadState("networkidle").catch(() => {});
      await main.getByText("Subtotal", { exact: true }).first().waitFor({ timeout: 20000 });
      await page.waitForTimeout(400);
    };
    // The totals block: "Subtotal", one VAT row per rate (or the header-tax row), "Total".
    const domTotals = async () => {
      const block = main.locator("div.max-w-xs").filter({ has: page.getByText("Subtotal", { exact: true }) }).last();
      await block.scrollIntoViewIfNeeded();
      const rows = await block.locator(":scope > div").evaluateAll((els) =>
        els.map((e) => Array.from(e.children).map((c) => c.textContent.trim().replace(/ /g, " ")))
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
      const step = { name, dom, api };
      out.steps.push(step);
      save();
      return step;
    };
    const saveChanges = async () => {
      const put = page.waitForResponse((r) => r.request().method() === "POST" && r.url().includes(`/invoices/${invoiceId}`), { timeout: 30000 }).catch(() => null);
      await main.getByRole("button", { name: "Save Changes", exact: true }).click();
      await put;
      await page.waitForLoadState("networkidle").catch(() => {});
      await page.waitForTimeout(800);
    };

    const s0 = await snap("0-draft-opening", "00-draft-opening");
    check("opening draft has per-line VAT", s0.api.hasPerLineTax && s0.api.taxAmount > 0, s0.api);
    check("opening tax == sum of line taxes", Math.abs(s0.api.taxAmount - s0.api.lineTaxSum) < 0.005, s0.api);

    const same = (s, label) => {
      check(`${label}: API taxAmount unchanged`, s.api.taxAmount === s0.api.taxAmount, { was: s0.api.taxAmount, now: s.api.taxAmount });
      check(`${label}: API total unchanged`, s.api.total === s0.api.total, { was: s0.api.total, now: s.api.total });
      check(`${label}: DOM Total unchanged`, s.dom.Total === s0.dom.Total, { was: s0.dom.Total, now: s.dom.Total });
      check(`${label}: DOM VAT rows unchanged`, JSON.stringify(s.api.taxBreakdown) === JSON.stringify(s0.api.taxBreakdown), s.dom);
    };

    // --- 1. due date only ---------------------------------------------------------------------
    const due = new Date(Date.now() + 45 * 864e5).toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" });
    await main.locator("#invoice-due-date").fill(due);
    await saveChanges();
    const s1 = await snap("1-after-due-date-save", "01-after-due-date-save");
    check("step 1: due date saved", s1.api.dueDate === due, { due, api: s1.api.dueDate });
    same(s1, "step 1");

    // --- 2. notes + PO number -----------------------------------------------------------------
    await main.locator("#invoice-notes").fill("F-666B-B1 OBS — notes edit");
    await main.locator("#invoice-po-number").fill("PO-VAT-OBS");
    await saveChanges();
    const s2 = await snap("2-after-notes-po-save", "02-after-notes-po-save");
    check("step 2: notes + PO saved", s2.api.notes === "F-666B-B1 OBS — notes edit" && s2.api.poNumber === "PO-VAT-OBS", s2.api);
    same(s2, "step 2");

    // --- 3. approve ---------------------------------------------------------------------------
    await main.getByRole("button", { name: "Approve", exact: true }).click();
    for (let i = 0; i < 30; i++) {
      if ((await get(tok, `/api/invoices/${invoiceId}`)).status === "APPROVED") break;
      await page.waitForTimeout(500);
    }
    const s3 = await snap("3-approved", "03-approved");
    check("step 3: approved", s3.api.status === "APPROVED", s3.api.status);
    same(s3, "step 3");
  } catch (e) {
    out.error = String(e && e.stack ? e.stack : e);
    await page.screenshot({ path: path.join(OUT, "zz-error.png"), fullPage: true }).catch(() => {});
  } finally {
    out.allChecksPass = !out.error && out.checks.length > 0 && out.checks.every((c) => c.ok);
    save();
    await browser.close();
    console.log(JSON.stringify({ allChecksPass: out.allChecksPass, error: out.error ?? null, steps: out.steps.map((s) => ({ name: s.name, tax: s.api.taxAmount, total: s.api.total, domTotal: s.dom.Total })) }, null, 2));
  }
})();
