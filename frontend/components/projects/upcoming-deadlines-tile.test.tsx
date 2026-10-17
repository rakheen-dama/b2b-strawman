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

  it("renders COURT rows with a Court badge", () => {
    render(
      <UpcomingDeadlinesTile
        deadlines={[
          {
            type: "COURT",
            date: "2026-06-10",
            description: "Pretoria High Court — Judge Mokgoatlheng",
            status: "SCHEDULED",
          },
          {
            type: "COURT",
            date: "2026-07-25",
            description: "Johannesburg High Court — Trial",
            status: "SCHEDULED",
          },
        ]}
      />
    );

    expect(screen.getByText("Pretoria High Court — Judge Mokgoatlheng")).toBeInTheDocument();
    expect(screen.getByText("Johannesburg High Court — Trial")).toBeInTheDocument();

    // One Court badge and one court-row test id per row; no other deadline types exist.
    expect(screen.getAllByText("Court")).toHaveLength(2);
    expect(screen.getAllByTestId("matter-deadline-row-court")).toHaveLength(2);
    expect(screen.queryByText("Regulatory")).not.toBeInTheDocument();
  });

  it("preserves the order in which deadlines arrive (backend sorted)", () => {
    render(
      <UpcomingDeadlinesTile
        deadlines={[
          { type: "COURT", date: "2026-05-01", description: "First", status: "SCHEDULED" },
          { type: "COURT", date: "2026-06-10", description: "Second", status: "SCHEDULED" },
          { type: "COURT", date: "2026-07-25", description: "Third", status: "SCHEDULED" },
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
