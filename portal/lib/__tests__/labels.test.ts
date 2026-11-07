import { describe, it, expect } from "vitest";
import { DEADLINE_STATUS_LABELS, labelFor } from "@/lib/labels";
import type { PortalDeadlineStatus } from "@/lib/api/deadlines";

// 633C D2: portal deadline badges printed the raw enum ("UPCOMING", "DUE SOON").
describe("DEADLINE_STATUS_LABELS", () => {
  // Every value of PortalDeadlineStatus (lib/api/deadlines.ts).
  const ALL_STATUSES: PortalDeadlineStatus[] = [
    "UPCOMING",
    "DUE_SOON",
    "OVERDUE",
    "COMPLETED",
    "CANCELLED",
  ];

  it("labels every portal deadline status in sentence case", () => {
    expect(DEADLINE_STATUS_LABELS).toEqual({
      UPCOMING: "Upcoming",
      DUE_SOON: "Due soon",
      OVERDUE: "Overdue",
      COMPLETED: "Completed",
      CANCELLED: "Cancelled",
    });
    expect(Object.keys(DEADLINE_STATUS_LABELS).sort()).toEqual(
      [...ALL_STATUSES].sort(),
    );
  });

  it("never returns the raw enum, even for an unknown status", () => {
    for (const status of ALL_STATUSES) {
      expect(labelFor(DEADLINE_STATUS_LABELS, status)).not.toBe(status);
    }
    expect(labelFor(DEADLINE_STATUS_LABELS, "ON_HOLD")).toBe("On hold");
  });
});
