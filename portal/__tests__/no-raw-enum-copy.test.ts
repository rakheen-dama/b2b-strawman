import fs from "node:fs";
import path from "node:path";
import ts from "typescript";
import { describe, it, expect } from "vitest";
import allowList from "./raw-enum-allow-list.json";

// Phase 89 633C (arch §14.3.15, §14.6.5, B9; G88-F20/F29/F30/F31): no raw backend enum reaches a
// client. A SEPARATE guard from no-terminology-remnants.test.ts — that file's <terminology-scanner>
// block must stay byte-identical to the frontend's (B9), so nothing is added to it. Same TS AST
// walk over app/, components/, lib/, hooks/ (tests, __tests__/, e2e and node_modules are skipped).
// A hit is a JSX expression container whose expression is a bare property access ending in
// .status / .transactionType / .sourceEntity (parentheses, `!`, `as` and optional chaining see
// through), or a .replace(/_/g, …) / .replaceAll("_", …) call on one (also mid-chain). Render
// through a label map (lib/labels.ts) or humaniseStatus instead.
// A prop hit (<StatusBadge status={task.status} />) is allowed only when the element is a badge
// component listed in raw-enum-allow-list.json as { component, file, reason }: the component maps
// the value before it renders. ADR-357 style — reviewable data, a fixed reason prefix, every entry
// live. `key` / `data-*` props are never rendered and are not scanned. Not caught by design:
// template literals and helper calls such as formatStatus(x.status).

const PORTAL_ROOT = path.resolve(__dirname, "..");
const SCAN_ROOTS = ["app", "components", "lib", "hooks"];
const RAW_ENUM_FIELDS = new Set(["status", "transactionType", "sourceEntity"]);
const UNRENDERED_PROP = /^(key|data-[\w-]+)$/;
const REASON_PREFIX = "label map — ";

interface AllowEntry {
  component: string;
  file: string;
  reason: string;
}
const ALLOW: AllowEntry[] = allowList;

interface Hit {
  file: string;
  line: number;
  text: string;
  where: string;
  element: string | null;
}

// Same walk as no-terminology-remnants.test.ts collectSourceFiles (a convenience copy — the
// terminology test's own copy is the drift-checked one).
function collectSourceFiles(dir: string, out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "__tests__" || entry.name === "node_modules") continue;
      collectSourceFiles(full, out);
    } else if (
      /\.tsx?$/.test(entry.name) &&
      !/\.(test|spec)\.tsx?$/.test(entry.name)
    ) {
      out.push(full);
    }
  }
  return out;
}

const sourceFiles = SCAN_ROOTS.flatMap((root) =>
  collectSourceFiles(path.join(PORTAL_ROOT, root)),
).map((full) => ({
  rel: path.relative(PORTAL_ROOT, full).split(path.sep).join("/"),
  text: fs.readFileSync(full, "utf8"),
}));

function unwrap(expr: ts.Expression): ts.Expression {
  let e = expr;
  while (
    ts.isParenthesizedExpression(e) ||
    ts.isNonNullExpression(e) ||
    ts.isAsExpression(e)
  ) {
    e = e.expression;
  }
  return e;
}

function isRawEnumAccess(expr: ts.Expression): boolean {
  const e = unwrap(expr);
  return ts.isPropertyAccessExpression(e) && RAW_ENUM_FIELDS.has(e.name.text);
}

/** `<raw>.replace(/_/g, …)` or `<raw>.replaceAll("_", …)`, anywhere in a call chain. */
function isUnderscoreReplace(expr: ts.Expression): boolean {
  let e = unwrap(expr);
  while (
    ts.isCallExpression(e) &&
    ts.isPropertyAccessExpression(e.expression)
  ) {
    const callee = e.expression;
    if (
      /^replace(All)?$/.test(callee.name.text) &&
      isRawEnumAccess(callee.expression)
    ) {
      const pattern = e.arguments[0];
      return (
        pattern !== undefined &&
        ((ts.isRegularExpressionLiteral(pattern) &&
          pattern.text.startsWith("/_/")) ||
          (ts.isStringLiteralLike(pattern) && pattern.text === "_"))
      );
    }
    e = unwrap(callee.expression);
  }
  return false;
}

function rawEnumHits(rel: string, source: string): Hit[] {
  const kind = rel.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
  const sf = ts.createSourceFile(
    rel,
    source,
    ts.ScriptTarget.Latest,
    true,
    kind,
  );
  const hits: Hit[] = [];
  const visit = (node: ts.Node) => {
    if (
      ts.isJsxExpression(node) &&
      node.expression &&
      (isRawEnumAccess(node.expression) || isUnderscoreReplace(node.expression))
    ) {
      const attr = node.parent;
      let where: string | null = "jsx-child";
      let element: string | null = null;
      if (ts.isJsxAttribute(attr)) {
        const prop = attr.name.getText(sf);
        element = (
          attr.parent.parent as ts.JsxOpeningLikeElement
        ).tagName.getText(sf);
        where = UNRENDERED_PROP.test(prop)
          ? null
          : `prop ${prop} on <${element}>`;
      }
      if (where) {
        const { line } = sf.getLineAndCharacterOfPosition(node.getStart(sf));
        hits.push({
          file: rel,
          line: line + 1,
          text: node.getText(sf),
          where,
          element,
        });
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return hits;
}

const allowedComponents = new Set(ALLOW.map((e) => e.component));
const isAllowed = (h: Hit) =>
  h.element !== null && allowedComponents.has(h.element);

const hits = sourceFiles.flatMap((f) => rawEnumHits(f.rel, f.text));

describe("no raw backend enum is rendered to a client (Phase 89 633C, §14.3.15, B9)", () => {
  it("detector: bare .status / .transactionType / .sourceEntity renders and _-replace calls", () => {
    const sample = [
      "const a = <p>{item.status}</p>;", // 1 hit: JSX child
      "const b = <td>{txn.transactionType}</td>;", // 2 hit
      "const c = <span>{d.sourceEntity}</span>;", // 3 hit
      'const d = <p>{r.status.replace(/_/g, " ")}</p>;', // 4 hit: _-replace
      "const e = <p>• status {detail?.status}</p>;", // 5 hit: optional chain
      "const f = <StatusBadge status={task.status} />;", // 6 hit: prop (allow-listed component)
      "const g = <p>{LABELS[r.status] ?? humaniseStatus(r.status)}</p>;", // 7 mapped
      "const h = <li key={x.status} data-status={x.status} />;", // 8 never rendered
      "const i = <p>{item.name}</p>;", // 9 another field
      'const j = x.status === "DONE" ? <p>Done</p> : null;', // 10 comparison
      "const k = <Foo status={x.status} />;", // 11 hit: prop on an unlisted component
      "const l = <p>{(item.status as string)}</p>;", // 12 hit: parens + as
      'const m = <p>{x.status.replace(/_/g, " ").toLowerCase()}</p>;', // 13 hit: mid-chain
    ].join("\n");
    const found = rawEnumHits("sample.tsx", sample);
    expect(found.map((h) => h.line)).toEqual([1, 2, 3, 4, 5, 6, 11, 12, 13]);
    expect(found.filter((h) => !isAllowed(h)).map((h) => h.line)).toEqual([
      1, 2, 3, 4, 5, 11, 12, 13,
    ]);
  });

  it("scans the real source tree", () => {
    expect(sourceFiles.length).toBeGreaterThan(40);
    expect(
      sourceFiles.some((f) => f.rel === "components/status-badge.tsx"),
    ).toBe(true);
    expect(
      sourceFiles.some(
        (f) => f.rel === "app/(authenticated)/requests/[id]/page.tsx",
      ),
    ).toBe(true);
  });

  it("zero raw enum renders outside the allow-list", () => {
    expect(
      hits
        .filter((h) => !isAllowed(h))
        .map((h) => `${h.file}:${h.line} [${h.where}]: ${h.text}`),
    ).toEqual([]);
  });

  it("allow-list entries are live, name an exported component and carry a label-map reason", () => {
    const used = new Set(hits.flatMap((h) => (h.element ? [h.element] : [])));
    expect(
      ALLOW.filter((e) => {
        const full = path.join(PORTAL_ROOT, e.file);
        return (
          !used.has(e.component) ||
          !fs.existsSync(full) ||
          !fs
            .readFileSync(full, "utf8")
            .includes(`export function ${e.component}(`) ||
          !e.reason.startsWith(REASON_PREFIX)
        );
      }),
    ).toEqual([]);
  });
});
