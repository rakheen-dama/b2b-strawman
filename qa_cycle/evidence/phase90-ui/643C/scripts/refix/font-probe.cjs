// 643C re-OBS: where does the body font come from vs the `font-sans` class (Plex)?
const fs = require("fs"), path = require("path");
const { chromium, PROF, OUT, session, open } = require("./common.cjs");
(async () => {
  const browser = await chromium.launch(); const { page } = await session(browser, {});
  await open(page, PROF, '[data-slot="card"] table');
  const out = await page.evaluate(async () => {
    const f = (e) => e ? getComputedStyle(e).fontFamily.slice(0, 60) : null;
    const card = (title) => [...document.querySelectorAll('[data-slot="card-title"]')].find((e) => e.textContent.trim() === title).closest('[data-slot="card"]');
    const m = card("Matter Profitability"), c = card("Client Profitability"), u = card("Team Utilization");
    const cs = getComputedStyle(document.documentElement), bs = getComputedStyle(document.body);
    await document.fonts.ready;
    return { html: f(document.documentElement), body: f(document.body), htmlFontSansVar: cs.getPropertyValue("--font-sans"), htmlFontPlexVar: cs.getPropertyValue("--font-plex"), bodyFontPlexVar: bs.getPropertyValue("--font-plex").slice(0, 40),
      h1: f(document.querySelector("#main-content h1")), cardTitle: f(m.querySelector('[data-slot="card-title"]')),
      matterNameCell: f(m.querySelector("tbody td")), matterHoursCell: f(m.querySelector("tbody td:nth-child(4)")), matterRevenueSpan: f(m.querySelector("tbody td:nth-child(5) span")), matterHeaderBtn: f(m.querySelector("thead button")),
      clientNameCell: f(c.querySelector("tbody td:nth-child(2)")), clientRevenueCell: f([...c.querySelectorAll("tbody tr:first-child td")].find((td) => /^R/.test(td.textContent.trim()))),
      utilNameCell: f(u.querySelector("tbody td:nth-child(2)")),
      plexLoaded: [...document.fonts].filter((x) => /plex/i.test(x.family)).map((x) => x.family + " " + x.weight + " " + x.status),
      elementsUsingPlex: [...document.querySelectorAll("#main-content *")].filter((e) => /plex/i.test(getComputedStyle(e).fontFamily)).length,
      elementsInMain: document.querySelectorAll("#main-content *").length };
  });
  fs.writeFileSync(path.join(OUT, "font-probe-raw.json"), JSON.stringify(out)); console.log(JSON.stringify(out, null, 1)); await browser.close();
})();
