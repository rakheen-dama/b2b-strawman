import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";

vi.mock("server-only", () => ({}));

vi.mock("@/lib/api/audit-events", () => ({
  listAuditEvents: vi.fn(),
  getAuditMetadata: vi.fn(),
  listFacetActors: vi.fn(),
  listFacetEventTypes: vi.fn(),
  listFacetEntityTypes: vi.fn(),
}));

const pushMock = vi.fn();
const useSearchParamsMock = vi.fn(() => new URLSearchParams());

vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({
    push: pushMock,
    refresh: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  usePathname: () => "/org/acme/settings/audit-log",
  useSearchParams: () => useSearchParamsMock(),
}));

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import AuditLogPage from "./page";
import { listAuditEvents, type AuditEventResponse } from "@/lib/api/audit-events";
import { ApiError } from "@/lib/api/client";

const mockListAuditEvents = listAuditEvents as ReturnType<typeof vi.fn>;

function makeEvent(overrides: Partial<AuditEventResponse> = {}): AuditEventResponse {
  return {
    id: `evt-${Math.random().toString(36).slice(2, 8)}`,
    eventType: "customer.created",
    entityType: "customer",
    entityId: "00000000-0000-0000-0000-000000000123",
    actorId: "user-alice",
    actorType: "USER",
    source: "API",
    ipAddress: "10.0.0.1",
    userAgent: "Mozilla/5.0",
    details: { name: "Acme matter" },
    occurredAt: "2026-04-25T12:34:56Z",
    label: "Customer Created",
    severity: "INFO",
    group: "STANDARD",
    actorDisplayName: "Alice Smith",
    ...overrides,
  };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  pushMock.mockClear();
  useSearchParamsMock.mockReset();
  useSearchParamsMock.mockReturnValue(new URLSearchParams());
});

describe("AuditLogPage (server shell)", () => {
  beforeEach(() => {
    mockListAuditEvents.mockReset();
  });

  it("passes initial filter from search params to the API", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [makeEvent({ id: "evt-a", label: "Event A" })],
      page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({
        severities: "CRITICAL,WARNING",
        actorId: "actor-xyz",
        entityType: "customer",
      }),
    });
    render(page);

    expect(mockListAuditEvents).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 50,
        severities: ["CRITICAL", "WARNING"],
        actorId: "actor-xyz",
        entityType: "customer",
      })
    );
    expect(screen.getByRole("heading", { name: "Audit log" })).toBeInTheDocument();
    expect(screen.getByText("Event A")).toBeInTheDocument();
  });

  it("shows 'Not authorised' copy when the API returns 403", async () => {
    mockListAuditEvents.mockRejectedValue(
      new ApiError(403, "Forbidden", {
        type: "about:blank",
        title: "Forbidden",
        status: 403,
      })
    );

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText(/Not authorised/)).toBeInTheDocument();
    expect(screen.getByText(/TEAM_OVERSIGHT/)).toBeInTheDocument();
  });

  it("renders the empty-state copy when there are no events and no filters", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [],
      page: { totalElements: 0, totalPages: 0, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText(/audit log is empty/i)).toBeInTheDocument();
  });

  it("renders the row collapsed by default and toggles AuditDetailsViewer on expand", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [
        makeEvent({
          id: "evt-1",
          details: {
            name: { from: "Old", to: "New" },
          },
        }),
      ],
      page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.queryByTestId("audit-details-diff")).not.toBeInTheDocument();

    const toggle = screen.getByTestId("audit-row-toggle-evt-1");
    fireEvent.click(toggle);

    expect(screen.getByTestId("audit-details-diff")).toBeInTheDocument();
    expect(screen.getByText("Old")).toBeInTheDocument();
    expect(screen.getByText("New")).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("audit-row-toggle-evt-1"));
    expect(screen.queryByTestId("audit-details-diff")).not.toBeInTheDocument();
  });

  it("renders entity cell as a deep-link for entityType=customer", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [
        makeEvent({
          id: "evt-cust",
          entityType: "customer",
          entityId: "abcdef12-3456-7890-abcd-ef1234567890",
        }),
      ],
      page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    const link = screen.getByTestId("entity-cell-link");
    expect(link).toHaveAttribute(
      "href",
      "/org/acme/customers/abcdef12-3456-7890-abcd-ef1234567890"
    );
    expect(link.getAttribute("data-entity-type")).toBe("customer");
  });

  it("renders entity cell as literal label for unknown entityType=task", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [
        makeEvent({
          id: "evt-task",
          entityType: "task",
          entityId: "11111111-2222-3333-4444-555555555555",
        }),
      ],
      page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    const literal = screen.getByTestId("entity-cell-literal");
    expect(literal).toHaveTextContent("task:11111111");
    expect(screen.queryByTestId("entity-cell-link")).not.toBeInTheDocument();
  });

  it("toggling a severity chip pushes a URL with the severities param", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [],
      page: { totalElements: 0, totalPages: 0, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    const criticalToggle = screen.getByTestId("severity-toggle-CRITICAL");
    fireEvent.click(criticalToggle);

    await waitFor(() => expect(pushMock).toHaveBeenCalled());
    const urlPushed = pushMock.mock.calls[0]?.[0] as string;
    expect(urlPushed).toContain("severities=CRITICAL");
    expect(urlPushed).toContain("/org/acme/settings/audit-log");
  });

  it("typing an actor ID and blurring builds a URL with actorId + entityType combined", async () => {
    useSearchParamsMock.mockReturnValue(new URLSearchParams("entityType=customer"));
    mockListAuditEvents.mockResolvedValue({
      content: [],
      page: { totalElements: 0, totalPages: 0, size: 50, number: 0 },
    });

    const page = await AuditLogPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({ entityType: "customer" }),
    });
    render(page);

    const actorInput = screen.getByLabelText("Actor ID") as HTMLInputElement;
    fireEvent.change(actorInput, { target: { value: "actor-xyz" } });
    fireEvent.blur(actorInput);

    await waitFor(() => expect(pushMock).toHaveBeenCalled());
    const urlPushed = pushMock.mock.calls[0]?.[0] as string;
    expect(urlPushed).toContain("actorId=actor-xyz");
    expect(urlPushed).toContain("entityType=customer");
    expect(urlPushed).toContain("/org/acme/settings/audit-log");
  });
});
