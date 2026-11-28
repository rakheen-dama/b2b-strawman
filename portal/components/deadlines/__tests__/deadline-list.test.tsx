import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DeadlineList } from "@/components/deadlines/deadline-list";
import type {
  PortalDeadline,
  PortalDeadlineStatus,
  PortalDeadlineType,
} from "@/lib/api/deadlines";
import { inZone } from "@/__tests__/helpers/time-zone";

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
        id: "medium",
        sourceEntity: "FILING_SCHEDULE",
        dueDate: "2026-05-02", // 12 days away → amber (8–14)
        label: "Approaching",
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
      deadline({
        id: "done",
        sourceEntity: "FILING_SCHEDULE",
        dueDate: "2026-04-25", // would be red by days, but status overrides
        status: "COMPLETED",
        label: "Already completed",
      }),
    ];

    render(<Harness deadlines={items} now={now} />);

    expect(
      screen.getByTestId("deadline-row-FILING_SCHEDULE-far"),
    ).toHaveAttribute("data-tone", "grey");
    expect(
      screen.getByTestId("deadline-row-FILING_SCHEDULE-medium"),
    ).toHaveAttribute("data-tone", "amber");
    expect(screen.getByTestId("deadline-row-COURT_DATE-soon")).toHaveAttribute(
      "data-tone",
      "red",
    );
    expect(
      screen.getByTestId("deadline-row-PRESCRIPTION_TRACKER-late"),
    ).toHaveAttribute("data-tone", "red-solid");
    expect(
      screen.getByTestId("deadline-row-FILING_SCHEDULE-done"),
    ).toHaveAttribute("data-tone", "green");
  });

  it("prints the client-facing status label on each row badge, not the raw enum (633C D2)", () => {
    render(
      <Harness
        deadlines={[
          deadline({ id: "soon", status: "DUE_SOON", label: "Soon item" }),
        ]}
        now={new Date("2026-04-20T00:00:00")}
      />,
    );

    const row = screen.getByTestId("deadline-row-FILING_SCHEDULE-soon");
    expect(row.textContent).toContain("Due soon");
    expect(row.textContent).not.toContain("DUE SOON");
    expect(row.textContent).not.toContain("DUE_SOON");
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

describe("DeadlineList urgency on the South African calendar", () => {
  // Today in South Africa is Fri 27 Nov 2026 at each of these instants.
  const instants = [
    ["01:00 SAST", "2026-11-26T23:00:00Z"],
    ["11:40 SAST", "2026-11-27T09:40:00Z"],
    ["23:30 SAST", "2026-11-27T21:30:00Z"],
  ] as const;
  // A client's browser may run in any zone. The count must not depend on it.
  const zones = ["UTC", "Africa/Johannesburg", "America/New_York"];
  // Days from SA today → tone: overdue (< 0) red-solid, 0–7 red, 8–14 amber, else grey. The
  // threshold days are where an off-by-one count changes the colour. Statuses are what the
  // backend sends (PortalDeadlineStatus.derive: past OVERDUE, 0–7 DUE_SOON, else UPCOMING).
  const dates = [
    ["yesterday", "2026-11-26", "OVERDUE", "red-solid"],
    ["today", "2026-11-27", "DUE_SOON", "red"],
    ["tomorrow", "2026-11-28", "DUE_SOON", "red"],
    ["plus7", "2026-12-04", "DUE_SOON", "red"],
    ["plus8", "2026-12-05", "UPCOMING", "amber"],
    ["plus14", "2026-12-11", "UPCOMING", "amber"],
    ["plus15", "2026-12-12", "UPCOMING", "grey"],
  ] as const;

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllEnvs();
  });

  function tones(
    items: ReadonlyArray<readonly [string, string, string, string]>,
  ) {
    render(
      <Harness
        deadlines={items.map(([id, dueDate, status]) =>
          deadline({ id, dueDate, status: status as PortalDeadlineStatus }),
        )}
      />,
    );
    return items.map(([id, dueDate]) => [
      dueDate,
      screen
        .getByTestId(`deadline-row-FILING_SCHEDULE-${id}`)
        .getAttribute("data-tone"),
    ]);
  }

  describe.each(zones)("runtime zone %s", (zone) => {
    it.each(instants)(
      "at %s, yesterday is overdue, today and tomorrow are red, and the 7/14-day thresholds hold",
      (_label, instant) => {
        inZone(zone);
        vi.useFakeTimers({ toFake: ["Date"] });
        vi.setSystemTime(new Date(instant));
        expect(tones(dates)).toEqual(
          dates.map(([, dueDate, , tone]) => [dueDate, tone]),
        );
      },
    );
  });

  it("counts calendar days across a US clock change (New York, 14 Mar 2027)", () => {
    inZone("America/New_York");
    vi.useFakeTimers({ toFake: ["Date"] });
    // 11:40 SAST on Fri 5 Mar 2027; New York springs forward on Sun 14 Mar, so +15 days of
    // local midnights is one hour short of 15 × 24 h (+8 is before the change, a control).
    vi.setSystemTime(new Date("2027-03-05T09:40:00Z"));
    const acrossDst = [
      ["plus8", "2027-03-13", "UPCOMING", "amber"],
      ["plus15", "2027-03-20", "UPCOMING", "grey"],
    ] as const;
    expect(tones(acrossDst)).toEqual([
      ["2027-03-13", "amber"],
      ["2027-03-20", "grey"],
    ]);
  });
});
