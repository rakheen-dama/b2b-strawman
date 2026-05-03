import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("@/lib/actions/audit-events", () => ({
  fetchEntityAuditPage: vi.fn(),
}));

import { fetchEntityAuditPage } from "@/lib/actions/audit-events";
import { CapabilityProvider } from "@/lib/capabilities";
import { AuditHistoryDisclosure } from "../audit-history-disclosure";

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

describe("<AuditHistoryDisclosure>", () => {
  it("renders nothing when TEAM_OVERSIGHT is missing", () => {
    render(
      <CapabilityProvider capabilities={[]} role="Member" isAdmin={false} isOwner={false}>
        <AuditHistoryDisclosure entityType="proposal" entityId="prop-no-cap" />
      </CapabilityProvider>
    );

    expect(screen.queryByTestId("audit-history-disclosure")).not.toBeInTheDocument();
    expect(screen.queryByTestId("audit-history-disclosure-trigger")).not.toBeInTheDocument();
    expect(mocked).not.toHaveBeenCalled();
  });

  it("renders trigger when TEAM_OVERSIGHT is granted", () => {
    render(
      <CapabilityProvider
        capabilities={["TEAM_OVERSIGHT"]}
        role="Admin"
        isAdmin
        isOwner={false}
      >
        <AuditHistoryDisclosure entityType="proposal" entityId="prop-with-cap" />
      </CapabilityProvider>
    );

    expect(screen.getByTestId("audit-history-disclosure")).toBeInTheDocument();
    expect(screen.getByTestId("audit-history-disclosure-trigger")).toBeInTheDocument();
  });

  it("does NOT mount the audit timeline before first expand", () => {
    render(
      <CapabilityProvider
        capabilities={["TEAM_OVERSIGHT"]}
        role="Admin"
        isAdmin
        isOwner={false}
      >
        <AuditHistoryDisclosure entityType="proposal" entityId="prop-lazy" />
      </CapabilityProvider>
    );

    // Trigger renders, but inner timeline is not mounted yet → no fetch.
    expect(screen.getByTestId("audit-history-disclosure-trigger")).toBeInTheDocument();
    expect(screen.queryByTestId("audit-timeline")).not.toBeInTheDocument();
    expect(screen.queryByTestId("audit-timeline-loading")).not.toBeInTheDocument();
    expect(mocked).not.toHaveBeenCalled();
  });

  it("mounts the audit timeline with correct entity props after expand", async () => {
    const user = userEvent.setup();
    render(
      <CapabilityProvider
        capabilities={["TEAM_OVERSIGHT"]}
        role="Admin"
        isAdmin
        isOwner={false}
      >
        <AuditHistoryDisclosure entityType="matter_closure" entityId="closure-42" />
      </CapabilityProvider>
    );

    expect(mocked).not.toHaveBeenCalled();

    await user.click(screen.getByTestId("audit-history-disclosure-trigger"));

    await waitFor(() => {
      expect(mocked).toHaveBeenCalledWith("matter_closure", "closure-42", 0, 20);
    });
  });
});
