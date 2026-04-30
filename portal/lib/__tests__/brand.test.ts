import { describe, it, expect } from "vitest";
import { readFileSync, readdirSync, statSync } from "fs";
import { join, resolve } from "path";
import { BRAND_NAME } from "@/lib/brand";

// OBS-404 — guardrail: ensure the retired brand name "DocTeams" never leaks
// back into portal source. The product is "Kazi" (company "b2mash"). See
// project CLAUDE.md and feedback_product_name_kazi.md.
describe("brand", () => {
  it("exposes Kazi as the canonical brand name", () => {
    expect(BRAND_NAME).toBe("Kazi");
  });

  it("has no DocTeams references in portal source", () => {
    // Resolve the portal root from this test file's location so the walk is
    // independent of vitest's cwd.
    const portalRoot = resolve(__dirname, "..", "..");
    const roots = ["app", "components", "lib"].map((r) => join(portalRoot, r));
    const offenders: string[] = [];

    function walk(p: string): void {
      for (const entry of readdirSync(p)) {
        if (
          entry === "node_modules" ||
          entry === ".next" ||
          entry === "__tests__"
        ) {
          continue;
        }
        const full = join(p, entry);
        const s = statSync(full);
        if (s.isDirectory()) {
          walk(full);
        } else if (/\.(tsx?|jsx?)$/.test(entry)) {
          const content = readFileSync(full, "utf8");
          if (content.includes("DocTeams")) {
            offenders.push(full);
          }
        }
      }
    }

    roots.forEach(walk);
    expect(offenders).toEqual([]);
  });
});
