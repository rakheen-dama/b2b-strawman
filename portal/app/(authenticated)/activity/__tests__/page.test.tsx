import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/activity",
}));

const mockPortalGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
}));

import ActivityPage from "@/app/(authenticated)/activity/page";

const mockEvents = {
  content: [
    {
      id: "evt-1",
      eventType: "portal.document.downloaded",
      actorType: "PORTAL_CONTACT",
      actorName: null,
      entityId: "doc-1",
      entityType: "document",
      projectId: "proj-1",
      summary: "Document downloaded",
      occurredAt: new Date(Date.now() - 60_000).toISOString(),
    },
    {
      id: "evt-2",
      eventType: "invoice.payment_recorded",
      actorType: "USER",
      actorName: "Alice",
      entityId: "inv-1",
      entityType: "invoice",
      projectId: "proj-1",
      summary: "Payment recorded",
      occurredAt: new Date(Date.now() - 3_600_000).toISOString(),
    },
  ],
  page: { totalElements: 2 },
};

describe("ActivityPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it("renders both tabs and refetches with FIRM when 'Firm actions' is clicked", async () => {
    mockPortalGet.mockResolvedValue(mockEvents);

    render(<ActivityPage />);

    expect(screen.getByText("Activity")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Your actions" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Firm actions" })).toBeInTheDocument();

    // Initial fetch is for the default tab (mine)
    await waitFor(() => {
      expect(mockPortalGet).toHaveBeenCalledWith("/portal/activity?tab=MINE");
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole("tab", { name: "Firm actions" }));

    await waitFor(() => {
      expect(mockPortalGet).toHaveBeenCalledWith("/portal/activity?tab=FIRM");
    });
  });

  it("shows the empty state when the response is empty", async () => {
    mockPortalGet.mockResolvedValue({ content: [], page: { totalElements: 0 } });

    render(<ActivityPage />);

    await waitFor(() => {
      expect(screen.getByText("No activity yet.")).toBeInTheDocument();
    });
  });

  it("renders rows with summaries when events load", async () => {
    mockPortalGet.mockResolvedValue(mockEvents);

    render(<ActivityPage />);

    await waitFor(() => {
      expect(screen.getByText("Document downloaded")).toBeInTheDocument();
      expect(screen.getByText("Payment recorded")).toBeInTheDocument();
    });

    // PORTAL_CONTACT actor renders as "You"; USER actor renders the actor name.
    expect(screen.getByText("You")).toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();
  });
});
