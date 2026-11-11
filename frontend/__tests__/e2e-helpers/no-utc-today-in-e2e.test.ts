import fs from "node:fs";
import path from "node:path";
import { describe, expect, it, vi } from "vitest";
import { todayInSA } from "@b2mash/shared/date";
import { FRONTEND_ROOT } from "../helpers/source-scan";

// G89-F66: e2e specs that dated entries with the UTC date (`new Date().toISOString().split("T")[0]`)
// failed every night 00:00–02:00 SAST, because the product's "today" is the South African date
// (KaziTime; G88-F49) — My Work filtered the entry out. "Today" in an e2e spec or helper comes
// from `todayInSA()` (@b2mash/shared/date) or `isoDate()` (helpers/legal-depth-ii.ts). Scans the
// frontend and portal e2e trees.

const E2E_ROOTS = [path.join(FRONTEND_ROOT, "e2e"), path.join(FRONTEND_ROOT, "../portal/e2e")];
const UTC_TODAY =
  /new Date\(\)\s*\.toISOString\(\)\s*\.(?:split\(\s*["']T["']\s*\)|slice\(\s*0\s*,\s*10\s*\)|substring\(\s*0\s*,\s*10\s*\)|substr\(\s*0\s*,\s*10\s*\))/;

function collect(dir: string, out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === "screenshots") continue;
      collect(full, out);
    } else if (/\.(ts|tsx|mjs|js)$/.test(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

describe("e2e 'today' is the SA date (G89-F66)", () => {
  it("no e2e spec or helper derives today from the UTC ISO string", () => {
    const hits = E2E_ROOTS.flatMap((root) => collect(root)).flatMap((file) =>
      fs
        .readFileSync(file, "utf8")
        .split("\n")
        .flatMap((line, i) =>
          UTC_TODAY.test(line) ? [`${path.relative(FRONTEND_ROOT, file)}:${i + 1}`] : []
        )
    );
    expect(hits).toEqual([]);
  });

  it("the two differ inside the 00:00–02:00 SAST window", () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date("2026-11-11T23:30:00Z")); // 01:30 SAST on 12 Nov
      expect(new Date().toISOString().split("T")[0]).toBe("2026-11-11");
      expect(todayInSA()).toBe("2026-11-12");
    } finally {
      vi.useRealTimers();
    }
  });
});
