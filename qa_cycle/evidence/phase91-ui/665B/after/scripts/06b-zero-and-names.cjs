// 665B OBS-after step 6b (NEW, after-only, read-only; Alice 1440 light): on the zero-results state
// (Entity ID = an unused UUID, the same URL 03 uses) count the smoke spec's `/No audit events/i`, the
// pager's exact text, the EmptyState heading; and the accessible names of the audit section headings
// (a heading's name is what a screen-reader heading list reads; the Events h2 contains the total span).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  const sink = []; L.watch(p, sink, { label: "06b" });
  const R = {};
  await p.goto(`${L.BASE}/settings/firm?tab=audit-trail`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  R.headingNames = (await p.accessibility?.snapshot?.().catch(() => null)) ? "n/a" : undefined;
  R.headings = await p.evaluate(() => [...document.querySelectorAll("#main-content h1,#main-content h2")].map((h) => ({ tag: h.tagName.toLowerCase(), textContent: h.textContent, innerText: h.innerText })));
  R.eventsHeadingByRole = { "name /^Events \\d/ (space)": await p.getByRole("heading", { level: 2, name: /^Events \d/ }).count(), "name /^Events\\d/ (no space)": await p.getByRole("heading", { level: 2, name: /^Events\d/ }).count(), "name 'Events' exact": await p.getByRole("heading", { name: "Events", exact: true }).count() };
  await p.goto(`${L.BASE}/settings/firm?tab=audit-trail&entityId=00000000-0000-4000-8000-000000000665`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  const nav = p.getByRole("navigation", { name: "Audit log pagination" });
  R.zero = {
    "audit-smoke.spec:53 body text /No audit events/i": await p.getByText(/No audit events/i).count(),
    "heading 'No audit events in this range.'": await p.getByRole("heading", { name: "No audit events in this range." }).count(),
    pagerLabelText: (await nav.locator("p").innerText()).trim(),
    "pager text 'No events' exact": await nav.getByText("No events", { exact: true }).count(),
    "pager 'Page 1 of 0'": await nav.getByText(/Page 1 of 0/).count(),
    prevDisabled: await nav.getByRole("button", { name: "Previous", exact: true }).isDisabled(),
    nextDisabled: await nav.getByRole("button", { name: "Next", exact: true }).isDisabled(),
  };
  await browser.close();
  R.console = sink;
  fs.writeFileSync(path.join(L.OUT, "zero-and-names.json"), JSON.stringify(R) + "\n");
  console.log(JSON.stringify(R, null, 1));
})();
