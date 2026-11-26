// 665B OBS-after step 5b (NEW, after-only): copy $RAW/after-checks.json (06) to ../after-checks.json
// without tiles/console bodies (console kept as a count + texts), and build one sheet from 06's tiles:
// K-109 at 390 light + dark, the audit trail at 1024 / 1280 with row 1 expanded, the dark DSAR section.
// Also copies the two K-109 390 crops as separate PNGs (measured crops).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
(async () => {
  const R = JSON.parse(fs.readFileSync(path.join(L.RAW, "after-checks.json"), "utf8"));
  const { tiles, console: c, ...rest } = R;
  fs.writeFileSync(path.join(L.OUT, "after-checks.json"), JSON.stringify({ ...rest, console: { count: c.length, texts: [...new Set(c.map((m) => `${m.type}: ${m.text}`))] } }) + "\n");
  const T = Object.fromEntries(tiles.map(([k, f, cap]) => [k, { f, cap }]));
  const order = ["k109__390-light", "k109__390-dark", "ovf__audit-expanded-1024", "ovf__audit-expanded-1280", "dark__dsar-section"].filter((k) => T[k]);
  const browser = await L.chromium.launch();
  await L.sheet(browser, path.join(L.OUT, "sheet-after-checks.png"), "665B after — K-109 at 390 (light | dark), audit trail row 1 expanded at 1024 and 1280 (light, viewport), Data Subject Access Requests section at 1440 dark", order.map((k) => [T[k].cap, T[k].f]), 2, 640);
  await browser.close();
  for (const t of ["light", "dark"]) if (T[`k109__390-${t}`]) fs.copyFileSync(T[`k109__390-${t}`].f, path.join(L.OUT, `crop-k109-390-${t}.png`));
  console.log("written after-checks.json sheet-after-checks.png crop-k109-390-{light,dark}.png");
})();
