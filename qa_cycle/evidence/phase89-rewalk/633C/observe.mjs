// Observation walker for 633C (e2e portal prod build :3002 -> :8081). Run with portal's playwright.
import { createRequire } from "node:module";
const require = createRequire(process.argv[2] + "/package.json");
const { chromium } = require("@playwright/test");
const OUT = process.argv[3];
const B = "http://localhost:8081", P = "http://localhost:3002", ORG = "e2e-test-org";
const EMAIL = "contact@acme.example.com";
const RUN = Date.now().toString(36).slice(-5);
const tok = await (await fetch("http://localhost:8090/token", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId: "user_e2e_alice", orgSlug: ORG }) })).json();
async function firm(path, method = "GET", body) {
  const r = await fetch(B + path, { method, headers: { "Content-Type": "application/json", Authorization: `Bearer ${tok.access_token}` }, body: body === undefined ? undefined : JSON.stringify(body) });
  const t = await r.text(); if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${t}`); return t ? JSON.parse(t) : null;
}
const log = [];
const note = (s) => { log.push(s); console.log(s); };
const clients = await firm("/api/clients?size=200"); const acme = (clients.content ?? clients).find((c) => c.name === "Acme Corp");
const matters = await firm("/api/matters"); const wr = (matters.content ?? matters).find((m) => m.name === "Website Redesign");
const contacts = await firm(`/api/clients/${acme.id}/portal-contacts`); const contact = contacts.find((c) => c.email === EMAIL);
// Client-level request (no matter) -> G88-F30
const ir = await firm("/api/information-requests", "POST", { clientId: acme.id, portalContactId: contact.id, items: [{ name: `Proof of address ${RUN}`, description: "Upload a utility bill", responseType: "FILE_UPLOAD", required: true, fileTypeHints: "PDF", sortOrder: 0 }] });
const sent = await firm(`/api/information-requests/${ir.id}/send`, "POST");
note(`firm: client-level request ${sent.requestNumber} status ${sent.status} matterId=${sent.matterId ?? null}`);
// Tasks OPEN / IN_PROGRESS / DONE on Website Redesign -> G88-F20
const tA = await firm(`/api/matters/${wr.id}/tasks`, "POST", { title: `Obs open ${RUN}`, priority: "MEDIUM" });
const tB = await firm(`/api/matters/${wr.id}/tasks`, "POST", { title: `Obs in progress ${RUN}`, priority: "MEDIUM" });
const tC = await firm(`/api/matters/${wr.id}/tasks`, "POST", { title: `Obs done ${RUN}`, priority: "MEDIUM" });
await firm(`/api/tasks/${tB.id}/claim`, "POST"); await firm(`/api/tasks/${tC.id}/claim`, "POST");
const done = await firm(`/api/tasks/${tC.id}/complete`, "PATCH");
note(`firm: tasks ${tA.status} / ${(await firm(`/api/tasks/${tB.id}`)).status} / ${done.status}`);
// Court date + prescription -> §5b row 19 (removed again at the end)
const today = new Date(Date.now() + 2 * 3600e3).toISOString().slice(0, 10);
const add = (d, n) => { const [y, m, dd] = d.split("-").map(Number); return new Date(Date.UTC(y, m - 1, dd + n)).toISOString().slice(0, 10); };
const due = add(today, 21); const [yy, mm, ddd] = due.split("-").map(Number);
// Everything that creates firm state or opens a browser sits inside the try, so the finally
// always runs; each cleanup step is guarded and independent (one failing never skips the next).
let cd = null, pt = null, sess = null, browser = null;
const pget = async (p) => (await fetch(B + p, { headers: { Authorization: `Bearer ${sess.token}` } })).json();
try {
cd = await firm("/api/court-dates", "POST", { matterId: wr.id, dateType: "HEARING", scheduledDate: add(today, 14), scheduledTime: "10:00:00", courtName: `Johannesburg High Court OBS-${RUN}`, courtReference: `JHB-OBS-${RUN}`, judgeMagistrate: `Judge FIRM-ONLY-${RUN}`, description: `FIRM-ONLY-NOTES ${RUN}`, reminderDays: 7 });
pt = await firm("/api/prescription-trackers", "POST", { matterId: wr.id, causeOfActionDate: new Date(Date.UTC(yy - 3, mm - 1, ddd)).toISOString().slice(0, 10), prescriptionType: "GENERAL_3Y", notes: `FIRM-ONLY-NOTES ${RUN}` });
// Portal session
const link = await (await fetch(B + "/portal/auth/request-link", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: EMAIL, orgId: ORG }) })).json();
const token = link.magicLink.split("token=").pop().split("&")[0];
sess = await (await fetch(B + "/portal/auth/exchange", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ token, orgId: ORG }) })).json();
const trust = await pget("/portal/trust/summary");
note(`portal: trust summary ${JSON.stringify(trust.matters.map((m) => ({ matterId: m.matterId, matterName: m.matterName })))}`);
browser = await chromium.launch();
const RAW = /\b(OPEN|IN_PROGRESS|IN PROGRESS|DONE|SENT|SUBMITTED|DEPOSIT|PAYMENT|REFUND|FEE_TRANSFER)\b|Matter [0-9a-f]{8}\b/;
for (const [w, h] of [[1024, 768], [1440, 900]]) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h } });
  const page = await ctx.newPage();
  await page.goto(P + "/login");
  await page.evaluate((s) => { localStorage.setItem("portal_jwt", s.token); localStorage.setItem("portal_customer", JSON.stringify({ id: s.clientId, name: s.clientName, email: s.email, orgId: s.orgId })); localStorage.setItem("portal_last_org_id", s.orgId); }, { ...sess, email: EMAIL, orgId: ORG });
  const shot = async (name, path, waitText, extra) => {
    await page.goto(P + path); await page.getByText(waitText, { exact: false }).filter({ visible: true }).first().waitFor({ timeout: 15000 });
    if (extra) await extra();
    await page.waitForTimeout(300);
    const main = await page.locator("main").innerText();
    const ov = await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth);
    const raw = main.match(RAW);
    await page.screenshot({ path: `${OUT}/portal-${w}-${name}.png`, fullPage: true });
    note(`\n### ${w} ${name} (${path}) — raw enum/id: ${raw ? "FOUND " + raw[0] : "none"}; no horizontal overflow: ${ov}\n\`\`\`\n${main.trim().slice(0, 900)}\n\`\`\``);
  };
  await shot("requests-list", "/requests", sent.requestNumber);
  await shot("request-client-level", `/requests/${sent.id}`, `Request ${sent.requestNumber}`);
  await shot("matter-tasks", `/matters/${wr.id}`, `Obs done ${RUN}`);
  await shot("trust", `/trust/${wr.id}`, "Website Redesign", async () => { await page.getByRole("table", { name: "Trust transactions" }).getByRole("cell", { name: "Deposit", exact: true }).first().waitFor(); });
  await shot("deadlines", "/deadlines", `Johannesburg High Court OBS-${RUN}`, async () => {
    await page.getByTestId(`deadline-row-COURT_DATE-${cd.id}`).click();
    await page.getByRole("dialog", { name: `Hearing — Johannesburg High Court OBS-${RUN}` }).waitFor();
    const panel = await page.getByRole("dialog").innerText();
    note(`${w} hearing panel: ${JSON.stringify(panel)}; judge shown: ${panel.includes("FIRM-ONLY")}`);
  });
  await page.goto(P + "/deadlines"); await page.getByTestId(`deadline-row-PRESCRIPTION_TRACKER-${pt.id}`).click();
  const ptPanel = await page.getByRole("dialog", { name: "Prescription deadline" }).innerText();
  note(`${w} prescription panel: ${JSON.stringify(ptPanel)}; notes shown: ${ptPanel.includes("FIRM-ONLY")}`);
  await ctx.close();
}
} finally {
  const step = async (name, fn) => { try { await fn(); } catch (e) { note(`cleanup ${name} FAILED: ${e?.message ?? e}`); } };
  if (cd) await step("cancel court date", () => firm(`/api/court-dates/${cd.id}/cancel`, "POST", { reason: `633C obs ${RUN}` }));
  if (pt) await step("interrupt prescription", () => firm(`/api/prescription-trackers/${pt.id}/interrupt`, "POST", { interruptionDate: today, interruptionReason: `633C obs ${RUN}` }));
  if (sess && (cd || pt)) await step("check portal rows", async () => {
    const left = (await pget("/portal/deadlines")).filter((d) => d.id === cd?.id || d.id === pt?.id);
    note(`after cancel/interrupt: portal rows for the two = ${left.length}`);
  });
  if (browser) await step("browser close", () => browser.close());
  await step("write log", async () => (await import("node:fs")).writeFileSync(`${OUT}/observe-log.md`, log.join("\n")));
}
