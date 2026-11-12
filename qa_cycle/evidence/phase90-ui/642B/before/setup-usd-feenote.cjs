// 642B observe-first setup (task 642.6): create a second-currency (USD) fee note for Acme Corp
// THROUGH THE UI on the e2e mock-auth stack (frontend :3001) as Alice (owner). No SQL, no REST.
// Stages are run one at a time so a re-run never duplicates data:
//   node setup-usd-feenote.cjs rate     — Acme client page → Fees › Tariffs → Add Override (Alice, USD 100/h)
//   node setup-usd-feenote.cjs task     — Website Redesign matter → Tasks → New Task
//   node setup-usd-feenote.cjs time     — task row → Log Time 1h (rate preview must say USD)
//   node setup-usd-feenote.cjs feenote  — Acme → Fees › Fee notes → New fee note, currency USD → Create Draft
//   node setup-usd-feenote.cjs send     — fee note detail → Approve → Send fee note
// Step screenshots go to /tmp/642b-setup (not committed).
const { chromium } = require(
  "/Users/rakheendama/Projects/2026/b2b-strawman/frontend/node_modules/@playwright/test"
);

const BASE = "http://localhost:3001";
const SLUG = "e2e-test-org";
const ACME = "3f3219bf-f8cd-47ce-bb59-f1bf1020330e";
const WEBSITE_REDESIGN = "ebd55ff0-81b1-44e2-be2e-0ba28d3ad776";
const TASK_TITLE = "642B USD fee note probe";
const SHOTS = "/tmp/642b-setup";
const stage = process.argv[2];
const invoiceId = process.argv[3];

async function shot(page, name) {
  await page.screenshot({ path: `${SHOTS}/${stage}-${name}.png`, fullPage: true });
}

async function login(page) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.selectOption("#user-select", "user_e2e_alice");
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/org\/e2e-test-org\/dashboard/, { timeout: 60000 });
}

async function stageRate(page) {
  await page.goto(`${BASE}/org/${SLUG}/clients/${ACME}?tab=rates`, { waitUntil: "load" });
  const panel = page.locator("#main-content");
  await page.getByRole("button", { name: "Add Override" }).first().click();
  const dlg = page.locator('[data-slot="dialog-content"]');
  await dlg.locator("#customer-rate-member").selectOption({ label: "Alice Owner" });
  await dlg.locator("#customer-rate-hourly").fill("100");
  await dlg.locator("button[role=combobox]").click();
  await page.getByPlaceholder("Search currency...").fill("USD");
  await page.getByRole("option", { name: /^USD/ }).click();
  await shot(page, "1-filled");
  await dlg.getByRole("button", { name: "Create Override" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 20000 });
  await page.waitForTimeout(1500);
  await shot(page, "2-done");
  console.log("rate panel text:", (await panel.innerText()).replace(/\s+/g, " ").slice(0, 800));
}

async function stageTask(page) {
  await page.goto(`${BASE}/org/${SLUG}/matters/${WEBSITE_REDESIGN}?tab=tasks`, { waitUntil: "load" });
  await page.getByRole("button", { name: "New Task" }).first().click();
  const dlg = page.locator('[data-slot="dialog-content"]');
  await dlg.locator("#task-title").fill(TASK_TITLE);
  await shot(page, "1-filled");
  await dlg.getByRole("button", { name: "Create Task" }).click();
  await dlg.waitFor({ state: "hidden", timeout: 20000 });
  await page.getByText(TASK_TITLE).first().waitFor({ timeout: 20000 });
  await shot(page, "2-done");
  console.log("task created");
}

async function stageTime(page) {
  await page.goto(`${BASE}/org/${SLUG}/matters/${WEBSITE_REDESIGN}?tab=tasks`, { waitUntil: "load" });
  const row = page.locator("tr", { hasText: TASK_TITLE }).first();
  await row.getByRole("button", { name: "Log Time" }).click();
  const dlg = page.locator('[data-slot="dialog-content"]');
  await dlg.locator("#time-hours").fill("1");
  await dlg.locator("#time-description").fill("642B second-currency probe");
  await page.waitForTimeout(2500); // debounced rate resolution
  const preview = await dlg.getByTestId("rate-preview").innerText().catch(() => "(no rate-preview)");
  console.log("rate preview:", preview.replace(/\s+/g, " "));
  await shot(page, "1-filled");
  await dlg.getByRole("button", { name: /^Log Time$/ }).click();
  await dlg.waitFor({ state: "hidden", timeout: 20000 });
  await page.waitForTimeout(1500);
  await shot(page, "2-done");
}

async function stageFeenote(page) {
  await page.goto(`${BASE}/org/${SLUG}/clients/${ACME}?tab=invoices`, { waitUntil: "load" });
  await page.getByRole("button", { name: "New fee note" }).click();
  const dlg = page.locator('[data-slot="dialog-content"]');
  await dlg.locator("#invoice-currency").fill("USD");
  await shot(page, "1-currency");
  await dlg.getByRole("button", { name: "Fetch Unbilled Time" }).click();
  await dlg.getByText("Select Unbilled Items").waitFor({ timeout: 20000 });
  await page.waitForTimeout(1000);
  const boxes = dlg.getByRole("checkbox");
  const n = await boxes.count();
  for (let i = 0; i < n; i++) {
    const b = boxes.nth(i);
    const label = (await b.getAttribute("aria-label")) || "";
    const disabled = await b.isDisabled();
    console.log(`checkbox ${i}: label="${label}" disabled=${disabled} checked=${await b.isChecked()}`);
  }
  await shot(page, "2-unbilled");
  console.log("dialog text:", (await dlg.innerText()).replace(/\s+/g, " ").slice(0, 1200));
  // select every enabled, unchecked entry checkbox
  for (let i = 0; i < n; i++) {
    const b = boxes.nth(i);
    if (!(await b.isDisabled()) && !(await b.isChecked())) await b.click();
  }
  await page.waitForTimeout(500);
  await shot(page, "3-selected");
  await dlg.getByRole("button", { name: /Validate & Create Draft/ }).click();
  await dlg.getByTestId("validation-checklist").waitFor({ timeout: 20000 });
  await shot(page, "4-validated");
  await dlg.getByRole("button", { name: /^Create Draft/ }).click();
  // Observed: the dialog closes and the page stays on ?tab=invoices (no redirect to the draft).
  await dlg.waitFor({ state: "hidden", timeout: 30000 });
  await page.waitForTimeout(2000);
  console.log("url after create:", page.url());
  await shot(page, "5-draft");
}

async function stageSend(page) {
  await page.goto(`${BASE}/org/${SLUG}/invoices/${invoiceId}`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Approve" }).click();
  await page.getByRole("button", { name: "Send fee note" }).waitFor({ timeout: 30000 });
  await shot(page, "1-approved");
  await page.getByRole("button", { name: "Send fee note" }).click();
  await page.waitForTimeout(2500);
  await shot(page, "2-after-send-click");
  const dlg = page.locator('[data-slot="dialog-content"]');
  if (await dlg.isVisible().catch(() => false)) {
    console.log("send dialog:", (await dlg.innerText()).replace(/\s+/g, " ").slice(0, 800));
  }
  console.log("page text:", (await page.locator("#main-content").innerText()).replace(/\s+/g, " ").slice(0, 600));
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  page.on("console", (m) => {
    if (m.type() === "error") console.log("[console.error]", m.text().slice(0, 300));
  });
  await login(page);
  try {
    if (stage === "rate") await stageRate(page);
    else if (stage === "task") await stageTask(page);
    else if (stage === "time") await stageTime(page);
    else if (stage === "feenote") await stageFeenote(page);
    else if (stage === "send") await stageSend(page);
    else throw new Error(`unknown stage ${stage}`);
  } catch (e) {
    await shot(page, "FAILED");
    throw e;
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
