// 666B OBS step 3: drive the five OBS drafts (02 / 02b) to their target status through the detail
// page UI (Alice, 1440×900 light), and observe what the page updates WITHOUT a reload.
//   approved     : Approve.
//   sent-overdue : draft form Due Date ← 2026-11-01 → Save Changes (is a past due date accepted?) →
//                  Approve → Send fee note (send-override-dialog → Send Anyway, if it opens).
//   partial      : Approve → Send → Record Payment R 1 000,00 EFT ref "666B-OBS-PART" → Confirm.
//   paid         : Approve → Send → Record Payment (default = full balance) ref "666B-OBS-PAID" → Confirm.
//   void         : Approve → Void (native confirm → accept).
// Plus, read-only on the seeded draft: "Add Line" (placeholder "Line item description") and the row
// "Edit …" form opened and cancelled (no save).
// At every transition: the DETAIL probe right after the action settles (no reload), and for the two
// payments also after a reload, so before / after-save / after-reload can be compared. Server-action
// POSTs and RSC fetches after each click are counted. Each step is skipped when the API shows the
// fee note already past it. Writes $RAW/lifecycle.json + tiles.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");

const out = { at: new Date().toISOString(), notes: {}, draftEditors: null, mailpit: {} };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const net = [];
const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(800); };
const shot = async (p, key, caption, opt = { fullPage: true }) => { const f = path.join(L.RAW, `${key}.png`); await p.screenshot({ path: f, ...opt }); tiles.push([key, f, caption]); };
const probe = async (p) => { await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); await p.evaluate(`window.__detail = (${P.DETAIL.toString()})`); return p.evaluate(() => window.__detail()); };
const pick = (d) => ({ h1: d.h1 && d.h1.text, badges: d.badges.map((b) => `${b.text}/${b.variant}`), meta: d.meta, buttons: d.buttons.map((b) => `${b.text}/${b.variant}`), totals: d.totals && d.totals.rows, testids: Object.fromEntries(Object.entries(d.testids).filter(([, v]) => v.n).map(([k, v]) => [k, `${v.n} ${v.text || ""}`.trim()])), paymentHistory: d.paymentHistory && { rows: d.paymentHistory.rows, text: d.paymentHistory.text.slice(0, 200) }, indicators: d.indicators.map((x) => x.text.slice(0, 160)), readonly: d.readonly, alerts: d.alerts, generatedDocs: d.generatedDocs && d.generatedDocs.text.slice(0, 160), audit: d.audit && d.audit.text.slice(0, 300), collectionActivity: d.collectionActivity && d.collectionActivity.text.slice(0, 160) });
const mailCount = async () => (await (await fetch("http://localhost:8026/api/v1/messages?limit=1")).json()).total;

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const t = await L.idpToken("alice");
  const setup = JSON.parse(fs.readFileSync(path.join(L.RAW, "setup-02b.json"), "utf8"));
  const ids = setup.notes;
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  const nativeDialogs = [];
  p.on("dialog", (d) => { nativeDialogs.push({ label: lab.label, type: d.type(), message: d.message() }); d.accept(); });
  p.on("request", (r) => { if (r.method() === "POST" || r.headers()["rsc"]) net.push({ label: lab.label, method: r.method(), url: r.url().replace(L.C.APP, "").slice(0, 120), action: !!r.headers()["next-action"], rsc: !!r.headers()["rsc"] }); });
  const api = async (id) => (await L.apiGet(t, `/api/invoices/${id}`)).json;
  const open = async (id) => { await p.goto(`${L.BASE}/invoices/${id}`, { waitUntil: "load" }); await settle(p); };
  const step = async (key, label, fn) => {
    lab.label = `${key}|${label}`;
    const n0 = net.length, m0 = await mailCount();
    await fn();
    await settle(p);
    const d = await probe(p);
    const a = await api(ids[key]);
    const s = { step: label, at: new Date().toISOString(), url: p.url().replace(L.BASE, ""), dom: pick(d), api: { status: a.status, invoiceNumber: a.invoiceNumber, issueDate: a.issueDate, dueDate: a.dueDate, amountPaid: a.amountPaid, balanceDue: a.balanceDue, total: a.total, paidAt: a.paidAt }, requests: net.slice(n0).map((r) => `${r.method}${r.action ? " action" : ""}${r.rsc ? " rsc" : ""} ${r.url}`), mailpitDelta: (await mailCount()) - m0 };
    out.notes[key].steps.push(s);
    await shot(p, `lc__${key}-${out.notes[key].steps.length}-${label.replace(/\W+/g, "_")}`, `${a.invoiceNumber || "Draft"} (${key}) — ${label} → API ${a.status}; h1 "${s.dom.h1}"; badge ${s.dom.badges.join(",")}; buttons ${s.dom.buttons.map((x) => x.split("/")[0]).join(", ")}`);
    return { d, a, s };
  };
  const send = async (key) => {
    await p.locator("#main-content").getByRole("button", { name: "Send fee note", exact: true }).click();
    const r = await Promise.race([p.getByTestId("send-override-dialog").waitFor({ timeout: 20000 }).then(() => "override"), p.locator("#main-content").getByRole("button", { name: "Record Payment" }).waitFor({ timeout: 20000 }).then(() => "sent")]);
    out.notes[key].sendResult = r;
  };
  const sendFlow = async (key) => {
    const { s } = await step(key, "Send fee note clicked", () => send(key));
    if (out.notes[key].sendResult === "override") {
      out.notes[key].overrideText = (await p.getByTestId("send-override-dialog").innerText()).replace(/\s+/g, " ");
      await step(key, "Send Anyway", async () => { await p.getByTestId("send-override-dialog").getByRole("button", { name: "Send Anyway" }).click(); await p.locator("#main-content").getByRole("button", { name: "Record Payment" }).waitFor({ timeout: 20000 }); });
    }
    return s;
  };

  for (const key of ["approved", "sent-overdue", "partial", "paid", "void"]) {
    const id = ids[key];
    out.notes[key] = { id, steps: [] };
    let a = await api(id);
    lab.label = `${key}|open`;
    await open(id);
    const d0 = await probe(p);
    out.notes[key].initial = { status: a.status, dom: pick(d0) };
    if (key === "sent-overdue" && a.status === "DRAFT") {
      const target = L.SETUP.notes.find((n) => n.key === key).dueDate;
      await step(key, `Due Date ${target} + Save Changes`, async () => {
        await p.locator("#invoice-due-date").fill(target);
        await p.locator("#main-content").getByRole("button", { name: "Save Changes" }).click();
      });
      a = await api(id);
      out.notes[key].dueDateAfterSave = a.dueDate;
    }
    if (a.status === "DRAFT") {
      await step(key, "Approve", async () => { await p.locator("#main-content").getByRole("button", { name: "Approve", exact: true }).click(); await p.locator("#main-content").getByRole("button", { name: "Send fee note", exact: true }).waitFor({ timeout: 20000 }); });
      a = await api(id);
    }
    if (key === "approved") continue;
    if (key === "void") {
      if (a.status === "APPROVED") await step(key, "Void (confirm accepted)", async () => { await p.locator("#main-content").getByRole("button", { name: "Void", exact: true }).click(); await p.locator("#main-content").getByRole("button", { name: "Void", exact: true }).waitFor({ state: "detached", timeout: 20000 }); });
      continue;
    }
    if (a.status === "APPROVED") { await sendFlow(key); a = await api(id); }
    if (key === "sent-overdue") continue;
    if (a.status === "SENT") {
      const amount = key === "partial" ? "1000" : null;
      const ref = key === "partial" ? "666B-OBS-PART" : "666B-OBS-PAID";
      lab.label = `${key}|before payment`;
      out.notes[key].beforePayment = pick(await probe(p));
      await shot(p, `lc__${key}-pay-0-before`, `${a.invoiceNumber} (${key}) — before Record Payment (SENT)`);
      await step(key, "Record Payment opened", async () => { await p.locator("#main-content").getByRole("button", { name: "Record Payment" }).click(); await p.getByTestId("record-payment-form").waitFor(); });
      const form = p.getByTestId("record-payment-form");
      out.notes[key].formDefaults = { amount: await form.getByLabel("Amount", { exact: true }).inputValue(), method: (await form.getByTestId("payment-method-select").innerText()).trim(), balanceLine: (await p.getByTestId("payment-form-balance-due").innerText()).trim(), labels: await form.locator("label").allInnerTexts() };
      if (amount) await form.getByLabel("Amount", { exact: true }).fill(amount);
      await form.getByPlaceholder(/CHK-12345/).fill(ref);
      await shot(p, `lc__${key}-pay-1-filled`, `${a.invoiceNumber} (${key}) — record-payment-form filled: amount ${amount || out.notes[key].formDefaults.amount} EFT ref ${ref} (before Confirm Payment)`);
      const { s: after } = await step(key, "Confirm Payment (no reload)", async () => { await form.getByRole("button", { name: "Confirm Payment" }).click(); await form.waitFor({ state: "detached", timeout: 20000 }); await p.waitForTimeout(1500); });
      out.notes[key].afterSaveNoReload = after.dom;
      await step(key, "after reload", async () => { await p.reload({ waitUntil: "load" }); });
      out.notes[key].afterReload = out.notes[key].steps[out.notes[key].steps.length - 1].dom;
    }
  }

  // Seeded draft: the inline line editors (opened, then cancelled — nothing saved).
  const draft = L.list((await L.apiGet(t, "/api/invoices")).json).find((i) => i.status === "DRAFT" && i.notes === "E2E seed fee note — draft") || L.list((await L.apiGet(t, "/api/invoices")).json).find((i) => i.status === "DRAFT" && !Object.values(ids).includes(i.id));
  if (draft) {
    lab.label = "draft-editors|open";
    await open(draft.id);
    const before = await probe(p);
    const ed = { id: draft.id, lineRows: before.lineTable && before.lineTable.rows, placeholderBefore: before.placeholderLineDesc };
    await p.locator("#main-content").getByRole("button", { name: "Add Line", exact: true }).click();
    await p.waitForTimeout(500);
    const add = await probe(p);
    ed.placeholderAfterAddLine = add.placeholderLineDesc;
    ed.addForm = add.indicators.concat([]).map((x) => x.text);
    await shot(p, "lc__draft-add-line-open", `seeded Draft (${draft.id.slice(0, 8)}) — "Add Line" clicked: Add Line Item form (placeholder "Line item description" ×${add.placeholderLineDesc}); cancelled after`);
    await p.locator("#main-content").getByRole("button", { name: "Cancel" }).first().click();
    const editBtn = p.locator("#main-content").getByRole("button", { name: /^Edit / }).first();
    ed.editButtonLabel = await editBtn.getAttribute("aria-label");
    await editBtn.click();
    await p.waitForTimeout(500);
    const edit = await probe(p);
    ed.placeholderAfterEdit = edit.placeholderLineDesc;
    ed.editFormHeading = await p.locator("#main-content h3", { hasText: "Edit Line Item" }).count();
    await shot(p, "lc__draft-edit-line-open", `seeded Draft — "${ed.editButtonLabel}" clicked: Edit Line Item form (placeholder ×${edit.placeholderLineDesc}); cancelled after`);
    await p.locator("#main-content").getByRole("button", { name: "Cancel" }).first().click();
    const a2 = (await L.apiGet(t, `/api/invoices/${draft.id}`)).json;
    ed.unchanged = { status: a2.status, updatedAt: a2.updatedAt, lines: a2.lines.length };
    out.draftEditors = ed;
  }
  await ctx.close();
  await browser.close();
  out.nativeDialogs = nativeDialogs;
  out.console = consoleSink;
  out.tiles = tiles;
  out.net = net;
  fs.writeFileSync(path.join(L.RAW, "lifecycle.json"), JSON.stringify(out));
  for (const [k, n] of Object.entries(out.notes)) {
    console.log("==", k, n.id, "initial", n.initial.status, "send", n.sendResult || "-", "dueAfterSave", n.dueDateAfterSave || "-");
    for (const s of n.steps) console.log("  ", s.step, JSON.stringify(s.api), "mail+", s.mailpitDelta, "req", JSON.stringify(s.requests).slice(0, 300));
  }
  console.log("partial before", JSON.stringify(out.notes.partial && out.notes.partial.beforePayment));
  console.log("partial afterNoReload", JSON.stringify(out.notes.partial && out.notes.partial.afterSaveNoReload));
  console.log("partial afterReload", JSON.stringify(out.notes.partial && out.notes.partial.afterReload));
  console.log("draftEditors", JSON.stringify(out.draftEditors));
  console.log("native", JSON.stringify(nativeDialogs), "console", consoleSink.length, JSON.stringify(consoleSink.slice(0, 6)));
})();
