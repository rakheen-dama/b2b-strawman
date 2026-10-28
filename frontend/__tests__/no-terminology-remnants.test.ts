import { describe, it, expect } from "vitest";
import ts from "typescript";
import { isModuleSpecifier, readSourceTree } from "./helpers/source-scan";
import allowList from "./terminology-allow-list.json";

// Phase 87 S5 (611D.3; ADR-338) + Phase 88 628A (ADR-357): the runtime terminology layer is deleted
// and the legal word is written into the copy. Two assertions over app/, components/, lib/, hooks/
// (tests, __tests__/, e2e and node_modules are skipped by readSourceTree):
//  1. BANNED — no source file names the deleted mechanism (raw text, line-level; comments count).
//  2. Generic nouns — no user-visible literal says project(s) / customer(s) / invoice(s), any case.
//     "User-visible" = the ADR-357 positions: JSX text; string/template literals that are JSX
//     children; label-ish JSX props (label, title, description, placeholder, aria-label, alt,
//     emptyMessage, *Label, *Title, *Message, *Description, *Text…); label-ish object keys and
//     UPPER_SNAKE enum→label maps; any key whose value is a bare capitalised noun
//     ({ customer: "Customer" }); toast.* / setError / set*Error / setMessage / confirm / Error /
//     zod messages; `return "…"` prose or `return "Customer"`; `const *Label|*Message|*Error = "…"`. Tokens that are not
//     words are stripped first: {customer.name} / {{project.name}} placeholders, dotted paths,
//     route paths (/invoices, /api/invoices). Comments, identifiers, import specifiers, test-ids,
//     ids, hrefs, enum values and comparisons are never visited as hits.
// Legitimate uses go in terminology-allow-list.json as { file, text, reason } — matched on file +
// whitespace-collapsed text (not line numbers); every entry must stay live and its reason must be
// one of the ADR-357 reasons. Portal twin: portal/__tests__/no-terminology-remnants.test.ts (628B).

const SCAN_ROOTS = ["app", "components", "lib", "hooks"];
const BANNED =
  /useTerminology|TerminologyProvider|TerminologyText|TerminologyHeading|terminology-map|withIndefiniteArticle|auditTabLabel|PORTAL_TERMINOLOGY/;
const NOUN = /\b(projects?|customers?|invoices?)\b/i;

// ADR-357 Decision reason set + "third-party object name" (ADR-357 Consequences, R8 note 1).
const REASONS = [
  "API/route path",
  "INV- numbering",
  "statutory tax invoice",
  "ADR-342 literal",
  "MCP tool description",
  "marketing copy",
  "archived code",
  "third-party object name",
];

// `text` is display copy (also Tiptap text nodes); `name` is NOT here — it is the form-field key
// (name="customerId", RHF `name: "…"`), a wire identifier, not copy.
const LABEL_NAME =
  /^(text|label|title|description|placeholder|aria-label|alt|emptyMessage|error|message|tooltip|heading|subtitle|category|[A-Za-z]*(Label|Title|Message|Description|Placeholder|Heading|Text|Error))$/;
const MESSAGE_VAR = /(label|message|msg|error|title|description|heading|text)$/i;
const MESSAGE_CALL =
  /^(setError|set[A-Za-z]*Error|setMessage|toast(\.[a-z]+)?|confirm|alert|Error)$|\.(min|max|length|nonempty|email|url|uuid|regex|refine|superRefine)$/;
const UPPER_KEY = /^[A-Z][A-Z0-9_]*$/;
// A literal that is exactly one banned noun, capitalised ("Customer", "Projects"): display copy in
// any key's value ({ customer: "Customer" }) or as a bare `return "Customer"`. The lower-case
// single word stays a stored literal ({ PROJECT: "project" }, `return "project"`; ADR-342).
const BARE_NOUN = /^(Projects?|Customers?|Invoices?)$/;
const PASS_THROUGH = new Set([
  ts.SyntaxKind.BarBarToken,
  ts.SyntaxKind.QuestionQuestionToken,
  ts.SyntaxKind.PlusToken,
]);

interface AllowEntry {
  file: string;
  text: string;
  reason: string;
}
const ALLOW: AllowEntry[] = allowList;
const key = (file: string, text: string) => `${file}::${text}`;

/** The literal with its non-word tokens removed ({customer.name}, a.b.c paths, /route/paths). */
function prose(text: string): string {
  return text
    .replace(/\{\{?[^}]*\}?\}/g, " ")
    .replace(/\b[A-Za-z_]+(?:\.[A-Za-z_]+)+\b/g, " ")
    .replace(/(^|\s)\/\S*/g, " ");
}

function nameText(node: ts.Node, sf: ts.SourceFile): string {
  if (ts.isIdentifier(node) || ts.isStringLiteral(node)) return node.text;
  return node.getText(sf);
}

/** ADR-357 position of a literal, or null when the literal is not user-visible copy. */
function position(node: ts.Node, text: string, sf: ts.SourceFile): string | null {
  if (ts.isJsxText(node)) return "jsx-text";
  let child: ts.Node = node;
  let parent = node.parent;
  while (
    parent &&
    (ts.isConditionalExpression(parent) ||
      ts.isParenthesizedExpression(parent) ||
      ts.isTemplateExpression(parent) ||
      ts.isTemplateSpan(parent) ||
      ts.isAsExpression(parent) ||
      (ts.isBinaryExpression(parent) && PASS_THROUGH.has(parent.operatorToken.kind)))
  ) {
    if (ts.isConditionalExpression(parent) && parent.condition === child) return null;
    child = parent;
    parent = parent.parent;
  }
  if (!parent) return null;
  if (ts.isJsxExpression(parent)) {
    const attr = parent.parent;
    if (attr && ts.isJsxAttribute(attr)) {
      const name = nameText(attr.name, sf);
      return LABEL_NAME.test(name) ? `prop ${name}` : null;
    }
    return "jsx-expression";
  }
  if (ts.isJsxAttribute(parent)) {
    const name = nameText(parent.name, sf);
    return LABEL_NAME.test(name) ? `prop ${name}` : null;
  }
  if (ts.isPropertyAssignment(parent) && parent.initializer === child) {
    const name = nameText(parent.name, sf);
    if (LABEL_NAME.test(name)) return `key ${name}`;
    // enum → label maps ({ PROJECT_CREATION: "Project Creation" }); a single lower-case word is a
    // stored literal ({ PROJECT: "project" }, ADR-342), not copy.
    if (UPPER_KEY.test(name) && /[A-Z]|\s/.test(text)) return `map ${name}`;
    // lower-case-keyed label maps ({ project: "Project" }) — any key, bare capitalised noun.
    if (BARE_NOUN.test(text)) return `map-value ${name}`;
    return null;
  }
  if (ts.isCallExpression(parent) || ts.isNewExpression(parent)) {
    const callee = parent.expression.getText(sf).replace(/\s+/g, "");
    return MESSAGE_CALL.test(callee) ? `call ${callee}` : null;
  }
  if (ts.isReturnStatement(parent)) {
    return /\s/.test(text.trim()) || BARE_NOUN.test(text) ? "return" : null;
  }
  if (ts.isVariableDeclaration(parent)) {
    return MESSAGE_VAR.test(nameText(parent.name, sf)) ? "const" : null;
  }
  return null;
}

function bannedLines(rel: string, text: string): string[] {
  return text
    .split("\n")
    .flatMap((line, i) => (BANNED.test(line) ? [`${rel}:${i + 1}: ${line.trim()}`] : []));
}

function remnantHits(rel: string, source: string) {
  const kind = rel.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
  const sf = ts.createSourceFile(rel, source, ts.ScriptTarget.Latest, true, kind);
  const hits: { file: string; line: number; text: string; where: string }[] = [];
  const visit = (node: ts.Node) => {
    const isText =
      ts.isStringLiteralLike(node) || ts.isTemplateLiteralToken(node) || ts.isJsxText(node);
    if (isText && !isModuleSpecifier(node) && NOUN.test(prose(node.text))) {
      const text = node.text.trim().replace(/\s+/g, " ");
      const where = position(node, text, sf);
      if (where) {
        const { line } = sf.getLineAndCharacterOfPosition(node.getStart(sf));
        hits.push({ file: rel, line: line + 1, text, where });
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return hits;
}

const sourceFiles = readSourceTree(SCAN_ROOTS);
const hits = sourceFiles.flatMap((f) => remnantHits(f.rel, f.text));

describe("no runtime terminology layer remains (Phase 87 611D, ADR-338; Phase 88 628A, ADR-357)", () => {
  it("detectors: BANNED catches the mechanism; the noun scan sees user-visible literals only", () => {
    expect(
      bannedLines("s.tsx", 'import { useTerminology } from "@/lib/terminology";')
    ).toHaveLength(1);
    expect(bannedLines("s.tsx", "<TerminologyProvider verticalProfile={p}>")).toHaveLength(1);
    expect(bannedLines("s.tsx", 'const { t } = createMessages("help");')).toEqual([]);
    const sample = [
      "const a = <h1>Projects</h1>;", // 1 hit: JSX text
      "const b = <Label>Customer</Label>;", // 2 hit
      'const c = <Input placeholder="Search customers" />;', // 3 hit: label-ish prop
      'const d = <p>{"Customers"}</p>;', // 4 hit: JSX expression child
      "// const e = <h1>Projects</h1>;", // 5 comment
      "const f = <p>Matters and clients</p>;", // 6 legal words
      "const g = <p>Projectsummary</p>;", // 7 no word boundary
      'const h = { label: "Invoice Status" };', // 8 hit: label key
      'toast.success("Party unlinked from project");', // 9 hit: toast
      'function i() { return { success: false, error: "Customer name is required." }; }', // 10 hit
      'const j = z.string().uuid("Please select a customer");', // 11 hit: zod message
      'const k = { PROJECT_CREATION: "Project Creation" };', // 12 hit: enum → label map
      'const l = <a href="/invoices">Fee notes</a>;', // 13 route path
      'fetch("/api/invoices");', // 14 API path
      'const m = <Input placeholder="{customer.name} - {name}" />;', // 15 template token
      'if (entityType === "PROJECT") {}', // 16 comparison
      'import n from "@/components/projects/n";', // 17 import specifier
      'const o = <div data-testid="customer-row" />;', // 18 test-id
      'const p = { PROJECT: "project" };', // 19 stored literal map (ADR-342)
      'function q(s: string) { return s === "PROJECT_OVERRIDE" ? "project override" : ""; }', // 20 hit
      'const errorMsg = result.error ?? "Failed to complete project.";', // 21 hit: message const
      "setError(`Failed to set due date on ${n} invoices.`);", // 22 hit: template literal
      'const r = { project: "Project", customer: "Customer" };', // 23 hit x2: lower-case-keyed map
      'function s(t: string) { if (t === "CUSTOMER") return "Customer"; return ""; }', // 24 hit
      'function u(t: string) { return t === "P" ? "Invoices" : "Matters"; }', // 25 hit: bare return
      'const v = { project: "project" };', // 26 lower-case stored literal (ADR-342)
      'function w() { return "customer"; }', // 27 lower-case stored literal
      'const x = { kind: "Matter" };', // 28 legal word
      'const y = { text: "Open the customer portal" };', // 29 hit: text key
      'const z = <input name="customer" />;', // 30 form-field key, not copy
    ].join("\n");
    expect(remnantHits("sample.tsx", sample).map((h) => h.line)).toEqual([
      1, 2, 3, 4, 8, 9, 10, 11, 12, 20, 21, 22, 23, 23, 24, 25, 29,
    ]);
  });

  it("scans the real source tree", () => {
    expect(sourceFiles.length).toBeGreaterThan(500);
    expect(sourceFiles.some((f) => f.rel === "app/(app)/org/[slug]/layout.tsx")).toBe(true);
    expect(sourceFiles.some((f) => f.rel.startsWith("components/"))).toBe(true);
    expect(sourceFiles.some((f) => f.rel.startsWith("lib/"))).toBe(true);
  });

  it("zero references to the deleted mechanism", () => {
    expect(sourceFiles.flatMap((f) => bannedLines(f.rel, f.text))).toEqual([]);
  });

  it("zero user-visible project/customer/invoice literals outside the allow-list (ADR-357)", () => {
    const allowed = new Set(ALLOW.map((e) => key(e.file, e.text)));
    expect(
      hits
        .filter((h) => !allowed.has(key(h.file, h.text)))
        .map((h) => `${h.file}:${h.line} [${h.where}]: ${h.text}`)
    ).toEqual([]);
  });

  it("allow-list entries are live and carry an ADR-357 reason", () => {
    const live = new Set(hits.map((h) => key(h.file, h.text)));
    const valid = (reason: string) =>
      REASONS.some((r) => reason === r || reason.startsWith(`${r} — `));
    expect(ALLOW.filter((e) => !live.has(key(e.file, e.text)) || !valid(e.reason))).toEqual([]);
  });
});
