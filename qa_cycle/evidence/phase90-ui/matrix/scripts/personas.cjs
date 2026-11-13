// 644B task 644.4 persona shots on the KC dev stack (1440 light):
//   1. Carol (system Member) on Home (expect fee-earner audience, no firm KPI row) and on the
//      Estate Late Peter Moroka matter (she is a member; expect financial hero tiles omitted, trust tile present).
//   2. FINANCIAL_VISIBILITY custom role (ruling R6): Thandi creates "OBS 644B Financial Viewer"
//      (Financial Visibility only), assigns it to Carol, Carol's Home is shot (expect Billable % "—" +
//      hint, no delta: OQ #1), then Carol is restored to her original role and the role is deleted.
//      Every step is screenshotted to matrix/role-flow/ and recorded in personas-report.json.
//   3. Carol's Home again after the restore (must match step 1).
// The role steps are UI-only (no SQL). Recipe: $EV/640A/obs.cjs:206-305 (openCarolSheet/assignRole).
const path = require("path");
const fs = require("fs");
const { chromium, ORG, MATRIX, session, open, shot, writeJson } = require("./common-kc.cjs");
const MATTER = "9181f514-1742-43c5-b446-99e1d41f220e";
const ROLE = "OBS 644B Financial Viewer";
const FLOW = path.join(MATRIX, "role-flow");
fs.mkdirSync(FLOW, { recursive: true });
const log = (m) => console.log(`[${new Date().toISOString().slice(11, 19)}] ${m}`);

const homeFacts = (page) => page.evaluate(() => ({
  audience: document.querySelector('[data-testid="home-page"]')?.getAttribute("data-audience") ?? null,
  cells: [...document.querySelectorAll('[data-testid^="home-cell-"]')].map((e) => e.getAttribute("data-testid").replace("home-cell-", "")),
  statCards: [...document.querySelectorAll('[data-slot="stat-card"]')].map((c) => ({
    label: c.querySelector('[data-slot="stat-card-label"]')?.textContent.trim(),
    value: c.querySelector('[data-slot="stat-card-value"]')?.textContent.trim(),
    delta: c.querySelector('[data-slot="stat-card-delta"]')?.textContent.trim() ?? null,
    text: c.innerText.replace(/\s+/g, " ").trim().slice(0, 160),
  })),
}));
const heroFacts = (page) => page.evaluate(() => {
  const card = document.querySelector('[data-testid="matter-header-card"]');
  return {
    heroTiles: card ? [...card.querySelectorAll('[data-slot="stat-card"]')].map((c) => c.innerText.replace(/\s+/g, " ").trim()) : null,
    heroText: card ? card.innerText.replace(/\s+/g, " ").trim().slice(0, 400) : null,
  };
});

async function openCarolSheet(page, tag) {
  await page.goto(ORG + "/team", { waitUntil: "load" });
  const row = page.locator('[data-testid^="member-row-"]').filter({ hasText: "Carol" });
  await row.first().waitFor({ timeout: 60000 });
  if ((await row.count()) !== 1) throw new Error(`expected 1 Carol row, got ${await row.count()}`);
  await page.waitForTimeout(1000);
  const testid = await row.getAttribute("data-testid");
  const badge = (await row.locator('[data-testid="member-role-badge"]').textContent()).trim();
  const overrideCount = (await row.locator('[data-testid="override-count"]').count()) ? (await row.locator('[data-testid="override-count"]').textContent()).trim() : null;
  await page.screenshot({ path: path.join(FLOW, `team-${tag}.png`), fullPage: true });
  await row.click();
  const sheet = page.locator('[data-slot="sheet-content"]');
  await sheet.waitFor({ timeout: 15000 });
  await sheet.getByText("Loading capabilities...").waitFor({ state: "detached", timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(800);
  const roleValue = (await sheet.getByRole("combobox").textContent()).trim();
  const checkedCaps = await sheet.locator('button[role="checkbox"][data-state="checked"]').evaluateAll((els) => els.map((e) => e.getAttribute("aria-label")));
  const overrideMarks = await sheet.locator("span.text-teal-600, span.text-destructive").allTextContents();
  await page.screenshot({ path: path.join(FLOW, `team-${tag}-sheet.png`) });
  return { testid, badge, overrideCount, roleValue, checkedCaps, overrideMarks, sheet };
}

async function assignRole(page, sheet, roleName, tag) {
  await sheet.getByRole("combobox").click();
  await page.getByRole("option", { name: roleName, exact: true }).click();
  await page.waitForTimeout(400);
  const checkedCaps = await sheet.locator('button[role="checkbox"][data-state="checked"]').evaluateAll((els) => els.map((e) => e.getAttribute("aria-label")));
  await page.screenshot({ path: path.join(FLOW, `team-${tag}-selected.png`) });
  await sheet.getByRole("button", { name: "Save" }).click();
  await sheet.waitFor({ state: "detached", timeout: 30000 });
  await page.waitForTimeout(1500);
  return checkedCaps;
}

async function rolesPage(page, file) {
  await page.goto(ORG + "/settings/firm?tab=roles", { waitUntil: "load" });
  await page.getByRole("heading", { name: "Custom Roles" }).waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(FLOW, file), fullPage: true });
  return (await page.getByText(ROLE, { exact: true }).count()) > 0;
}

(async () => {
  const browser = await chromium.launch(); const consoleLog = []; const report = { capturedAt: new Date().toISOString(), role: ROLE, shots: [], flow: {} };
  let t = null; let assigned = false; let created = false;
  try {
    // 1. Carol as Member
    const c = await session(browser, "carol", { consoleSink: consoleLog });
    await open(c, ORG + "/dashboard", '[data-testid="home-page"]', "home-member-carol");
    report.shots.push({ name: "home-member", persona: "member-carol", ...(await homeFacts(c.page)), ...(await shot(c, MATRIX, "home-member-carol-1440-light.png")) });
    await open(c, `${ORG}/matters/${MATTER}`, '[data-testid="matter-header-card"]', "matter-detail-member-carol");
    report.shots.push({ name: "matter-detail-member", persona: "member-carol", ...(await heroFacts(c.page)), ...(await shot(c, MATRIX, "matter-detail-member-carol-1440-light.png")) });
    await c.ctx.close();
    log("member shots done");

    // 2. Role flow as Thandi
    t = await session(browser, "thandi", { consoleSink: consoleLog });
    report.flow.roleExistedBefore = await rolesPage(t.page, "01-roles-before.png");
    if (report.flow.roleExistedBefore) throw new Error("temp role already exists before the run; aborting");
    const orig = await openCarolSheet(t.page, "02-carol-original");
    report.flow.original = { ...orig, sheet: undefined };
    log(`Carol original: badge=${orig.badge} role=${orig.roleValue} overrides=${orig.overrideCount} marks=${JSON.stringify(orig.overrideMarks)} caps=${JSON.stringify(orig.checkedCaps)}`);
    await t.page.keyboard.press("Escape");
    if (orig.overrideCount || orig.overrideMarks.length) throw new Error("Carol has capability overrides — a role change would reset them; aborting before any change");

    await t.page.goto(ORG + "/settings/firm?tab=roles", { waitUntil: "load" });
    await t.page.getByRole("heading", { name: "Custom Roles" }).waitFor({ timeout: 60000 });
    await t.page.getByRole("button", { name: "New Role" }).click();
    const dlg = t.page.getByRole("dialog");
    await dlg.waitFor();
    await dlg.locator("#role-name").fill(ROLE);
    await dlg.locator("#role-description").fill("644B exit-matrix OBS persona: Financial Visibility only (temporary)");
    await dlg.getByRole("checkbox", { name: "Financial Visibility" }).click();
    await t.page.screenshot({ path: path.join(FLOW, "03-role-create-dialog.png") });
    await dlg.getByRole("button", { name: "Create" }).click();
    await dlg.waitFor({ state: "detached", timeout: 30000 });
    created = true;
    report.flow.roleVisibleAfterCreate = await rolesPage(t.page, "04-roles-after-create.png");
    log(`role created, visible=${report.flow.roleVisibleAfterCreate}`);

    const pre = await openCarolSheet(t.page, "05-carol-before-assign");
    report.flow.assignedCapsShown = await assignRole(t.page, pre.sheet, ROLE, "06-carol-assign");
    assigned = true;
    const after = await openCarolSheet(t.page, "07-carol-on-fv-role");
    report.flow.afterAssign = { ...after, sheet: undefined };
    await t.page.keyboard.press("Escape");
    log(`Carol after assign: badge=${after.badge} role=${after.roleValue} caps=${JSON.stringify(after.checkedCaps)}`);

    // Carol's Home on the FV-only role (fresh login)
    const f = await session(browser, "carol", { consoleSink: consoleLog });
    await open(f, ORG + "/dashboard", '[data-testid="home-page"]', "home-fv-role-carol");
    report.shots.push({ name: "home-fv-role", persona: "fv-custom-role-carol", ...(await homeFacts(f.page)), ...(await shot(f, MATRIX, "home-fv-role-carol-1440-light.png")) });
    await f.ctx.close();
    log("FV home shot done");
  } catch (e) {
    report.error = String(e); log(`ERROR ${e}`);
  } finally {
    // 3. Restore (always attempted once anything changed)
    try {
      if (t && assigned) {
        const orig = report.flow.original;
        const pre = await openCarolSheet(t.page, "08-carol-pre-restore");
        await assignRole(t.page, pre.sheet, orig.roleValue, "09-carol-restore");
        const rest = await openCarolSheet(t.page, "10-carol-restored");
        await t.page.keyboard.press("Escape");
        await t.page.reload({ waitUntil: "load" });
        const row = t.page.locator('[data-testid^="member-row-"]').filter({ hasText: "Carol" });
        await row.first().waitFor({ timeout: 60000 });
        await t.page.waitForTimeout(1000);
        const badgeAfterReload = (await row.locator('[data-testid="member-role-badge"]').textContent()).trim();
        await t.page.screenshot({ path: path.join(FLOW, "11-team-carol-restored-after-reload.png"), fullPage: true });
        report.flow.restored = { ...rest, sheet: undefined, badgeAfterReload };
        report.flow.restoreVerified = rest.badge === orig.badge && badgeAfterReload === orig.badge && rest.roleValue === orig.roleValue
          && !rest.overrideCount && JSON.stringify(rest.checkedCaps) === JSON.stringify(orig.checkedCaps);
        log(`Carol restored: badge=${rest.badge}/${badgeAfterReload} role=${rest.roleValue} caps=${JSON.stringify(rest.checkedCaps)} verified=${report.flow.restoreVerified}`);
      }
      if (t && created) {
        await t.page.goto(ORG + "/settings/firm?tab=roles", { waitUntil: "load" });
        await t.page.getByRole("heading", { name: "Custom Roles" }).waitFor({ timeout: 60000 });
        await t.page.getByRole("button", { name: `Delete ${ROLE}` }).click();
        const ad = t.page.getByRole("alertdialog");
        await ad.waitFor({ timeout: 15000 });
        await t.page.screenshot({ path: path.join(FLOW, "12-role-delete-confirm.png") });
        await ad.getByRole("button", { name: "Delete", exact: true }).click();
        await ad.waitFor({ state: "detached", timeout: 30000 });
        report.flow.roleVisibleAfterDelete = await rolesPage(t.page, "13-roles-after-delete.png");
        log(`role deleted, still visible=${report.flow.roleVisibleAfterDelete}`);
      }
    } catch (e) { report.restoreError = String(e); log(`RESTORE ERROR ${e}`); }
    if (t) await t.ctx.close();
    // 4. Carol's Home after the restore
    try {
      const c2 = await session(browser, "carol", { consoleSink: consoleLog });
      await open(c2, ORG + "/dashboard", '[data-testid="home-page"]', "home-member-carol-after-restore");
      report.shots.push({ name: "home-member-after-restore", persona: "member-carol", ...(await homeFacts(c2.page)), ...(await shot(c2, FLOW, "14-home-carol-after-restore-1440-light.png")) });
      await c2.ctx.close();
    } catch (e) { report.afterRestoreError = String(e); }
    writeJson(MATRIX, "personas-report.json", report);
    writeJson(MATRIX, "personas-console.json", consoleLog);
    await browser.close();
  }
  console.log(JSON.stringify({ error: report.error, restoreError: report.restoreError, flow: { ...report.flow, original: report.flow.original && { badge: report.flow.original.badge, role: report.flow.original.roleValue, caps: report.flow.original.checkedCaps }, afterAssign: report.flow.afterAssign && { badge: report.flow.afterAssign.badge, role: report.flow.afterAssign.roleValue, caps: report.flow.afterAssign.checkedCaps }, restored: report.flow.restored && { badge: report.flow.restored.badge, badgeAfterReload: report.flow.restored.badgeAfterReload, role: report.flow.restored.roleValue, caps: report.flow.restored.checkedCaps } }, shots: report.shots.map((s) => ({ name: s.name, audience: s.audience, cells: s.cells, statCards: s.statCards, heroTiles: s.heroTiles, dark: s.dark })) }, null, 1));
})().catch((e) => { console.error("OBS FAILED", e); process.exit(1); });
