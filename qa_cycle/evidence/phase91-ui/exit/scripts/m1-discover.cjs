// 670A M1 step 0: discover the <id>s the settings sub-pages need, in the browser (Alice, 1440 light):
// open the owning tab and read the first row's link (href in #main-content); if the rows are not
// links, click the first body row and read the URL it navigates to. Read-only (no save, no create).
// Also records, for M2, the first matter / client id from /matters and /clients the same way.
// Writes ../ids.json (single line). Usage: node m1-discover.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const B = L.BASE;
const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
const FIND = [
  // id key, owning tab URL, href regex of the detail page
  ["documentTemplateId", `${B}/settings/documents?tab=templates`, `/settings/documents/templates/(${UUID})`],
  ["checklistId", `${B}/settings/matters?tab=checklists`, `/settings/matters/checklists/(${UUID})`],
  ["requestTemplateId", `${B}/settings/matters?tab=request-templates`, `/settings/matters/request-templates/(${UUID})`],
  ["matterTemplateId", `${B}/settings/matters?tab=templates`, `/settings/matters/templates/(${UUID})`],
  ["matterId", `${B}/matters`, `/(?:matters|projects)/(${UUID})`],
  ["clientId", `${B}/clients`, `/(?:clients|customers)/(${UUID})`],
];

(async () => {
  const browser = await L.chromium.launch();
  const state = await L.loginState(browser, "alice");
  const ctx = await L.ctxFor(browser, state, "1440-light");
  const page = await ctx.newPage();
  const ids = { discoveredAt: new Date().toISOString(), by: "alice 1440 light, browser DOM (first row link, else first row click)", how: {} };
  for (const [k, url, re] of FIND) {
    await page.goto(url, { waitUntil: "load" });
    await L.settle(page);
    let hit = await page.evaluate((re) => {
      const rx = new RegExp(re);
      const main = document.querySelector("#main-content") || document.body;
      for (const a of main.querySelectorAll("a[href]")) { const m = a.getAttribute("href").match(rx); if (m) return { id: m[1], href: a.getAttribute("href"), text: a.textContent.trim().slice(0, 60) }; }
      return null;
    }, re);
    let how = "first row link";
    if (!hit) {
      how = "first row click";
      const row = page.locator("#main-content tbody tr").first();
      if (await row.count()) {
        await row.locator("td").first().click().catch(() => {});
        await page.waitForURL(new RegExp(re), { timeout: 15000 }).catch(() => {});
        const m = page.url().match(new RegExp(re));
        if (m) hit = { id: m[1], href: new URL(page.url()).pathname, text: null };
      }
    }
    ids[k] = hit ? hit.id : null;
    ids.how[k] = hit ? { how, from: url.replace(B, ""), href: hit.href.replace(`/org/${L.ORG}`, ""), text: hit.text } : { how: "not found", from: url.replace(B, "") };
    console.log(k, JSON.stringify(ids.how[k]));
  }
  await ctx.close();
  await browser.close();
  fs.writeFileSync(path.join(L.EXIT, "ids.json"), JSON.stringify(ids) + "\n");
})().catch((e) => { console.error("DISCOVER FAIL", e); process.exit(1); });
