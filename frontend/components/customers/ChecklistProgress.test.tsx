import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ChecklistProgress } from "./ChecklistProgress";
import type { ChecklistInstanceResponse, ChecklistInstanceItemResponse } from "@/lib/types";

vi.mock("@/lib/actions/checklists", () => ({
  completeChecklistItem: vi.fn(),
  skipChecklistItem: vi.fn(),
  reopenChecklistItem: vi.fn(),
}));

function makeInstance(
  overrides: Partial<ChecklistInstanceResponse> = {},
): ChecklistInstanceResponse {
  return {
    id: "inst-1",
    templateId: "tmpl-1",
    templateName: "FICA Individual Onboarding",
    customerId: "cust-1",
    status: "IN_PROGRESS",
    startedAt: "2026-01-01T00:00:00Z",
    completedAt: null,
    completedBy: null,
    itemCount: 4,
    completedCount: 2,
    requiredCount: 3,
    requiredCompletedCount: 2,
    ...overrides,
  };
}

function makeItems(): ChecklistInstanceItemResponse[] {
  return [
    {
      id: "item-1",
      instanceId: "inst-1",
      templateItemId: "ti-1",
      name: "Verify ID",
      description: null,
      sortOrder: 0,
      required: true,
      requiresDocument: false,
      requiredDocumentLabel: null,
      status: "COMPLETED",
      completedAt: "2026-01-02T00:00:00Z",
      completedBy: "user-1",
      notes: null,
      documentId: null,
      dependsOnItemId: null,
    },
    {
      id: "item-2",
      instanceId: "inst-1",
      templateItemId: "ti-2",
      name: "Proof of Address",
      description: null,
      sortOrder: 1,
      required: true,
      requiresDocument: true,
      requiredDocumentLabel: "Address Proof",
      status: "COMPLETED",
      completedAt: "2026-01-03T00:00:00Z",
      completedBy: "user-1",
      notes: null,
      documentId: "doc-1",
      dependsOnItemId: null,
    },
    {
      id: "item-3",
      instanceId: "inst-1",
      templateItemId: "ti-3",
      name: "Tax Number",
      description: null,
      sortOrder: 2,
      required: true,
      requiresDocument: false,
      requiredDocumentLabel: null,
      status: "PENDING",
      completedAt: null,
      completedBy: null,
      notes: null,
      documentId: null,
      dependsOnItemId: null,
    },
    {
      id: "item-4",
      instanceId: "inst-1",
      templateItemId: "ti-4",
      name: "Optional Note",
      description: null,
      sortOrder: 3,
      required: false,
      requiresDocument: false,
      requiredDocumentLabel: null,
      status: "PENDING",
      completedAt: null,
      completedBy: null,
      notes: null,
      documentId: null,
      dependsOnItemId: null,
    },
  ];
}

describe("ChecklistProgress", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("displays progress bar with correct percentage", () => {
    const instance = makeInstance({ requiredCompletedCount: 2, requiredCount: 3 });
    render(
      <ChecklistProgress
        instance={instance}
        items={makeItems()}
        canManage={true}
        slug="acme"
        customerId="cust-1"
      />,
    );

    // 2/3 = 67%
    expect(screen.getByText("67%")).toBeInTheDocument();
  });

  it("shows completed badge when all required items done", () => {
    const instance = makeInstance({
      status: "COMPLETED",
      requiredCompletedCount: 3,
      requiredCount: 3,
    });
    render(
      <ChecklistProgress
        instance={instance}
        items={makeItems()}
        canManage={true}
        slug="acme"
        customerId="cust-1"
      />,
    );

    expect(screen.getByText("Completed")).toBeInTheDocument();
  });

  it("shows correct item count summary", () => {
    const instance = makeInstance({ requiredCompletedCount: 2, requiredCount: 3 });
    render(
      <ChecklistProgress
        instance={instance}
        items={makeItems()}
        canManage={true}
        slug="acme"
        customerId="cust-1"
      />,
    );

    expect(screen.getByText("2 of 3 required items completed")).toBeInTheDocument();
  });
});
