// 665C OBS step 3: the data the sub-pages and the take-on wizard need, created THROUGH THE UI as Alice
// (1440×900 light), each step idempotent (skipped when the named record already exists):
//   1. Checklist template "OBS 665C Checklist" (a digit-first name is refused — probe below) via /settings/matters/checklists/new (Name, one item,
//      "Create Template") — the stack only has PLATFORM checklists, and checklists/[id]/edit renders
//      for ORG_CUSTOM sources only (edit/page.tsx:56 redirects otherwise).
//   2. Request template "665C OBS Request Template" via /settings/matters/request-templates/new
//      (Name, Description, "Create Template" → lands on request-templates/[id]); only PLATFORM ones exist.
//   3. No matter template is created: 6 SEEDER templates exist to open at templates/[id]; templates/new
//      is the same route with id "new".
//   4. Take-on batch "665C OBS take-on (mid-wizard)": Matters › Take-on → Clients "Start" → batch name
//      + ../fixtures/665c-obs-clients.csv (3 rows) → "Upload" → wizard at Mapping (shot) → "Save
//      mapping" → Validation → "Run dry run" (nothing is created in Kazi) → cards + rows (shot).
//      LEFT THERE (status VALIDATED, never attested or committed) for OBS-after.
// Writes $RAW/data.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const FIXTURE = path.resolve(__dirname, "../fixtures/665c-obs-clients.csv");
const out = { at: new Date().toISOString(), steps: [] };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const log = (o) => { out.steps.push(o); console.log(JSON.stringify(o)); };
const shot = async (p, key, caption, opt = {}) => {
  const f = path.join(L.RAW, `${key}.png`);
  await p.screenshot({ path: f, ...opt });
  tiles.push([key, f, caption]);
};
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(400); };

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  let T = await L.targets();

  // 1. checklist
  lab.label = "data|checklist";
  if (T.checklistCustom && T.checklistCustom.name === L.NAMES.checklist) log({ step: "checklist", result: "exists — skipped", id: T.checklistCustom.id });
  else {
    await p.goto(`${L.BASE}/settings/matters/checklists/new`, { waitUntil: "load" });
    await settle(p);
    await p.locator("#checklist-description").fill("Created by the 665C OBS-before for the checklist sub-pages.");
    // The form opens with one empty item row (no "Add Item" needed).
    await p.getByPlaceholder("Item name").first().fill("Proof of address");
    // Probe (creates nothing): a name starting with a digit. The first attempt with "665C OBS Checklist"
    // failed with the backend's slug-pattern message; recorded here as a defect, then the real name.
    await p.locator("#checklist-name").fill(L.NAMES.digitFirstProbe);
    await p.getByRole("button", { name: "Create Template" }).click();
    await p.locator("p.text-destructive").first().waitFor({ timeout: 20000 }).catch(() => {});
    const probeErr = await p.locator("p.text-destructive").allInnerTexts();
    await shot(p, "defect__checklist-digit-name-slug-error", `Alice 1440 light — checklists/new, Name "${L.NAMES.digitFirstProbe}" → Create Template: stays on the page with "${probeErr.join(" | ")}"`, { fullPage: true });
    log({ step: "checklist-digit-name-probe", name: L.NAMES.digitFirstProbe, url: p.url().replace(L.BASE, ""), errors: probeErr });
    await p.locator("#checklist-name").fill(L.NAMES.checklist);
    await shot(p, "data__checklist-new-filled", "Alice 1440 light — checklists/new filled (Name, Description, 1 item) before Create Template", { fullPage: true });
    await p.getByRole("button", { name: "Create Template" }).click();
    await p.waitForURL(/settings\/matters\?tab=checklists/, { timeout: 20000 }).catch(async (e) => {
      await shot(p, "data__checklist-new-failed", "Alice 1440 light — checklists/new after Create Template did not navigate", { fullPage: true });
      log({ step: "checklist", result: "no navigation", url: p.url(), errors: await p.locator("p.text-destructive").allInnerTexts() });
      throw e;
    });
    await settle(p);
    T = await L.targets();
    log({ step: "checklist", result: "created", landedOn: p.url().replace(L.BASE, ""), id: T.checklistCustom && T.checklistCustom.id, source: T.checklistCustom && T.checklistCustom.source });
  }

  // 2. request template
  lab.label = "data|request-template";
  if (T.requestTemplate && T.requestTemplate.name === L.NAMES.requestTemplate) log({ step: "request-template", result: "exists — skipped", id: T.requestTemplate.id });
  else {
    await p.goto(`${L.BASE}/settings/matters/request-templates/new`, { waitUntil: "load" });
    await settle(p);
    await p.locator("#template-name").fill(L.NAMES.requestTemplate);
    await p.locator("#template-description").fill("Created by the 665C OBS-before for the request-template sub-pages.");
    await p.getByRole("button", { name: "Create Template" }).click();
    await p.waitForURL(/settings\/matters\/request-templates\/[0-9a-f-]{36}/, { timeout: 20000 });
    await settle(p);
    T = await L.targets();
    log({ step: "request-template", result: "created", landedOn: p.url().replace(L.BASE, "").replace(/[0-9a-f-]{36}/, "{id}"), id: T.requestTemplate && T.requestTemplate.id, source: T.requestTemplate && T.requestTemplate.source });
  }

  // 4. take-on batch, left mid-wizard
  lab.label = "data|take-on";
  if (T.batch) log({ step: "take-on", result: "exists — skipped", id: T.batch.id, status: T.batch.status });
  else {
    await p.goto(`${L.BASE}/settings/matters?tab=take-on`, { waitUntil: "load" });
    await settle(p);
    await p.getByTestId("take-on-start-clients").click();
    await p.getByTestId("take-on-new-upload").waitFor();
    await p.getByTestId("take-on-batch-name").fill(L.NAMES.takeOnBatch);
    await p.getByTestId("take-on-file-input").setInputFiles(FIXTURE);
    await shot(p, "data__take-on-upload-filled", "Alice 1440 light — Take-on › Clients › Start: batch name + 665c-obs-clients.csv chosen, before Upload", { fullPage: true });
    await p.getByTestId("take-on-upload-submit").click();
    // The reader's warnings (if any) hold the picker on a "Continue" button first (dataset-picker.tsx:56).
    await Promise.race([p.getByTestId("take-on-wizard").waitFor({ timeout: 60000 }), p.getByTestId("take-on-continue").waitFor({ timeout: 60000 })]);
    if (await p.getByTestId("take-on-continue").count()) {
      log({ step: "take-on-upload-warnings", text: await p.getByTestId("take-on-new-upload").innerText() });
      await p.getByTestId("take-on-continue").click();
    }
    await p.getByTestId("take-on-wizard").waitFor({ timeout: 60000 });
    await p.getByTestId("take-on-mapping").waitFor({ timeout: 60000 });
    await settle(p);
    const mappingUrl = p.url().replace(L.BASE, "");
    const steps = await p.locator('[data-testid^="take-on-step-"]').evaluateAll((els) => els.map((e) => `${e.dataset.testid.replace("take-on-step-", "")}:${e.getAttribute("aria-current") || e.getAttribute("data-state") || ""}`));
    await shot(p, "data__take-on-mapping", `Alice 1440 light — wizard at Mapping (${mappingUrl.replace(/[0-9a-f-]{36}/, "{id}")}) right after Upload; steps ${steps.join(" ")}`, { fullPage: true });
    log({ step: "take-on-upload", mappingUrl, steps, batchStatus: (await p.getByTestId("take-on-batch-status").innerText().catch(() => null)) });
    await p.getByTestId("take-on-save-mapping").click();
    await p.getByTestId("take-on-validation").waitFor({ timeout: 60000 });
    await p.getByTestId("take-on-validate").click();
    await p.getByTestId("take-on-cards").waitFor({ timeout: 90000 });
    await settle(p);
    const cards = {};
    for (const id of ["total", "valid", "error", "duplicate"]) cards[id] = (await p.getByTestId(`take-on-card-${id}`).innerText().catch(() => null));
    const rowStatus = await p.locator('[data-testid^="take-on-row-status-"]').evaluateAll((els) => els.map((e) => `${e.dataset.testid.replace("take-on-row-status-", "")}:${e.textContent.trim()}`));
    await shot(p, "data__take-on-validated", `Alice 1440 light — wizard at Validation after "Run dry run" (nothing created); rows ${rowStatus.join(", ")}`, { fullPage: true });
    T = await L.targets();
    log({ step: "take-on", result: "created, left at Validation (dry run done, not attested, not committed)", id: T.batch && T.batch.id, status: T.batch && T.batch.status, url: `/org/${L.ORG}/settings/matters?tab=take-on&batch=${T.batch && T.batch.id}`, cards, rowStatus, batchStatusChip: (await p.getByTestId("take-on-batch-status").innerText().catch(() => null)) });
  }
  await ctx.close();
  await browser.close();
  out.targets = await L.targets();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "data.json"), JSON.stringify(out));
  console.log("targets", JSON.stringify(out.targets), "console", consoleSink.length);
})();
