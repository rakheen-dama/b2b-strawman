import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import type { AuditEventResponse, AuditEventsPage } from "@/lib/api/audit-events";

// The component imports the server action — mock it so we can drive the
// fetch behaviour deterministically from tests. (Server actions are
// regular async functions at runtime; mocking the module replaces them.)
vi.mock("@/lib/actions/audit-events", () => ({
  fetchEntityAuditPage: vi.fn(),
  fetchClientActivityPage: vi.fn(),
}));

import { fetchClientActivityPage, fetchEntityAuditPage } from "@/lib/actions/audit-events";
import { CapabilityProvider } from "@/lib/capabilities";
import { CustomerAuditTab } from "@/app/(app)/org/[slug]/clients/[id]/audit-tab";
import { AuditTimeline, auditRowSuffix } from "../audit-timeline";

const mocked = vi.mocked(fetchEntityAuditPage);
const mockedClientActivity = vi.mocked(fetchClientActivityPage);

function makeRow(
  overrides: Partial<AuditEventResponse> & { id: string; occurredAt: string }
): AuditEventResponse {
  return {
    eventType: "CUSTOMER_UPDATED",
    entityType: "customer",
    entityId: "cust-1",
    actorId: "actor-1",
    actorType: "MEMBER",
    source: "WEB",
    ipAddress: "1.2.3.4",
    userAgent: null,
    details: null,
    label: "Customer updated",
    severity: "INFO",
    group: "DATA",
    actorDisplayName: "Alice Member",
    ...overrides,
  };
}

function makePage(rows: AuditEventResponse[], totalPages = 1, pageNumber = 0): AuditEventsPage {
  return {
    content: rows,
    page: {
      totalElements: rows.length + (totalPages - 1) * rows.length,
      totalPages,
      size: 20,
      number: pageNumber,
    },
  };
}

beforeEach(() => {
  mocked.mockReset();
  mockedClientActivity.mockReset();
});

afterEach(() => {
  cleanup();
});

describe("<AuditTimeline>", () => {
  it("renders events in DESC order returned by the API", async () => {
    const rows = [
      makeRow({ id: "e3", occurredAt: "2026-05-03T10:00:00Z", label: "Third event" }),
      makeRow({ id: "e2", occurredAt: "2026-05-02T10:00:00Z", label: "Second event" }),
      makeRow({ id: "e1", occurredAt: "2026-05-01T10:00:00Z", label: "First event" }),
    ];
    mocked.mockResolvedValueOnce(makePage(rows, 1, 0));

    render(<AuditTimeline entityType="customer" entityId="cust-1" />);

    await waitFor(() => {
      expect(screen.getByTestId("audit-timeline")).toBeInTheDocument();
    });
    const renderedRows = screen.getAllByTestId("audit-timeline-row");
    expect(renderedRows.map((el) => el.getAttribute("data-event-id"))).toEqual(["e3", "e2", "e1"]);
  });

  it("expands details viewer when a row is clicked", async () => {
    const rows = [
      makeRow({
        id: "e1",
        occurredAt: "2026-05-01T10:00:00Z",
        details: { name: { from: "Old", to: "New" } },
        label: "Renamed customer",
      }),
    ];
    mocked.mockResolvedValueOnce(makePage(rows));

    render(<AuditTimeline entityType="customer" entityId="cust-1" />);

    const row = await screen.findByTestId("audit-timeline-row");
    expect(screen.queryByTestId("audit-details-diff")).not.toBeInTheDocument();

    await userEvent.click(row.querySelector("button")!);

    await waitFor(() => {
      expect(screen.getByTestId("audit-details-diff")).toBeInTheDocument();
    });
  });

  it("renders an empty state with the entity-type-aware copy when zero events", async () => {
    mocked.mockResolvedValueOnce(makePage([], 0, 0));

    render(<AuditTimeline entityType="customer" entityId="cust-1" />);

    await waitFor(() => {
      expect(screen.getByText("No audit events for this client")).toBeInTheDocument();
    });
  });

  it("'Load more' fetches the next page and appends rows", async () => {
    const firstPage = makePage(
      [makeRow({ id: "p1-a", occurredAt: "2026-05-03T10:00:00Z", label: "Page 1 row A" })],
      2,
      0
    );
    const secondPage = makePage(
      [makeRow({ id: "p2-a", occurredAt: "2026-05-02T10:00:00Z", label: "Page 2 row A" })],
      2,
      1
    );
    mocked.mockResolvedValueOnce(firstPage).mockResolvedValueOnce(secondPage);

    render(<AuditTimeline entityType="customer" entityId="cust-1" />);

    const loadMore = await screen.findByTestId("audit-timeline-load-more");
    expect(screen.getAllByTestId("audit-timeline-row")).toHaveLength(1);

    await userEvent.click(loadMore);

    await waitFor(() => {
      expect(screen.getAllByTestId("audit-timeline-row")).toHaveLength(2);
    });
    // Second-page fetch fired with page=1
    expect(mocked).toHaveBeenCalledTimes(2);
    expect(mocked).toHaveBeenLastCalledWith("customer", "cust-1", 1, 20);
  });

  it("labels the empty-state copy with the legal word, not the storage literal", async () => {
    mocked.mockResolvedValueOnce(makePage([], 0, 0));

    render(<AuditTimeline entityType="project" entityId="m-1" />);

    expect(await screen.findByText("No audit events for this matter")).toBeInTheDocument();
  });

  it("uses a supplied loadPage instead of the by-entity fetcher", async () => {
    const loadPage = vi
      .fn()
      .mockResolvedValueOnce(
        makePage([makeRow({ id: "lp-1", occurredAt: "2026-05-03T10:00:00Z" })], 2, 0)
      )
      .mockResolvedValueOnce(
        makePage([makeRow({ id: "lp-2", occurredAt: "2026-05-02T10:00:00Z" })], 2, 1)
      );

    render(<AuditTimeline entityType="customer" entityId="cust-1" loadPage={loadPage} />);

    await userEvent.click(await screen.findByTestId("audit-timeline-load-more"));
    await waitFor(() => expect(screen.getAllByTestId("audit-timeline-row")).toHaveLength(2));
    expect(loadPage).toHaveBeenNthCalledWith(1, 0, 20);
    expect(loadPage).toHaveBeenNthCalledWith(2, 1, 20);
    expect(mocked).not.toHaveBeenCalled();
  });

  it("appends the lifecycle from/to and the checklist item name to the row label", async () => {
    mocked.mockResolvedValueOnce(
      makePage([
        makeRow({
          id: "lc",
          occurredAt: "2026-05-03T10:00:00Z",
          eventType: "customer.lifecycle.transitioned",
          label: "Client lifecycle transitioned",
          details: { oldStatus: "ONBOARDING", newStatus: "ACTIVE" },
        }),
        makeRow({
          id: "ck",
          occurredAt: "2026-05-02T10:00:00Z",
          eventType: "checklist.item.completed",
          entityType: "checklist_instance_item",
          label: "Checklist item completed",
          details: { itemName: "Proof of address" },
        }),
      ])
    );

    render(<AuditTimeline entityType="customer" entityId="cust-1" />);

    expect(
      await screen.findByText("Client lifecycle transitioned (ONBOARDING → ACTIVE)")
    ).toBeInTheDocument();
    expect(screen.getByText("Checklist item completed — Proof of address")).toBeInTheDocument();
  });

  it("auditRowSuffix ignores non-string details and unrelated events", () => {
    const base = makeRow({ id: "x", occurredAt: "2026-05-03T10:00:00Z" });
    expect(auditRowSuffix({ ...base, eventType: "project.updated", details: { name: "n" } })).toBe(
      ""
    );
    expect(
      auditRowSuffix({
        ...base,
        eventType: "customer.lifecycle.transitioned",
        details: { oldStatus: "PROSPECT" },
      })
    ).toBe("");
    expect(
      auditRowSuffix({
        ...base,
        eventType: "checklist.item.skipped",
        details: { itemName: { from: "a", to: "b" } },
      })
    ).toBe("");
    expect(
      auditRowSuffix({
        ...base,
        eventType: "checklist.item.skipped",
        details: { itemName: "ID document" },
      })
    ).toBe(" — ID document");
    expect(auditRowSuffix({ ...base, details: null })).toBe("");
  });
});

describe("<CustomerAuditTab> (client Activity tab, G-B2)", () => {
  it("reads the paged client activity feed and renders the checklist rows", async () => {
    mockedClientActivity.mockResolvedValueOnce(
      makePage([
        makeRow({
          id: "ck-1",
          occurredAt: "2026-05-03T10:00:00Z",
          eventType: "checklist.item.completed",
          entityType: "checklist_instance_item",
          entityId: "item-1",
          label: "Checklist item completed",
          details: { itemName: "Certified ID copy" },
        }),
        makeRow({
          id: "lc-1",
          occurredAt: "2026-05-02T10:00:00Z",
          eventType: "customer.lifecycle.transitioned",
          label: "Client lifecycle transitioned",
          details: { oldStatus: "ONBOARDING", newStatus: "ACTIVE" },
        }),
      ])
    );

    render(
      <CapabilityProvider capabilities={["TEAM_OVERSIGHT"]} role="Admin" isAdmin isOwner={false}>
        <CustomerAuditTab customerId="cust-1" />
      </CapabilityProvider>
    );

    expect(
      await screen.findByText("Checklist item completed — Certified ID copy")
    ).toBeInTheDocument();
    expect(
      screen.getByText("Client lifecycle transitioned (ONBOARDING → ACTIVE)")
    ).toBeInTheDocument();
    expect(mockedClientActivity).toHaveBeenCalledWith("cust-1", 0, 20);
    expect(mockedClientActivity).toHaveBeenCalledTimes(1);
    expect(mocked).not.toHaveBeenCalled();
  });
});
