import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DeadlineList } from "@/components/deadlines/deadline-list";
import type {
  PortalDeadline,
  PortalDeadlineStatus,
  PortalDeadlineType,
} from "@/lib/api/deadlines";

function deadline(
  overrides: Partial<PortalDeadline> & { id: string },
): PortalDeadline {
  return {
    sourceEntity: "FILING_SCHEDULE",
    deadlineType: "FILING",
    label: "VAT 201",
    dueDate: "2026-05-01",
    status: "UPCOMING",
    descriptionSanitised: "",
    matterId: null,
    ...overrides,
  };
}

interface HarnessProps {
  deadlines: PortalDeadline[];
  now?: Date;
  onSelect?: (d: PortalDeadline) => void;
}

function Harness({ deadlines, now, onSelect }: HarnessProps) {
  return (
    <DeadlineList
      deadlines={deadlines}
      isLoading={false}
      error={null}
      statusFilter={"ALL" as "ALL" | PortalDeadlineStatus}
      onStatusFilterChange={() => {}}
      typeFilter={"ALL" as "ALL" | PortalDeadlineType}
      onTypeFilterChange={() => {}}
      onSelect={onSelect ?? (() => {})}
      now={now}
    />
  );
}

describe("DeadlineList", () => {
  afterEach(() => {
    cleanup();
  });

  it("groups deadlines into separate weeks when they fall in different ISO weeks", () => {
    // 2026-04-20 is Monday; 2026-04-27 is the following Monday.
    const items = [
      deadline({
        id: "d1",
        sourceEntity: "FILING_SCHEDULE",
        dueDate: "2026-04-22",
        label: "VAT 201 (Week 1)",
      }),
      deadline({
        id: "d2",
        sourceEntity: "COURT_DATE",
        dueDate: "2026-04-28",
        label: "Opposing motion (Week 2)",
      }),
    ];

    render(<Harness deadlines={items} now={new Date("2026-04-21T00:00:00")} />);

    const week1 = screen.getByTestId("deadline-week-2026-04-20");
    const week2 = screen.getByTestId("deadline-week-2026-04-27");
    expect(week1).toBeInTheDocument();
    expect(week2).toBeInTheDocument();
    expect(week1.textContent).toContain("VAT 201 (Week 1)");
    expect(week2.textContent).toContain("Opposing motion (Week 2)");
  });

  it("applies urgency tones by days-until-due", () => {
    const now = new Date("2026-04-20T12:00:00");
    const items = [
      deadline({
        id: "far",
        sourceEntity: "FILING_SCHEDULE",
        dueDate: "2026-06-01", // > 14 days away → grey
        label: "Far future",
      }),
      deadline({
        id: "soon",
        sourceEntity: "COURT_DATE",
        dueDate: "2026-04-25", // ≤ 7 days → red
        label: "Very soon",
      }),
      deadline({
        id: "late",
        sourceEntity: "PRESCRIPTION_TRACKER",
        dueDate: "2026-04-10", // negative → red-solid (overdue)
        label: "Overdue item",
      }),
    ];

    render(<Harness deadlines={items} now={now} />);

    expect(
      screen.getByTestId("deadline-row-FILING_SCHEDULE-far"),
    ).toHaveAttribute("data-tone", "grey");
    expect(
      screen.getByTestId("deadline-row-COURT_DATE-soon"),
    ).toHaveAttribute("data-tone", "red");
    expect(
      screen.getByTestId("deadline-row-PRESCRIPTION_TRACKER-late"),
    ).toHaveAttribute("data-tone", "red-solid");
  });

  it("invokes onSelect with the clicked deadline", async () => {
    const onSelect = vi.fn();
    const items = [
      deadline({
        id: "click-me",
        sourceEntity: "FILING_SCHEDULE",
        label: "Click target",
      }),
    ];

    render(
      <Harness
        deadlines={items}
        onSelect={onSelect}
        now={new Date("2026-04-20T00:00:00")}
      />,
    );

    const user = userEvent.setup();
    await user.click(
      screen.getByTestId("deadline-row-FILING_SCHEDULE-click-me"),
    );

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ id: "click-me", label: "Click target" }),
    );
  });
});
