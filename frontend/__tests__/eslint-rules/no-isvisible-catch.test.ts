// @vitest-environment node
import path from "node:path";
import { describe, it, expect } from "vitest";
import { ESLint, RuleTester } from "eslint";
import rule from "../../e2e/eslint/no-isvisible-catch.mjs";

// Phase 88 628E (U22, R10 §0 M1): Playwright locator state checks (isVisible & co.) never wait and
// reject only on a strict-mode violation, so `.catch(() => false)` on them hides "matched twice" and
// turns guarded flow steps into silent no-ops. Part 1 tests the rule module (RuleTester, pattern
// no-aschild-adjacency.test.ts); part 2 lints probe snippets through the real flat config, so it
// proves the rule is wired to the e2e files it is scoped to (pattern no-terminology-imports.test.ts).

// ESLint's RuleTester defaults to mocha-style globals; bind to vitest so
// the test cases register with vitest's runner.
RuleTester.describe = describe;
RuleTester.it = it;
RuleTester.itOnly = it.only;

const ruleTester = new RuleTester({
  languageOptions: { ecmaVersion: "latest", sourceType: "module" },
});

const swallowed = (method: string, handler = "catch") => ({
  messageId: "swallowed",
  data: { method, handler },
});
const timeoutIgnored = (method: string) => ({ messageId: "timeoutIgnored", data: { method } });

ruleTester.run("no-isvisible-catch", rule as never, {
  valid: [
    // Web-first assertion — the replacement the rule points to.
    { code: `await expect(page.getByText("x").first()).toBeVisible();` },
    // A bare state check (the sanctioned branch after a deterministic wait) is not flagged.
    { code: `if (await a.isVisible()) {}` },
    { code: `await expect(a.or(b).first()).toBeVisible(); if (await a.isVisible()) {}` },
    // waitFor() waits and honours its timeout; a bounded retry on it is legitimate (openGroupedTab).
    {
      code: `await item.waitFor({ state: "visible", timeout: 2000 }).then(() => true, () => false);`,
    },
    // Unrelated promises.
    { code: `promise.catch(() => false);` },
    { code: `fetch(url).then((r) => r.json(), () => null);` },
    // page.isVisible(selector) without a handler or options.
    { code: `page.isVisible("sel");` },
    // Only exact method names.
    { code: `foo.isVisibleish().catch(() => false);` },
    // One-argument then() does not swallow a rejection.
    { code: `x.isVisible().then((v) => v);` },
    { code: `x.isEnabled();` },
    // isEnabled/isChecked honour no timeout either, but only isVisible/isHidden document it as ignored.
    { code: `await x.isEnabled({ timeout: 1000 });` },
    // Computed access is not resolved.
    { code: `loc["isVisible"]().catch(() => false);` },
    // `methods` option narrows the checked set.
    { code: `x.isChecked().catch(() => false);`, options: [{ methods: ["isVisible"] }] },
    // `checkTimeout: false` disables (iii).
    { code: `await loc.isVisible({ timeout: 5000 });`, options: [{ checkTimeout: false }] },
  ],
  invalid: [
    {
      code: `const v = await loc.isVisible().catch(() => false);`,
      errors: [swallowed("isVisible")],
    },
    {
      // The multi-line form the legal-lifecycle suite used everywhere.
      code: [
        "const has = await page",
        "  .getByText(/x/i)",
        "  .isVisible({ timeout: 3000 })",
        "  .catch(() => false);",
      ].join("\n"),
      errors: [swallowed("isVisible"), timeoutIgnored("isVisible")],
    },
    {
      code: `expect.soft(await loc.isVisible().catch(() => false)).toBeTruthy();`,
      errors: [swallowed("isVisible")],
    },
    {
      code: `loc.isVisible().then(() => true, () => false);`,
      errors: [swallowed("isVisible", "then")],
    },
    { code: `loc?.isVisible()?.catch(() => false);`, errors: [swallowed("isVisible")] },
    { code: `loc.isEnabled().catch(() => false);`, errors: [swallowed("isEnabled")] },
    { code: `loc.isDisabled().catch(() => false);`, errors: [swallowed("isDisabled")] },
    { code: `loc.isChecked().catch(() => null);`, errors: [swallowed("isChecked")] },
    { code: `loc.isEditable().catch(() => false);`, errors: [swallowed("isEditable")] },
    { code: `loc.isHidden().catch(() => true);`, errors: [swallowed("isHidden")] },
    { code: `await loc.isVisible({ timeout: 5000 });`, errors: [timeoutIgnored("isVisible")] },
    { code: `await loc.isHidden({ "timeout": 1 });`, errors: [timeoutIgnored("isHidden")] },
    {
      code: `loc.isVisible({ timeout: 1 }).catch(() => false);`,
      options: [{ checkTimeout: false }],
      errors: [swallowed("isVisible")],
    },
    {
      // The option replaces the default set.
      code: `x.isVisible().catch(() => false); x.isChecked().catch(() => false);`,
      options: [{ methods: ["isChecked"] }],
      errors: [swallowed("isChecked")],
    },
  ],
});

// ── Part 2: wiring through eslint.config.mjs ─────────────────────────────────────────────────
const FRONTEND_ROOT = path.resolve(__dirname, "../..");
const RULE_ID = "kazi-frontend-local/no-isvisible-catch";
const PROBE = 'await page.getByText("x").isVisible().catch(() => false);\n';
const eslint = new ESLint({ cwd: FRONTEND_ROOT }); // discovers eslint.config.mjs

async function ruleMessages(file: string, code = PROBE): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath: path.join(FRONTEND_ROOT, file) });
  return result.messages.filter((m) => m.ruleId === RULE_ID).map((m) => m.messageId ?? m.message);
}

describe("no-isvisible-catch — eslint.config.mjs wiring", { timeout: 30_000 }, () => {
  it.each([
    "e2e/tests/legal-lifecycle/day-00-firm-setup.spec.ts",
    "e2e/tests/legal-lifecycle/day-01-03-onboarding.spec.ts",
    "e2e/tests/legal-lifecycle/day-07-14-work-trust.spec.ts",
    "e2e/tests/legal-lifecycle/day-30-billing.spec.ts",
    "e2e/tests/legal-lifecycle/day-45-recon-prescription.spec.ts",
    "e2e/tests/legal-lifecycle/day-60-interest.spec.ts",
    "e2e/tests/legal-lifecycle/day-75-complex.spec.ts",
    "e2e/tests/legal-lifecycle/day-90-review.spec.ts",
    "e2e/tests/legal-lifecycle/matter-team.spec.ts",
    "e2e/tests/legal-lifecycle/__probe__.spec.ts",
    "e2e/tests/legal-depth-ii/__probe__.spec.ts",
    "e2e/helpers/__probe__.ts",
    "e2e/fixtures/__probe__.ts",
  ])("is enforced in %s", async (file) => {
    expect(await ruleMessages(file)).toEqual(["swallowed"]);
  });

  // Deliberate scope (Phase 88 628E R-2 / OD-2 (b)): the rule covers the two legal suites, helpers
  // and fixtures; 628F widens it to e2e/** when it sweeps the default-config specs. Flip these
  // assertions when widening.
  it.each(["e2e/tests/settings/__probe__.spec.ts", "components/__probe__.tsx"])(
    "is not (yet) enforced in %s",
    async (file) => {
      expect(await ruleMessages(file)).toEqual([]);
    }
  );
});
