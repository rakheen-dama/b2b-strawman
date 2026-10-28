import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { TodaysAgenda } from "@/components/dashboard/todays-agenda";
import { formatDiaryDay, type DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";
import type { MyWorkTimeEntryItem } from "@/lib/types";

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

function today(): string {
  return new Date().toLocaleDateString("en-CA");
}

function daysFromNow(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${dd}`;
}

function makeEvent(o: Partial<DiaryEventResponse> & { id: string }): DiaryEventResponse {
  return {
    kind: "TASK_DUE",
    date: today(),
    title: `Event ${o.id}`,
    status: "OPEN",
    matterId: "m1",
    matterName: "Ndlovu v RAF",
    overdue: false,
    href: `/matters/m1?taskId=${o.id}`,
    ...o,
  };
}

const emptyEntries: MyWorkTimeEntryItem[] = [];

describe("TodaysAgenda", () => {
  afterEach(() => {
    cleanup();
  });

  // Phase 87 613D: section 1 is "Today's diary", driven by GET /api/diary?from=today&to=today.
  it("renders today's diary rows with kind labels and links to the matter", () => {
    const events = [
      makeEvent({
        id: "cd1",
        kind: "COURT_DATE",
        title: "Pre-trial conference",
        status: "SCHEDULED",
        time: "09:30:00",
        href: "/matters/m1?tab=court-dates&courtDateId=cd1",
      }),
      makeEvent({ id: "t1", title: "File heads of argument" }),
    ];

    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={events}
        todayEntries={emptyEntries}
        upcomingEvents={[]}
      />
    );

    const section = screen.getByTestId("todays-diary");
    expect(within(section).getByText("Today's diary")).toBeInTheDocument();
    expect(within(section).getByText("Pre-trial conference")).toBeInTheDocument();
    expect(within(section).getByText("File heads of argument")).toBeInTheDocument();
    expect(within(section).getByText("Court date")).toBeInTheDocument();
    expect(within(section).getByText("Task due")).toBeInTheDocument();
    expect(screen.getByTestId("diary-event-cd1")).toHaveAttribute(
      "href",
      "/org/acme/matters/m1?tab=court-dates&courtDateId=cd1"
    );
    expect(within(section).getByRole("link", { name: "Open diary" })).toHaveAttribute(
      "href",
      "/org/acme/diary?view=day"
    );
    // Read-only: no row actions outside the Diary page.
    expect(screen.queryByTestId("diary-row-actions-cd1")).not.toBeInTheDocument();
  });

  it("shows the empty copy when only overdue rows came back", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[makeEvent({ id: "o1", date: daysFromNow(-3), overdue: true })]}
        todayEntries={emptyEntries}
        upcomingEvents={[]}
      />
    );

    expect(screen.getByText("Nothing in the diary today")).toBeInTheDocument();
    expect(screen.queryByText("Event o1")).not.toBeInTheDocument();
  });

  it("links the overdue band to the Diary's Day view", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[
          makeEvent({ id: "o1", date: daysFromNow(-3), overdue: true }),
          makeEvent({ id: "o2", date: daysFromNow(-1), overdue: true }),
          makeEvent({ id: "t1" }),
        ]}
        todayEntries={emptyEntries}
        upcomingEvents={[]}
      />
    );

    const overdue = screen.getByTestId("todays-diary-overdue");
    expect(overdue).toHaveTextContent("2 overdue");
    expect(overdue).toHaveAttribute("href", "/org/acme/diary?view=day");
  });

  it("shows no overdue link when nothing is overdue", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[makeEvent({ id: "t1" })]}
        todayEntries={emptyEntries}
        upcomingEvents={[]}
      />
    );

    expect(screen.queryByTestId("todays-diary-overdue")).not.toBeInTheDocument();
  });

  it("caps today's rows at five and links the rest to the Diary", () => {
    const events = Array.from({ length: 7 }, (_, i) => makeEvent({ id: `t${i + 1}` }));

    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={events}
        todayEntries={emptyEntries}
        upcomingEvents={[]}
      />
    );

    expect(screen.getByText("Event t5")).toBeInTheDocument();
    expect(screen.queryByText("Event t6")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "+2 more" })).toHaveAttribute(
      "href",
      "/org/acme/diary?view=day"
    );
  });

  it("fails soft when the diary could not be loaded", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={null}
        todayEntries={emptyEntries}
        upcomingEvents={[]}
      />
    );

    expect(screen.getByText("Today's diary could not be loaded.")).toBeInTheDocument();
    expect(screen.queryByText(/identify your member profile/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open diary" })).toBeInTheDocument();
    expect(screen.getByTestId("time-progress-today")).toBeInTheDocument();
    expect(screen.getByTestId("next-deadline")).toBeInTheDocument();
  });

  it("says the user couldn't be identified (not a load failure) and points to the Diary", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents="unidentified"
        todayEntries={emptyEntries}
        upcomingEvents={[]}
      />
    );

    const section = screen.getByTestId("todays-diary");
    expect(within(section).getByText(/Couldn't identify your member profile/)).toBeInTheDocument();
    expect(
      within(section).queryByText("Today's diary could not be loaded.")
    ).not.toBeInTheDocument();
    expect(within(section).queryByText("Nothing in the diary today")).not.toBeInTheDocument();
    expect(within(section).getByRole("link", { name: "Open the Diary" })).toHaveAttribute(
      "href",
      "/org/acme/diary"
    );
    expect(within(section).queryByTestId("todays-diary-overdue")).not.toBeInTheDocument();
  });

  it("renders time progress bar", () => {
    const entries: MyWorkTimeEntryItem[] = [
      {
        id: "e1",
        taskId: "t1",
        taskTitle: "Task 1",
        matterId: "p1",
        matterName: "Project 1",
        date: today(),
        durationMinutes: 135, // 2h 15m
        billable: true,
        description: null,
      },
    ];

    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[]}
        todayEntries={entries}
        upcomingEvents={[]}
        weeklyCapacityHours={40}
      />
    );

    const progressSection = screen.getByTestId("time-progress-today");
    expect(progressSection).toBeInTheDocument();
    expect(screen.getByText("2h 15m")).toBeInTheDocument();
  });

  // Phase 88 G-D5: Next deadline is the first row of the upcoming diary (tomorrow through
  // today + 14). Fixtures are in server order — date, then court dates first within a day
  // (DiaryService.ORDER) — and the agenda does not re-sort.
  it("renders the next deadline from the first upcoming row (court date first within its day)", () => {
    const upcoming = [
      makeEvent({
        id: "u-cd1",
        kind: "COURT_DATE",
        date: daysFromNow(3),
        time: "10:00:00",
        title: "Case Management Conference",
        status: "SCHEDULED",
        matterName: "Mthembu v RAF",
        href: "/matters/m2?tab=court-dates&courtDateId=u-cd1",
      }),
      makeEvent({ id: "u-t1", date: daysFromNow(3), title: "Prepare CMC bundle" }),
    ];

    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[]}
        todayEntries={emptyEntries}
        upcomingEvents={upcoming}
      />
    );

    const deadline = screen.getByTestId("next-deadline");
    expect(
      within(deadline).getByText(`Court date · ${formatDiaryDay(daysFromNow(3))}`)
    ).toBeInTheDocument();
    expect(
      within(deadline).getByRole("link", { name: "Case Management Conference" })
    ).toHaveAttribute("href", "/org/acme/matters/m2?tab=court-dates&courtDateId=u-cd1");
    expect(within(deadline).getByText("Mthembu v RAF")).toBeInTheDocument();
    expect(within(deadline).getByText("3d remaining")).toBeInTheDocument();
    expect(within(deadline).queryByText("Prepare CMC bundle")).not.toBeInTheDocument();
  });

  it("lists upcoming rows with dates in server order, capped at five, then '+n more'", () => {
    const upcoming = [
      makeEvent({
        id: "u-cd1",
        kind: "COURT_DATE",
        date: daysFromNow(2),
        time: "10:00:00",
        title: "Case Management Conference",
        status: "SCHEDULED",
      }),
      ...Array.from({ length: 6 }, (_, i) =>
        makeEvent({ id: `u-t${i + 1}`, date: daysFromNow(2 + i), title: `Upcoming task ${i + 1}` })
      ),
    ];

    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[]}
        todayEntries={emptyEntries}
        upcomingEvents={upcoming}
      />
    );

    const section = screen.getByTestId("upcoming-diary");
    expect(within(section).getByText("Upcoming · next 14 days")).toBeInTheDocument();
    const rows = within(section).getAllByRole("listitem");
    expect(rows).toHaveLength(5);
    expect(rows[0]).toHaveTextContent("Case Management Conference");
    expect(rows[1]).toHaveTextContent("Upcoming task 1");
    // Rows span days, so each shows its date.
    expect(within(rows[0]).getByText(formatDiaryDay(daysFromNow(2)))).toBeInTheDocument();
    expect(within(section).getByText("Upcoming task 4")).toBeInTheDocument();
    expect(within(section).queryByText("Upcoming task 5")).not.toBeInTheDocument();
    // "+n more" opens the List view at the first hidden row (Upcoming task 5); the header link
    // opens it at tomorrow, where the window starts.
    expect(within(section).getByRole("link", { name: "+2 more" })).toHaveAttribute(
      "href",
      `/org/acme/diary?view=list&from=${daysFromNow(6)}&feeEarner=mine`
    );
    expect(within(section).getByRole("link", { name: "Open diary list" })).toHaveAttribute(
      "href",
      `/org/acme/diary?view=list&from=${daysFromNow(1)}&feeEarner=mine`
    );
  });

  // The Diary's List view shows one calendar month, so "+n more" must anchor on the month that
  // holds the first hidden row — here November, while the window starts in October.
  it("anchors '+n more' on the first hidden row's month when the window crosses a month end", () => {
    const dates = [
      "2026-10-29",
      "2026-10-30",
      "2026-10-31",
      "2026-11-01",
      "2026-11-02",
      "2026-11-03",
      "2026-11-05",
    ];
    const upcoming = dates.map((date, i) =>
      makeEvent({ id: `m-t${i + 1}`, date, title: `Month task ${i + 1}` })
    );

    render(
      <TodaysAgenda
        slug="acme"
        today="2026-10-28"
        diaryEvents={[]}
        todayEntries={emptyEntries}
        upcomingEvents={upcoming}
      />
    );

    const section = screen.getByTestId("upcoming-diary");
    expect(within(section).getByRole("link", { name: "+2 more" })).toHaveAttribute(
      "href",
      "/org/acme/diary?view=list&from=2026-11-03&feeEarner=mine"
    );
    expect(within(section).getByRole("link", { name: "Open diary list" })).toHaveAttribute(
      "href",
      "/org/acme/diary?view=list&from=2026-10-29&feeEarner=mine"
    );
  });

  it("shows the empty upcoming copy and no next deadline when nothing is upcoming", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[]}
        todayEntries={emptyEntries}
        upcomingEvents={[]}
      />
    );

    expect(
      within(screen.getByTestId("upcoming-diary")).getByText(
        "Nothing in the diary for the next 14 days"
      )
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId("next-deadline")).getByText("No upcoming deadlines")
    ).toBeInTheDocument();
  });

  it("says the upcoming diary could not be loaded when the load failed", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[]}
        todayEntries={emptyEntries}
        upcomingEvents={null}
      />
    );

    expect(
      within(screen.getByTestId("next-deadline")).getByText(
        "The upcoming diary could not be loaded."
      )
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId("next-deadline")).queryByText("No upcoming deadlines")
    ).not.toBeInTheDocument();
    expect(
      within(screen.getByTestId("upcoming-diary")).getByText(
        "The upcoming diary could not be loaded."
      )
    ).toBeInTheDocument();
  });

  it("says the member couldn't be identified in Next deadline and Upcoming alike", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents="unidentified"
        todayEntries={emptyEntries}
        upcomingEvents="unidentified"
      />
    );

    const deadline = screen.getByTestId("next-deadline");
    expect(
      within(deadline).getByText("Couldn't identify your member profile.")
    ).toBeInTheDocument();
    expect(within(deadline).queryByText("No upcoming deadlines")).not.toBeInTheDocument();
    const upcoming = screen.getByTestId("upcoming-diary");
    expect(
      within(upcoming).getByText("Couldn't identify your member profile.")
    ).toBeInTheDocument();
    expect(
      within(upcoming).queryByText("Nothing in the diary for the next 14 days")
    ).not.toBeInTheDocument();
    // A "mine" link would open every fee earner's diary when the member can't be resolved.
    expect(
      within(upcoming).queryByRole("link", { name: "Open diary list" })
    ).not.toBeInTheDocument();
  });

  it("handles an empty diary without crashing", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[]}
        todayEntries={emptyEntries}
        upcomingEvents={[]}
      />
    );

    expect(screen.getByTestId("todays-diary")).toBeInTheDocument();
    expect(screen.getByText("Nothing in the diary today")).toBeInTheDocument();
    expect(screen.getByTestId("next-deadline")).toBeInTheDocument();
    expect(screen.getByText("No upcoming deadlines")).toBeInTheDocument();
  });
});
