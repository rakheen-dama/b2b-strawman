// OBS-after one-off (20:47Z), as executed: why 04-saves run 1 stopped — re-submits Add Tax Rate with the OBS-before name and prints the dialog error, then Cancels. No row is created (backend rejects the duplicate).
const L = require("../../before/scripts/lib.cjs");
(async () => {
  const b = await L.chromium.launch(); const S = await L.loginState(b, "alice");
  const ctx = await L.newCtx(b, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA }); const p = await ctx.newPage();
  const net = []; p.on("response", (r) => { if (r.request().method() === "POST") net.push(`${r.status()} ${r.url().slice(0, 120)}`); });
  await p.goto(`${L.BASE}/settings/fees?tab=tax`, { waitUntil: "load" }); await p.waitForLoadState("networkidle");
  await p.getByRole("button", { name: "Add Tax Rate" }).first().click();
  const dlg = p.getByRole("dialog", { name: "Add Tax Rate" }); await dlg.waitFor();
  await dlg.getByLabel("Name", { exact: true }).fill("OBS 665D Rate"); await dlg.getByLabel("Rate (%)").fill("5");
  await dlg.getByRole("button", { name: "Create", exact: true }).click(); await p.waitForTimeout(2500);
  console.log(JSON.stringify({ open: await dlg.isVisible(), text: (await dlg.innerText()).replace(/\s+/g, " "), net }));
  await p.screenshot({ path: "/tmp/665D-after-raw/probe__tax-dup-create.png" });
  await dlg.getByRole("button", { name: "Cancel" }).click(); await b.close();
})();
