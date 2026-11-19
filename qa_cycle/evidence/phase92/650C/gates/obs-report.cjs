// 650C OBS — drive one report through the firm UI: fill the parameter form, Run Report, read the
// summary cards and the table (textContent, so CSS upper-casing of headers is not mistaken for a code),
// then Export CSV and Export PDF through the buttons (downloads saved under obs/), and extract the PDF text.
const L = require("./obs-lib.cjs");
const { fs, path, OUT, BASE, flat, settle, snap, pdfText, scanAll } = L;

async function fillParams(p, params) {
  for (const [name, v] of Object.entries(params)) {
    const el = p.locator(`#param-${name}`);
    await el.waitFor({ timeout: 30_000 });
    if (v && typeof v === "object" && v.pick) {
      await el.click();
      const opt = p.getByRole("option", { name: v.pick }).first();
      await opt.waitFor({ timeout: 30_000 });
      await opt.click();
    } else if (v && typeof v === "object" && v.option) {
      await el.click();
      await p.getByRole("option", { name: v.option, exact: true }).click();
    } else {
      await el.fill(String(v));
    }
  }
}
/** What the results area shows after a run. */
async function readResults(p) {
  return p.evaluate(() => {
    const t = (e) => (e ? e.textContent.replace(/\s+/g, " ").trim() : null);
    const cards = [...document.querySelectorAll("p.font-mono.text-2xl")].map((v) => ({ label: t(v.previousElementSibling), value: t(v) }));
    const table = document.querySelector("main table") || document.querySelector("table");
    const headers = table ? [...table.querySelectorAll("thead th")].map(t) : [];
    const rows = table ? [...table.querySelectorAll("tbody tr")].map((tr) => [...tr.querySelectorAll("td")].map(t)) : [];
    const noData = [...document.querySelectorAll("p")].some((e) => /No data found for the selected parameters/.test(e.textContent));
    const err = document.querySelector("div.border-red-200");
    const pager = [...document.querySelectorAll("p")].map(t).find((s) => /^Page \d+ of \d+$/.test(s || "")) || null;
    const toasts = [...document.querySelectorAll("[data-sonner-toast]")].map(t);
    const formErrors = [...document.querySelectorAll("form p.text-xs.text-red-600")].map(t);
    return { cards, headers, rows, noData, error: t(err), pager, toasts, formErrors };
  });
}
async function waitForRun(p) {
  // the button shows a spinner while loading; wait for a result, the "no data" text or the error box
  for (let i = 0; i < 120; i++) {
    const r = await readResults(p);
    const busy = await p.getByRole("button", { name: "Run Report" }).isDisabled().catch(() => false);
    if (!busy && (r.cards.length || r.rows.length || r.noData || r.error || r.formErrors.length)) return r;
    await p.waitForTimeout(500);
  }
  return readResults(p);
}
async function download(p, button, file) {
  const errBefore = await p.locator("div.border-red-200").count();
  const [dl] = await Promise.all([p.waitForEvent("download", { timeout: 90_000 }).catch(() => null), p.getByRole("button", { name: button }).click()]);
  if (!dl) return { ok: false, error: flat(await p.locator("div.border-red-200").first().textContent().catch(() => "")) || "no download", errBefore };
  const f = path.join(OUT, file);
  await dl.saveAs(f);
  return { ok: true, file, suggested: dl.suggestedFilename(), bytes: fs.statSync(f).size };
}
/**
 * Run `slug` with `params` (date strings, {pick: /label/} for entity pickers, {option} for enums).
 * Returns the observed page (title, cards, table, errors), CSV/PDF downloads and their scans.
 */
async function runReport(p, tag, slug, params, { exports = true, shot = true } = {}) {
  const out = { slug, params: JSON.parse(JSON.stringify(params, (k, v) => (v instanceof RegExp ? String(v) : v))) };
  await p.goto(`${BASE}/reports/${slug}`, { waitUntil: "load" });
  await p.locator("h1").first().waitFor({ timeout: 60_000 });
  await settle(p, 500);
  out.title = flat(await p.locator("h1").first().textContent());
  out.description = flat(await p.locator("h1 + p").first().textContent().catch(() => ""));
  out.paramLabels = (await p.locator("form label").allTextContents()).map(flat);
  await fillParams(p, params);
  await p.getByRole("button", { name: "Run Report" }).click();
  await p.waitForTimeout(500);
  out.result = await waitForRun(p);
  await settle(p, 500);
  out.result = await readResults(p);
  if (shot) out.shot = await snap(p, tag, `report-${slug}`);
  const cardText = out.result.cards.map((c) => `${c.label}: ${c.value}`).join("\n");
  out.cardScan = scanAll(cardText);
  // table cells: codes everywhere; ISO dates / raw decimals reported per column type (dates are the generic
  // results table's String(value) rendering — recorded, not attributed to the slice, see README)
  const dateCols = new Set();
  out.tableCodeHits = [...new Set(out.result.headers.concat(out.result.rows.flat()).flatMap((c) => L.hits(c ?? "", L.CODES)))];
  out.tableIsoHits = [...new Set(out.result.rows.flat().flatMap((c) => L.hits(c ?? "", L.ISO)))];
  out.tableRawDecimalHits = [...new Set(out.result.rows.flat().flatMap((c) => L.hits(c ?? "", L.RAWDEC)))];
  void dateCols;
  if (exports && !out.result.error) {
    out.csv = await download(p, "Export CSV", `${tag}-${slug}.csv`);
    if (out.csv.ok) {
      const text = fs.readFileSync(path.join(OUT, out.csv.file), "utf8");
      out.csv.lines = text.split(/\r?\n/).filter(Boolean).length;
      out.csv.header = text.split(/\r?\n/)[0];
      out.csv.codeHits = L.hits(text, L.CODES);
    }
    out.pdf = await download(p, "Export PDF", `${tag}-${slug}.pdf`);
    if (out.pdf.ok) {
      out.pdf.magic = fs.readFileSync(path.join(OUT, out.pdf.file)).subarray(0, 5).toString();
      const text = pdfText(path.join(OUT, out.pdf.file));
      fs.writeFileSync(path.join(OUT, `${tag}-${slug}.pdf.txt`), text);
      out.pdf.textChars = text.length;
      out.pdf.extractOk = !text.startsWith("ERR") && text.trim().length > 0;
      out.pdf.scopeNote = flat(text).includes("Trust cash, estates and other trust assets are not recorded in Kazi and are not included in this comparison.");
      out.pdf.scan = scanAll(text);
      // the PDF template upper-cases table headers (CSS text-transform), so plain words like "GROSS" /
      // "CLIENT" in a header are not codes: in the PDF only underscore constants and literals count
      out.pdf.codes = [...new Set(L.stripStatute(text).match(/\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\b|\btrue\b|\bfalse\b|\bnull\b|\bundefined\b|\[object Object\]/g) || [])];
      // clipping: every on-screen column label must appear in the PDF text (whitespace-insensitive)
      const squash = (s) => String(s).toUpperCase().replace(/\s+/g, "");
      const pdfSquashed = squash(text);
      out.pdf.missingColumns = (out.result.headers || []).filter((h) => h && !pdfSquashed.includes(squash(h)));
      try { require("child_process").execFileSync("sips", ["-s", "format", "png", path.join(OUT, out.pdf.file), "--out", path.join(OUT, `${tag}-${slug}.pdf-page1.png`)], { stdio: "ignore" }); out.pdf.page1 = `${tag}-${slug}.pdf-page1.png`; } catch { out.pdf.page1 = null; }
    }
    out.afterExport = await readResults(p);
  }
  return out;
}
module.exports = { runReport, readResults, fillParams, download };
