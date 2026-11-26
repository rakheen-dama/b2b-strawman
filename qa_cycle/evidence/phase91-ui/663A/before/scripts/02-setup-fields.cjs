// 663A OBS-before step 2: put the matter and client Fields tabs into their CustomFieldSection empty
// state through the UI only (Alice, owner). The org has PROJECT/CUSTOMER field definitions and
// auto-apply groups, so the hand-rolled `matter-fields-tab` org-empty branch is unreachable without
// deleting org settings (not done). Instead:
//  A. New Matter dialog -> "663A OBS empty-fields matter" (no client); Fields tab -> remove every
//     applied group via its "Remove <group>" chip button; Team tab -> Add Member -> Carol.
//  B. New Client dialog -> "663A OBS empty-fields client"; Fields tab -> remove every applied group.
// Idempotent (reuses entities found by name). Writes .tmp/663A-before-raw/setup.json.
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");
const RAW = path.join(C.W, ".tmp/663A-before-raw");
const BASE = `${C.APP}/org/${C.ORG}`;
const MATTER = "663A OBS empty-fields matter";
const CLIENT = "663A OBS empty-fields client";
const log = (...a) => console.log(...a);
async function ready(page) {
  await page.locator("#main-content h1").first().waitFor({ timeout: 30000 });
  await page.waitForTimeout(1200);
}
async function removeGroups(page) {
  for (let i = 0; i < 8; i++) {
    const btn = page.locator('[data-testid="field-group-selector"] button[aria-label^="Remove "]').filter({ visible: true }).first();
    if (!(await btn.count())) break;
    const label = await btn.getAttribute("aria-label");
    await btn.click();
    await page.locator(`[data-testid="field-group-selector"] button[aria-label="${label}"]`).filter({ visible: true }).waitFor({ state: "detached", timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(800);
    log("  removed", label);
  }
}
(async () => {
  const browser = await C.chromium.launch();
  const st = JSON.parse(fs.readFileSync(path.join(RAW, "state-alice.json"), "utf8"));
  const ctx = await C.newCtx(browser, st, { width: 1440, height: 900 });
  const page = await ctx.newPage();
  const out = {};
  // ---- A. matter
  await page.goto(`${BASE}/matters`, { waitUntil: "load" });
  await ready(page);
  let link = page.getByRole("link", { name: MATTER, exact: true }).first();
  if (!(await link.isVisible().catch(() => false))) {
    await page.getByRole("button", { name: "New Matter" }).click();
    const dlg = page.getByRole("dialog", { name: "Create Matter" });
    await dlg.waitFor();
    await dlg.getByLabel("Name", { exact: true }).fill(MATTER);
    await dlg.getByRole("button", { name: "Create Matter" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 20000 });
    log("matter created via UI dialog");
    await page.goto(`${BASE}/matters`, { waitUntil: "load" });
    await ready(page);
    link = page.getByRole("link", { name: MATTER, exact: true }).first();
  } else log("matter reused");
  const mhref = await link.getAttribute("href");
  out.matterId = mhref.match(/[0-9a-f-]{36}/)[0];
  await page.goto(`${BASE}/matters/${out.matterId}?tab=fields`, { waitUntil: "load" });
  await ready(page);
  await removeGroups(page);
  await page.goto(`${BASE}/matters/${out.matterId}?tab=team`, { waitUntil: "load" });
  await ready(page);
  const panel = page.getByRole("tabpanel");
  if (!(await panel.getByText(/Carol/).first().isVisible().catch(() => false))) {
    await panel.getByRole("button", { name: "Add Member" }).click();
    const dlg = page.getByRole("dialog", { name: "Add Member" });
    await dlg.waitFor();
    await dlg.getByRole("option", { name: /Carol/ }).first().click();
    await dlg.waitFor({ state: "hidden", timeout: 20000 });
    log("Carol added to matter via Add Member dialog");
  } else log("Carol already on matter");
  // ---- B. client
  await page.goto(`${BASE}/clients`, { waitUntil: "load" });
  await ready(page);
  let clink = page.getByRole("link", { name: CLIENT, exact: true }).first();
  if (!(await clink.isVisible().catch(() => false))) {
    await page.getByRole("button", { name: "New Client" }).click();
    const dlg = page.getByRole("dialog", { name: /^(Create Client|Additional Information)$/ });
    await dlg.waitFor();
    await dlg.getByLabel("Name", { exact: true }).fill(CLIENT);
    await dlg.getByLabel("Email", { exact: true }).fill("obs-663a-client@example.com");
    await dlg.getByRole("button", { name: "Next" }).click();
    await dlg.getByText("Step 2 of 2").waitFor({ timeout: 15000 });
    await dlg.getByRole("button", { name: "Create Client" }).click();
    await page.waitForURL(/\/clients\/[0-9a-f-]{36}/, { timeout: 30000 });
    log("client created via UI dialog");
    out.clientId = page.url().match(/clients\/([0-9a-f-]{36})/)[1];
  } else {
    out.clientId = (await clink.getAttribute("href")).match(/[0-9a-f-]{36}/)[0];
    log("client reused");
  }
  await page.goto(`${BASE}/clients/${out.clientId}?tab=fields`, { waitUntil: "load" });
  await ready(page);
  await removeGroups(page);
  fs.writeFileSync(path.join(RAW, "setup.json"), JSON.stringify(out));
  log(JSON.stringify(out));
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
