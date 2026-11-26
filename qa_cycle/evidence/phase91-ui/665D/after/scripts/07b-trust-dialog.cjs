// OBS-after one-off: Add Trust Account dialog opened, settled 1.2 s (07 shot it mid fade-in), shot light + dark, Cancelled.
const L = require("../../before/scripts/lib.cjs");
(async () => {
  const b = await L.chromium.launch(); const S = await L.loginState(b, "alice");
  for (const theme of ["light", "dark"]) {
    const ctx = await L.newCtx(b, S, { width: 1440, height: 900, theme, extra: L.EXTRA }); const p = await ctx.newPage();
    await p.goto(`${L.BASE}/settings/fees?tab=trust-accounting`, { waitUntil: "load" }); await p.waitForLoadState("networkidle");
    await p.getByTestId("add-trust-account-button").click(); const d = p.getByRole("dialog"); await d.waitFor(); await p.waitForTimeout(1200);
    console.log(theme, await d.evaluate((e) => getComputedStyle(e).opacity));
    await d.screenshot({ path: `/tmp/665D-after-raw/dark__trust-add-dialog-settled-${theme}.png` });
    await d.getByRole("button", { name: "Cancel" }).click(); await d.waitFor({ state: "hidden" }); await ctx.close();
  }
  await b.close();
})();
