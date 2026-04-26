import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { UpcomingDeadlinesTile } from "./upcoming-deadlines-tile";

describe("UpcomingDeadlinesTile (GAP-L-58)", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders the empty state when there are no deadlines", () => {
    render(<UpcomingDeadlinesTile deadlines={[]} />);
    expect(screen.getByText("No upcoming deadlines.")).toBeInTheDocument();
  });

  it("renders mixed COURT and REGULATORY rows with badges", () => {
    render(
      <UpcomingDeadlinesTile
        deadlines={[
          {
            type: "REGULATORY",
            date: "2026-05-01",
            description: "SARS Provisional Tax 1",
            status: "pending",
          },
          {
            type: "COURT",
            date: "2026-06-10",
            description: "Pretoria High Court — Judge Mokgoatlheng",
            status: "SCHEDULED",
          },
          {
            type: "REGULATORY",
            date: "2026-07-25",
            description: "SARS VAT Return",
            status: "pending",
          },
        ]}
      />
    );

    // Both descriptions render
    expect(screen.getByText("SARS Provisional Tax 1")).toBeInTheDocument();
    expect(screen.getByText("Pretoria High Court — Judge Mokgoatlheng")).toBeInTheDocument();
    expect(screen.getByText("SARS VAT Return")).toBeInTheDocument();

    // Badges distinguish the types — exactly 1 Court badge + 2 Regulatory badges
    expect(screen.getAllByText("Court")).toHaveLength(1);
    expect(screen.getAllByText("Regulatory")).toHaveLength(2);

    // Per-type test ids — exactly 1 court row + 2 regulatory rows
    expect(screen.getAllByTestId("matter-deadline-row-court")).toHaveLength(1);
    expect(screen.getAllByTestId("matter-deadline-row-regulatory")).toHaveLength(2);
  });

  it("preserves the order in which deadlines arrive (backend sorted)", () => {
    render(
      <UpcomingDeadlinesTile
        deadlines={[
          { type: "REGULATORY", date: "2026-05-01", description: "First", status: "pending" },
          { type: "COURT", date: "2026-06-10", description: "Second", status: "SCHEDULED" },
          { type: "REGULATORY", date: "2026-07-25", description: "Third", status: "pending" },
        ]}
      />
    );

    const rows = screen.getAllByTestId(/^matter-deadline-row-/);
    expect(rows).toHaveLength(3);
    expect(rows[0]).toHaveTextContent("First");
    expect(rows[1]).toHaveTextContent("Second");
    expect(rows[2]).toHaveTextContent("Third");
  });
});
