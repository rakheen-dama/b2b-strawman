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
    // Recursive-walk roots. `hooks` added per slop-hunt-PR-1228 finding #1
    // so brand strings can't leak through hooks. `e2e` is test code (same
    // class as the existing `__tests__` exclusion) and is intentionally
    // excluded.
    const roots = ["app", "components", "lib", "hooks"].map((r) =>
      join(portalRoot, r),
    );
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

    // Top-level portal-root files (e.g. middleware.ts, next.config.ts) ship
    // to users but live under no walked directory. Scan them non-recursively
    // so brand strings can't leak through root-level code.
    for (const entry of readdirSync(portalRoot)) {
      const full = join(portalRoot, entry);
      const s = statSync(full);
      if (s.isFile() && /\.(tsx?|jsx?)$/.test(entry)) {
        const content = readFileSync(full, "utf8");
        if (content.includes("DocTeams")) {
          offenders.push(full);
        }
      }
    }

    expect(offenders).toEqual([]);
  });
});
