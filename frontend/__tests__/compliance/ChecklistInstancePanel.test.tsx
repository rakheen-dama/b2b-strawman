import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChecklistInstancePanel } from "@/components/compliance/ChecklistInstancePanel";
import type { ChecklistInstanceResponse, ChecklistTemplateResponse } from "@/lib/types";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: vi.fn() }),
}));

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/customers/[id]/checklist-actions", () => ({
  completeChecklistItem: vi.fn().mockResolvedValue({ success: true }),
  skipChecklistItem: vi.fn().mockResolvedValue({ success: true }),
  reopenChecklistItem: vi.fn().mockResolvedValue({ success: true }),
  instantiateChecklist: vi.fn().mockResolvedValue({ success: true }),
}));

const mockInstances: ChecklistInstanceResponse[] = [
  {
    id: "inst-1",
    templateId: "tpl-1",
    customerId: "c1",
    status: "IN_PROGRESS",
    startedAt: "2026-02-18T10:00:00Z",
    completedAt: null,
    completedBy: null,
    items: [
      {
        id: "item-1",
        instanceId: "inst-1",
        templateItemId: "ti-1",
        name: "Verify identity document",
        description: "Check passport or ID",
        sortOrder: 1,
        required: true,
        requiresDocument: true,
        requiredDocumentLabel: "Certified copy of ID",
        status: "COMPLETED",
        completedAt: "2026-02-18T11:00:00Z",
        completedBy: "m1",
        notes: "Verified in person",
        documentId: "doc-1",
        dependsOnItemId: null,
        createdAt: "2026-02-18T10:00:00Z",
        updatedAt: "2026-02-18T11:00:00Z",
      },
      {
        id: "item-2",
        instanceId: "inst-1",
        templateItemId: "ti-2",
        name: "Sign engagement letter",
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
        createdAt: "2026-02-18T10:00:00Z",
        updatedAt: "2026-02-18T10:00:00Z",
      },
      {
        id: "item-3",
        instanceId: "inst-1",
        templateItemId: "ti-3",
        name: "Optional welcome call",
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
        createdAt: "2026-02-18T10:00:00Z",
        updatedAt: "2026-02-18T10:00:00Z",
      },
    ],
    createdAt: "2026-02-18T10:00:00Z",
    updatedAt: "2026-02-18T10:00:00Z",
  },
];

const mockTemplates: ChecklistTemplateResponse[] = [
  {
    id: "tpl-1",
    name: "Generic Onboarding",
    slug: "generic-onboarding",
    description: "Standard onboarding checklist",
    customerType: "ANY",
    source: "PLATFORM",
    packId: "generic-onboarding",
    active: true,
    autoInstantiate: true,
    sortOrder: 1,
    items: [],
    createdAt: "2026-02-18T10:00:00Z",
    updatedAt: "2026-02-18T10:00:00Z",
  },
];

const templateNames: Record<string, string> = { "tpl-1": "Generic Onboarding" };

describe("ChecklistInstancePanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders empty state when instances array is empty", () => {
    render(
      <ChecklistInstancePanel
        customerId="c1"
        instances={[]}
        isAdmin={false}
        slug="acme"
        templateNames={{}}
      />,
    );
    expect(screen.getByText("No checklists yet.")).toBeInTheDocument();
  });

  it("renders instance template name and status badge", () => {
    render(
      <ChecklistInstancePanel
        customerId="c1"
        instances={mockInstances}
        isAdmin={false}
        slug="acme"
        templateNames={templateNames}
      />,
    );
    expect(screen.getByText("Generic Onboarding")).toBeInTheDocument();
    expect(screen.getByText("In Progress")).toBeInTheDocument();
  });

  it("shows progress bar with correct counts", () => {
    render(
      <ChecklistInstancePanel
        customerId="c1"
        instances={mockInstances}
        isAdmin={false}
        slug="acme"
        templateNames={templateNames}
      />,
    );
    // 1 completed out of 3 total, 1 required completed out of 2 required
    expect(screen.getByText("1/3 completed (1/2 required)")).toBeInTheDocument();
  });

  it("shows 'Manually Add Checklist' button when isAdmin is true", () => {
    render(
      <ChecklistInstancePanel
        customerId="c1"
        instances={mockInstances}
        isAdmin={true}
        slug="acme"
        templateNames={templateNames}
        templates={mockTemplates}
      />,
    );
    expect(screen.getByText("Manually Add Checklist")).toBeInTheDocument();
  });

  it("hides 'Manually Add Checklist' button when isAdmin is false", () => {
    render(
      <ChecklistInstancePanel
        customerId="c1"
        instances={mockInstances}
        isAdmin={false}
        slug="acme"
        templateNames={templateNames}
      />,
    );
    expect(screen.queryByText("Manually Add Checklist")).not.toBeInTheDocument();
  });
});
