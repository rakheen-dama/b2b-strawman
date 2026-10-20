import fs from "node:fs";
import path from "node:path";
import ts from "typescript";

// Shared AST source scan for the Phase 87 literal-ban tests (`no-legacy-hrefs.test.ts`,
// `lib/api/no-inline-matter-client-literals.test.ts`). Each test keeps its own pattern, scan
// roots, allowlist and self-test; this module only walks the tree and finds literal chunks.
// Not a test file (no `.test.` suffix), and the walk skips `__tests__/`, so it is never scanned.

export const FRONTEND_ROOT = path.resolve(__dirname, "../..");

/** Every `.ts` / `.tsx` file under `dir`, skipping `__tests__/`, `node_modules/` and `*.test.*` / `*.spec.*`. */
export function collectSourceFiles(dir: string, out: string[] = []): string[] {
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

/** Source files under each of `roots` (relative to the frontend root), as `{ rel, text }`. */
export function readSourceTree(roots: readonly string[]): { rel: string; text: string }[] {
  return roots
    .flatMap((root) => collectSourceFiles(path.join(FRONTEND_ROOT, root)))
    .map((full) => ({
      rel: path.relative(FRONTEND_ROOT, full).split(path.sep).join("/"),
      text: fs.readFileSync(full, "utf8"),
    }));
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
export function findLiterals(fileName: string, source: string, pattern: RegExp): string[] {
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
