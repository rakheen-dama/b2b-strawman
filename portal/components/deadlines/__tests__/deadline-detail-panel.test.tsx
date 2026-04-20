import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DeadlineDetailPanel } from "@/components/deadlines/deadline-detail-panel";
import type { PortalDeadline } from "@/lib/api/deadlines";

function makeDeadline(
  overrides: Partial<PortalDeadline> = {},
): PortalDeadline {
  return {
    id: "d-1",
    sourceEntity: "FILING_SCHEDULE",
    deadlineType: "FILING",
    label: "VAT 201 submission",
    dueDate: "2026-05-01",
    status: "UPCOMING",
    descriptionSanitised: "Submit before 5pm",
    matterId: null,
    ...overrides,
  };
}

describe("DeadlineDetailPanel", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders label, due date, status badge, description, and source-entity reference", () => {
    render(
      <DeadlineDetailPanel
        open
        deadline={makeDeadline()}
        onClose={() => {}}
      />,
    );

    const panel = screen.getByTestId("deadline-detail-panel");
    expect(panel.getAttribute("data-state")).toBe("open");
    expect(panel.textContent).toContain("VAT 201 submission");
    // Formatted by formatDate (en-GB).
    expect(panel.textContent).toMatch(/1 May 2026/);
    // Status "UPCOMING" formatted → "UPCOMING" (no underscore in this status).
    expect(panel.textContent).toContain("UPCOMING");
    // Description rendered as-is (pre-sanitised server-side per ADR-254).
    expect(panel.textContent).toContain("Submit before 5pm");
    // Source-entity reference rendered with humanised label.
    expect(panel.textContent).toContain("From: Filing schedule");
  });

  it("calls onClose when the backdrop is clicked", async () => {
    const onClose = vi.fn();
    render(
      <DeadlineDetailPanel
        open
        deadline={makeDeadline()}
        onClose={onClose}
      />,
    );

    const user = userEvent.setup();
    await user.click(screen.getByTestId("deadline-detail-backdrop"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("calls onClose when the Escape key is pressed", async () => {
    const onClose = vi.fn();
    render(
      <DeadlineDetailPanel
        open
        deadline={makeDeadline()}
        onClose={onClose}
      />,
    );

    const user = userEvent.setup();
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("animates off-screen when closed (panel stays mounted for exit animation)", () => {
    render(
      <DeadlineDetailPanel
        open={false}
        deadline={makeDeadline()}
        onClose={() => {}}
      />,
    );
    const panel = screen.getByTestId("deadline-detail-panel");
    expect(panel.getAttribute("data-state")).toBe("closed");
    expect(panel.className).toContain("translate-x-full");
  });
});
