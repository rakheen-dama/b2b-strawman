import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OverflowActionsMenu } from "@/components/projects/overflow-actions-menu";
import type { Project, TagResponse, TemplateListResponse } from "@/lib/types";

// ---- Mocks ----

// Mock terminology
vi.mock("@/lib/terminology", () => ({
  useTerminology: () => ({
    t: (key: string) => key,
  }),
}));

// Mock org-profile (controls module gates like disbursements)
const mockIsModuleEnabled = vi.fn(() => false);
vi.mock("@/lib/org-profile", () => ({
  useOrgProfile: () => ({
    verticalProfile: null,
    enabledModules: [],
    terminologyNamespace: null,
    isModuleEnabled: mockIsModuleEnabled,
  }),
}));

// Mock capabilities (controls capability gates)
const mockHasCapability = vi.fn(() => false);
vi.mock("@/lib/capabilities", () => ({
  RequiresCapability: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useCapabilities: () => ({
    capabilities: new Set(),
    role: "Admin",
    isAdmin: true,
    isOwner: true,
    hasCapability: mockHasCapability,
  }),
}));

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/projects/actions", () => ({
  archiveProject: vi.fn(),
}));

// Mock dialog components as simple fragments (they render nothing without open=true)
vi.mock("@/components/projects/edit-project-dialog", () => ({
  EditProjectDialog: () => <div data-testid="edit-project-dialog" />,
}));

vi.mock("@/components/projects/delete-project-dialog", () => ({
  DeleteProjectDialog: () => <div data-testid="delete-project-dialog" />,
}));

vi.mock("@/components/templates/SaveAsTemplateDialog", () => ({
  SaveAsTemplateDialog: () => <div data-testid="save-template-dialog" />,
}));

vi.mock("@/components/proposals/create-proposal-dialog", () => ({
  CreateProposalDialog: () => <div data-testid="create-proposal-dialog" />,
}));

// Mock standalone components rendered outside dropdown
vi.mock("@/components/templates/GenerateDocumentDropdown", () => ({
  GenerateDocumentDropdown: () => (
    <button data-testid="generate-document-dropdown">Generate Document</button>
  ),
}));

vi.mock("@/components/projects/generate-statement-action", () => ({
  GenerateStatementOfAccountAction: () => (
    <button data-testid="generate-statement-action">Generate Statement</button>
  ),
}));

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }),
}));

// ---- Test Data ----

const mockProject: Project = {
  id: "proj-1",
  name: "Test Matter Alpha",
  description: "A test project",
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

const mockTemplates: TemplateListResponse[] = [
  { id: "tpl-1", name: "Invoice Template", format: "HTML" } as TemplateListResponse,
];

const defaultProps = {
  slug: "test-org",
  projectId: "proj-1",
  projectName: "Test Matter Alpha",
  projectStatus: "ACTIVE" as const,
  canEdit: true,
  canManage: true,
  isAdmin: true,
  isOwner: true,
  templates: mockTemplates,
  primaryCustomer: { id: "cust-1", name: "Acme Corp", email: "acme@test.com" },
  projectTags: [] as TagResponse[],
  project: mockProject,
  tasks: [],
  primaryCustomerLifecycleStatus: "ACTIVE",
};

// ---- Tests ----

describe("OverflowActionsMenu", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsModuleEnabled.mockReturnValue(false);
    mockHasCapability.mockReturnValue(false);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders the MoreHorizontal trigger button", () => {
    render(<OverflowActionsMenu {...defaultProps} />);
    expect(screen.getByTestId("overflow-actions-trigger")).toBeInTheDocument();
  });

  it('shows "Edit Project" when canEdit is true and hides when false', async () => {
    const user = userEvent.setup();

    // canEdit = true — Edit should be visible
    const { unmount } = render(<OverflowActionsMenu {...defaultProps} canEdit={true} />);
    await user.click(screen.getByTestId("overflow-actions-trigger"));
    expect(screen.getByText(/^Edit Project$/)).toBeInTheDocument();
    unmount();
    cleanup();

    // canEdit = false — Edit should not be visible
    render(<OverflowActionsMenu {...defaultProps} canEdit={false} />);
    await user.click(screen.getByTestId("overflow-actions-trigger"));
    expect(screen.queryByText(/^Edit Project$/)).not.toBeInTheDocument();
  });

  it('hides "Delete Project" when isOwner is false and shows when true', async () => {
    const user = userEvent.setup();

    // isOwner = false — Delete should not be visible
    const { unmount } = render(<OverflowActionsMenu {...defaultProps} isOwner={false} />);
    await user.click(screen.getByTestId("overflow-actions-trigger"));
    expect(screen.queryByText(/^Delete/)).not.toBeInTheDocument();
    unmount();
    cleanup();

    // isOwner = true — Delete should be visible
    render(<OverflowActionsMenu {...defaultProps} isOwner={true} />);
    await user.click(screen.getByTestId("overflow-actions-trigger"));
    expect(screen.getByText(/^Delete/)).toBeInTheDocument();
  });

  it('hides "Generate Document" button when templates array is empty', () => {
    // templates not empty — GenerateDocumentDropdown should render
    const { unmount } = render(<OverflowActionsMenu {...defaultProps} templates={mockTemplates} />);
    expect(screen.getByTestId("generate-document-dropdown")).toBeInTheDocument();
    unmount();
    cleanup();

    // templates empty — GenerateDocumentDropdown should not render
    render(<OverflowActionsMenu {...defaultProps} templates={[]} />);
    expect(screen.queryByTestId("generate-document-dropdown")).not.toBeInTheDocument();
  });

  it("renders GenerateStatementOfAccountAction (gating delegated to component)", () => {
    // The GenerateStatementOfAccountAction self-gates internally via useOrgProfile.
    // We verify it's always rendered — the component itself handles module/capability gating.
    render(<OverflowActionsMenu {...defaultProps} />);
    expect(screen.getByTestId("generate-statement-action")).toBeInTheDocument();
  });
});
