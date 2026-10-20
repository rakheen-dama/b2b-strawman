import fs from "node:fs";
import path from "node:path";
import ts from "typescript";
import { describe, it, expect } from "vitest";

// Phase 87 S4 (610C, §12.10.2 row 4): the backend `/api/matters` and `/api/clients` paths are
// spelled in one place — `lib/api/paths.ts`, re-exported by `lib/api/matters.ts` and
// `lib/api/clients.ts`. Every other source file calls a module function or builds the URL with
// `matterPath()` / `clientPath()`.
//
// Scanned: every string literal ("…", '…', `…`), every chunk of a template literal (so
// `${API_BASE}/api/clients${qs}` is caught) and JSX text, in app/, components/, lib/, hooks/.
// Not scanned: comments (documenting an endpoint is not building a URL); import/export module
// specifiers (`"@/lib/api/matters"` contains the substring); tests (`__tests__/`, `*.test.*`,
// `*.spec.*`) — wire-body tests assert literal URLs on purpose, as an oracle independent of
// `paths.ts`; e2e/ (external callers, outside the scan roots).

const FRONTEND_ROOT = path.resolve(__dirname, "../../..");
const SCAN_ROOTS = ["app", "components", "lib", "hooks"];
const ALLOWED = new Set(["lib/api/matters.ts", "lib/api/clients.ts", "lib/api/paths.ts"]);
const WIRE_LITERAL = /\/api\/(matters|clients)\b/;

function collectSourceFiles(dir: string, out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "__tests__" || entry.name === "node_modules") continue;
      collectSourceFiles(full, out);
    } else if (/\.tsx?$/.test(entry.name) && !/\.(test|spec)\.tsx?$/.test(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

function isModuleSpecifier(node: ts.Node): boolean {
  const parent = node.parent;
  if (!parent) return false;
  if (ts.isImportDeclaration(parent) || ts.isExportDeclaration(parent)) {
    return parent.moduleSpecifier === node;
  }
  if (ts.isExternalModuleReference(parent)) return true;
  if (ts.isLiteralTypeNode(parent) && ts.isImportTypeNode(parent.parent)) return true;
  return ts.isCallExpression(parent) && parent.expression.kind === ts.SyntaxKind.ImportKeyword;
}

/** `file:line: text` for every non-comment, non-specifier literal chunk matching `pattern`. */
function findLiterals(fileName: string, source: string, pattern: RegExp): string[] {
  const kind = fileName.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
  const sf = ts.createSourceFile(fileName, source, ts.ScriptTarget.Latest, true, kind);
  const hits: string[] = [];
  const visit = (node: ts.Node) => {
    const isText =
      ts.isStringLiteralLike(node) || ts.isTemplateLiteralToken(node) || ts.isJsxText(node);
    if (isText && !isModuleSpecifier(node) && pattern.test(node.text)) {
      const { line } = sf.getLineAndCharacterOfPosition(node.getStart(sf));
      hits.push(`${fileName}:${line + 1}: ${node.text.trim()}`);
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return hits;
}

const sourceFiles = SCAN_ROOTS.flatMap((root) =>
  collectSourceFiles(path.join(FRONTEND_ROOT, root))
).map((full) => ({
  rel: path.relative(FRONTEND_ROOT, full).split(path.sep).join("/"),
  text: fs.readFileSync(full, "utf8"),
}));

describe("no inline /api/matters | /api/clients literals (Phase 87 610C)", () => {
  it("detector: catches every quoting form, skips comments and module specifiers", () => {
    const sample = [
      'import { listMatters } from "@/lib/api/matters";',
      'const lazy = () => import("@/lib/api/clients");',
      "// GET /api/matters/{id}/health",
      "/** `/api/clients/{id}` archives */",
      'const a = "/api/matters";',
      "const b = '/api/clients/' + id;",
      "const c = `/api/matters/${id}/tasks`;",
      "const d = `${API_BASE}/api/clients${qs}`;",
    ].join("\n");
    expect(findLiterals("sample.ts", sample, WIRE_LITERAL).map((h) => h.split(":")[1])).toEqual([
      "5",
      "6",
      "7",
      "8",
    ]);
  });

  it("scans the real source tree", () => {
    expect(sourceFiles.length).toBeGreaterThan(500);
    expect(sourceFiles.some((f) => f.rel === "lib/api/paths.ts")).toBe(true);
  });

  it("zero literals outside lib/api/{matters,clients,paths}.ts", () => {
    const hits = sourceFiles
      .filter((f) => !ALLOWED.has(f.rel))
      .flatMap((f) => findLiterals(f.rel, f.text, WIRE_LITERAL));
    expect(hits).toEqual([]);
  });
});
