// 665D OBS-after step 8 (NEW): write ../dark-probe.json (07's raw output minus console/tiles, with a
// console total) and one contact sheet of 07's crops + the run-1 duplicate-name rejection probe tile.
// Usage: PHASE=after node 08-dark-assemble.cjs
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");
(async () => {
  const d = JSON.parse(fs.readFileSync(path.join(L.RAW, "dark-probe.json"), "utf8"));
  const { console: c, tiles, ...rest } = d;
  rest.consoleTotals = { messages: c.length, errors: c.filter((m) => m.type === "error" || m.type === "pageerror").length, hydration: c.filter((m) => /hydrat/i.test(m.text)).length };
  fs.writeFileSync(path.join(L.OUT, "dark-probe.json"), JSON.stringify(rest) + "\n");
  // 07 shot the Add Trust Account dialog mid fade-in; 07b's settled light / dark shots replace that tile.
  const settled = ["light", "dark"].map((t) => path.join(L.RAW, `dark__trust-add-dialog-settled-${t}.png`)).filter((f) => fs.existsSync(f));
  const T = tiles.filter(([k]) => !(k === "dark__trust-add-dialog" && settled.length)).map(([k, f, cap]) => [cap, f]);
  settled.forEach((f) => T.push([`Alice 1440 ${f.includes("-dark") ? "dark" : "light"} — Trust accounting › Add Account → dialog "Add Trust Account" (settled; then Cancel; accounts 1 → 1)`, f]));
  const dup = path.join(L.RAW, "probe__tax-dup-create.png");
  if (fs.existsSync(dup)) T.push(["Alice 1440 light — 04-saves run 1 (20:46Z): Add Tax Rate with the OBS-before name \"OBS 665D Rate\" → \"A tax rate with this name already exists.\" (dialog stays open; Cancelled). Backend uniqueness, not 665D.", dup]);
  const b = await L.chromium.launch();
  await L.sheet(b, path.join(L.OUT, "sheet-dark-and-dialogs.png"), "665D after — 07-dark-probe crops (Alice): Tax Rates section 1024 light | dark; Billing Rates with a rate (Edit / Delete) 1440 light | dark; Trust Accounts 1440 dark; Add Trust Account dialog (Cancelled); tax-rate duplicate-name rejection", T, 2, 720);
  await b.close();
  console.log("tiles", T.length, JSON.stringify(rest.consoleTotals));
})();
