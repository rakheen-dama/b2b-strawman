import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

// Phase 87 613C — Day view: one date grouped by kind (court dates first), semantic sections with
// visible kind headings, the Overdue strip above, and its skeleton.

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

import { DiaryDaySkeleton, DiaryDayView } from "@/components/diary/diary-day-view";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

afterEach(() => cleanup());

function ev(overrides: Partial<DiaryEventResponse> = {}): DiaryEventResponse {
  return {
    id: "t-1",
    kind: "TASK_DUE",
    date: "2026-10-21",
    title: "Draft particulars of claim",
    status: "OPEN",
    matterId: "m-1",
    matterName: "Naidoo v Metro Municipality",
    overdue: false,
    href: "/matters/m-1?taskId=t-1",
    ...overrides,
  };
}

// Server order: tasks first here on purpose — the Day view groups by kind, not by input order.
const EVENTS: DiaryEventResponse[] = [
  ev({ id: "t-1" }),
  ev({ id: "t-2", title: "Consult with expert witness" }),
  ev({ id: "md-1", kind: "MATTER_DUE", title: "Naidoo v Metro Municipality", status: "ACTIVE" }),
  ev({
    id: "cd-1",
    kind: "COURT_DATE",
    time: "10:00:00",
    title: "Durban Magistrates Court — Hearing",
    status: "SCHEDULED",
  }),
];

describe("DiaryDayView", () => {
  it("groups the day by kind with court dates first and empty kinds omitted", () => {
    render(<DiaryDayView events={EVENTS} band={[]} slug="acme" />);
    const sections = screen.getAllByRole("region");
    expect(sections.map((s) => s.getAttribute("data-testid"))).toEqual([
      "diary-day-group-COURT_DATE",
      "diary-day-group-MATTER_DUE",
      "diary-day-group-TASK_DUE",
    ]);
    expect(screen.queryByTestId("diary-day-group-PRESCRIPTION")).toBeNull();
  });

  it("labels each section with a visible kind heading, aria-hidden icon and count", () => {
    render(<DiaryDayView events={EVENTS} band={[]} slug="acme" />);
    const tasks = screen.getByRole("region", { name: /Tasks/ });
    const heading = within(tasks).getByRole("heading", { level: 2 });
    expect(heading).toHaveTextContent("Tasks2");
    expect(heading.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByRole("heading", { name: /Court dates/ })).toBeInTheDocument();
  });

  it("lists rows in server order within a kind", () => {
    render(<DiaryDayView events={EVENTS} band={[]} slug="acme" />);
    const tasks = screen.getByTestId("diary-day-group-TASK_DUE");
    const items = within(tasks).getAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText("Draft particulars of claim")).toBeInTheDocument();
    expect(within(items[1]).getByText("Consult with expert witness")).toBeInTheDocument();
  });

  it("keeps the kind encoding on each row", () => {
    render(<DiaryDayView events={EVENTS} band={[]} slug="acme" />);
    const court = screen.getByTestId("diary-event-cd-1");
    expect(court.className).toContain("border-l-teal-600");
    expect(within(court).getByText("Court date")).toHaveClass("sr-only");
    expect(within(court).getByText("10:00")).toHaveClass("font-mono");
  });

  it("renders the overdue band in the strip above, not in a kind group", () => {
    const old = ev({ id: "old-1", date: "2026-10-02", title: "Serve notice", overdue: true });
    render(<DiaryDayView events={EVENTS} band={[old]} slug="acme" />);
    const strip = screen.getByRole("region", { name: "Overdue" });
    expect(within(strip).getByTestId("diary-event-old-1")).toBeInTheDocument();
    expect(
      within(screen.getByTestId("diary-day-group-TASK_DUE")).queryByText("Serve notice")
    ).toBeNull();
  });

  it("says so when only overdue items remain for the day", () => {
    const old = ev({ id: "old-1", date: "2026-10-02", title: "Serve notice", overdue: true });
    render(<DiaryDayView events={[]} band={[old]} slug="acme" />);
    expect(screen.getByText("Nothing is due on this day.")).toBeInTheDocument();
  });

  it("is not a grid (a single day has nothing to arrow between)", () => {
    render(<DiaryDayView events={EVENTS} band={[]} slug="acme" />);
    expect(screen.queryByRole("grid")).toBeNull();
  });
});

describe("DiaryDaySkeleton", () => {
  it("renders a busy placeholder", () => {
    render(<DiaryDaySkeleton />);
    expect(screen.getByTestId("diary-day-skeleton")).toHaveAttribute("aria-busy", "true");
  });
});
