import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { DeadlineList } from "@/components/deadlines/deadline-list";
import type { PortalDeadline } from "@/lib/api/deadlines";

function makeDeadlines(): PortalDeadline[] {
  return [
    {
      id: "d-1",
      sourceEntity: "FILING_SCHEDULE",
      deadlineType: "FILING",
      label: "VAT 201 submission",
      dueDate: "2099-01-05",
      status: "UPCOMING",
      descriptionSanitised: "",
      matterId: null,
    },
    {
      id: "d-2",
      sourceEntity: "COURT_DATE",
      deadlineType: "COURT_DATE",
      label: "Pre-trial conference",
      dueDate: "2099-01-12",
      status: "UPCOMING",
      descriptionSanitised: "",
      matterId: null,
    },
  ];
}

/**
 * Epic 499B — The deadline list renders full-width rows (not a table). Each
 * row is already list-like and collapses cleanly below md; the responsive
 * contract is: rows remain full-width, tap targets are at least 44px.
 */
describe("DeadlineList responsive layout", () => {
  afterEach(() => {
    cleanup();
  });

  it("row buttons hit the 44px minimum tap target", () => {
    render(
      <DeadlineList
        deadlines={makeDeadlines()}
        isLoading={false}
        error={null}
        statusFilter="ALL"
        onStatusFilterChange={() => {}}
        typeFilter="ALL"
        onTypeFilterChange={() => {}}
        onSelect={() => {}}
      />,
    );
    const rows = screen.getAllByRole("button", {
      name: /view details for/i,
    });
    expect(rows.length).toBeGreaterThan(0);
    for (const row of rows) {
      expect(row.className).toContain("min-h-11");
      expect(row.className).toContain("w-full");
    }
  });

  it("filter selects use min-h-11 so they are touch-friendly on mobile", () => {
    render(
      <DeadlineList
        deadlines={makeDeadlines()}
        isLoading={false}
        error={null}
        statusFilter="ALL"
        onStatusFilterChange={() => {}}
        typeFilter="ALL"
        onTypeFilterChange={() => {}}
        onSelect={() => {}}
      />,
    );
    const statusSelect = screen.getByLabelText(/status/i);
    const typeSelect = screen.getByLabelText(/type/i);
    expect(statusSelect.className).toContain("min-h-11");
    expect(typeSelect.className).toContain("min-h-11");
  });
});
