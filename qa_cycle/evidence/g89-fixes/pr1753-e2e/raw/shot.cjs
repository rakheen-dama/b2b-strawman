// PR #1753 gate F46 evidence: screenshot a page and report which font faces the browser loaded.
// usage: node shot.cjs <pkgDir> <frontend|portal> <url> <out.png>
//  frontend: <url> is the base (http://localhost:3001); logs in via /mock-login (Alice), lands on the dashboard
//  portal:   <url> is a full exchange URL (/auth/exchange?token=…&orgId=…) or any portal page
const { createRequire } = require("module");
const [, , pkgDir, mode, url, out] = process.argv;
const { chromium } = createRequire(pkgDir + "/package.json")("@playwright/test");
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
  const fontReqs = [];
  p.on("response", (r) => {
    if (/\.woff2|fonts\.g(oogleapis|static)/.test(r.url()))
      fontReqs.push(`${r.status()} ${r.headers()["content-type"]} ${r.url()}`);
  });
  if (mode === "frontend") {
    await p.goto(url + "/mock-login");
    await p.getByRole("button", { name: /sign in/i }).first().click();
    await p.waitForURL(/dashboard/, { timeout: 60000 });
  } else {
    await p.goto(url);
    await p.waitForURL((u) => !/\/auth\/exchange|\/login/.test(u.pathname), { timeout: 60000 });
  }
  await p.waitForLoadState("networkidle");
  await p.waitForTimeout(1500);
  await p.evaluate(() => document.fonts.ready);
  const info = await p.evaluate(() => {
    const pick = (sel) => {
      const el = document.querySelector(sel);
      return el ? { sel, text: el.textContent.trim().slice(0, 50), fontFamily: getComputedStyle(el).fontFamily } : null;
    };
    return {
      url: location.href,
      loadedFaces: [...document.fonts].filter((f) => f.status === "loaded").map((f) => `${f.family} w=${f.weight}`),
      heading: pick("h1") || pick("h2"),
      body: getComputedStyle(document.body).fontFamily,
      htmlClass: document.documentElement.className,
    };
  });
  console.log(JSON.stringify({ ...info, fontResponses: fontReqs }, null, 1));
  await p.screenshot({ path: out });
  await b.close();
})().catch((e) => {
  console.error("SHOT-FAIL", e);
  process.exit(1);
});
