import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

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

import { DiaryListSkeleton, DiaryListView } from "@/components/diary/diary-list-view";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

afterEach(() => cleanup());

function ev(overrides: Partial<DiaryEventResponse> = {}): DiaryEventResponse {
  return {
    id: "t-1",
    kind: "TASK_DUE",
    date: "2026-10-21",
    title: "Consult with client",
    status: "OPEN",
    matterId: "m-1",
    matterName: "Mokoena Divorce",
    overdue: false,
    href: "/matters/m-1?taskId=t-1",
    ...overrides,
  };
}

const band = [
  ev({ id: "old-1", date: "2026-10-02", title: "Lodge discovery affidavit", overdue: true }),
];
const inWindow = [
  ev({
    id: "cd-1",
    kind: "COURT_DATE",
    date: "2026-10-20",
    time: "10:00:00",
    title: "Pretoria Magistrates Court — Hearing",
    subtype: "HEARING",
    status: "SCHEDULED",
    matterReference: "FAM-014",
    feeEarnerId: "fe-1",
    feeEarnerName: "Sipho Mahlangu",
    href: "/matters/m-1?tab=court-dates&courtDateId=cd-1",
  }),
  // Same date, server order: court date first, then a task with a title that sorts earlier.
  ev({ id: "t-2", date: "2026-10-20", title: "Agree trial bundle" }),
  ev({ id: "t-3", date: "2026-10-22", title: "Brief counsel" }),
];

describe("DiaryListView", () => {
  it("renders four column headers and an sr-only caption", () => {
    const { container } = render(<DiaryListView events={inWindow} band={band} slug="acme" />);
    const cols = container.querySelectorAll('th[scope="col"]');
    expect([...cols].map((th) => th.textContent)).toEqual([
      "Time",
      "Event",
      "Matter",
      "Fee earner",
    ]);
    expect(screen.getByText("Diary events")).toHaveClass("sr-only");
  });

  it("puts the Overdue band first, then date groups in ascending order", () => {
    const { container } = render(<DiaryListView events={inWindow} band={band} slug="acme" />);
    const groupHeads = [...container.querySelectorAll('th[scope="rowgroup"]')].map(
      (th) => th.textContent
    );
    expect(groupHeads).toEqual(["Overdue", "Tuesday 20 October 2026", "Thursday 22 October 2026"]);
    const overdue = screen.getByTestId("diary-list-group-overdue");
    expect(within(overdue).getByText("Lodge discovery affidavit")).toBeInTheDocument();
    expect(within(overdue).getByText("Fri 2 Oct")).toBeInTheDocument();
    expect(within(overdue).getByText("Overdue", { selector: "[data-slot=badge]" })).toBeTruthy();
  });

  it("preserves backend order within a date", () => {
    render(<DiaryListView events={inWindow} band={[]} slug="acme" />);
    const group = screen.getByTestId("diary-list-group-2026-10-20");
    const ids = [...group.querySelectorAll("[data-testid^='diary-list-row-']")].map((r) =>
      r.getAttribute("data-testid")
    );
    expect(ids).toEqual(["diary-list-row-cd-1", "diary-list-row-t-2"]);
  });

  it("omits the Overdue group when the band is empty", () => {
    render(<DiaryListView events={inWindow} band={[]} slug="acme" />);
    expect(screen.queryByTestId("diary-list-group-overdue")).toBeNull();
  });

  it("renders kind encoding, time, matter and fee earner per row", () => {
    render(<DiaryListView events={inWindow} band={[]} slug="acme" />);
    const row = screen.getByTestId("diary-list-row-cd-1");
    expect(row).toHaveAttribute("data-kind", "COURT_DATE");
    expect(row.querySelector("td")!.className).toContain("border-l-teal-600");
    expect(within(row).getByText("10:00")).toHaveClass("font-mono");
    expect(within(row).getByText("Court date")).toHaveClass("sr-only");
    expect(within(row).getByText("Hearing")).toBeInTheDocument();
    expect(within(row).getByText("FAM-014")).toBeInTheDocument();
    expect(within(row).getByText("Sipho Mahlangu")).toBeInTheDocument();
  });

  it("renders a row without a time or fee earner", () => {
    render(<DiaryListView events={inWindow} band={[]} slug="acme" />);
    const row = screen.getByTestId("diary-list-row-t-3");
    expect(within(row).getByText("Brief counsel")).toBeInTheDocument();
    expect(within(row).queryByText(/\d{2}:\d{2}/)).toBeNull();
    expect(within(row).getByText("Task due")).toHaveClass("sr-only");
  });

  it("links each row to the backend href under the org", () => {
    render(<DiaryListView events={inWindow} band={band} slug="acme" />);
    expect(screen.getByTestId("diary-event-cd-1")).toHaveAttribute(
      "href",
      "/org/acme/matters/m-1?tab=court-dates&courtDateId=cd-1"
    );
    expect(screen.getByTestId("diary-event-old-1")).toHaveAttribute(
      "href",
      "/org/acme/matters/m-1?taskId=t-1"
    );
  });
});

describe("DiaryListSkeleton", () => {
  it("renders the four-column shape with six placeholder rows", () => {
    const { container } = render(<DiaryListSkeleton />);
    expect(container.querySelectorAll('th[scope="col"]')).toHaveLength(4);
    expect(container.querySelectorAll("tbody tr")).toHaveLength(6);
  });
});
