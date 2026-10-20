import { describe, it, expect } from "vitest";
import { findLiterals, readSourceTree } from "../../helpers/source-scan";

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

const SCAN_ROOTS = ["app", "components", "lib", "hooks"];
const ALLOWED = new Set(["lib/api/matters.ts", "lib/api/clients.ts", "lib/api/paths.ts"]);
const WIRE_LITERAL = /\/api\/(matters|clients)\b/;

const sourceFiles = readSourceTree(SCAN_ROOTS);

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
