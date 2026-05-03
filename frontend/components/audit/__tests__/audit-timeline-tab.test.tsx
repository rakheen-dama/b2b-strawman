import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

vi.mock("@/lib/actions/audit-events", () => ({
  fetchEntityAuditPage: vi.fn(),
}));

import { fetchEntityAuditPage } from "@/lib/actions/audit-events";
import { CapabilityProvider } from "@/lib/capabilities";
import { AuditTimelineTab } from "../audit-timeline-tab";

const mocked = vi.mocked(fetchEntityAuditPage);

beforeEach(() => {
  mocked.mockReset();
  mocked.mockResolvedValue({
    content: [],
    page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
  });
});

afterEach(() => {
  cleanup();
});

describe("<AuditTimelineTab>", () => {
  it("hides content when TEAM_OVERSIGHT capability is missing", async () => {
    render(
      <CapabilityProvider capabilities={[]} role="Member" isAdmin={false} isOwner={false}>
        <AuditTimelineTab entityType="customer" entityId="cust-tab-hidden" />
      </CapabilityProvider>
    );

    // Gate yields null — no content, no fetch fired.
    expect(screen.queryByTestId("audit-timeline")).not.toBeInTheDocument();
    expect(screen.queryByTestId("audit-timeline-loading")).not.toBeInTheDocument();
    expect(mocked).not.toHaveBeenCalled();
  });

  it("renders content when TEAM_OVERSIGHT capability is granted", async () => {
    render(
      <CapabilityProvider capabilities={["TEAM_OVERSIGHT"]} role="Admin" isAdmin isOwner={false}>
        <AuditTimelineTab entityType="customer" entityId="cust-tab-visible" />
      </CapabilityProvider>
    );

    await waitFor(() => {
      // Empty timeline with zero events surfaces the empty state.
      expect(screen.getByText("No audit events for this customer")).toBeInTheDocument();
    });
    expect(mocked).toHaveBeenCalledWith("customer", "cust-tab-visible", 0, 20);
  });
});
