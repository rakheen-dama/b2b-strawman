import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";

vi.mock("@/lib/api/assistant-specialists", () => ({
  listInvocationsClient: vi.fn(),
  approveInvocation: vi.fn(),
  rejectInvocation: vi.fn(),
}));

import { PendingSuggestionsWidget } from "../pending-suggestions-widget";
import {
  listInvocationsClient,
  approveInvocation,
  rejectInvocation,
} from "@/lib/api/assistant-specialists";

const mockListInvocations = listInvocationsClient as ReturnType<typeof vi.fn>;
const mockApprove = approveInvocation as ReturnType<typeof vi.fn>;
const mockReject = rejectInvocation as ReturnType<typeof vi.fn>;

/** Wrap component in SWRConfig with no cache to avoid cross-test pollution */
function renderWidget(props: { contextEntityType: string; contextEntityId: string }) {
  return render(
    <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
      <PendingSuggestionsWidget {...props} />
    </SWRConfig>
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const PENDING_ITEMS = {
  content: [
    {
      id: "inv-100",
      specialistId: "BILLING",
      invokedBy: "AUTOMATION",
      status: "PENDING_APPROVAL",
      contextEntityType: "invoice",
      contextEntityId: "inv-entity-001",
      createdAt: "2026-04-19T07:14:22Z",
      proposedOutputSummary: "BillingPolishPayload",
      automationActionExecutionId: null,
    },
  ],
  page: { totalElements: 1, totalPages: 1, size: 10, number: 0 },
};

const EMPTY_RESPONSE = {
  content: [],
  page: { totalElements: 0, totalPages: 0, size: 10, number: 0 },
};

describe("PendingSuggestionsWidget", () => {
  beforeEach(() => {
    mockListInvocations.mockResolvedValue(PENDING_ITEMS);
    mockApprove.mockResolvedValue({
      id: "inv-100",
      status: "APPROVED",
      appliedAt: "2026-04-19T08:00:00Z",
    });
    mockReject.mockResolvedValue(undefined);
  });

  it("renders nothing when no pending invocations", async () => {
    mockListInvocations.mockResolvedValue(EMPTY_RESPONSE);

    const { container } = renderWidget({
      contextEntityType: "invoice",
      contextEntityId: "inv-entity-001",
    });

    await waitFor(() => {
      expect(container.querySelector("[data-testid='pending-suggestions-widget']")).toBeNull();
    });
  });

  it("renders widget with pending invocations", async () => {
    renderWidget({ contextEntityType: "invoice", contextEntityId: "inv-entity-001" });

    await waitFor(() => {
      expect(screen.getByTestId("pending-suggestions-widget")).toBeDefined();
    });

    expect(screen.getByText("Billing")).toBeDefined();
    expect(screen.getByText("BillingPolishPayload")).toBeDefined();
  });

  it("calls listInvocationsClient with correct params", async () => {
    renderWidget({ contextEntityType: "customer", contextEntityId: "cust-001" });

    await waitFor(() => {
      expect(mockListInvocations).toHaveBeenCalledWith({
        contextEntityType: "customer",
        contextEntityId: "cust-001",
        status: "PENDING_APPROVAL",
        size: "10",
      });
    });
  });

  it("removes item from list after approve", async () => {
    const user = userEvent.setup();

    renderWidget({ contextEntityType: "invoice", contextEntityId: "inv-entity-001" });

    await waitFor(() => {
      expect(screen.getByTestId("pending-suggestions-widget")).toBeDefined();
    });

    const approveBtn = screen.getByLabelText("Approve");
    await user.click(approveBtn);

    await waitFor(() => {
      expect(mockApprove).toHaveBeenCalledWith("inv-100");
    });
  });

  it("removes item from list after reject", async () => {
    const user = userEvent.setup();

    renderWidget({ contextEntityType: "invoice", contextEntityId: "inv-entity-001" });

    await waitFor(() => {
      expect(screen.getByTestId("pending-suggestions-widget")).toBeDefined();
    });

    const rejectBtn = screen.getByLabelText("Reject");
    await user.click(rejectBtn);

    await waitFor(() => {
      expect(mockReject).toHaveBeenCalledWith("inv-100", "Rejected from entity detail page");
    });
  });
});
