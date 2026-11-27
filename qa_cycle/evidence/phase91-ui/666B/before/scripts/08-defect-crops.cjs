// 666B OBS step 8: measured crops of the fee-note detail page (Alice, light): the header block per
// width, the take-on badge, the totals block of the fee note whose VAT was dropped by the draft save,
// and the Record Payment form (opened on the PARTIALLY_PAID note, then Cancel — nothing saved).
// Read-only. Writes $RAW/crops.json + crop PNGs (05 copies them into ../).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");

const out = { at: new Date().toISOString(), crops: [] };
const consoleSink = [];
const lab = { label: "" };

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const t = await L.idpToken("alice");
  const ids = JSON.parse(fs.readFileSync(path.join(L.RAW, "setup-02b.json"), "utf8")).notes;
  const takeOn = L.list((await L.apiGet(t, "/api/invoices")).json).find((i) => i.invoiceNumber === "PS-1001");
  const CROPS = [
    // [key, id, width, what, caption]
    ["defect__overdue-header-1440", ids["sent-overdue"], 1440, "header", "INV-0009 (SENT, due 1 Nov 2026, issued 27 Nov 2026) header at 1440: badge \"Sent\", no overdue cue; due date before issue date"],
    ["defect__vat-dropped-totals-1440", ids["sent-overdue"], 1440, "lines+totals", "INV-0009 line items + totals at 1440: line VAT R 281,25 and breakdown row VAT — Standard (15%) R 281,25, but Total R 1 875,00 = subtotal (VAT zeroed by the draft Save Changes)"],
    ["header-wrap-1024", ids.partial, 1024, "header", "INV-0010 (PARTIALLY_PAID) header at 1024: meta row wraps to 2 lines (Currency alone), header block 84 px"],
    ["header-1440", ids.partial, 1440, "header", "INV-0010 (PARTIALLY_PAID) header at 1440: one meta row, header block 60 px"],
    ["takeon-badge-1440", takeOn.id, 1440, "header", "PS-1001 (take-on, SENT, due 14 Aug 2026) header at 1440: badges \"Sent\" + \"Brought forward — take-on\""],
    ["record-payment-form-1440", ids.partial, 1440, "payment-form", "INV-0010 Record Payment opened (then Cancel): record-payment-form, Balance due line, Amount / Payment method (payment-method-select) / Payment Reference"],
  ];
  for (const [key, id, w, what, caption] of CROPS) {
    const ctx = await L.newCtx(browser, S, { width: w, height: 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    lab.label = `crops|${key}`;
    await p.goto(`${L.BASE}/invoices/${id}`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.waitForTimeout(700);
    const f = path.join(L.RAW, `${key}.png`);
    let box = null;
    if (what === "header") {
      const hdr = p.locator("#main-content h1").locator("xpath=ancestor::div[contains(@class,'justify-between')][1]");
      box = await hdr.boundingBox();
      await hdr.screenshot({ path: f });
    } else if (what === "lines+totals") {
      await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); await p.evaluate(`window.__detail = (${P.DETAIL.toString()})`);
      const d = await p.evaluate(() => window.__detail());
      const top = await p.locator("#main-content h2", { hasText: "Line Items" }).boundingBox();
      const tb = d.totals.box;
      box = { x: d.pageFrame.x - 8, y: top.y - 8, width: d.pageFrame.w + 16, height: tb.y + tb.h - top.y + 16 };
      await p.screenshot({ path: f, fullPage: true, clip: box });
    } else {
      await p.locator("#main-content").getByRole("button", { name: "Record Payment" }).click();
      const form = p.getByTestId("record-payment-form");
      await form.waitFor();
      box = await form.boundingBox();
      await form.screenshot({ path: f });
      out.paymentFormText = (await form.innerText()).replace(/\s+/g, " ");
      out.paymentFormCounts = { form: await p.getByTestId("record-payment-form").count(), method: await p.getByTestId("payment-method-select").count(), balance: await p.getByTestId("payment-form-balance-due").count() };
      await form.getByRole("button", { name: "Cancel" }).click();
      await form.waitFor({ state: "detached" });
    }
    out.crops.push({ key, id, width: w, what, caption, box: box && Object.fromEntries(Object.entries(box).map(([k, v]) => [k, Math.round(v)])) });
    await ctx.close();
  }
  await browser.close();
  out.console = consoleSink;
  out.tiles = out.crops.map((c) => [c.key, path.join(L.RAW, `${c.key}.png`), c.caption]);
  fs.writeFileSync(path.join(L.RAW, "crops.json"), JSON.stringify(out));
  console.log(JSON.stringify(out.crops.map((c) => [c.key, c.box])), JSON.stringify(out.paymentFormCounts), out.paymentFormText, "console", consoleSink.length);
})();
