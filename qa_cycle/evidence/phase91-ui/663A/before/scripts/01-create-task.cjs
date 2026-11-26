// 663A OBS-before step 1: create ONE fresh task through the UI (Alice, owner) on the "Team spec"
// matter (a matter Carol is also a member of), so the task Sheet has no time entries and no comments.
// Idempotent: if a task with the same title already exists in the list, it is reused.
// Writes ../../../../../../.tmp/663A-before-raw/task.json {matterId, taskId, title}.
// Usage (worktree root): NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/663A/before/scripts/01-create-task.cjs
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");
const RAW = path.join(C.W, ".tmp/663A-before-raw");
fs.mkdirSync(RAW, { recursive: true });
const MATTER = "663cf013-d6ed-4f31-a303-2d0649b98f54"; // "Team spec mwwndiru39b8" (visible to Alice and Carol)
const TITLE = "663A OBS empty-state task";
(async () => {
  const browser = await C.chromium.launch();
  const st = await C.loginState(browser, "alice");
  fs.writeFileSync(path.join(RAW, "state-alice.json"), JSON.stringify(st));
  fs.writeFileSync(path.join(RAW, "state-carol.json"), JSON.stringify(await C.loginState(browser, "carol")));
  const ctx = await C.newCtx(browser, st, { width: 1440, height: 900 });
  const page = await ctx.newPage();
  const url = `${C.APP}/org/${C.ORG}/matters/${MATTER}?tab=tasks`;
  await page.goto(url, { waitUntil: "load" });
  await page.locator("#main-content h1").first().waitFor({ timeout: 30000 });
  await page.getByRole("button", { name: "New Task" }).waitFor({ timeout: 30000 });
  let row = page.getByText(TITLE, { exact: true }).first();
  if (!(await row.isVisible().catch(() => false))) {
    await page.getByRole("button", { name: "New Task" }).click();
    const dlg = page.getByRole("dialog");
    await dlg.locator("#task-title").fill(TITLE);
    await dlg.getByRole("button", { name: /^Create/ }).click();
    await dlg.waitFor({ state: "hidden", timeout: 30000 });
    console.log("created via UI dialog");
  } else console.log("reused existing task row");
  row = page.getByText(TITLE, { exact: true }).first();
  await row.waitFor({ timeout: 30000 });
  await row.click();
  await page.waitForURL(/taskId=/, { timeout: 30000 });
  const taskId = new URL(page.url()).searchParams.get("taskId");
  fs.writeFileSync(path.join(RAW, "task.json"), JSON.stringify({ matterId: MATTER, taskId, title: TITLE }));
  console.log("task", taskId, page.url());
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
