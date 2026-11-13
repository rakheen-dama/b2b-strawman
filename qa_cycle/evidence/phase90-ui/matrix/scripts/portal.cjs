// 644B task 644.4: portal /home and /trust at 1024 light on the KC dev stack (portal :3002 → backend :8080).
// Browser-driven magic-link login as the Moroka Family Trust contact (populated trust: DEPOSIT R 50 000 +
// TRANSFER_IN R 100). The login form is filled in the browser; the link is taken from the page's
// dev-mode panel when the backend returns it, else from Mailpit (:8025, the one allowed REST use).
// Writes: one magic-link token + one Mailpit email (ruling R6). Portal is never shot dark (token retune only).
const path = require("path");
const { chromium, MATRIX, writeJson } = require("./common-kc.cjs");
const PORTAL = "http://localhost:3002", MAILPIT = "http://localhost:8025", ORG = "mathebula-partners", EMAIL = "moroka.trust@email.co.za";

(async () => {
  const browser = await chromium.launch(); const logs = []; const report = { capturedAt: new Date().toISOString(), email: EMAIL, pages: [] };
  try {
    const ctx = await browser.newContext({ viewport: { width: 1024, height: 768 }, colorScheme: "light", reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" });
    const page = await ctx.newPage();
    page.on("console", (m) => { if (["error", "warning"].includes(m.type()) || /hydrat/i.test(m.text())) logs.push({ url: page.url().replace(PORTAL, ""), type: m.type(), text: m.text().slice(0, 300) }); });
    page.on("pageerror", (e) => logs.push({ url: page.url().replace(PORTAL, ""), type: "pageerror", text: String(e).slice(0, 300) }));
    const sentAfter = Date.now();
    await page.goto(`${PORTAL}/login?orgId=${ORG}`, { waitUntil: "load", timeout: 120000 });
    await page.locator("#email").waitFor({ timeout: 60000 });
    await page.locator("#email").fill(EMAIL);
    await page.getByRole("button", { name: "Send Magic Link" }).click();
    await page.waitForTimeout(3000);
    await page.screenshot({ path: path.join(MATRIX, "portal-login-link-sent-1024-light.png") });
    let link = await page.locator('a[href*="token="]').first().getAttribute("href", { timeout: 5000 }).catch(() => null);
    report.linkSource = link ? "dev-mode panel on /login" : null;
    for (let i = 0; !link && i < 20; i++) {
      const list = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent("to:" + EMAIL)}&limit=5`)).json();
      const msg = (list.messages || []).find((m) => new Date(m.Created).getTime() >= sentAfter - 5000);
      if (msg) {
        const full = await (await fetch(`${MAILPIT}/api/v1/message/${msg.ID}`)).json();
        const m = (full.HTML || full.Text || "").match(/https?:\/\/[^"'\s<>]*token=[^"'\s<>]+/);
        if (m) { link = m[0].replace(/&amp;/g, "&"); report.linkSource = `Mailpit message ${msg.ID}`; }
      }
      if (!link) await new Promise((r) => setTimeout(r, 1500));
    }
    if (!link) throw new Error("no magic link found on page or in Mailpit");
    link = new URL(link, PORTAL).href; // the dev-mode panel renders a relative /auth/exchange?token=… href (first run failed on it)
    report.link = link.replace(/token=[^&]+/, "token=<redacted>");
    await page.goto(link, { waitUntil: "load", timeout: 120000 });
    // Second run found: under `next dev` (React StrictMode double effect) the first exchange call stores
    // the session but its router.push is cancelled, and the second call 401s on the consumed token, so the
    // page shows "Login Failed" while localStorage already holds portal_jwt. Record that state, then continue.
    await page.waitForTimeout(6000);
    await page.screenshot({ path: path.join(MATRIX, "portal-exchange-after-link-1024-light.png") });
    report.exchange = await page.evaluate(() => ({ url: location.pathname, heading: document.querySelector("h1, [data-slot=card-title]")?.textContent?.trim() ?? null, text: document.body.innerText.replace(/\s+/g, " ").slice(0, 200), hasJwt: !!localStorage.getItem("portal_jwt") }));
    for (const p of ["home", "trust"]) {
      await page.goto(`${PORTAL}/${p}`, { waitUntil: "load", timeout: 120000 });
      await page.waitForSelector("h1", { timeout: 60000 }).catch(() => {});
      await page.mouse.move(1, 1);
      await page.waitForTimeout(4000);
      await page.screenshot({ path: path.join(MATRIX, `portal-${p}-1024-light.png`), fullPage: true });
      report.pages.push({ page: p, url: page.url().replace(PORTAL, ""), ...(await page.evaluate(() => ({
        h1: document.querySelector("h1")?.textContent?.trim(), dark: document.documentElement.classList.contains("dark"),
        bodyBg: getComputedStyle(document.body).backgroundColor, text: document.body.innerText.replace(/\s+/g, " ").slice(0, 600),
        docOverflow: Math.max(0, document.documentElement.scrollWidth - document.documentElement.clientWidth),
      }))) });
    }
    await ctx.close();
  } catch (e) { report.error = String(e); }
  finally {
    report.console = logs;
    const redacted = JSON.parse(JSON.stringify(report).replace(/token=[A-Za-z0-9_\-]+/g, "token=<redacted>"));
    writeJson(MATRIX, "portal-report.json", redacted); await browser.close();
  }
  console.log(JSON.stringify(report, null, 1));
})().catch((e) => { console.error("OBS FAILED", e); process.exit(1); });
