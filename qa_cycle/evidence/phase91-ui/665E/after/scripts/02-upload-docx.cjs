// 665E OBS-after step 2 (NEW, after only): make sure a Word (DOCX) template row exists for the K-117
// Format-column check. The stack has none (discover-pre: 22 PLATFORM Tiptap templates) and the repo
// has no .docx fixture, so a minimal OOXML package generated in /tmp (/tmp/665E-after-fixture/
// obs-665e-after.docx, one paragraph) is uploaded through the UI as Alice (1440 light):
//   Templates › "Upload Word Template" → setInputFiles(docx-file-input) → Name "OBS 665E Word" →
//   "Upload Template" (category / entity left at the defaults ENGAGEMENT_LETTER / PROJECT).
// Idempotent: skips the upload if an active DOCX template already exists. Writes $RAW/upload.json.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const FILE = "/tmp/665E-after-fixture/obs-665e-after.docx";
const NAME = "OBS 665E Word";

(async () => {
  const a = await L.idpToken("alice");
  const before = L.list((await L.apiGet(a, "/api/templates")).json);
  const existing = before.filter((t) => t.format === "DOCX" && t.active);
  const out = { at: new Date().toISOString(), docxBefore: existing.map((t) => ({ id: t.id, name: t.name })), uploaded: null };
  if (existing.length) {
    console.log("DOCX template exists, skipping", JSON.stringify(out.docxBefore));
  } else {
    const browser = await L.chromium.launch();
    const S = await L.loginState(browser, "alice");
    const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    const posts = [];
    p.on("response", (r) => { const q = r.request(); if (q.method() === "POST" && q.headers()["next-action"]) posts.push({ url: r.url().replace(L.BASE, ""), status: r.status() }); });
    await p.goto(`${L.BASE}/settings/documents?tab=templates`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.getByRole("button", { name: "Upload Word Template" }).click();
    const dlg = p.getByRole("dialog", { name: "Upload Word Template" });
    await dlg.waitFor();
    await dlg.getByTestId("docx-file-input").setInputFiles(FILE);
    await dlg.locator("#docx-template-name").fill(NAME);
    const btn = dlg.getByRole("button", { name: "Upload Template" });
    await btn.click();
    await p.waitForURL(/\/templates\/[0-9a-f-]{36}\/edit/, { timeout: 30000 }).catch(() => {});
    out.urlAfter = p.url().replace(L.BASE, "");
    out.dialogError = (await dlg.count()) && (await dlg.isVisible().catch(() => false)) ? (await dlg.innerText()).slice(0, 400) : null;
    out.serverActionPosts = posts;
    await browser.close();
    const after = L.list((await L.apiGet(a, "/api/templates")).json);
    const t = after.find((x) => x.name === NAME && x.format === "DOCX");
    out.uploaded = t ? { id: t.id, name: t.name, category: t.category, format: t.format, source: t.source, active: t.active, primaryEntityType: t.primaryEntityType, docxFileName: t.docxFileName, docxFileSize: t.docxFileSize } : null;
    console.log(JSON.stringify(out));
  }
  fs.writeFileSync(path.join(L.RAW, "upload.json"), JSON.stringify(out));
})();
