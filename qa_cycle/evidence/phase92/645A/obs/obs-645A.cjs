// OBS 645A — library-script real Chromium (Playwright MCP Chrome profile is held by the user's live session)
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-645A/frontend/node_modules/@playwright/test");
const fs = require("fs");
const OUT = "/tmp/p92-645A-obs";
const FE = "http://localhost:3001";
const BE = "http://localhost:8081";
const IDP = "http://localhost:8090";
const PAGE_URL = `${FE}/org/e2e-test-org/trust-accounting/interest`;
const USERS = {
  alice: { userId: "user_e2e_alice", orgId: "org_e2e_test", orgSlug: "e2e-test-org", orgRole: "org:owner" },
  bob: { userId: "user_e2e_bob", orgId: "org_e2e_test", orgSlug: "e2e-test-org", orgRole: "org:admin" },
  carol: { userId: "user_e2e_carol", orgId: "org_e2e_test", orgSlug: "e2e-test-org", orgRole: "org:member" },
};
const NOTICE = "Calculated interest is not posted. Interest on trust money vests in the Legal Practitioners' Fidelity Fund (section 86(5) of the Legal Practice Act). Record interest from the bank statement when you reconcile.";
const TITLE = "Interest runs are not posted";
const DETAIL = "Interest on trust money vests in the Legal Practitioners' Fidelity Fund (section 86(5) of the Legal Practice Act). Kazi no longer posts calculated interest. Record interest from the bank statement instead.";
const BAD = /post|approve/i;
const phase = process.argv[2]; // "ui" or "api"

async function token(user) {
  const r = await fetch(`${IDP}/token`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(USERS[user]) });
  if (!r.ok) throw new Error(`token ${user} ${r.status}`);
  return (await r.json()).access_token;
}

async function newSession(browser, user) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await ctx.addCookies([{ name: "mock-auth-token", value: await token(user), domain: "localhost", path: "/", httpOnly: false, sameSite: "Lax" }]);
  const page = await ctx.newPage();
  const consoleErrors = [];
  page.on("console", (m) => { if (m.type() === "error") consoleErrors.push(m.text()); });
  page.on("pageerror", (e) => consoleErrors.push("pageerror: " + e.message));
  return { ctx, page, consoleErrors };
}

async function controlsIn(locator) {
  return locator.evaluate((root) => {
    const sel = 'button, a[href], [role="button"], [role="link"], [role="menuitem"], [role="tab"]';
    return [...root.querySelectorAll(sel)].map((el) => ({
      tag: el.tagName.toLowerCase(),
      role: el.getAttribute("role") || (el.tagName === "A" ? "link" : "button"),
      name: (el.getAttribute("aria-label") || el.innerText || el.textContent || "").replace(/\s+/g, " ").trim(),
      testid: el.getAttribute("data-testid"),
      visible: !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length),
    }));
  });
}

async function ui(browser, user) {
  const { ctx, page, consoleErrors } = await newSession(browser, user);
  const res = { user, url: PAGE_URL };
  const resp = await page.goto(PAGE_URL, { waitUntil: "networkidle" });
  res.httpStatus = resp && resp.status();
  res.finalUrl = page.url();
  await page.waitForTimeout(1500);
  const pageRoot = page.getByTestId("interest-page");
  res.interestPageVisible = await pageRoot.isVisible().catch(() => false);
  res.h1 = await page.locator("h1").allInnerTexts().catch(() => []);
  const notice = page.getByTestId("interest-posting-notice");
  res.noticePresent = (await notice.count()) > 0;
  res.noticeText = res.noticePresent ? (await notice.innerText()).replace(/\s+/g, " ").trim() : null;
  res.noticeVerbatim = res.noticeText === NOTICE;
  res.noticeRole = res.noticePresent ? await notice.getAttribute("role") : null;
  await page.screenshot({ path: `${OUT}/interest-page-${user}.png`, fullPage: true });
  // whole page (incl. app shell) and page-body controls
  res.allPageControls = await controlsIn(page.locator("body"));
  res.pageBodyControls = res.interestPageVisible ? await controlsIn(pageRoot) : [];
  // Accessible-name role queries (what Playwright/AT sees)
  res.roleQueryMatches = {
    buttonPostApprove_page: await page.getByRole("button", { name: BAD }).count(),
    linkPostApprove_page: await page.getByRole("link", { name: BAD }).count(),
    menuitemPostApprove_page: await page.getByRole("menuitem", { name: BAD }).count(),
  };
  const rows = page.getByTestId("interest-runs-table").locator('[data-testid^="run-row-"]');
  const n = await rows.count();
  res.rows = [];
  for (let i = 0; i < n; i++) {
    const r = rows.nth(i);
    const tid = await r.getAttribute("data-testid");
    const status = await r.getByTestId("run-status").innerText().catch(() => null);
    const postedAt = await r.getByTestId("run-posted-at").innerText().catch(() => null);
    const controls = await controlsIn(r);
    // open any kebab/dropdown menu triggers in the row to enumerate menuitems
    const menuItems = [];
    const triggers = r.locator('[aria-haspopup="menu"]');
    for (let k = 0; k < (await triggers.count()); k++) {
      await triggers.nth(k).click();
      await page.waitForTimeout(400);
      menuItems.push(...(await page.getByRole("menuitem").allInnerTexts()));
      await page.keyboard.press("Escape");
    }
    res.rows.push({ testid: tid, runId: tid && tid.replace(/^run-row-/, ""), status, postedAt, controls, menuItems });
  }
  const offending = [
    ...res.pageBodyControls.filter((c) => BAD.test(c.name)),
    ...res.rows.flatMap((r) => r.controls.filter((c) => BAD.test(c.name))),
    ...res.rows.flatMap((r) => r.menuItems.filter((t) => BAD.test(t))),
  ];
  res.shellControlsMatchingPostApprove = res.allPageControls.filter((c) => BAD.test(c.name));
  res.offendingPageBodyOrRowControls = offending;
  res.noPostApproveControl = offending.length === 0 && res.roleQueryMatches.buttonPostApprove_page === 0 && res.roleQueryMatches.menuitemPostApprove_page === 0;

  // Wizard: open the row action (Review / Continue) if present
  res.wizard = { opened: false };
  if (n > 0) {
    const action = rows.first().getByRole("button", { name: /^(Review|Continue)$/ });
    if ((await action.count()) > 0) {
      res.wizard.actionName = (await action.first().innerText()).trim();
      await action.first().click();
      const wiz = page.getByTestId("interest-run-wizard");
      await wiz.waitFor({ state: "visible", timeout: 15000 });
      await page.waitForTimeout(1500);
      res.wizard.opened = true;
      res.wizard.text = (await wiz.innerText()).replace(/\s+/g, " ").trim().slice(0, 1500);
      const dialogRoot = page.getByRole("dialog");
      res.wizard.controls = await controlsIn((await dialogRoot.count()) ? dialogRoot.first() : wiz);
      res.wizard.roleQueryPostApproveReview = await wiz.getByRole("button", { name: /approve|post/i }).count();
      res.wizard.noPostApprove = res.wizard.controls.filter((c) => BAD.test(c.name)).length === 0 && res.wizard.roleQueryPostApproveReview === 0;
      await page.screenshot({ path: `${OUT}/wizard-${user}.png`, fullPage: true });
      await page.keyboard.press("Escape");
    } else {
      res.wizard.reason = "no Review/Continue action on the row for this user";
    }
  }
  res.consoleErrors = consoleErrors;
  fs.writeFileSync(`${OUT}/controls-${user}.json`, JSON.stringify(res, null, 2));
  await ctx.close();
  return res;
}

async function apiCalls(browser, runId) {
  const network = [];
  const out = { runId, calls: [] };
  for (const [user, action] of [["bob", "approve"], ["alice", "post"]]) {
    const { ctx, page, consoleErrors } = await newSession(browser, user);
    page.on("request", (rq) => { if (rq.url().startsWith(BE)) network.push({ user, phase: "request", method: rq.method(), url: rq.url(), headers: { ...rq.headers(), authorization: rq.headers().authorization ? "Bearer <redacted mock-IDP JWT>" : undefined } }); });
    page.on("response", async (rs) => {
      if (!rs.url().startsWith(BE)) return;
      let body = null; try { body = await rs.text(); } catch (e) { body = "<unreadable: " + e.message + ">"; }
      network.push({ user, phase: "response", method: rs.request().method(), url: rs.url(), status: rs.status(), statusText: rs.statusText(), headers: rs.headers(), body });
    });
    await page.goto(PAGE_URL, { waitUntil: "networkidle" });
    const result = await page.evaluate(async ({ be, runId, action }) => {
      const tok = decodeURIComponent((document.cookie.split("; ").find((c) => c.startsWith("mock-auth-token=")) || "").split("=")[1] || "");
      const r = await fetch(`${be}/api/interest-runs/${runId}/${action}`, { method: "POST", headers: { Authorization: `Bearer ${tok}`, "Content-Type": "application/json" } });
      const text = await r.text();
      return { tokenFromBrowserCookie: tok.length > 0, status: r.status, contentType: r.headers.get("content-type"), text };
    }, { be: BE, runId, action });
    let body = null; try { body = JSON.parse(result.text); } catch {}
    out.calls.push({
      user, endpoint: `POST /api/interest-runs/${runId}/${action}`, madeFrom: "page.evaluate(fetch) on " + PAGE_URL,
      tokenFromBrowserCookie: result.tokenFromBrowserCookie, status: result.status, contentType: result.contentType, body,
      titleMatches: body && body.title === TITLE, detailMatches: body && body.detail === DETAIL,
    });
    // reload and read status from UI
    await page.reload({ waitUntil: "networkidle" });
    const row = page.locator(`[data-testid="run-row-${runId}"]`);
    out.calls[out.calls.length - 1].uiStatusAfter = await row.getByTestId("run-status").innerText().catch(() => null);
    out.calls[out.calls.length - 1].uiPostedAtAfter = await row.getByTestId("run-posted-at").innerText().catch(() => null);
    out.calls[out.calls.length - 1].consoleErrors = consoleErrors;
    await ctx.close();
  }
  fs.writeFileSync(`${OUT}/api-refusals.json`, JSON.stringify(out, null, 2));
  fs.writeFileSync(`${OUT}/network.json`, JSON.stringify(network, null, 2));
  return out;
}

(async () => {
  const browser = await chromium.launch();
  try {
    if (phase === "ui") {
      const summary = {};
      for (const u of ["alice", "bob", "carol"]) {
        const r = await ui(browser, u);
        summary[u] = { httpStatus: r.httpStatus, finalUrl: r.finalUrl, pageVisible: r.interestPageVisible, noticeVerbatim: r.noticeVerbatim, rows: r.rows.map((x) => ({ runId: x.runId, status: x.status, controls: x.controls.map((c) => c.name), menuItems: x.menuItems })), offending: r.offendingPageBodyOrRowControls, shellMatches: r.shellControlsMatchingPostApprove.map((c) => c.name), roleQuery: r.roleQueryMatches, wizard: { opened: r.wizard.opened, action: r.wizard.actionName, reason: r.wizard.reason, controls: (r.wizard.controls || []).map((c) => c.name), noPostApprove: r.wizard.noPostApprove }, consoleErrors: r.consoleErrors };
      }
      console.log(JSON.stringify(summary, null, 2));
    } else if (phase === "api") {
      console.log(JSON.stringify(await apiCalls(browser, process.argv[3]), null, 2));
    }
  } finally { await browser.close(); }
})().catch((e) => { console.error(e); process.exit(1); });
