import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 87 613C — Month view: Mon–Sun grid for the calendar month, three chips + "+n", kind and
// overdue never colour-only, click / Enter opens the day, roving tabindex over in-month days.

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { DiaryMonthSkeleton, DiaryMonthView } from "@/components/diary/diary-month-view";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

afterEach(() => cleanup());

function ev(overrides: Partial<DiaryEventResponse> = {}): DiaryEventResponse {
  return {
    id: "t-1",
    kind: "TASK_DUE",
    date: "2026-10-21",
    title: "Prepare trial bundle",
    status: "OPEN",
    matterId: "m-1",
    matterName: "Botha v Botha",
    overdue: false,
    href: "/matters/m-1?taskId=t-1",
    ...overrides,
  };
}

const BUSY_DAY: DiaryEventResponse[] = [
  ev({
    id: "cd-1",
    kind: "COURT_DATE",
    time: "09:30:00",
    title: "Cape Town High Court — Trial",
    status: "SCHEDULED",
  }),
  ev({ id: "p-1", kind: "PRESCRIPTION", title: "Prescription — Delict (3 years)" }),
  ev({ id: "t-1" }),
  ev({ id: "t-2", title: "Brief advocate" }),
  ev({ id: "t-3", title: "Confirm witness availability" }),
];

function renderMonth(
  events: DiaryEventResponse[] = BUSY_DAY,
  band: DiaryEventResponse[] = [],
  today = "2026-10-21"
) {
  const onSelectDay = vi.fn();
  const utils = render(
    <DiaryMonthView
      events={events}
      band={band}
      from="2026-10-01"
      today={today}
      slug="acme"
      onSelectDay={onSelectDay}
    />
  );
  return { ...utils, onSelectDay };
}

describe("DiaryMonthView", () => {
  it("lays October 2026 out as a labelled grid of five Mon–Sun weeks", () => {
    renderMonth([]);
    const grid = screen.getByRole("grid", { name: "October 2026" });
    // Weekday header + five week rows.
    expect(within(grid).getAllByRole("row")).toHaveLength(6);
    const headers = within(grid).getAllByRole("columnheader");
    expect(headers).toHaveLength(7);
    expect(headers[0]).toHaveTextContent("Monday");
    expect(headers[6]).toHaveTextContent("Sunday");
    expect(within(grid).getAllByRole("gridcell")).toHaveLength(35);
    expect(screen.getAllByTestId(/^diary-month-day-/)).toHaveLength(31);
  });

  it("leaves days outside the month empty, disabled and out of the tab order", () => {
    renderMonth([]);
    const blanks = screen.getAllByTestId("diary-month-blank");
    // Mon 28 – Wed 30 Sep, and Sun 1 Nov.
    expect(blanks).toHaveLength(4);
    for (const blank of blanks) {
      expect(blank).toHaveAttribute("aria-disabled", "true");
      expect(blank).not.toHaveAttribute("tabindex");
      expect(blank).toBeEmptyDOMElement();
    }
    const firstRow = screen.getAllByRole("row")[1];
    expect(within(firstRow).getAllByRole("gridcell")[3]).toBe(
      screen.getByTestId("diary-month-day-2026-10-01")
    );
  });

  it("shows three chips and +n for a busy day, in server order", () => {
    renderMonth();
    const day = screen.getByTestId("diary-month-day-2026-10-21");
    const chips = within(day).getAllByTestId(/^diary-month-chip-/);
    expect(chips.map((c) => c.getAttribute("data-testid"))).toEqual([
      "diary-month-chip-cd-1",
      "diary-month-chip-p-1",
      "diary-month-chip-t-1",
    ]);
    const more = within(day).getByTestId("diary-month-more-2026-10-21");
    expect(more).toHaveTextContent("+2 more events");
    expect(within(more).getByText("more events")).toHaveClass("sr-only");
  });

  it("encodes kind with a rail, an aria-hidden icon and sr-only text on each chip", () => {
    renderMonth();
    const court = screen.getByTestId("diary-month-chip-cd-1");
    expect(court).toHaveAttribute("data-kind", "COURT_DATE");
    expect(court.className).toContain("border-l-teal-600");
    expect(within(court).getByText("Court date")).toHaveClass("sr-only");
    expect(court.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(within(court).getByText("09:30")).toHaveClass("font-mono");
    expect(
      within(screen.getByTestId("diary-month-chip-p-1")).getByText("Prescription")
    ).toHaveClass("sr-only");
  });

  it("marks overdue chips with an icon and sr-only text, not colour alone", () => {
    renderMonth([
      ev({ id: "t-9", date: "2026-10-05", title: "File replying affidavit", overdue: true }),
    ]);
    const chip = screen.getByTestId("diary-month-chip-t-9");
    expect(within(chip).getByText("Overdue")).toHaveClass("sr-only");
    expect(chip.querySelectorAll('svg[aria-hidden="true"]')).toHaveLength(2);
  });

  it("chips are not links (the whole day is the target)", () => {
    renderMonth();
    expect(within(screen.getByTestId("diary-month-grid")).queryAllByRole("link")).toHaveLength(0);
  });

  it("opens the Day view when a day is clicked", async () => {
    const user = userEvent.setup();
    const { onSelectDay } = renderMonth();
    await user.click(screen.getByTestId("diary-month-chip-t-1"));
    expect(onSelectDay).toHaveBeenCalledWith("2026-10-21");
    await user.click(screen.getByTestId("diary-month-day-2026-10-08"));
    expect(onSelectDay).toHaveBeenLastCalledWith("2026-10-08");
  });

  it("marks today and gives every day an accessible long date", () => {
    renderMonth([]);
    const today = screen.getByTestId("diary-month-day-2026-10-21");
    expect(today).toHaveAttribute("aria-current", "date");
    expect(within(today).getByText("Wednesday 21 October 2026")).toHaveClass("sr-only");
    expect(screen.getByTestId("diary-month-day-2026-10-20")).not.toHaveAttribute("aria-current");
  });

  it("has one tab stop (today) and moves it with the arrow keys", async () => {
    const user = userEvent.setup();
    renderMonth([]);
    const cells = screen.getAllByTestId(/^diary-month-day-/);
    expect(cells.filter((c) => c.getAttribute("tabindex") === "0")).toEqual([
      screen.getByTestId("diary-month-day-2026-10-21"),
    ]);
    screen.getByTestId("diary-month-day-2026-10-21").focus();

    await user.keyboard("{ArrowRight}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-month-day-2026-10-22"));
    expect(screen.getByTestId("diary-month-day-2026-10-22")).toHaveAttribute("tabindex", "0");
    expect(screen.getByTestId("diary-month-day-2026-10-21")).toHaveAttribute("tabindex", "-1");

    await user.keyboard("{ArrowDown}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-month-day-2026-10-29"));
    await user.keyboard("{ArrowUp}{ArrowUp}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-month-day-2026-10-15"));
    await user.keyboard("{ArrowLeft}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-month-day-2026-10-14"));
    await user.keyboard("{Home}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-month-day-2026-10-12"));
    await user.keyboard("{End}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-month-day-2026-10-18"));
  });

  it("ignores moves onto days outside the month", async () => {
    const user = userEvent.setup();
    renderMonth([], [], "2026-10-01");
    const first = screen.getByTestId("diary-month-day-2026-10-01");
    first.focus();
    await user.keyboard("{ArrowLeft}");
    expect(document.activeElement).toBe(first);
    await user.keyboard("{Home}");
    expect(document.activeElement).toBe(first);
    await user.keyboard("{ArrowUp}");
    expect(document.activeElement).toBe(first);
  });

  it("falls back to the 1st as the tab stop when today is in another month", () => {
    renderMonth([], [], "2026-12-03");
    expect(screen.getByTestId("diary-month-day-2026-10-01")).toHaveAttribute("tabindex", "0");
  });

  it("opens the focused day with Enter or Space", async () => {
    const user = userEvent.setup();
    const { onSelectDay } = renderMonth([]);
    screen.getByTestId("diary-month-day-2026-10-21").focus();
    await user.keyboard("{Enter}");
    expect(onSelectDay).toHaveBeenCalledWith("2026-10-21");
    await user.keyboard("{ArrowRight} ");
    expect(onSelectDay).toHaveBeenLastCalledWith("2026-10-22");
  });

  it("shows a visible focus ring on day cells", () => {
    renderMonth([]);
    const cell = screen.getByTestId("diary-month-day-2026-10-21");
    expect(cell.className).toContain("focus-visible:ring-2");
    expect(cell.className).toContain("outline-none");
  });

  it("renders the overdue band in the strip above the grid", () => {
    const old = ev({ id: "old-1", date: "2026-09-15", title: "Pay sheriff fees", overdue: true });
    renderMonth([], [old]);
    const strip = screen.getByRole("region", { name: "Overdue" });
    expect(within(strip).getByText("Pay sheriff fees")).toBeInTheDocument();
    expect(
      within(screen.getByTestId("diary-month-grid")).queryByText("Pay sheriff fees")
    ).toBeNull();
  });
});

describe("DiaryMonthSkeleton", () => {
  it("renders five weeks of placeholder days", () => {
    render(<DiaryMonthSkeleton />);
    expect(screen.getByTestId("diary-month-skeleton")).toHaveAttribute("aria-busy", "true");
    expect(screen.getAllByTestId("diary-month-skeleton-day")).toHaveLength(35);
  });
});
