import { describe, it, expect } from "vitest";
import { billingRunTitle } from "@/components/billing-runs/billing-run-title";

// Phase 91 666A, K-65 (founder-approved copy): an unnamed fee run is titled by its period.
describe("billingRunTitle", () => {
  const period = { periodFrom: "2026-11-01", periodTo: "2026-11-30" };

  it("keeps a run's own name", () => {
    expect(billingRunTitle({ name: "November run", ...period })).toBe("November run");
  });

  it("falls back to the period when the name is null", () => {
    expect(billingRunTitle({ name: null, ...period })).toBe("Fee run · 01 Nov 2026 – 30 Nov 2026");
  });

  it.each(["", "  "])("falls back to the period when the name is blank (%j)", (name) => {
    expect(billingRunTitle({ name, ...period })).toBe("Fee run · 01 Nov 2026 – 30 Nov 2026");
  });
});
