// @vitest-environment node
import fs from "node:fs";
import path from "node:path";
import { describe, it, expect } from "vitest";
import { ESLint } from "eslint";

// Phase 88 628G (R-8): portal twin of frontend/__tests__/eslint-rules/no-isvisible-catch.test.ts.
// Playwright locator state checks (isVisible & co.) reject only when they cannot answer, so
// `.catch(() => false)` on them hides "matched twice" / "never appeared" and turns guarded flow
// steps into silent no-ops (the portal 90-day suite "passed" that way until 628G). The rule's own
// behaviour is covered by the frontend RuleTester suite; this file proves (1) the portal copy has
// not drifted from the frontend rule and (2) it is wired through the real flat config
// (eslint.config.mjs) to every portal e2e file — pattern no-terminology-imports.test.ts.

const PORTAL_ROOT = path.resolve(__dirname, "../..");
const PORTAL_RULE = path.join(PORTAL_ROOT, "e2e/eslint/no-isvisible-catch.mjs");
const FRONTEND_RULE = path.resolve(
  PORTAL_ROOT,
  "../frontend/e2e/eslint/no-isvisible-catch.mjs",
);
const RULE_ID = "kazi-portal-local/no-isvisible-catch";
const eslint = new ESLint({ cwd: PORTAL_ROOT }); // discovers eslint.config.mjs

async function ruleMessages(file: string, code: string): Promise<string[]> {
  const [result] = await eslint.lintText(code, {
    filePath: path.join(PORTAL_ROOT, file),
  });
  return result.messages
    .filter((m) => m.ruleId === RULE_ID)
    .map((m) => m.messageId ?? m.message);
}

const SWALLOWED = 'await page.getByText("x").isVisible().catch(() => false);\n';

describe(
  "no-isvisible-catch — portal copy + eslint.config.mjs wiring",
  { timeout: 30_000 },
  () => {
    it("is a byte-identical copy of the frontend rule", () => {
      expect(fs.readFileSync(PORTAL_RULE, "utf8")).toBe(
        fs.readFileSync(FRONTEND_RULE, "utf8"),
      );
    });

    it.each([
      "e2e/tests/portal-client-90day/day-00-07.spec.ts",
      "e2e/tests/portal-client-90day/__probe__.spec.ts",
      "e2e/tests/responsive/portal-pages.spec.ts",
      "e2e/tests/login-bare.spec.ts",
      "e2e/tests/__probe__.spec.ts",
      "e2e/helpers/__probe__.ts",
      "e2e/__probe__.ts",
    ])("is enforced in %s", async (file) => {
      expect(await ruleMessages(file, SWALLOWED)).toEqual(["swallowed"]);
    });

    it("flags the ignored isVisible({ timeout }) and a two-argument then()", async () => {
      expect(
        await ruleMessages(
          "e2e/tests/__probe__.spec.ts",
          [
            "await loc.isVisible({ timeout: 5000 });",
            "await loc.isHidden().then(() => true, () => false);",
            "",
          ].join("\n"),
        ),
      ).toEqual(["timeoutIgnored", "swallowed"]);
    });

    it("leaves web-first assertions and a bare state read after an a.or(b) wait alone", async () => {
      expect(
        await ruleMessages(
          "e2e/tests/__probe__.spec.ts",
          [
            'await expect(page.getByText("x").first()).toBeVisible();',
            "await expect(a.or(b).first()).toBeVisible();",
            "if (await a.isVisible()) {}",
            "",
          ].join("\n"),
        ),
      ).toEqual([]);
    });

    it.each([
      "components/__probe__.tsx",
      "app/__probe__.tsx",
      "lib/__probe__.ts",
    ])("is not enforced outside e2e/ in %s", async (file) => {
      expect(await ruleMessages(file, SWALLOWED)).toEqual([]);
    });
  },
);
