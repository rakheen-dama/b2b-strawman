import { describe, it, expect } from "vitest";
import { readSourceTree } from "./helpers/source-scan";
import allowlist from "./no-adhoc-currency.allowlist.json";

// Phase 88 626F (arch §13.8.5 "formatCurrency everywhere"): money has one formatter on the frontend,
// `formatCurrency` from `@/lib/format` (@b2mash/shared/format behind the @formatjs polyfill). A
// `toFixed(2)` on the same line as a currency marker is an ad-hoc money formatter —
// "R ${x.toFixed(2)}", "R {(c / 100).toFixed(2)}", "${curr} ${amount.toFixed(2)}" — and renders en-US
// decimals ("R 1250.00") next to the app's en-ZA "R 1 250,00". Raw-text, line-level (toFixed is a
// call, not a literal, so findLiterals cannot see it); comment lines are skipped. Percentages
// ("…toFixed(2)}%") carry no currency marker and are not money.
// Scan: app/, components/, lib/ (tests, e2e and node_modules are skipped by readSourceTree;
// packages/ and portal/ are out of scope). Allow-list entries { file, text, reason } match
// file + trimmed line text (not line numbers, which drift); every entry must stay live.

const SCAN_ROOTS = ["app", "components", "lib"];
// Markers: `R ${…}` / `R${…}` (template), `R {…}` / `R{…}` (JSX text), "ZAR", any `${…currency…}` /
// `${…Currency…}` / `${curr}` interpolation (e.g. `${expense.currency}`, `${currencyCode}`), and a
// "R " string concatenation.
const CURRENCY_MARKER =
  /(\bR\s?\$\{|\bR\s?\{|\bZAR\b|\$\{curr\}|\$\{[\w.]*[cC]urrency[\w.]*\}|["'`]R\s?["'`]\s*\+)/;
const TO_FIXED_2 = /\.toFixed\(2\)/;

interface AllowEntry {
  file: string;
  text: string;
  reason: string;
}
const ALLOW: AllowEntry[] = allowlist;
const key = (file: string, text: string) => `${file}::${text}`;

function adHocMoneyLines(
  rel: string,
  source: string
): { file: string; line: number; text: string }[] {
  return source.split("\n").flatMap((line, i) => {
    const t = line.trim();
    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return [];
    return TO_FIXED_2.test(line) && CURRENCY_MARKER.test(line)
      ? [{ file: rel, line: i + 1, text: t }]
      : [];
  });
}

const sourceFiles = readSourceTree(SCAN_ROOTS);
const hits = sourceFiles.flatMap((f) => adHocMoneyLines(f.rel, f.text));

describe("no ad-hoc money formatting (Phase 88 626F, arch §13.8.5)", () => {
  it("detector: catches the census shapes, ignores percentages, sizes and comments", () => {
    const sample = [
      "  return `R ${amount.toFixed(2)}`;",
      "  return `R ${(cents / 100).toFixed(2)}`;",
      "  <span>Cost: R {(state.result.costCents / 100).toFixed(2)}</span>",
      "    return `${curr} ${amount.toFixed(2)}`;",
      '  const s = "R " + total.toFixed(2);',
      "  const z = `${amount.toFixed(2)} ZAR`;",
      "  const e = `${expense.currency} ${expense.amount.toFixed(2)}`;",
      "  const c = `${currencyCode} ${total.toFixed(2)}`;",
      "  const i = `${amount.toFixed(2)} ${invoice.currency}`;",
      "  <span>R{(row.amountCents / 100).toFixed(2)}</span>",
      "  <td>R {line.total.toFixed(2)}</td>",
      "  {(rate * 100).toFixed(2)}%",
      "  {`${(margin * 100).toFixed(2)}%`}",
      "  <span>{(utilisation * 100).toFixed(2)}%</span>",
      "  `${(bytes / 1024).toFixed(1)} KB`",
      "  // return `R ${amount.toFixed(2)}`;",
      "   * `R ${amount.toFixed(2)}` was the old formatter",
      "  {formatCurrency(item.amount)}",
      "  const r = Number(value).toFixed(2);",
    ].join("\n");
    expect(adHocMoneyLines("sample.tsx", sample).map((h) => h.line)).toEqual([
      1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
    ]);
  });

  it("scans the real source tree", () => {
    expect(sourceFiles.length).toBeGreaterThan(500);
    expect(sourceFiles.some((f) => f.rel === "components/legal/tariff-line-dialog.tsx")).toBe(true);
  });

  it("zero ad-hoc money formatters outside the allow-list", () => {
    const allowed = new Set(ALLOW.map((e) => key(e.file, e.text)));
    expect(
      hits
        .filter((h) => !allowed.has(key(h.file, h.text)))
        .map((h) => `${h.file}:${h.line}: ${h.text}`)
    ).toEqual([]);
  });

  it("allow-list entries are live and justified", () => {
    const live = new Set(hits.map((h) => key(h.file, h.text)));
    expect(ALLOW.filter((e) => !live.has(key(e.file, e.text)) || !e.reason.trim())).toEqual([]);
  });
});
