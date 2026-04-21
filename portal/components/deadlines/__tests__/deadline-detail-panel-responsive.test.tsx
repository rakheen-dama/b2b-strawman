import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
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

/**
 * Epic 499B — The DeadlineDetailPanel is a right-side drawer on desktop and a
 * bottom sheet below md. Both variants are rendered via the same aside — the
 * layout/anchor toggles via Tailwind responsive prefixes, so there is no JS
 * width detection to avoid hydration mismatches.
 */
describe("DeadlineDetailPanel responsive layout", () => {
  afterEach(() => {
    cleanup();
  });

  it("anchors to bottom on mobile (inset-x-0 bottom-0) and right on md+", () => {
    render(
      <DeadlineDetailPanel open deadline={makeDeadline()} onClose={() => {}} />,
    );
    const panel = screen.getByTestId("deadline-detail-panel");
    // Mobile: anchored bottom, full-width
    expect(panel.className).toContain("inset-x-0");
    expect(panel.className).toContain("bottom-0");
    expect(panel.className).toContain("max-h-[75vh]");
    expect(panel.className).toContain("rounded-t-xl");
    // md+: right-side drawer — anchored right, released from mobile inset-x.
    expect(panel.className).toContain("md:left-auto");
    expect(panel.className).toContain("md:right-0");
    expect(panel.className).toContain("md:top-0");
    expect(panel.className).toContain("md:max-w-md");
  });

  it("open state uses translate-y-0 on mobile, translate-x-0 on md+", () => {
    render(
      <DeadlineDetailPanel open deadline={makeDeadline()} onClose={() => {}} />,
    );
    const panel = screen.getByTestId("deadline-detail-panel");
    expect(panel.className).toContain("translate-y-0");
    expect(panel.className).toContain("md:translate-x-0");
  });

  it("closed state uses translate-y-full on mobile, md:translate-x-full on md+", () => {
    render(
      <DeadlineDetailPanel
        open={false}
        deadline={makeDeadline()}
        onClose={() => {}}
      />,
    );
    const panel = screen.getByTestId("deadline-detail-panel");
    expect(panel.className).toContain("translate-y-full");
    expect(panel.className).toContain("md:translate-x-full");
  });

  it("renders a mobile-only grab handle (hidden on md+)", () => {
    render(
      <DeadlineDetailPanel open deadline={makeDeadline()} onClose={() => {}} />,
    );
    const handle = screen.getByTestId("deadline-detail-handle");
    expect(handle).toBeDefined();
    expect(handle.className).toContain("md:hidden");
  });

  it("close button is at least 44x44px (WCAG 2.2 AA tap target)", () => {
    render(
      <DeadlineDetailPanel open deadline={makeDeadline()} onClose={() => {}} />,
    );
    const closeBtn = screen.getByRole("button", { name: /close panel/i });
    expect(closeBtn.className).toContain("min-h-11");
    expect(closeBtn.className).toContain("min-w-11");
  });
});
