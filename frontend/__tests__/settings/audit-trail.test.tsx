import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

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
  usePathname: () => "/org/acme/settings/firm",
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

import { renderAuditTrailTab } from "@/app/(app)/org/[slug]/settings/firm/_tabs/audit-trail";
import {
  getAuditMetadata,
  listAuditEvents,
  listFacetActors,
  listFacetEntityTypes,
  listFacetEventTypes,
  type ActorFacet,
  type AuditEventResponse,
  type EntityTypeFacet,
  type EventTypeFacet,
} from "@/lib/api/audit-events";
import { ApiError } from "@/lib/api/client";

const mockListAuditEvents = listAuditEvents as ReturnType<typeof vi.fn>;
const mockGetAuditMetadata = getAuditMetadata as ReturnType<typeof vi.fn>;
const mockListFacetActors = listFacetActors as ReturnType<typeof vi.fn>;
const mockListFacetEntityTypes = listFacetEntityTypes as ReturnType<typeof vi.fn>;
const mockListFacetEventTypes = listFacetEventTypes as ReturnType<typeof vi.fn>;

const ALICE_ID = "2e38845d-7fd5-4348-9b70-280c5d442df6";
const FORMER_ID = "9a9a9a9a-1111-2222-3333-444444444444";
const PORTAL_A_ID = "65f788e3-aaaa-bbbb-cccc-dddddddddddd";
const PORTAL_B_ID = "77e1b2c3-aaaa-bbbb-cccc-dddddddddddd";

const ACTOR_FACETS: ActorFacet[] = [
  { actorId: ALICE_ID, actorDisplayName: "Alice Smith", actorType: "USER", eventCount: 3 },
  {
    actorId: FORMER_ID,
    actorDisplayName: `Former member (${FORMER_ID})`,
    actorType: "USER",
    eventCount: 1,
  },
  {
    actorId: PORTAL_A_ID,
    actorDisplayName: "Portal Contact",
    actorType: "PORTAL_CONTACT",
    eventCount: 2,
  },
  {
    actorId: PORTAL_B_ID,
    actorDisplayName: "Portal Contact",
    actorType: "PORTAL_CONTACT",
    eventCount: 1,
  },
];

const ENTITY_TYPE_FACETS: EntityTypeFacet[] = [
  { entityType: "project", label: "Matter", count: 5 },
  { entityType: "customer", label: "Client", count: 4 },
  { entityType: "invoice", label: "Fee note", count: 3 },
];

const EVENT_TYPE_FACETS: EventTypeFacet[] = [
  {
    eventType: "project.updated",
    label: "Matter updated",
    severity: "INFO",
    group: "STANDARD",
    count: 9,
  },
  { eventType: "invoice.sent", label: "Fee note", severity: "INFO", group: "FINANCIAL", count: 3 },
  {
    eventType: "matter_closure.closed",
    label: "Matter closure closed",
    severity: "NOTICE",
    group: "COMPLIANCE",
    count: 1,
  },
];

const EMPTY_PAGE = {
  content: [],
  page: { totalElements: 0, totalPages: 0, size: 50, number: 0 },
};

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
    label: "Client created",
    severity: "INFO",
    group: "STANDARD",
    actorDisplayName: "Alice Smith",
    ...overrides,
  };
}

async function renderTab(searchParams: Record<string, string> = {}) {
  useSearchParamsMock.mockReturnValue(new URLSearchParams(searchParams));
  const page = await renderAuditTrailTab({ slug: "acme", searchParams });
  render(page);
}

function pushedUrl(): string {
  return pushMock.mock.calls[0]?.[0] as string;
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  pushMock.mockClear();
  useSearchParamsMock.mockReset();
  useSearchParamsMock.mockReturnValue(new URLSearchParams());
});

describe("Firm › Audit trail tab loader (server shell)", () => {
  beforeEach(() => {
    mockListAuditEvents.mockReset();
    mockGetAuditMetadata.mockReset();
    mockGetAuditMetadata.mockResolvedValue([]);
    mockListFacetActors.mockReset();
    mockListFacetActors.mockResolvedValue(ACTOR_FACETS);
    mockListFacetEntityTypes.mockReset();
    mockListFacetEntityTypes.mockResolvedValue(ENTITY_TYPE_FACETS);
    mockListFacetEventTypes.mockReset();
    mockListFacetEventTypes.mockResolvedValue(EVENT_TYPE_FACETS);
  });

  it("passes initial filter from search params to the API", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [makeEvent({ id: "evt-a", label: "Event A" })],
      page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
    });

    await renderTab({
      severities: "CRITICAL,WARNING",
      actorId: ALICE_ID,
      entityType: "customer",
    });

    expect(mockListAuditEvents).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 50,
        severities: ["CRITICAL", "WARNING"],
        actorId: ALICE_ID,
        entityType: "customer",
      })
    );
    expect(screen.getByRole("heading", { name: "Audit log" })).toBeInTheDocument();
    expect(screen.getByText("Event A")).toBeInTheDocument();
  });

  it("fetches the facets over the list's range (all-time when no from)", async () => {
    mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

    await renderTab({ to: "2026-10-27T23:59:59.999Z" });

    const range = { from: "1970-01-01T00:00:00Z", to: "2026-10-27T23:59:59.999Z" };
    expect(mockListFacetActors).toHaveBeenCalledWith(range);
    expect(mockListFacetEntityTypes).toHaveBeenCalledWith(range);
    expect(mockListFacetEventTypes).toHaveBeenCalledWith(range);
  });

  it("a facet failure is not fatal — the trail still renders", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [makeEvent({ id: "evt-b", label: "Event B" })],
      page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
    });
    mockListFacetActors.mockRejectedValue(new ApiError(500, "boom"));

    await renderTab();

    expect(screen.getByText("Event B")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Actor" })).toHaveTextContent("All actors");
  });

  it("drops a non-UUID actorId / entityId from the URL instead of failing the page (R-6)", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [makeEvent({ id: "evt-c", label: "Event C" })],
      page: { totalElements: 1, totalPages: 1, size: 50, number: 0 },
    });

    await renderTab({ actorId: "Alice Owner", entityId: "not-a-uuid", entityType: "project" });

    const filter = mockListAuditEvents.mock.calls[0]?.[0];
    expect(filter).toEqual(expect.objectContaining({ entityType: "project" }));
    expect(filter.actorId).toBeUndefined();
    expect(filter.entityId).toBeUndefined();
    expect(screen.getByText("Event C")).toBeInTheDocument();
  });

  it("shows 'Not authorised' copy when the API returns 403", async () => {
    mockListAuditEvents.mockRejectedValue(
      new ApiError(403, "Forbidden", {
        type: "about:blank",
        title: "Forbidden",
        status: 403,
      })
    );

    await renderTab();

    expect(screen.getByText(/Not authorised/)).toBeInTheDocument();
    expect(screen.getByText(/TEAM_OVERSIGHT/)).toBeInTheDocument();
  });

  it("renders the empty-state copy when there are no events and no filters", async () => {
    mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

    await renderTab();

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

    await renderTab();

    expect(screen.queryByTestId("audit-details-diff")).not.toBeInTheDocument();

    const toggle = screen.getByTestId("audit-row-toggle-evt-1");
    fireEvent.click(toggle);

    expect(screen.getByTestId("audit-details-diff")).toBeInTheDocument();
    expect(screen.getByText("Old")).toBeInTheDocument();
    expect(screen.getByText("New")).toBeInTheDocument();

    fireEvent.click(screen.getByTestId("audit-row-toggle-evt-1"));
    expect(screen.queryByTestId("audit-details-diff")).not.toBeInTheDocument();
  });

  it("renders entity cell as a deep-link for entityType=customer with the legal label", async () => {
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

    await renderTab();

    const link = screen.getByTestId("entity-cell-link");
    expect(link).toHaveAttribute("href", "/org/acme/clients/abcdef12-3456-7890-abcd-ef1234567890");
    expect(link.getAttribute("data-entity-type")).toBe("customer");
    expect(link).toHaveTextContent(/^Client abcdef12$/);
  });

  it("renders entity cell as a legal label for a non-linked entityType=task", async () => {
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

    await renderTab();

    const literal = screen.getByTestId("entity-cell-literal");
    expect(literal).toHaveTextContent(/^Task 11111111$/);
    expect(literal.getAttribute("data-entity-type")).toBe("task");
    expect(screen.queryByTestId("entity-cell-link")).not.toBeInTheDocument();
  });

  it("names a fee note by its number: 'Fee note INV-0003'", async () => {
    mockListAuditEvents.mockResolvedValue({
      content: [
        makeEvent({
          id: "evt-inv",
          eventType: "invoice.sent",
          entityType: "invoice",
          entityId: "7b7e7dde-0000-0000-0000-000000000000",
          details: { invoice_number: "INV-0003" },
          label: "Fee note",
        }),
        makeEvent({
          id: "evt-ck",
          eventType: "checklist.item.completed",
          entityType: "checklist_instance_item",
          entityId: "5831f9d6-0000-0000-0000-000000000000",
          details: { itemName: "Proof of address" },
          label: "Checklist item completed",
        }),
      ],
      page: { totalElements: 2, totalPages: 1, size: 50, number: 0 },
    });

    await renderTab();

    expect(screen.getByTestId("entity-cell-link")).toHaveTextContent(/^Fee note INV-0003$/);
    expect(screen.getByTestId("entity-cell-literal")).toHaveTextContent(
      /^FICA checklist item 5831f9d6$/
    );
    expect(screen.queryByText(/invoice 7b7e7dde/)).not.toBeInTheDocument();
  });

  it("toggling a severity chip pushes a URL with the severities param", async () => {
    mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

    await renderTab();

    const criticalToggle = screen.getByTestId("severity-toggle-CRITICAL");
    fireEvent.click(criticalToggle);

    await waitFor(() => expect(pushMock).toHaveBeenCalled());
    const urlPushed = pushedUrl();
    expect(urlPushed).toContain("severities=CRITICAL");
    expect(urlPushed).toContain("/org/acme/settings/firm");
    expect(urlPushed).toContain("tab=audit-trail");
  });

  it("selecting Sensitive preset pushes URL with severities=WARNING,CRITICAL and from", async () => {
    mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

    await renderTab();

    const user = userEvent.setup();
    await user.click(screen.getByTestId("audit-preset-select"));
    await user.click(await screen.findByRole("option", { name: /Sensitive/i }));

    await waitFor(() => expect(pushMock).toHaveBeenCalled());
    const urlPushed = pushedUrl();
    // URLSearchParams encodes commas as %2C
    expect(decodeURIComponent(urlPushed)).toContain("severities=WARNING,CRITICAL");
    expect(urlPushed).toMatch(/from=\d{4}-\d{2}-\d{2}T/);
    expect(urlPushed).toContain("/org/acme/settings/firm");
    expect(urlPushed).toContain("tab=audit-trail");
    // Sensitive is severity-only — no eventType sentinel and no banner.
    expect(urlPushed).not.toContain("eventType=");
    expect(screen.queryByTestId("audit-preset-multi-event-banner")).not.toBeInTheDocument();
  });

  it("selecting Financial approvals preset uses multi-event sentinel and shows banner", async () => {
    mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

    await renderTab();

    const user = userEvent.setup();
    await user.click(screen.getByTestId("audit-preset-select"));
    await user.click(await screen.findByRole("option", { name: /Financial approvals/i }));

    await waitFor(() => expect(pushMock).toHaveBeenCalled());
    const urlPushed = pushedUrl();
    // Fail-closed: sentinel goes on the URL so the result set is empty
    // (the user is not misled into thinking the first event-type is the
    // whole preset).
    expect(decodeURIComponent(urlPushed)).toContain("eventType=__multi__");
    expect(urlPushed).toMatch(/from=/);

    // Banner names the resolved event types.
    const banner = await screen.findByTestId("audit-preset-multi-event-banner");
    expect(banner).toHaveTextContent("trust.transaction.approved");
    expect(banner).toHaveTextContent("invoice.sent");
  });

  it("selecting a group preset with empty metadata still uses sentinel + shows fallback banner", async () => {
    mockGetAuditMetadata.mockResolvedValue([]);
    mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

    await renderTab();

    const user = userEvent.setup();
    await user.click(screen.getByTestId("audit-preset-select"));
    await user.click(await screen.findByRole("option", { name: /Compliance/i }));

    await waitFor(() => expect(pushMock).toHaveBeenCalled());
    const urlPushed = pushedUrl();
    // Even with zero matching metadata entries, the group preset is fail-closed.
    expect(decodeURIComponent(urlPushed)).toContain("eventType=__multi__");
    const banner = await screen.findByTestId("audit-preset-multi-event-banner");
    expect(banner).toHaveTextContent(/metadata unavailable/i);
  });

  describe("Actor Select (G-D6)", () => {
    it("picking an actor by name builds a URL with actorId + the existing entityType", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab({ entityType: "customer" });

      expect(screen.queryByLabelText("Actor ID")).not.toBeInTheDocument();
      const user = userEvent.setup();
      await user.click(screen.getByRole("combobox", { name: "Actor" }));
      await user.click(await screen.findByRole("option", { name: /Alice Smith/ }));

      await waitFor(() => expect(pushMock).toHaveBeenCalled());
      const urlPushed = pushedUrl();
      expect(urlPushed).toContain(`actorId=${ALICE_ID}`);
      expect(urlPushed).toContain("entityType=customer");
      expect(urlPushed).toContain("/org/acme/settings/firm");
      expect(urlPushed).toContain("tab=audit-trail");
    });

    it("offers members, former members and portal contacts in labelled groups", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab();

      const user = userEvent.setup();
      await user.click(screen.getByRole("combobox", { name: "Actor" }));
      expect(await screen.findByRole("option", { name: /All actors/ })).toBeInTheDocument();
      expect(screen.getByRole("option", { name: /Former member/ })).toBeInTheDocument();
      // Repeated labels are disambiguated with a short id.
      expect(
        screen.getByRole("option", { name: /Portal Contact \(65f788e3\)/ })
      ).toBeInTheDocument();
      expect(
        screen.getByRole("option", { name: /Portal Contact \(77e1b2c3\)/ })
      ).toBeInTheDocument();
      expect(screen.getByText("Members")).toBeInTheDocument();
      expect(screen.getByText("Portal contacts")).toBeInTheDocument();
    });

    it("choosing 'All actors' clears the actorId param", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab({ actorId: ALICE_ID });

      const trigger = screen.getByRole("combobox", { name: "Actor" });
      expect(trigger).toHaveTextContent("Alice Smith");
      const user = userEvent.setup();
      await user.click(trigger);
      await user.click(await screen.findByRole("option", { name: /All actors/ }));

      await waitFor(() => expect(pushMock).toHaveBeenCalled());
      expect(pushedUrl()).not.toContain("actorId=");
    });

    it("shows 'Unknown actor (…)' for a URL actorId outside the facet list", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab({ actorId: "deadbeef-0000-0000-0000-000000000000" });

      expect(screen.getByRole("combobox", { name: "Actor" })).toHaveTextContent(
        "Unknown actor (deadbeef)"
      );
    });
  });

  describe("Entity type Select (G-D6)", () => {
    it("labels the stored literal with the legal word and sends the literal", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab();

      const user = userEvent.setup();
      await user.click(screen.getByRole("combobox", { name: "Entity type" }));
      expect(await screen.findByRole("option", { name: /Client/ })).toBeInTheDocument();
      expect(screen.getByRole("option", { name: /Fee note/ })).toBeInTheDocument();
      await user.click(screen.getByRole("option", { name: /Matter/ }));

      await waitFor(() => expect(pushMock).toHaveBeenCalled());
      const urlPushed = pushedUrl();
      expect(urlPushed).toContain("entityType=project");
      expect(urlPushed).not.toContain("entityType=matter");
      expect(urlPushed).not.toContain("entityType=Matter");
    });

    it("shows the legal word on the trigger for the current URL literal", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab({ entityType: "project" });

      expect(screen.getByRole("combobox", { name: "Entity type" })).toHaveTextContent("Matter");
    });

    it("keeps a URL literal that is outside the facet range selectable", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab({ entityType: "legal_disbursement" });

      expect(screen.getByRole("combobox", { name: "Entity type" })).toHaveTextContent(
        "Disbursement"
      );
    });
  });

  describe("Entity ID text input", () => {
    it("Enter commits the value once; the following blur does not push again", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab();

      const input = screen.getByLabelText("Entity ID") as HTMLInputElement;
      fireEvent.change(input, { target: { value: ALICE_ID } });
      fireEvent.keyDown(input, { key: "Enter" });

      await waitFor(() => expect(pushMock).toHaveBeenCalledTimes(1));
      expect(pushedUrl()).toContain(`entityId=${ALICE_ID}`);

      fireEvent.blur(input);
      expect(pushMock).toHaveBeenCalledTimes(1);
    });

    it("blur still commits a changed value", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab();

      const input = screen.getByLabelText("Entity ID") as HTMLInputElement;
      fireEvent.change(input, { target: { value: ALICE_ID } });
      fireEvent.blur(input);

      await waitFor(() => expect(pushMock).toHaveBeenCalledTimes(1));
      expect(pushedUrl()).toContain(`entityId=${ALICE_ID}`);
    });
  });

  describe("Event type combobox (G-D6)", () => {
    it("searches by legal label or key and commits the picked key", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab();

      const user = userEvent.setup();
      await user.click(screen.getByRole("combobox", { name: "Event type" }));
      await user.type(await screen.findByPlaceholderText("Search event types…"), "inv");
      const option = await screen.findByRole("option", { name: /Fee note.*invoice\.sent/ });
      expect(screen.queryByRole("option", { name: /Matter updated/ })).not.toBeInTheDocument();
      await user.click(option);

      await waitFor(() => expect(pushMock).toHaveBeenCalled());
      expect(pushedUrl()).toContain("eventType=invoice.sent");
    });

    it("Enter commits the typed prefix through the 'Starts with' item", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab();

      const user = userEvent.setup();
      await user.click(screen.getByRole("combobox", { name: "Event type" }));
      const search = await screen.findByPlaceholderText("Search event types…");
      await user.type(search, "proj");
      expect(screen.getByRole("option", { name: /Starts with “proj”/ })).toBeInTheDocument();
      await user.keyboard("{Enter}");

      await waitFor(() => expect(pushMock).toHaveBeenCalled());
      expect(pushedUrl()).toContain("eventType=proj");
      expect(pushedUrl()).not.toContain("eventType=project.updated");
    });

    it("Enter on an exact key commits that key (the e2e helper flow)", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab();

      const user = userEvent.setup();
      await user.click(screen.getByRole("combobox", { name: "Event type" }));
      const search = await screen.findByPlaceholderText("Search event types…");
      // fireEvent.change instead of a long userEvent.type (5 s CI flake lesson).
      fireEvent.change(search, { target: { value: "matter_closure.closed" } });
      expect(
        await screen.findByRole("option", { name: /Matter closure closed/ })
      ).toBeInTheDocument();
      expect(screen.queryByRole("option", { name: /Starts with/ })).not.toBeInTheDocument();
      await user.keyboard("{Enter}");

      await waitFor(() => expect(pushMock).toHaveBeenCalled());
      expect(pushedUrl()).toContain("eventType=matter_closure.closed");
    });

    it("shows the legal label for the current URL event type", async () => {
      mockListAuditEvents.mockResolvedValue(EMPTY_PAGE);

      await renderTab({ eventType: "matter_closure.closed" });

      const trigger = screen.getByRole("combobox", { name: "Event type" });
      expect(trigger).toHaveAttribute("id", "audit-filter-event-type");
      expect(trigger).toHaveTextContent("Matter closure closed");
    });
  });
});
