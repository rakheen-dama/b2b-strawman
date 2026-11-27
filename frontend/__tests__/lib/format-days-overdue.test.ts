import { describe, it, expect } from "vitest";
import { formatDaysOverdue } from "@/lib/format";

// Phase 91 666A (§18.2.6): the Debtor book's days-relative-to-due label, shared from lib/format.
describe("formatDaysOverdue", () => {
  it.each([
    [62, "62d overdue"],
    [1, "1d overdue"],
    [0, "Due today"],
    [-1, "1d until due"],
    [-3, "3d until due"],
  ])("%i → %s", (days, label) => {
    expect(formatDaysOverdue(days)).toBe(label);
  });
});
