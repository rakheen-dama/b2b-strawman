// @vitest-environment node
import path from "node:path";
import { describe, it, expect } from "vitest";
import { ESLint } from "eslint";

// Phase 87 611E.2 (ADR-338): portal twin of
// frontend/__tests__/eslint-rules/no-terminology-imports.test.ts. Lints probe snippets through
// the real flat config (eslint.config.mjs), so it proves the no-restricted-imports rule is wired —
// a later flat-config object re-setting the rule would replace it and turn this red.

const PORTAL_ROOT = path.resolve(__dirname, "../..");
const MESSAGE =
  "Phase 87: write the legal word; no runtime vocabulary layer (ADR-338)";
const eslint = new ESLint({ cwd: PORTAL_ROOT }); // discovers eslint.config.mjs

async function restrictedImportMessages(
  code: string,
  file = "components/__terminology_probe__.tsx",
): Promise<string[]> {
  const [result] = await eslint.lintText(code, {
    filePath: path.join(PORTAL_ROOT, file),
  });
  return result.messages
    .filter((m) => m.ruleId === "no-restricted-imports")
    .map((m) => m.message);
}

describe(
  "no-restricted-imports — runtime terminology layer (portal, ADR-338)",
  { timeout: 30_000 },
  () => {
    it.each([
      ["@/lib/terminology", "useTerminology"],
      ["@/components/terminology-heading", "TerminologyHeading"],
      ["@/components/terminology-text", "TerminologyText"],
      ["@b2mash/shared/terminology-map", "PORTAL_TERMINOLOGY"],
    ])("flags an import from %s", async (specifier, name) => {
      const messages = await restrictedImportMessages(
        `import { ${name} } from "${specifier}";\nexport const x = ${name};\n`,
      );
      expect(messages).toEqual([expect.stringContaining(MESSAGE)]);
    });

    it.each([
      "TERMINOLOGY",
      "TERMINOLOGY_BASE",
      "PORTAL_TERMINOLOGY",
      "auditTabLabel",
    ])("flags %s imported from the @b2mash/shared barrel", async (name) => {
      const messages = await restrictedImportMessages(
        `import { ${name} } from "@b2mash/shared";\nexport const x = ${name};\n`,
      );
      expect(messages).toEqual([expect.stringContaining(MESSAGE)]);
    });

    it("applies to test files too", async () => {
      const messages = await restrictedImportMessages(
        'import { TerminologyProvider } from "@/lib/terminology";\nexport const x = TerminologyProvider;\n',
        "__tests__/__terminology_probe__.test.tsx",
      );
      expect(messages).toEqual([expect.stringContaining(MESSAGE)]);
    });

    it("leaves formatting and portal-context imports alone", async () => {
      const messages = await restrictedImportMessages(
        [
          'import { formatDate } from "@b2mash/shared/format";',
          'import { useModules } from "@/hooks/use-portal-context";',
          "export const x = [formatDate, useModules];",
          "",
        ].join("\n"),
      );
      expect(messages).toEqual([]);
    });
  },
);
