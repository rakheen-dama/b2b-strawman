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

import { CourtDateStatusBadge } from "@/components/legal/court-date-status-badge";
import { DiaryEventRow, showsCourtDateStatus } from "@/components/diary/diary-event-row";
import { DiaryListView } from "@/components/diary/diary-list-view";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

afterEach(() => cleanup());

function courtDate(overrides: Partial<DiaryEventResponse> = {}): DiaryEventResponse {
  return {
    id: "cd-1",
    kind: "COURT_DATE",
    date: "2026-11-26",
    time: "09:30:00",
    title: "Johannesburg High Court — Trial",
    subtype: "TRIAL",
    status: "SCHEDULED",
    matterId: "m-1",
    matterName: "Khumalo — lease dispute",
    overdue: false,
    href: "/matters/m-1?tab=court-dates&courtDateId=cd-1",
    ...overrides,
  };
}

describe("CourtDateStatusBadge", () => {
  it.each([
    ["POSTPONED", "Postponed", "warning"],
    ["HEARD", "Heard", "success"],
    ["CANCELLED", "Cancelled", "neutral"],
  ])("renders %s as an icon plus the label %s (%s badge)", (status, label, variant) => {
    render(<CourtDateStatusBadge status={status} />);
    const badge = screen.getByText(label);
    expect(badge).toHaveAttribute("data-slot", "badge");
    expect(badge).toHaveAttribute("data-variant", variant);
    expect(badge).toHaveAttribute("data-status", status);
    expect(badge.querySelectorAll('svg[aria-hidden="true"]')).toHaveLength(1);
    // The icon adds no text: the accessible text is exactly the label.
    expect(badge).toHaveTextContent(new RegExp(`^${label}$`));
  });

  it("keeps the court-date list's blue for SCHEDULED, with an icon", () => {
    render(<CourtDateStatusBadge status="SCHEDULED" />);
    const badge = screen.getByText("Scheduled");
    expect(badge).toHaveAttribute("data-variant", "default");
    expect(badge).toHaveClass("bg-blue-100", "text-blue-700");
    expect(badge).not.toHaveClass("bg-slate-200");
    expect(badge.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
  });

  it("gives each status its own icon", () => {
    const { container } = render(
      <>
        {["SCHEDULED", "POSTPONED", "HEARD", "CANCELLED"].map((s) => (
          <CourtDateStatusBadge key={s} status={s} />
        ))}
      </>
    );
    const icons = [...container.querySelectorAll("svg")].map((svg) => svg.getAttribute("class"));
    expect(new Set(icons).size).toBe(4);
  });

  it("renders an unknown status as its raw value, neutral, without an icon", () => {
    render(<CourtDateStatusBadge status="ADJOURNED" />);
    const badge = screen.getByText("ADJOURNED");
    expect(badge).toHaveAttribute("data-variant", "neutral");
    expect(badge.querySelector("svg")).toBeNull();
  });

  it("does not treat Object.prototype keys as statuses", () => {
    render(<CourtDateStatusBadge status="constructor" />);
    expect(screen.getByText("constructor")).toHaveAttribute("data-variant", "neutral");
  });
});

describe("showsCourtDateStatus (Diary rows)", () => {
  it("is true only for a court date that is not SCHEDULED", () => {
    expect(showsCourtDateStatus(courtDate({ status: "SCHEDULED" }))).toBe(false);
    expect(showsCourtDateStatus(courtDate({ status: "POSTPONED" }))).toBe(true);
    expect(showsCourtDateStatus(courtDate({ status: "HEARD" }))).toBe(true);
    expect(
      showsCourtDateStatus(courtDate({ kind: "TASK_DUE", status: "CANCELLED", subtype: null }))
    ).toBe(false);
  });
});

describe("Diary rows render the badge only when not SCHEDULED", () => {
  it("DiaryEventRow: SCHEDULED renders no badge, POSTPONED renders 'Postponed' with an icon", () => {
    render(
      <>
        <DiaryEventRow event={courtDate({ id: "cd-s" })} slug="acme" />
        <DiaryEventRow event={courtDate({ id: "cd-p", status: "POSTPONED" })} slug="acme" />
      </>
    );
    const scheduled = screen.getByTestId("diary-event-cd-s");
    expect(scheduled.querySelector('[data-slot="badge"]')).toBeNull();
    expect(within(scheduled).queryByText("Scheduled")).toBeNull();

    const postponed = screen.getByTestId("diary-event-cd-p");
    const badge = within(postponed).getByText("Postponed");
    expect(badge.querySelector('svg[aria-hidden="true"]')).not.toBeNull();
  });

  it("DiaryEventRow: a postponed, overdue court date shows both its status and Overdue", () => {
    render(<DiaryEventRow event={courtDate({ status: "POSTPONED", overdue: true })} slug="acme" />);
    const row = screen.getByTestId("diary-event-cd-1");
    expect(within(row).getByText("Postponed")).toBeInTheDocument();
    expect(within(row).getByText("Overdue")).toBeInTheDocument();
  });

  it("DiaryListView: only the postponed row carries a status badge", () => {
    render(
      <DiaryListView
        events={[courtDate({ id: "cd-s" }), courtDate({ id: "cd-p", status: "POSTPONED" })]}
        band={[]}
        slug="acme"
      />
    );
    expect(within(screen.getByTestId("diary-list-row-cd-p")).getByText("Postponed")).toBeTruthy();
    expect(screen.queryByText("Scheduled")).toBeNull();
  });
});
