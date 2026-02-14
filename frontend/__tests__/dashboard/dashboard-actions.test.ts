import { describe, it, expect } from "vitest";
import { resolveDateRange } from "@/lib/date-utils";

describe("resolveDateRange", () => {
  it("returns provided from and to when both are present", () => {
    const result = resolveDateRange({ from: "2025-01-01", to: "2025-01-31" });
    expect(result).toEqual({ from: "2025-01-01", to: "2025-01-31" });
  });

  it("defaults to current month when neither from nor to is provided", () => {
    const result = resolveDateRange({});
    const now = new Date();
    const expectedYear = now.getFullYear();
    const expectedMonth = String(now.getMonth() + 1).padStart(2, "0");

    expect(result.from).toMatch(
      new RegExp(`^${expectedYear}-${expectedMonth}-01$`)
    );
    // Last day of month varies, just check it starts with the right year-month
    expect(result.to).toMatch(
      new RegExp(`^${expectedYear}-${expectedMonth}-\\d{2}$`)
    );
  });

  it("uses provided from with default to when only from is given", () => {
    const result = resolveDateRange({ from: "2025-03-15" });
    expect(result.from).toBe("2025-03-15");
    // to defaults to end of current month
    expect(result.to).toBeDefined();
  });

  it("uses provided to with default from when only to is given", () => {
    const result = resolveDateRange({ to: "2025-06-30" });
    expect(result.to).toBe("2025-06-30");
    // from defaults to start of current month
    expect(result.from).toBeDefined();
  });
});
