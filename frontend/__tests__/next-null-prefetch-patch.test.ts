// @vitest-environment node
/**
 * Guard for the pnpm patch to `next` (patches/next@<version>.patch, wired via
 * `patchedDependencies` in pnpm-workspace.yaml). The patch fixes a router race that left
 * pages blank after navigating into a segment with its own loading.tsx while that
 * segment's prefetch was still in flight — see tasks/next-null-prefetch-blank-segment.md.
 *
 * If this fails, the installed Next is running WITHOUT the fix (e.g. after a Next upgrade
 * or a reinstall that dropped the patch). Do not just delete this test.
 */
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { describe, expect, it } from "vitest";

const require = createRequire(import.meta.url);
const MARKER = "KAZI-PATCH(next-null-prefetch)";

const HOW_TO_FIX =
  `the ${MARKER} marker is missing from the installed Next build, so the ` +
  "blank-page-after-navigation fix is NOT applied. If Next was upgraded: check whether " +
  "upstream fixed the InnerLayoutRouter null-prefetch race; if not, re-apply the patch " +
  "for the new version (pnpm patch next@<version>, see tasks/next-null-prefetch-blank-segment.md) " +
  "and re-run the blank-matter click-through repro; if upstream fixed it, remove the patch, " +
  "this test and the 'Patched dependencies' entry in frontend/CLAUDE.md.";

describe("next null-prefetch patch (KAZI-PATCH)", () => {
  it.each([
    "next/dist/client/components/layout-router.js",
    "next/dist/esm/client/components/layout-router.js",
  ])("is applied to %s", (moduleId) => {
    const source = readFileSync(require.resolve(moduleId), "utf8");
    expect(source.includes(MARKER), `${moduleId}: ${HOW_TO_FIX}`).toBe(true);
  });
});
