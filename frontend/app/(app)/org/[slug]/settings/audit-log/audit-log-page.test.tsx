import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

vi.mock("@/lib/api/audit-events", () => ({
  listAuditEvents: vi.fn(),
}));

import AuditLogPage from "./page";
import { listAuditEvents, type AuditEventResponse } from "@/lib/api/audit-events";
import { ApiError } from "@/lib/api/client";

const mockListAuditEvents = listAuditEvents as ReturnType<typeof vi.fn>;

function makeEvent(overrides: Partial<AuditEventResponse> = {}): AuditEventResponse {
  return {
    id: `evt-${Math.random().toString(36).slice(2, 8)}`,
    eventType: "PROJECT_CREATED",
    entityType: "PROJECT",
    entityId: "proj-123",
    actorId: "user-alice",
    actorType: "MEMBER",
    source: "WEB",
    ipAddress: "10.0.0.1",
    userAgent: "Mozilla/5.0",
    details: { name: "Acme matter" },
    occurredAt: "2026-04-25T12:34:56Z",
    ...overrides,
  };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("AuditLogPage", () => {
  beforeEach(() => {
    mockListAuditEvents.mockReset();
  });

  it("renders 50 rows from the paginated API response", async () => {
    const events: AuditEventResponse[] = Array.from({ length: 50 }, (_, i) =>
      makeEvent({ id: `evt-${i}`, eventType: `EVENT_${i}` })
    );
    mockListAuditEvents.mockResolvedValue({
      content: events,
      page: { totalElements: 120, totalPages: 3, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(mockListAuditEvents).toHaveBeenCalledWith({ page: 0, size: 50 });
    expect(screen.getByRole("heading", { name: "Audit log" })).toBeInTheDocument();
    expect(screen.getByText("EVENT_0")).toBeInTheDocument();
    expect(screen.getByText("EVENT_49")).toBeInTheDocument();
    // 50 data rows + 1 header row
    expect(screen.getAllByRole("row")).toHaveLength(51);
    // Total counter visible
    expect(screen.getByText(/120 total/)).toBeInTheDocument();
    // First page has no Previous link, but has a Next link
    const nextLink = screen.getByRole("link", { name: "Next" });
    expect(nextLink).toHaveAttribute("href", "/org/acme/settings/audit-log?page=1");
    expect(screen.queryByRole("link", { name: "Previous" })).not.toBeInTheDocument();
  });

  it("advances to the requested page via search params", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [makeEvent({ id: "evt-mid", eventType: "MID_PAGE_EVT" })],
      page: { totalElements: 120, totalPages: 3, size: 50, number: 1 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({ page: "1" }),
    });
    render(page);

    expect(mockListAuditEvents).toHaveBeenCalledWith({ page: 1, size: 50 });
    // Both Previous and Next links exist on a middle page
    const previousLink = screen.getByRole("link", { name: "Previous" });
    expect(previousLink).toHaveAttribute("href", "/org/acme/settings/audit-log?page=0");
    const nextLink = screen.getByRole("link", { name: "Next" });
    expect(nextLink).toHaveAttribute("href", "/org/acme/settings/audit-log?page=2");
    expect(screen.getByText(/Page/)).toBeInTheDocument();
  });

  it("shows 'Not authorised' copy when the API returns 403", async () => {
    mockListAuditEvents.mockRejectedValue(
      new ApiError(403, "Forbidden", { type: "about:blank", title: "Forbidden", status: 403 })
    );

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText(/Not authorised/)).toBeInTheDocument();
    expect(screen.getByText(/TEAM_OVERSIGHT/)).toBeInTheDocument();
    // Table header should not render in the 403 branch
    expect(screen.queryByText("Occurred At")).not.toBeInTheDocument();
  });

  it("renders empty state when the API returns no events", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [],
      page: { totalElements: 0, totalPages: 0, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText("No audit events recorded yet.")).toBeInTheDocument();
    expect(screen.getByText("No events")).toBeInTheDocument();
  });

  it("collapses details JSON behind a <details> summary", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [makeEvent({ details: { name: "Acme matter", actorRole: "owner" } })],
      page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    const { container } = render(page);

    const detailsEl = container.querySelector("details");
    expect(detailsEl).not.toBeNull();
    expect(detailsEl?.tagName.toLowerCase()).toBe("details");
    expect(detailsEl?.textContent).toContain("Acme matter");
  });
});
