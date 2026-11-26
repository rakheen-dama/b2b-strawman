// 665B OBS step 3b (added after 03-audit.cjs ran; 03 is not edited): two follow-ups to 03's results.
//  (a) 03's console had "Failed to load resource: 404" errors after client navigation on the audit
//      tab, without URLs. Here every response >= 400 is recorded with its URL, on a fresh load of
//      page 1, after Next (client nav) and after hovering every entity link on the page (Next.js
//      <Link> prefetch), so the 404s can be attributed (K-45 links or something else).
//  (b) A tight crop of the pending state: Events card header + pager while the page RSC request is
//      held 3 s (1440×900 light, scrolled so both are in view), for the contact sheet.
// Read-only. Output: $RAW/audit-extra.json + tiles.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const TAB = `${L.BASE}/settings/firm?tab=audit-trail`;
const R = { capturedAt: new Date().toISOString(), failedResponses: {}, pendingCrop: null };
const consoleSink = [];
const tiles = [];
const lab = { label: "" };
const uuid = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const failed = [];
  p.on("response", (r) => { if (r.status() >= 400) failed.push({ phase: lab.label, status: r.status(), url: r.url().replace(L.APP, "").replace(uuid, "{uuid}"), rsc: /[?&]_rsc=/.test(r.url()) || r.request().headers()["rsc"] === "1", prefetch: r.request().headers()["next-router-prefetch"] || null }); });

  lab.label = "load";
  await p.goto(TAB, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(3000);
  lab.label = "after-next";
  await Promise.all([p.waitForURL(/page=1/), p.getByRole("button", { name: "Next", exact: true }).click()]);
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(3000);
  lab.label = "hover-links";
  const links = p.getByTestId("entity-cell-link");
  const n = await links.count();
  for (let i = 0; i < n; i++) { await links.nth(i).hover().catch(() => {}); await p.waitForTimeout(150); }
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(1500);
  const groups = {};
  for (const f of failed) { const k = `${f.phase} ${f.status} ${f.url.replace(/\?.*$/, "")}`; groups[k] = (groups[k] || 0) + 1; }
  R.failedResponses = { total: failed.length, grouped: groups, consoleErrors: consoleSink.filter((c) => c.type === "error").length };

  // (b) pending crop
  lab.label = "pending-crop";
  await p.goto(TAB, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.route("**/settings/firm**", async (route) => {
    const h = route.request().headers();
    if (h["rsc"] === "1" || /[?&]_rsc=/.test(route.request().url())) await new Promise((r) => setTimeout(r, 3000));
    await route.continue().catch(() => {});
  });
  // Shrink the gap: collapse to 3 rows visually is not possible (read-only), so take two clips:
  // the Events card title and the pager, at the in-flight moment.
  const title = p.locator('[data-slot="card-title"]', { hasText: /^Events/ });
  const nav = p.getByRole("navigation", { name: "Audit log pagination" });
  await nav.scrollIntoViewIfNeeded();
  await p.getByRole("button", { name: "Next", exact: true }).click();
  await p.waitForTimeout(700);
  const navInfo = await nav.evaluate((n) => ({ text: n.innerText.replace(/\s+/g, " ").trim(), nextDisabled: n.querySelectorAll("button")[1].disabled, url: location.search }));
  const f1 = path.join(L.RAW, "audit__pending-pager.png");
  await nav.screenshot({ path: f1 });
  tiles.push(["audit__pending-pager", f1, `pending (700 ms, RSC held): pager "${navInfo.text}", Next disabled=${navInfo.nextDisabled}, URL ${navInfo.search || navInfo.url}`]);
  await title.scrollIntoViewIfNeeded();
  const titleInfo = await title.evaluate((t) => ({ text: t.innerText.replace(/\s+/g, " ").trim(), tableOpacity: getComputedStyle(t.closest('[data-slot="card"]').querySelector("table")).opacity, ariaBusy: t.closest('[data-slot="card"]').getAttribute("aria-busy") }));
  const f2 = path.join(L.RAW, "audit__pending-title.png");
  const box = await title.boundingBox();
  await p.screenshot({ path: f2, clip: { x: box.x - 24, y: box.y - 16, width: 864, height: 260 } });
  tiles.push(["audit__pending-title", f2, `pending: Events card title "${titleInfo.text}", table opacity ${titleInfo.tableOpacity}, aria-busy ${titleInfo.ariaBusy}`]);
  await p.waitForURL(/page=1/, { timeout: 30000 });
  await p.unroute("**/settings/firm**");
  R.pendingCrop = { navInfo, titleInfo };

  await browser.close();
  R.console = consoleSink;
  R.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "audit-extra.json"), JSON.stringify(R));
  console.log(JSON.stringify({ failedResponses: R.failedResponses, pendingCrop: R.pendingCrop }, null, 1));
})();
