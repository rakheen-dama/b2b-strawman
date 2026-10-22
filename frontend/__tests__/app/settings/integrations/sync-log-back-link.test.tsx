import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import SyncLogPage from "@/app/(app)/org/[slug]/settings/connections/xero/sync-log/page";

// Phase 87 616D: the merged Connections › Xero tab opens on the integrations hub, so the sync-log
// "Xero Settings" back link must anchor to the Xero section (#xero), as AccountingIntegrationCard does.
const { mockCaps } = vi.hoisted(() => ({ mockCaps: vi.fn() }));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));

vi.mock("@/lib/api/integrations", () => ({
  getSyncEntries: vi.fn().mockResolvedValue({
    content: [],
    page: { totalPages: 0, totalElements: 0, number: 0, size: 20 },
  }),
}));

vi.mock("@/components/integrations/SyncLogTable", () => ({ SyncLogTable: () => null }));

vi.mock("@/app/(app)/org/[slug]/settings/connections/xero/sync-log/sync-log-filters", () => ({
  SyncLogFilters: () => null,
}));

async function renderPage() {
  const ui = await SyncLogPage({
    params: Promise.resolve({ slug: "acme" }),
    searchParams: Promise.resolve({}),
  });
  render(ui);
}

describe("Xero sync log back link", () => {
  beforeEach(() => {
    mockCaps.mockReset();
  });

  afterEach(() => {
    cleanup();
  });

  it("anchors to the Xero section for a user who can view the sync log", async () => {
    mockCaps.mockResolvedValue({ isAdmin: true, isOwner: false, capabilities: [] });
    await renderPage();
    expect(screen.getByRole("link", { name: /Xero Settings/ })).toHaveAttribute(
      "href",
      "/org/acme/settings/connections?tab=xero#xero"
    );
  });

  it("anchors to the Xero section on the permission-denied view", async () => {
    mockCaps.mockResolvedValue({ isAdmin: false, isOwner: false, capabilities: [] });
    await renderPage();
    expect(
      screen.getByText("You do not have permission to view the sync log.")
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Xero Settings/ })).toHaveAttribute(
      "href",
      "/org/acme/settings/connections?tab=xero#xero"
    );
  });
});
