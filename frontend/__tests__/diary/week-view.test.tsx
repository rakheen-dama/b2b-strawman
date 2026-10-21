import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

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

import { DiaryWeekSkeleton, DiaryWeekView } from "@/components/diary/diary-week-view";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

afterEach(() => cleanup());

function ev(overrides: Partial<DiaryEventResponse> = {}): DiaryEventResponse {
  return {
    id: "t-1",
    kind: "TASK_DUE",
    date: "2026-10-21",
    title: "Draft heads of argument",
    status: "OPEN",
    matterId: "m-1",
    matterName: "Dlamini v Road Accident Fund",
    overdue: false,
    href: "/matters/m-1?taskId=t-1",
    ...overrides,
  };
}

const court = ev({
  id: "cd-1",
  kind: "COURT_DATE",
  date: "2026-10-21",
  time: "09:30:00",
  title: "Johannesburg High Court — Trial",
  subtype: "PRE_TRIAL",
  status: "SCHEDULED",
  feeEarnerId: "fe-1",
  feeEarnerName: "Thandi Nkosi",
  matterReference: "LIT-001",
  href: "/matters/m-1?tab=court-dates&courtDateId=cd-1",
});
const prescription = ev({
  id: "p-1",
  kind: "PRESCRIPTION",
  date: "2026-10-23",
  title: "Prescription — General (3 years)",
  status: "RUNNING",
  href: "/matters/m-1?tab=court-dates&prescriptionId=p-1",
});
const matterDue = ev({
  id: "md-1",
  kind: "MATTER_DUE",
  date: "2026-10-24",
  title: "Zulu Estate",
  status: "ACTIVE",
  matterId: "m-2",
  matterName: "Zulu Estate",
  href: "/matters/m-2",
});
const task = ev({ id: "t-1", date: "2026-10-20", overdue: true });
const bandEvent = ev({
  id: "old-1",
  date: "2026-10-05",
  title: "Serve summons on defendant",
  overdue: true,
  href: "/matters/m-1?taskId=old-1",
});

function renderWeek(
  events = [task, court, prescription, matterDue],
  band: DiaryEventResponse[] = []
) {
  return render(
    <DiaryWeekView events={events} band={band} from="2026-10-19" today="2026-10-21" slug="acme" />
  );
}

describe("DiaryWeekView", () => {
  it("renders seven day cells Monday to Sunday", () => {
    renderWeek([]);
    for (const day of ["19", "20", "21", "22", "23", "24", "25"]) {
      expect(screen.getByTestId(`diary-day-2026-10-${day}`)).toBeInTheDocument();
    }
    expect(within(screen.getByTestId("diary-day-2026-10-19")).getByText("Mon 19 Oct")).toBeTruthy();
    expect(within(screen.getByTestId("diary-day-2026-10-25")).getByText("Sun 25 Oct")).toBeTruthy();
  });

  it("places each event in its date's cell and marks today", () => {
    renderWeek();
    const wed = screen.getByTestId("diary-day-2026-10-21");
    expect(within(wed).getByText("Johannesburg High Court — Trial")).toBeInTheDocument();
    expect(wed).toHaveAttribute("aria-current", "date");
    expect(screen.getByTestId("diary-day-2026-10-20")).not.toHaveAttribute("aria-current");
    expect(
      within(screen.getByTestId("diary-day-2026-10-23")).getByText(
        "Prescription — General (3 years)"
      )
    ).toBeInTheDocument();
  });

  it("encodes each kind with a rail, an icon and visually hidden kind text", () => {
    renderWeek();
    const courtRow = screen.getByTestId("diary-event-cd-1");
    expect(courtRow).toHaveAttribute("data-kind", "COURT_DATE");
    expect(courtRow.className).toContain("border-l-teal-600");
    expect(within(courtRow).getByText("Court date")).toHaveClass("sr-only");
    expect(courtRow.querySelector("svg")).toHaveAttribute("aria-hidden", "true");

    const prescriptionRow = screen.getByTestId("diary-event-p-1");
    expect(prescriptionRow.className).toContain("border-l-destructive");
    expect(within(prescriptionRow).getByText("Prescription")).toHaveClass("sr-only");

    const matterRow = screen.getByTestId("diary-event-md-1");
    expect(matterRow.className).toContain("border-dashed");
    expect(matterRow.className).toContain("border-l-slate-400");
    expect(within(matterRow).getByText("Matter due")).toHaveClass("sr-only");

    const taskRow = screen.getByTestId("diary-event-t-1");
    expect(taskRow.className).not.toContain("border-l-teal-600");
    expect(taskRow.className).not.toContain("border-l-destructive");
    expect(taskRow.className).not.toContain("border-dashed");
    expect(within(taskRow).getByText("Task due")).toHaveClass("sr-only");
  });

  it("renders the time in mono only when the event has one", () => {
    renderWeek();
    const time = within(screen.getByTestId("diary-event-cd-1")).getByText("09:30");
    expect(time).toHaveClass("font-mono");
    expect(within(screen.getByTestId("diary-event-p-1")).queryByText(/\d{2}:\d{2}/)).toBeNull();
  });

  it("shows court subtype, matter reference and fee earner on the secondary line", () => {
    renderWeek();
    const courtRow = screen.getByTestId("diary-event-cd-1");
    expect(within(courtRow).getByText("Pre-trial")).toBeInTheDocument();
    expect(within(courtRow).getByText("LIT-001")).toBeInTheDocument();
    expect(within(courtRow).getByText("Thandi Nkosi")).toBeInTheDocument();
  });

  it("badges overdue rows", () => {
    renderWeek();
    expect(within(screen.getByTestId("diary-event-t-1")).getByText("Overdue")).toBeInTheDocument();
    expect(within(screen.getByTestId("diary-event-cd-1")).queryByText("Overdue")).toBeNull();
  });

  it("links rows to the backend href under the org", () => {
    renderWeek();
    expect(screen.getByTestId("diary-event-cd-1")).toHaveAttribute(
      "href",
      "/org/acme/matters/m-1?tab=court-dates&courtDateId=cd-1"
    );
    expect(screen.getByTestId("diary-event-md-1")).toHaveAttribute("href", "/org/acme/matters/m-2");
  });

  it("renders the overdue band in its own strip, not in a day cell", () => {
    renderWeek([court], [bandEvent]);
    const strip = screen.getByRole("region", { name: "Overdue" });
    const bandRow = within(strip).getByTestId("diary-event-old-1");
    expect(within(bandRow).getByText("Mon 5 Oct")).toBeInTheDocument();
    for (const day of ["19", "20", "21", "22", "23", "24", "25"]) {
      expect(
        within(screen.getByTestId(`diary-day-2026-10-${day}`)).queryByText(
          "Serve summons on defendant"
        )
      ).toBeNull();
    }
  });

  it("omits the overdue strip when the band is empty", () => {
    renderWeek();
    expect(screen.queryByRole("region", { name: "Overdue" })).toBeNull();
  });
});

describe("DiaryWeekView grid (613C a11y)", () => {
  it("is a one-row grid of seven day cells labelled by their headings", () => {
    renderWeek([]);
    const grid = screen.getByRole("grid", { name: "Week of 19 October 2026" });
    expect(within(grid).getAllByRole("row")).toHaveLength(1);
    const cells = within(grid).getAllByRole("gridcell");
    expect(cells).toHaveLength(7);
    expect(screen.getByRole("gridcell", { name: "Wed 21 Oct" })).toBe(
      screen.getByTestId("diary-day-2026-10-21")
    );
  });

  it("has a single tab stop on today and roves it with the arrow keys, Home and End", async () => {
    const user = userEvent.setup();
    renderWeek([]);
    const cells = screen.getAllByRole("gridcell");
    expect(cells.filter((c) => c.getAttribute("tabindex") === "0")).toHaveLength(1);
    const wed = screen.getByTestId("diary-day-2026-10-21");
    expect(wed).toHaveAttribute("tabindex", "0");

    wed.focus();
    await user.keyboard("{ArrowRight}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-day-2026-10-22"));
    expect(screen.getByTestId("diary-day-2026-10-22")).toHaveAttribute("tabindex", "0");
    expect(wed).toHaveAttribute("tabindex", "-1");
    await user.keyboard("{End}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-day-2026-10-25"));
    await user.keyboard("{ArrowRight}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-day-2026-10-25"));
    await user.keyboard("{Home}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-day-2026-10-19"));
    await user.keyboard("{ArrowLeft}{ArrowDown}");
    expect(document.activeElement).toBe(screen.getByTestId("diary-day-2026-10-19"));
  });

  it("starts the tab stop on Monday when today is outside the week", () => {
    render(
      <DiaryWeekView events={[]} band={[]} from="2026-10-26" today="2026-10-21" slug="acme" />
    );
    expect(screen.getByTestId("diary-day-2026-10-26")).toHaveAttribute("tabindex", "0");
  });

  it("opens the focused day with Enter", async () => {
    const user = userEvent.setup();
    const onSelectDay = vi.fn();
    render(
      <DiaryWeekView
        events={[]}
        band={[]}
        from="2026-10-19"
        today="2026-10-21"
        slug="acme"
        onSelectDay={onSelectDay}
      />
    );
    screen.getByTestId("diary-day-2026-10-21").focus();
    await user.keyboard("{ArrowLeft}{Enter}");
    expect(onSelectDay).toHaveBeenCalledWith("2026-10-20");
  });

  it("keeps event links in the native tab order and leaves their keys alone", async () => {
    const user = userEvent.setup();
    renderWeek();
    const link = screen.getByTestId("diary-event-cd-1");
    expect(link).not.toHaveAttribute("tabindex");
    link.focus();
    await user.keyboard("{ArrowRight}");
    expect(document.activeElement).toBe(link);
  });

  it("shows a visible focus ring on day cells", () => {
    renderWeek([]);
    expect(screen.getByTestId("diary-day-2026-10-21").className).toContain("focus-visible:ring-2");
  });
});

describe("DiaryWeekSkeleton", () => {
  it("renders seven placeholder day cells", () => {
    render(<DiaryWeekSkeleton />);
    expect(screen.getAllByTestId("diary-week-skeleton-day")).toHaveLength(7);
  });
});
