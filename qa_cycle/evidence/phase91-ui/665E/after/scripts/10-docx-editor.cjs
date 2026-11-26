// 665E OBS-AFTER step 10 (NEW): the uploaded Word template's editor page. 00-discover post showed
// GET /api/templates/{docx id} → 500 for Alice and Carol (backend NPE in
// DocumentTemplateService.extractClauseIds on a DOCX template's null content). Read-only: open
// /settings/documents/templates/{id}/edit as Alice, 1440×900 light, probe and shoot. Also opens the
// Templates row link (the user path) to confirm the same page. Writes $RAW/docx-editor.json + tile.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

(async () => {
  const up = JSON.parse(fs.readFileSync(path.join(L.RAW, "upload.json"), "utf8"));
  const id = up.uploaded.id;
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  const sink = [];
  L.watch(p, sink, { label: "docx-editor" });
  await p.goto(`${L.BASE}/settings/documents?tab=templates`, { waitUntil: "load" });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.getByRole("link", { name: up.uploaded.name, exact: true }).click();
  await p.waitForURL(new RegExp(`/templates/${id}/edit`), { timeout: 30000 });
  await p.waitForLoadState("networkidle").catch(() => {});
  await p.waitForTimeout(800);
  const m = await p.evaluate(() => {
    const main = document.querySelector("main#main-content");
    const t = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
    return { url: location.pathname, h1: [...main.querySelectorAll("h1")].map(t), text: t(main).slice(0, 400), somethingWentWrong: document.body.innerText.includes("Something went wrong"), hasSave: [...main.querySelectorAll("button")].some((b) => t(b) === "Save"), title: document.title };
  });
  const f = path.join(L.RAW, "defect__docx-editor-1440.png");
  await p.screenshot({ path: f });
  await browser.close();
  const a = await L.idpToken("alice");
  const api = await L.apiGet(a, `/api/templates/${id}`);
  const out = { at: new Date().toISOString(), id, apiStatus: api.status, apiBody: api.json || api.text, page: m, console: sink, tiles: [["defect__docx-editor-1440", f, `Alice 1440 light — Templates › "${up.uploaded.name}" (Word) row link → editor: ${m.text.slice(0, 120)}`]] };
  fs.writeFileSync(path.join(L.RAW, "docx-editor.json"), JSON.stringify(out));
  console.log(JSON.stringify({ apiStatus: out.apiStatus, page: m, console: sink.map((x) => x.text.slice(0, 120)) }));
})();
