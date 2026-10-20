// @vitest-environment node
import path from "node:path";
import { describe, it, expect } from "vitest";
import { ESLint } from "eslint";

// Phase 87 611D.2 (ADR-338): eslint.config.mjs bans the deleted runtime terminology modules with
// no-restricted-imports. Unlike no-aschild-adjacency.test.ts (RuleTester on a custom rule module), this
// lints probe snippets through the real flat config, so it proves the rule is wired, not just that
// ESLint works.

const FRONTEND_ROOT = path.resolve(__dirname, "../..");
const MESSAGE = "Phase 87: write the legal word; no runtime vocabulary layer (ADR-338)";
const eslint = new ESLint({ cwd: FRONTEND_ROOT }); // discovers eslint.config.mjs

async function restrictedImportMessages(
  code: string,
  file = "components/__terminology_probe__.tsx"
): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath: path.join(FRONTEND_ROOT, file) });
  return result.messages.filter((m) => m.ruleId === "no-restricted-imports").map((m) => m.message);
}

describe("no-restricted-imports — runtime terminology layer (ADR-338)", { timeout: 30_000 }, () => {
  it.each([
    ["@/lib/terminology", "useTerminology"],
    ["@/components/terminology-heading", "TerminologyHeading"],
    ["@/components/terminology-text", "TerminologyText"],
    ["@b2mash/shared/terminology-map", "TERMINOLOGY"],
  ])("flags an import from %s", async (specifier, name) => {
    const messages = await restrictedImportMessages(
      `import { ${name} } from "${specifier}";\nexport const x = ${name};\n`
    );
    expect(messages).toHaveLength(1);
    expect(messages[0]).toContain(MESSAGE);
  });

  it.each(["TERMINOLOGY", "TERMINOLOGY_BASE", "PORTAL_TERMINOLOGY", "auditTabLabel"])(
    "flags %s imported from the @b2mash/shared barrel",
    async (name) => {
      const messages = await restrictedImportMessages(
        `import { ${name} } from "@b2mash/shared";\nexport const x = ${name};\n`
      );
      expect(messages).toEqual([expect.stringContaining(MESSAGE)]);
    }
  );

  it("applies to test files too", async () => {
    const messages = await restrictedImportMessages(
      'import { TerminologyProvider } from "@/lib/terminology";\nexport const x = TerminologyProvider;\n',
      "__tests__/__terminology_probe__.test.tsx"
    );
    expect(messages).toEqual([expect.stringContaining(MESSAGE)]);
  });

  it("leaves formatting and module-gating imports alone", async () => {
    const messages = await restrictedImportMessages(
      [
        'import { formatDate } from "@b2mash/shared/format";',
        'import { useOrgProfile } from "@/lib/org-profile";',
        "export const x = [formatDate, useOrgProfile];",
        "",
      ].join("\n")
    );
    expect(messages).toEqual([]);
  });
});
