// 651C OBS — headless Chromium (Playwright library) on the e2e mock-auth stack (:3001), Alice (owner).
// Observes: a sidebar Trust zone (7 items, no Interest) · b command palette · c removed route 404 ·
// d settings tab (no LPFF section) · e settings-nav description · f kept surfaces load. g/h run from the shell.
const fs = require("fs");
const path = require("path");
const W = path.resolve(__dirname, "../../../../..");
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.join(W, "frontend")] }));
const FIRM = "http://localhost:3001", BASE = `${FIRM}/org/e2e-test-org`;
const OUT = path.join(__dirname, "obs");
fs.mkdirSync(OUT, { recursive: true });
const R = { startedAt: new Date().toISOString(), consoleErrors: [], pageErrors: [], items: {} };
const settle = async (p, ms = 1200) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(ms); };
const shot = (p, n) => p.screenshot({ path: path.join(OUT, `${n}.png`), fullPage: true });

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, timezoneId: "Africa/Johannesburg" });
  const p = await ctx.newPage();
  p.on("console", (m) => { if (m.type() === "error") R.consoleErrors.push({ url: p.url(), text: m.text().slice(0, 300) }); });
  p.on("pageerror", (e) => R.pageErrors.push({ url: p.url(), text: String(e).slice(0, 300) }));
  await p.goto(`${FIRM}/mock-login`, { waitUntil: "load" });
  await p.locator("#user-select").selectOption("user_e2e_alice");
  await p.getByRole("button", { name: "Sign In", exact: true }).click();
  await p.waitForURL(/\/org\//, { timeout: 60_000 });
  await p.goto(`${BASE}/dashboard`); await settle(p, 2500);
  R.consoleErrorsAfterLoginSettle = R.consoleErrors.length; // spurious userinfo error after login is known noise

  // a. Sidebar — Trust starts collapsed; expand it (retry until hydrated, as sidebar-navigation.spec.ts does)
  const nav = p.getByRole("navigation", { name: "Main navigation" });
  const header = nav.getByRole("button", { name: "Trust", exact: true });
  for (let i = 0; i < 20 && (await header.getAttribute("aria-expanded")) !== "true"; i++) { await header.click().catch(() => {}); await p.waitForTimeout(500); }
  // Wait until the zone is open AND its links are visible (a click before hydration is dropped; a second click collapses).
  await nav.getByRole("link", { name: "Trust reports", exact: true }).waitFor({ state: "visible", timeout: 15_000 });
  const trustAnchors = nav.locator('a[href*="/trust-accounting"]');
  const trustLinks = [];
  for (const a of await trustAnchors.all()) if (await a.isVisible()) trustLinks.push((await a.innerText()).trim());
  const zoneExpanded = await header.getAttribute("aria-expanded");
  const interestCount = await p.getByRole("link", { name: "Interest", exact: true }).count();
  // Element screenshot of the nav (a fullPage capture resizes the viewport and re-renders the collapsible zones)
  await p.setViewportSize({ width: 1440, height: 1600 }); await p.waitForTimeout(500); // the rail scrolls at 1000 px
  await nav.screenshot({ path: path.join(OUT, "a-sidebar.png") });
  await p.setViewportSize({ width: 1440, height: 1000 });
  R.items.a = { zoneExpanded, visibleTrustLinks: trustLinks, interestLinkCount: interestCount,
    pass: zoneExpanded === "true" && interestCount === 0 && trustLinks.length === 7 && !trustLinks.some((t) => /^Interest$/.test(t)) };

  // b. Command palette
  await p.keyboard.press("Meta+k"); await p.waitForTimeout(600);
  if (!(await p.getByRole("dialog").count())) { await p.keyboard.press("Control+k"); await p.waitForTimeout(600); }
  const dlg = p.getByRole("dialog");
  const dialogOpen = (await dlg.count()) > 0;
  let interestOpts = [], otherOpts = [], invOpts = [];
  if (dialogOpen) {
    await p.keyboard.type("Interest"); await p.waitForTimeout(800);
    interestOpts = await dlg.getByRole("option").allTextContents();
    await shot(p, "b-palette-interest");
    const input = dlg.locator("input").first();
    await input.fill("Other trust"); await p.waitForTimeout(600);
    otherOpts = await dlg.getByRole("option").allTextContents();
    await input.fill("Investments"); await p.waitForTimeout(600);
    invOpts = await dlg.getByRole("option").allTextContents();
    await p.keyboard.press("Escape");
  }
  R.items.b = { dialogOpen, interestOpts, otherOpts, invOpts,
    pass: dialogOpen && !interestOpts.some((t) => /^\s*Interest\s*$/i.test(t) || /interest run/i.test(t))
      && otherOpts.some((t) => /Other trust creditors/i.test(t)) && invOpts.some((t) => /Investments/i.test(t)) };

  // c. Removed route
  const resp = await p.goto(`${BASE}/trust-accounting/interest`); await settle(p);
  const cText = await p.locator("body").innerText();
  await shot(p, "c-interest-route");
  R.items.c = { status: resp && resp.status(), notFoundText: /could not be found|not found/i.test(cText),
    pass: resp && resp.status() === 404 && /could not be found|not found/i.test(cText) };

  // d. Settings tab
  await p.goto(`${BASE}/settings/fees?tab=trust-accounting`); await settle(p, 2000);
  const dText = await p.locator("main").innerText().catch(async () => p.locator("body").innerText());
  const lpffSection = await p.locator("[data-testid=lpff-rates-section]").count();
  await shot(p, "d-settings-trust-tab");
  R.items.d = { lpffSection, hasLpffRateCopy: /LPFF rate|Add rate|Interest run/i.test(dText),
    // Section866Advisory (648C) — acceptance-grep KEEP (D-3)
    keptS866Advisory: /Contact the LPFF to verify/.test(dText),
    hasInterestRunDue: /Interest Run Due/i.test(dText),
    descriptionPresent: dText.includes("Manage trust accounts, approval settings, and reminder configuration."),
    cards: ["Trust Accounts", "Approval Settings", "Reminder Settings"].map((c) => [c, new RegExp(c, "i").test(dText)]),
    textExcerpt: dText.slice(0, 1500) };
  R.items.d.pass = lpffSection === 0 && !R.items.d.hasLpffRateCopy && !R.items.d.hasInterestRunDue && R.items.d.descriptionPresent
    && R.items.d.cards.every(([, ok]) => ok);

  // e. Settings-nav description: N/A — not rendered on any screen (command-palette-dialog.tsx:46 keeps
  //    descriptions out of the palette; settings rail/family pages render labels only). Probed anyway.
  const bodyText = await p.locator("body").innerText();
  R.items.e = { railDescription: bodyText.includes("Manage trust accounts and approval settings") };
  if (!R.items.e.railDescription) {
    await p.goto(`${BASE}/settings`); await settle(p, 1500);
    R.items.e.settingsIndex = (await p.locator("body").innerText()).includes("Manage trust accounts and approval settings");
    await shot(p, "e-settings-index");
  }
  R.items.e.pass = "N/A";

  // f. Kept surfaces
  R.items.f = {};
  for (const [k, url, re] of [["investments", "/trust-accounting/investments", /Investments/],
    ["other-creditors", "/trust-accounting/other-creditors", /Other trust creditors/i],
    ["reconciliation", "/trust-accounting/reconciliation", /Reconciliation/i]]) {
    const r = await p.goto(`${BASE}${url}`); await settle(p, 1500);
    const h1 = await p.locator("h1").first().innerText().catch(() => "");
    const t = await p.locator("body").innerText();
    R.items.f[k] = { status: r && r.status(), h1, errorBoundary: /Something went wrong|Application error/i.test(t),
      fundCopy: k === "other-creditors" ? /Fund interest and charges/i.test(t) : undefined };
    await shot(p, `f-${k}`);
    R.items.f[k].pass = r && r.status() === 200 && re.test(h1 + t) && !R.items.f[k].errorBoundary;
  }
  R.items.f.pass = ["investments", "other-creditors", "reconciliation"].every((k) => R.items.f[k].pass);

  await browser.close();
  R.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(OUT, "obs-result.json"), JSON.stringify(R, null, 2));
  console.log(JSON.stringify({ a: R.items.a.pass, b: R.items.b.pass, c: R.items.c.pass, d: R.items.d.pass, e: R.items.e.pass, f: R.items.f.pass,
    consoleErrors: R.consoleErrors.length, pageErrors: R.pageErrors.length }));
})().catch((e) => { console.error(e); process.exit(1); });
