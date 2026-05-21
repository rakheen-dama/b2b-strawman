import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MatterSidebar } from "@/components/projects/matter-sidebar";
import type { Project, Customer, TagResponse } from "@/lib/types";

// Mock child components to avoid complex Radix UI portal issues
vi.mock("@/components/field-definitions/CustomFieldSection", () => ({
  CustomFieldSection: (props: Record<string, unknown>) => (
    <div data-testid="custom-field-section" data-entity-id={props.entityId} />
  ),
}));

vi.mock("@/components/field-definitions/FieldGroupSelector", () => ({
  FieldGroupSelector: (props: Record<string, unknown>) => (
    <div data-testid="field-group-selector" data-entity-id={props.entityId} />
  ),
}));

vi.mock("@/components/tags/TagInput", () => ({
  TagInput: (props: Record<string, unknown>) => (
    <div data-testid="tag-input" data-entity-id={props.entityId} />
  ),
}));

vi.mock("@/components/ui/expandable-text", () => ({
  ExpandableText: ({ text }: { text: string | null | undefined }) =>
    text ? <div data-testid="expandable-text">{text}</div> : null,
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Mock lifecycle action components (they import server actions which trigger server-only)
vi.mock("@/components/projects/project-lifecycle-actions", () => ({
  ProjectLifecycleActions: () => <div data-testid="project-lifecycle-actions" />,
}));

vi.mock("@/components/projects/matter-closure-action", () => ({
  MatterClosureAction: () => <div data-testid="matter-closure-action" />,
}));

vi.mock("@/components/projects/matter-reopen-action", () => ({
  MatterReopenAction: () => <div data-testid="matter-reopen-action" />,
}));

const mockProject: Project = {
  id: "proj-1",
  name: "Test Matter Alpha",
  description: "A test project description",
  status: "ACTIVE",
  customerId: "cust-1",
  dueDate: "2099-12-31",
  referenceNumber: "REF-001",
  priority: "HIGH",
  workType: "Litigation",
  createdBy: "user-1",
  createdByName: "Alice",
  createdAt: "2026-01-15T10:00:00Z",
  updatedAt: "2026-01-20T10:00:00Z",
  completedAt: null,
  completedBy: null,
  completedByName: null,
  archivedAt: null,
  closedAt: null,
  retentionClockStartedAt: null,
  retentionEndsOn: null,
  projectRole: "lead",
  customFields: {},
  appliedFieldGroups: [],
};

const mockCustomers: Customer[] = [
  {
    id: "cust-1",
    name: "Acme Corp",
    email: "acme@example.com",
    lifecycleStatus: "ACTIVE",
  } as Customer,
];

const mockTags: TagResponse[] = [{ id: "tag-1", name: "urgent", color: "#ff0000" } as TagResponse];

const defaultProps = {
  project: mockProject,
  customers: mockCustomers,
  slug: "test-org",
  canEdit: true,
  canManage: true,
  isAdmin: true,
  isOwner: false,
  fieldDefinitions: [],
  fieldGroups: [],
  groupMembers: {},
  projectTags: mockTags,
  allTags: mockTags,
};

describe("MatterSidebar", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders project name", () => {
    render(<MatterSidebar {...defaultProps} />);
    expect(screen.getByTestId("matter-sidebar")).toBeInTheDocument();
    expect(screen.getByText("Test Matter Alpha")).toBeInTheDocument();
  });

  it("renders status badge", () => {
    render(<MatterSidebar {...defaultProps} />);
    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("renders client name as link", () => {
    render(<MatterSidebar {...defaultProps} />);
    const link = screen.getByText("Acme Corp");
    expect(link).toBeInTheDocument();
    expect(link.closest("a")).toHaveAttribute("href", "/org/test-org/customers/cust-1");
  });

  it("renders custom fields section when field definitions exist", () => {
    render(
      <MatterSidebar
        {...defaultProps}
        fieldDefinitions={[{ id: "fd-1", slug: "matter-type", label: "Matter Type" } as never]}
      />
    );
    expect(screen.getByTestId("custom-field-section")).toBeInTheDocument();
  });

  it("hides custom fields section when no definitions, groups, or applied groups", () => {
    render(<MatterSidebar {...defaultProps} fieldDefinitions={[]} fieldGroups={[]} />);
    expect(screen.queryByTestId("custom-field-section")).not.toBeInTheDocument();
  });

  it("renders tags section", () => {
    render(<MatterSidebar {...defaultProps} />);
    expect(screen.getByTestId("tag-input")).toBeInTheDocument();
  });

  it("renders sticky footer with lifecycle action", () => {
    render(<MatterSidebar {...defaultProps} />);
    expect(screen.getByTestId("sidebar-footer")).toBeInTheDocument();
    expect(screen.getByTestId("sidebar-lifecycle-action")).toBeInTheDocument();
  });
});
