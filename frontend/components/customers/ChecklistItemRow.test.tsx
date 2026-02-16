import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChecklistItemRow } from "./ChecklistItemRow";
import type { ChecklistInstanceItemResponse } from "@/lib/types";

const mockCompleteItem = vi.fn();
const mockSkipItem = vi.fn();
const mockReopenItem = vi.fn();

vi.mock("@/lib/actions/checklists", () => ({
  completeChecklistItem: (...args: unknown[]) => mockCompleteItem(...args),
  skipChecklistItem: (...args: unknown[]) => mockSkipItem(...args),
  reopenChecklistItem: (...args: unknown[]) => mockReopenItem(...args),
}));

function makeItem(overrides: Partial<ChecklistInstanceItemResponse> = {}): ChecklistInstanceItemResponse {
  return {
    id: "item-1",
    instanceId: "inst-1",
    templateItemId: "tmpl-item-1",
    name: "Verify ID Document",
    description: null,
    sortOrder: 0,
    required: true,
    requiresDocument: false,
    requiredDocumentLabel: null,
    status: "PENDING",
    completedAt: null,
    completedBy: null,
    notes: null,
    documentId: null,
    dependsOnItemId: null,
    ...overrides,
  };
}

describe("ChecklistItemRow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows complete button for pending item when admin", () => {
    const item = makeItem({ status: "PENDING" });
    render(
      <ChecklistItemRow item={item} canManage={true} slug="acme" customerId="cust-1" />,
    );

    expect(screen.getByRole("button", { name: /complete/i })).toBeInTheDocument();
  });

  it("shows skip button for pending non-required item when admin", () => {
    const item = makeItem({ status: "PENDING", required: false });
    render(
      <ChecklistItemRow item={item} canManage={true} slug="acme" customerId="cust-1" />,
    );

    expect(screen.getByRole("button", { name: /skip/i })).toBeInTheDocument();
  });

  it("hides skip button for required items", () => {
    const item = makeItem({ status: "PENDING", required: true });
    render(
      <ChecklistItemRow item={item} canManage={true} slug="acme" customerId="cust-1" />,
    );

    expect(screen.queryByRole("button", { name: /skip/i })).not.toBeInTheDocument();
  });

  it("shows reopen button for completed item when admin", () => {
    const item = makeItem({ status: "COMPLETED" });
    render(
      <ChecklistItemRow item={item} canManage={true} slug="acme" customerId="cust-1" />,
    );

    expect(screen.getByRole("button", { name: /reopen/i })).toBeInTheDocument();
  });

  it("calls completeChecklistItem action when complete button clicked", async () => {
    mockCompleteItem.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const item = makeItem({ status: "PENDING" });

    render(
      <ChecklistItemRow item={item} canManage={true} slug="acme" customerId="cust-1" />,
    );

    await user.click(screen.getByRole("button", { name: /complete/i }));

    await waitFor(() => {
      expect(mockCompleteItem).toHaveBeenCalledWith("acme", "cust-1", "item-1");
    });
  });
});
