// one-off: deactivate the "OBS 665D Rate after" left ACTIVE by the crashed run 2 of after/04-saves (UI only)
const L = require("../../before/scripts/lib.cjs");
(async () => {
  const b = await L.chromium.launch(); const S = await L.loginState(b, "alice");
  const ctx = await L.newCtx(b, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA }); const p = await ctx.newPage();
  await p.goto(`${L.BASE}/settings/fees?tab=tax`, { waitUntil: "load" }); await p.waitForLoadState("networkidle");
  const row = p.getByTestId("tax-rate-row").filter({ has: p.getByRole("cell", { name: "OBS 665D Rate after", exact: true }) });
  await row.getByRole("button", { name: "Deactivate OBS 665D Rate after" }).click();
  const dd = p.getByRole("alertdialog", { name: "Deactivate Tax Rate" }); await dd.waitFor();
  await dd.getByRole("button", { name: "Deactivate", exact: true }).click(); await dd.waitFor({ state: "hidden", timeout: 15000 });
  await row.filter({ hasText: "Inactive" }).waitFor({ timeout: 15000 });
  console.log(new Date().toISOString(), (await row.innerText()).replace(/\s+/g, " "));
  await b.close();
})();
