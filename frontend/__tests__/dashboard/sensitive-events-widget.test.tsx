import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";

// Mock next/navigation BEFORE importing the component.
const pushMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

// Mock next/link to a plain anchor so we can assert href.
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

import { SensitiveEventsWidget } from "@/components/dashboard/sensitive-events-widget";
import { CapabilityProvider } from "@/lib/capabilities";
import type { AuditEventResponse, EventTypeFacet } from "@/lib/api/audit-events";

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  pushMock.mockReset();
});

function withCaps(ui: React.ReactNode, { caps = ["TEAM_OVERSIGHT"], isAdmin = false } = {}) {
  return (
    <CapabilityProvider
      capabilities={caps}
      role={isAdmin ? "Admin" : "Member"}
      isAdmin={isAdmin}
      isOwner={false}
    >
      {ui}
    </CapabilityProvider>
  );
}

const SAMPLE_FACETS: EventTypeFacet[] = [
  // INFO must be ignored even when present.
  {
    eventType: "user.session.opened",
    label: "User logged in",
    severity: "INFO",
    group: "SECURITY",
    count: 99,
  },
  {
    eventType: "task.created",
    label: "Task created",
    severity: "NOTICE",
    group: "STANDARD",
    count: 4,
  },
  {
    eventType: "matter.note.added",
    label: "Note added",
    severity: "NOTICE",
    group: "STANDARD",
    count: 3,
  },
  {
    eventType: "trust.transaction.approved",
    label: "Trust transaction approved",
    severity: "WARNING",
    group: "FINANCIAL",
    count: 2,
  },
  {
    eventType: "matter.closed.override",
    label: "Matter closure override",
    severity: "CRITICAL",
    group: "COMPLIANCE",
    count: 1,
  },
];

const SAMPLE_EVENT: AuditEventResponse = {
  id: "evt-001",
  eventType: "trust.transaction.approved",
  entityType: "trust_transaction",
  entityId: "txn-42",
  actorId: "user-1",
  actorType: "USER",
  source: null,
  ipAddress: null,
  userAgent: null,
  details: null,
  occurredAt: "2026-05-03T12:00:00.000Z",
  label: "Trust transaction approved",
  severity: "WARNING",
  group: "FINANCIAL",
  actorDisplayName: "Alice",
};

describe("SensitiveEventsWidget", () => {
  it("renders three count pills (NOTICE/WARNING/CRITICAL) and never INFO", () => {
    render(
      withCaps(
        <SensitiveEventsWidget orgSlug="acme" facets={SAMPLE_FACETS} recent={[SAMPLE_EVENT]} />
      )
    );

    expect(screen.getByTestId("sensitive-events-widget")).toBeInTheDocument();

    // Aggregated counts: NOTICE=7, WARNING=2, CRITICAL=1. INFO=99 must NOT appear.
    const notice = screen.getByTestId("sensitive-count-NOTICE");
    expect(notice.textContent).toContain("NOTICE");
    expect(notice.textContent).toContain("7");

    const warning = screen.getByTestId("sensitive-count-WARNING");
    expect(warning.textContent).toContain("WARNING");
    expect(warning.textContent).toContain("2");

    const critical = screen.getByTestId("sensitive-count-CRITICAL");
    expect(critical.textContent).toContain("CRITICAL");
    expect(critical.textContent).toContain("1");

    // INFO pill must not be rendered.
    expect(screen.queryByTestId("sensitive-count-INFO")).not.toBeInTheDocument();
  });

  it("shows the empty state when there are zero recent CRITICAL/WARNING events", () => {
    render(withCaps(<SensitiveEventsWidget orgSlug="acme" facets={[]} recent={[]} />));

    expect(screen.getByTestId("sensitive-events-empty")).toBeInTheDocument();
    expect(screen.getByText("No sensitive events in the last 7 days.")).toBeInTheDocument();
  });

  it("navigates to the per-event deep link on row click (±1m window)", () => {
    render(
      withCaps(
        <SensitiveEventsWidget orgSlug="acme" facets={SAMPLE_FACETS} recent={[SAMPLE_EVENT]} />
      )
    );

    const row = screen.getByTestId(`sensitive-row-${SAMPLE_EVENT.id}`);
    fireEvent.click(row);

    expect(pushMock).toHaveBeenCalledTimes(1);
    const url = pushMock.mock.calls[0][0] as string;

    expect(url).toMatch(/^\/org\/acme\/settings\/audit-log\?/);
    expect(url).toContain("eventType=trust.transaction.approved");
    // ±1 minute window around 2026-05-03T12:00:00.000Z
    expect(url).toContain(encodeURIComponent("2026-05-03T11:59:00.000Z"));
    expect(url).toContain(encodeURIComponent("2026-05-03T12:01:00.000Z"));
  });

  it("renders a 'View all' link with the Sensitive preset filter", () => {
    render(withCaps(<SensitiveEventsWidget orgSlug="acme" facets={SAMPLE_FACETS} recent={[]} />));
    const link = screen.getByTestId("sensitive-events-view-all") as HTMLAnchorElement;
    expect(link.getAttribute("href")).toMatch(/^\/org\/acme\/settings\/audit-log\?/);
    expect(link.getAttribute("href")).toContain("severities=WARNING%2CCRITICAL");
    expect(link.getAttribute("href")).toContain("preset=sensitive");
  });

  it("hides the widget entirely when TEAM_OVERSIGHT capability is missing", () => {
    render(
      withCaps(
        <SensitiveEventsWidget orgSlug="acme" facets={SAMPLE_FACETS} recent={[SAMPLE_EVENT]} />,
        { caps: [], isAdmin: false }
      )
    );
    expect(screen.queryByTestId("sensitive-events-widget")).not.toBeInTheDocument();
  });
});
