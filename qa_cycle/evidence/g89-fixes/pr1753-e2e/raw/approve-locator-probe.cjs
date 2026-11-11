// Proof for the invoice-lifecycle:88 failure: on an APPROVED fee note, once the audit trail renders,
// getByRole('button', { name: 'Approve' }) (substring, case-insensitive) resolves to the audit row.
const { createRequire } = require("module");
const [, , pkgDir, base, invoiceId] = process.argv;
const { chromium } = createRequire(pkgDir + "/package.json")("@playwright/test");
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.goto(base + "/mock-login"); await p.getByRole("button", { name: /sign in/i }).first().click();
  await p.waitForURL(/dashboard/, { timeout: 60000 });
  await p.goto(`${base}/org/e2e-test-org/invoices/${invoiceId}`);
  await p.getByRole("heading", { name: "Audit trail" }).waitFor({ timeout: 20000 });
  await p.getByRole("button", { name: /Fee note approved/ }).first().waitFor({ timeout: 20000 });
  const sub = p.getByRole("button", { name: "Approve" });
  const exact = p.getByRole("button", { name: "Approve", exact: true });
  const names = await sub.evaluateAll((els) => els.map((e) => e.textContent.replace(/\s+/g, " ").trim()));
  console.log(JSON.stringify({ url: p.url(), status: await p.getByText("Approved", { exact: true }).first().isVisible(),
    sendFeeNoteVisible: await p.getByRole("button", { name: "Send fee note" }).isVisible(),
    substringApproveCount: await sub.count(), substringMatches: names, exactApproveCount: await exact.count() }, null, 1));
  await b.close();
})().catch((e) => { console.error("PROBE-FAIL", e); process.exit(1); });
