import fs from "node:fs";
import path from "node:path";
import { describe, it, expect } from "vitest";

// Phase 87 S5 (611E.2; ADR-338; arch §12.3.6 "Portal mirror", §12.10.5): portal twin of
// frontend/__tests__/no-terminology-remnants.test.ts. The portal's runtime terminology layer
// (lib/terminology.tsx, the TerminologyProvider mount, every t() site) and
// packages/shared/src/terminology-map.ts are deleted — the legal word is written into the copy.
// BANNED is raw-text, line-level over app/, components/, lib/, hooks/ (tests, __tests__/, e2e and
// node_modules are skipped). Own walker — node:fs globSync needs Node 22 (CI runs Node 20) and the
// portal cannot import the frontend's __tests__/helpers/source-scan.ts.
// The generic-noun JSX assertion of the frontend twin is NOT mirrored here: portal labels and
// routes are re-authored in Epic 617A (§12.8); the vocabulary assertion lands with 617.
// The ESLint no-restricted-imports rule (eslint.config.mjs) bans the deleted module paths;
// __tests__/eslint-rules/no-terminology-imports.test.ts proves that rule is wired.

const PORTAL_ROOT = path.resolve(__dirname, "..");
const SCAN_ROOTS = ["app", "components", "lib", "hooks"];
const BANNED =
  /useTerminology|TerminologyProvider|TerminologyText|TerminologyHeading|terminology-map|withIndefiniteArticle|auditTabLabel|PORTAL_TERMINOLOGY/;

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

function bannedLines(rel: string, text: string): string[] {
  return text
    .split("\n")
    .flatMap((line, i) =>
      BANNED.test(line) ? [`${rel}:${i + 1}: ${line.trim()}`] : [],
    );
}

describe("no runtime terminology layer remains in the portal (Phase 87 611E, ADR-338)", () => {
  it("detector: BANNED catches the mechanism, not the legal copy", () => {
    expect(
      bannedLines(
        "s.tsx",
        'import { useTerminology } from "@/lib/terminology";',
      ),
    ).toHaveLength(1);
    expect(
      bannedLines("s.tsx", "<TerminologyProvider verticalProfile={p}>"),
    ).toHaveLength(1);
    expect(
      bannedLines(
        "s.ts",
        'import { PORTAL_TERMINOLOGY } from "@b2mash/shared/terminology-map";',
      ),
    ).toHaveLength(1);
    expect(bannedLines("s.tsx", "<h1>Your Matters</h1>")).toEqual([]);
  });

  it("scans the real source tree", () => {
    expect(sourceFiles.length).toBeGreaterThan(40);
    expect(
      sourceFiles.some((f) => f.rel === "app/(authenticated)/layout.tsx"),
    ).toBe(true);
    expect(
      sourceFiles.some((f) => f.rel === "components/portal-sidebar.tsx"),
    ).toBe(true);
    expect(
      sourceFiles.some((f) => f.rel === "hooks/use-portal-context.ts"),
    ).toBe(true);
  });

  it("zero references to the deleted mechanism", () => {
    expect(sourceFiles.flatMap((f) => bannedLines(f.rel, f.text))).toEqual([]);
  });
});
