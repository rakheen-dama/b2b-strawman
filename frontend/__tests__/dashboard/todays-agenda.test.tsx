import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { TodaysAgenda } from "@/components/dashboard/todays-agenda";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";
import type { MyWorkTimeEntryItem } from "@/lib/types";
import type { PersonalDeadline } from "@/lib/dashboard-types";

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
const emptyDeadlines: PersonalDeadline[] = [];

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
        upcomingDeadlines={emptyDeadlines}
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
        upcomingDeadlines={emptyDeadlines}
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
        upcomingDeadlines={emptyDeadlines}
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
        upcomingDeadlines={emptyDeadlines}
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
        upcomingDeadlines={emptyDeadlines}
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
        upcomingDeadlines={emptyDeadlines}
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
        upcomingDeadlines={emptyDeadlines}
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
        upcomingDeadlines={emptyDeadlines}
        weeklyCapacityHours={40}
      />
    );

    const progressSection = screen.getByTestId("time-progress-today");
    expect(progressSection).toBeInTheDocument();
    expect(screen.getByText("2h 15m")).toBeInTheDocument();
  });

  it("renders next deadline", () => {
    const deadlines: PersonalDeadline[] = [
      {
        taskId: "t1",
        taskName: "Ship feature",
        matterName: "Alpha Project",
        dueDate: daysFromNow(5),
      },
    ];

    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[]}
        todayEntries={emptyEntries}
        upcomingDeadlines={deadlines}
      />
    );

    const deadlineSection = screen.getByTestId("next-deadline");
    expect(deadlineSection).toBeInTheDocument();
    expect(screen.getByText("Ship feature")).toBeInTheDocument();
    expect(screen.getByText("Alpha Project")).toBeInTheDocument();
    expect(screen.getByText("5d remaining")).toBeInTheDocument();
  });

  it("handles an empty diary without crashing", () => {
    render(
      <TodaysAgenda
        slug="acme"
        today={today()}
        diaryEvents={[]}
        todayEntries={emptyEntries}
        upcomingDeadlines={emptyDeadlines}
      />
    );

    expect(screen.getByTestId("todays-diary")).toBeInTheDocument();
    expect(screen.getByText("Nothing in the diary today")).toBeInTheDocument();
    expect(screen.getByTestId("next-deadline")).toBeInTheDocument();
    expect(screen.getByText("No upcoming deadlines")).toBeInTheDocument();
  });
});
