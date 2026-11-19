// 650C OBS — shared helpers (browser, read-only SQL, GET-only API, report runner, raw-code scans).
// Loaded by obs.cjs. Headless Chromium (Playwright library) on the e2e mock-auth stack (:3001 / :8081).
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const W = path.resolve(__dirname, "../../../../..");
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.join(W, "frontend")] }));

const FIRM = "http://localhost:3001", BACKEND = "http://localhost:8081", BASE = `${FIRM}/org/e2e-test-org`;
const OUT = path.join(__dirname, process.env.OBS_OUT || "obs"); // run 2 (re-probe): OBS_OUT=obs-r2
fs.mkdirSync(OUT, { recursive: true });

// ── raw-code scans (D-6 / AC 10) ──
// Enum / type constants that must never be printed on a report screen, CSV-visible label or PDF.
const CODE_WORDS = [
  "FUND_INTEREST", "FUND_INV_SHARE", "BANK_CHARGE", "CHARGE_REFUND", "FUND_SWEEP", "UNIDENT_[A-Z_]+", "UNIDENTIFIED_DEPOSITS?", "REVERSAL",
  "DEPOSIT", "PAYMENT", "TRANSFER_IN", "TRANSFER_OUT", "FEE_TRANSFER", "REFUND", "INTEREST_CREDIT", "INTEREST_LPFF", "INVESTMENT_[A-Z_]+",
  "CLIENT", "NON_CLIENT", "INTER_ACCOUNT", "CLIENT_INVESTMENT", "GENERAL", "INVESTMENT", "SECTION_86[A-Z_0-9]*", "S86_[234]",
  "RECORDED", "APPROVED", "AWAITING_APPROVAL", "REJECTED", "REVERSED", "HELD", "ALLOCATED", "PAID_TO_FUND", "COMPLETED", "DRAFT",
  "ACTIVE", "MATURED", "WITHDRAWN", "FIRM_DISCRETION", "CLIENT_INSTRUCTION", "GROSS", "NET", "FUND_INTEREST_AND_CHARGES",
  "LOCKED", "LIVE", "AVAILABLE_INDIVIDUALLY", "BACKDATED_AFTER_LOCK", "BANK_DIFFERENCE", "BOOKS_OUT_OF_BALANCE", "INTER_ACCOUNT_NOT_ZERO",
  "UNIDENTIFIED_ITEMS_MISMATCH", "INVESTMENT_BALANCE_MISMATCH", "UNRESOLVED_STATEMENT_LINES", "FUND_LEDGER_IN_DEBIT", "CLIENT_LEDGER_IN_DEBIT",
  "UNIDENTIFIED_OVER_ONE_YEAR", "CHARGES_TO_REFUND", "BLOCKING", "WARNING", "MATCHED", "UNMATCHED", "EXCLUDED",
];
const CODES = new RegExp(`\\b(${CODE_WORDS.join("|")})\\b|\\btrue\\b|\\bfalse\\b|\\bnull\\b|\\bundefined\\b|\\bNaN\\b|\\[object Object\\]`, "g");
const ISO = /\b\d{4}-\d{2}-\d{2}(T[\d:.]+Z?)?\b/g;
// "Rule 54.14.16", "Rule 54.28", "Section 86(4)", "s86(4)" are the statute's numbering, not decimals
const stripStatute = (s) => String(s).replace(/Rules? \d+(\.\d+)*/g, "Rule §").replace(/[Ss](ection )?\s?\d+\(\d\)/g, "s§");
const RAWDEC = /(?<![\d,])\b\d+\.\d{1,4}\b/g;
const hits = (s, re) => [...new Set(stripStatute(s).match(re) || [])];
const scanAll = (s) => ({ codes: hits(s, CODES), iso: hits(s, ISO), rawDecimals: hits(s, RAWDEC) });

// ── read-only SQL ──
const psql = (args) => execFileSync("docker", ["exec", "e2e-postgres", "psql", "-U", "postgres", "-d", "app", ...args], { encoding: "utf8" });
const SCHEMA = psql(["-XAt", "-c", "select schema_name from information_schema.schemata where schema_name like 'tenant_%' order by 1 limit 1"]).trim();
const ro = (sql) => { const s = sql.replace(/\bT\./g, `${SCHEMA}.`); if (!/^\s*(select|with)\b/i.test(s)) throw new Error("read-only SQL only"); return s; };
function sqlLog(tag, startedAt) {
  const f = path.join(OUT, `sql-${tag}.txt`);
  fs.writeFileSync(f, `-- 650C OBS phase ${tag} read-only SQL (schema ${SCHEMA}) ${startedAt}\n`);
  return (sql, label) => { const s = ro(sql); const out = psql(["-X", "-v", "ON_ERROR_STOP=1", "-c", s]); fs.appendFileSync(f, `\n== [${label}]\n${s}\n${out}`); return out; };
}
const qa = (sql) => psql(["-XAt", "-F", "|", "-c", ro(sql)]).trim();
const eq = (a, b) => a != null && b != null && Math.abs(Number(a) - Number(b)) < 0.005;
const flat = (s) => String(s ?? "").replace(/\s+/g, " ").trim();
const rand = (v) => (Number(v) < 0 ? "-" : "") + "R " + Math.abs(Number(v)).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, " ").replace(".", ",");
/** "R 1 234,56" / "-R 12,00" / "R -12,00" (any NBSP / narrow spaces) → number */
const parseRand = (s) => { const t = String(s).replace(/[\s  ]/g, ""); const m = t.match(/^(-?)R(-?)([\d]+),(\d{2})$/); return m ? (m[1] || m[2] ? -1 : 1) * Number(`${m[3]}.${m[4]}`) : null; };

// ── browser ──
function makeBrowser(R) {
  let browser;
  const open = async () => (browser = await chromium.launch({ headless: true }));
  const close = async () => browser && browser.close();
  async function login(user = "user_e2e_alice", width = 1440) {
    const ctx = await browser.newContext({ viewport: { width, height: 1000 }, timezoneId: "Africa/Johannesburg", acceptDownloads: true });
    const p = await ctx.newPage();
    p.on("console", (m) => { if (m.type() === "error") R.consoleErrors.push({ user, url: p.url(), text: m.text().slice(0, 300) }); });
    p.on("pageerror", (e) => R.pageErrors.push({ user, url: p.url(), text: String(e).slice(0, 300) }));
    await p.goto(`${FIRM}/mock-login`, { waitUntil: "load" });
    await p.locator("#user-select").selectOption(user);
    await p.getByRole("button", { name: "Sign In", exact: true }).click();
    await p.waitForURL(/\/org\//, { timeout: 60_000 });
    return { ctx, p };
  }
  return { open, close, login };
}
async function token(ctx) { return (await ctx.cookies()).find((c) => c.name === "mock-auth-token")?.value; }
/** Firm API — GET only. */
async function api(ctx, url, save, raw = false) {
  const res = await ctx.request.get(`${BACKEND}${url}`, { headers: { Authorization: `Bearer ${await token(ctx)}` } });
  const r = { method: "GET", url, status: res.status() };
  if (raw) r.text = await res.text(); else r.body = await res.json().catch(() => null);
  if (save) fs.writeFileSync(path.join(OUT, `api-${save}.json`), JSON.stringify(raw ? { ...r, text: undefined, bytes: r.text.length } : r, null, 2));
  return r;
}
const settle = async (p, ms = 1000) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(ms); };
async function snap(p, tag, name, el) {
  await p.waitForTimeout(300);
  const f = path.join(OUT, `${tag}-${name}.png`);
  if (el) await el.screenshot({ path: f }); else await p.screenshot({ path: f, fullPage: true });
  return path.basename(f);
}
/** PDF text through macOS PDFKit (pdftotext is not installed). */
function pdfText(file) {
  try { return execFileSync("osascript", ["-l", "JavaScript", path.join(__dirname, "pdftext.js"), file], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }); }
  catch (e) { return `ERR: ${String(e).slice(0, 200)}`; }
}

module.exports = { fs, path, FIRM, BACKEND, BASE, OUT, SCHEMA, CODES, ISO, RAWDEC, scanAll, hits, stripStatute, sqlLog, qa, eq, flat, rand, parseRand, makeBrowser, api, settle, snap, pdfText };
