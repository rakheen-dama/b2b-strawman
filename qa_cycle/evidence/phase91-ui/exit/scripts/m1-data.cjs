// 670A M1 data step (through the UI, Alice 1440 light), run once after m1-discover.cjs:
//  1. The first checklist on stack A is PLATFORM, and checklists/<id>/edit redirects a PLATFORM template
//     to its detail page (page.tsx: `template.source !== "ORG_CUSTOM"` → redirect), so the edit form
//     is only reachable for an org-custom checklist. None exists on stack A (read-only SELECT on
//     tenant_7d218705360b.checklist_templates: 5 rows, all PLATFORM), so one is created through
//     Settings › Matters › Checklists › New: name "Exit gate checklist 670A", client type default
//     (Company), auto-instantiate OFF (default), one item "Exit gate item". Skipped if it exists.
//     (Two earlier attempts named "670A exit checklist" were refused in the UI with "Generated slug
//     '670a-exit-checklist' … does not match pattern ^[a-z][a-z0-9-]*$" — the known 665C defect for
//     names starting with a digit; nothing was created.)
//  2. Reads (DOM) its id and the id of an org-custom document template ("E2E Cover Letter q280", made
//     by the suites) so the editable branch of templates/<id>/edit is observed next to the read-only
//     PLATFORM branch. Adds checklistCustomId / documentTemplateCustomId to ../ids.json.
// Usage: node m1-data.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const B = L.BASE;
const NAME = "Exit gate checklist 670A";
const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
const linkByText = (page, re, text) => page.evaluate(({ re, text }) => { for (const a of document.querySelectorAll("#main-content a[href]")) { const m = a.getAttribute("href").match(new RegExp(re)); if (m && a.textContent.includes(text)) return m[1]; } return null; }, { re, text });

(async () => {
  const browser = await L.chromium.launch();
  const state = await L.loginState(browser, "alice");
  const ctx = await L.ctxFor(browser, state, "1440-light");
  const page = await ctx.newPage();
  const ids = L.readIds();
  ids.created = ids.created || [];
  await page.goto(`${B}/settings/matters?tab=checklists`, { waitUntil: "load" });
  await L.settle(page);
  let cid = await linkByText(page, `/settings/matters/checklists/(${UUID})`, NAME);
  if (!cid) {
    await page.goto(`${B}/settings/matters/checklists/new`, { waitUntil: "load" });
    await L.settle(page);
    await page.locator("#checklist-name").fill(NAME);
    await page.getByPlaceholder("Item name").first().fill("Exit gate item");
    await page.getByRole("button", { name: "Create Template", exact: true }).click();
    await page.waitForURL(/settings\/matters\?tab=checklists/, { timeout: 30000 });
    await L.settle(page);
    cid = await linkByText(page, `/settings/matters/checklists/(${UUID})`, NAME);
    ids.created.push({ what: "checklist template (ORG_CUSTOM)", name: NAME, id: cid, via: "UI: Settings › Matters › Checklists › New → Create Template (Alice)", at: new Date().toISOString() });
    console.log("created checklist", cid);
  } else console.log("checklist exists", cid);
  ids.checklistCustomId = cid;
  await page.goto(`${B}/settings/documents?tab=templates`, { waitUntil: "load" });
  await L.settle(page);
  ids.documentTemplateCustomId = await linkByText(page, `/settings/documents/templates/(${UUID})`, "E2E Cover Letter");
  console.log("doc template custom", ids.documentTemplateCustomId);
  await ctx.close();
  await browser.close();
  fs.writeFileSync(path.join(L.EXIT, "ids.json"), JSON.stringify(ids) + "\n");
})().catch((e) => { console.error("DATA FAIL", e); process.exit(1); });
