import { describe, it, expect } from "vitest";
import ts from "typescript";
import { readSourceTree } from "./helpers/source-scan";
import allowlist from "./no-terminology-remnants.allowlist.json";

// Phase 87 S5 (611D.3; ADR-338; arch §12.3.6 "Enforcement" (2), §12.10.5): the runtime terminology layer is
// deleted — legal words are written into the copy. Two assertions over app/, components/, lib/, hooks/ (tests,
// e2e and node_modules are skipped by readSourceTree — NOT node:fs globSync, which needs Node 22; CI runs Node 20):
//  1. BANNED — no source file names the deleted mechanism. Raw-text, line-level (the tokens are identifiers,
//     which findLiterals never visits; comments count too, so a grep stays clean).
//  2. Generic nouns — no JSX text node (ts.isJsxText) under app/(app)/org/** says Project(s) / Customer(s). A
//     heuristic: attribute strings, {"…"} expressions and comments are not JSX text. Legitimate uses go in
//     no-terminology-remnants.allowlist.json as { "file", "text", "reason" } — matched on file +
//     whitespace-collapsed text (not line numbers, which drift); every entry must stay live.
// The ESLint no-restricted-imports rule (eslint.config.mjs) bans the deleted module paths; this scan catches
// the identifiers. The JSON message catalogues are guarded by lzkc-031-pr2-empty-states.test.tsx.
// Portal twin: portal/__tests__/no-terminology-remnants.test.ts (611E).

const SCAN_ROOTS = ["app", "components", "lib", "hooks"];
const BANNED =
  /useTerminology|TerminologyProvider|TerminologyText|TerminologyHeading|terminology-map|withIndefiniteArticle|auditTabLabel|PORTAL_TERMINOLOGY/;
const GENERIC_NOUN = /\b(Projects?|Customers?)\b/;
const JSX_SCOPE = "app/(app)/org/";

interface AllowEntry {
  file: string;
  text: string;
  reason: string;
}
const ALLOW: AllowEntry[] = allowlist;
const key = (file: string, text: string) => `${file}::${text}`;

function bannedLines(rel: string, text: string): string[] {
  return text
    .split("\n")
    .flatMap((line, i) => (BANNED.test(line) ? [`${rel}:${i + 1}: ${line.trim()}`] : []));
}

function jsxTextHits(rel: string, source: string) {
  const kind = rel.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
  const sf = ts.createSourceFile(rel, source, ts.ScriptTarget.Latest, true, kind);
  const hits: { file: string; line: number; text: string }[] = [];
  const visit = (node: ts.Node) => {
    if (ts.isJsxText(node) && GENERIC_NOUN.test(node.text)) {
      const { line } = sf.getLineAndCharacterOfPosition(node.getStart(sf));
      hits.push({ file: rel, line: line + 1, text: node.text.trim().replace(/\s+/g, " ") });
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return hits;
}

const sourceFiles = readSourceTree(SCAN_ROOTS);
const jsxHits = sourceFiles
  .filter((f) => f.rel.startsWith(JSX_SCOPE))
  .flatMap((f) => jsxTextHits(f.rel, f.text));

describe("no runtime terminology layer remains (Phase 87 611D, ADR-338)", () => {
  it("detectors: BANNED catches the mechanism; the noun scan sees JSX text only", () => {
    expect(
      bannedLines("s.tsx", 'import { useTerminology } from "@/lib/terminology";')
    ).toHaveLength(1);
    expect(bannedLines("s.tsx", "<TerminologyProvider verticalProfile={p}>")).toHaveLength(1);
    expect(bannedLines("s.tsx", 'const { t } = createMessages("help");')).toEqual([]);
    const sample = [
      "const a = <h1>Projects</h1>;",
      "const b = <Label>Customer</Label>;",
      'const c = <Input placeholder="Search customers" />;',
      'const d = <p>{"Customers"}</p>;',
      "// const e = <h1>Projects</h1>;",
      "const f = <p>Matters and clients</p>;",
      "const g = <p>Projectsummary</p>;",
    ].join("\n");
    expect(jsxTextHits("sample.tsx", sample).map((h) => h.line)).toEqual([1, 2]);
  });

  it("scans the real source tree", () => {
    expect(sourceFiles.length).toBeGreaterThan(500);
    expect(sourceFiles.some((f) => f.rel === "app/(app)/org/[slug]/layout.tsx")).toBe(true);
  });

  it("zero references to the deleted mechanism", () => {
    expect(sourceFiles.flatMap((f) => bannedLines(f.rel, f.text))).toEqual([]);
  });

  it("zero generic Project/Customer nouns in org JSX text outside the allow-list", () => {
    const allowed = new Set(ALLOW.map((e) => key(e.file, e.text)));
    expect(
      jsxHits
        .filter((h) => !allowed.has(key(h.file, h.text)))
        .map((h) => `${h.file}:${h.line}: ${h.text}`)
    ).toEqual([]);
  });

  it("allow-list entries are live and justified", () => {
    const live = new Set(jsxHits.map((h) => key(h.file, h.text)));
    expect(ALLOW.filter((e) => !live.has(key(e.file, e.text)) || !e.reason.trim())).toEqual([]);
  });
});
