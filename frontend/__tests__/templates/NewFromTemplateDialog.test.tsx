import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NewFromTemplateDialog } from "@/components/templates/NewFromTemplateDialog";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { OrgMember, Customer } from "@/lib/types";

const mockInstantiateTemplate = vi.fn();
const mockCheckEngagement = vi.fn();
const mockRouterPush = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/project-templates/actions",
  () => ({
    saveAsTemplateAction: vi.fn(),
    deleteTemplateAction: vi.fn(),
    duplicateTemplateAction: vi.fn(),
    createProjectTemplateAction: vi.fn(),
    updateProjectTemplateAction: vi.fn(),
    updateRequiredCustomerFieldsAction: vi.fn(),
    instantiateTemplateAction: (...args: unknown[]) =>
      mockInstantiateTemplate(...args),
  }),
);

vi.mock("@/lib/actions/prerequisite-actions", () => ({
  checkEngagementPrerequisitesAction: (...args: unknown[]) => mockCheckEngagement(...args),
}));

vi.mock("@/components/prerequisite/prerequisite-modal", () => ({
  PrerequisiteModal: ({ open, onResolved }: { open: boolean; onResolved: () => void }) =>
    open ? (
      <div data-testid="prerequisite-modal">
        <button onClick={onResolved}>Resolve Prerequisites</button>
      </div>
    ) : null,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockRouterPush, refresh: vi.fn() }),
}));

const TEMPLATES: ProjectTemplateResponse[] = [
  {
    id: "pt-1",
    name: "Monthly Bookkeeping",
    namePattern: "{customer} — {month} {year}",
    description: "Monthly bookkeeping template",
    billableDefault: true,
    source: "MANUAL",
    sourceProjectId: null,
    active: true,
    taskCount: 3,
    tagCount: 0,
    tasks: [],
    tags: [],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

const MEMBERS: OrgMember[] = [
  {
    id: "m-1",
    name: "Jane Smith",
    email: "jane@example.com",
    avatarUrl: null,
    orgRole: "org:admin",
  },
];

const CUSTOMERS: Customer[] = [
  {
    id: "c-1",
    name: "Acme Corp",
    email: "acme@example.com",
    phone: null,
    idNumber: null,
    status: "ACTIVE",
    notes: null,
    createdBy: "m-1",
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

describe("NewFromTemplateDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("opens dialog and shows step 1 template picker", async () => {
    const user = userEvent.setup();
    render(
      <NewFromTemplateDialog
        slug="acme"
        templates={TEMPLATES}
        orgMembers={MEMBERS}
        customers={CUSTOMERS}
      >
        <button>New from Template trigger</button>
      </NewFromTemplateDialog>,
    );
    await user.click(screen.getByText("New from Template trigger"));
    expect(screen.getByText("Monthly Bookkeeping")).toBeInTheDocument();
  });

  it("Next button disabled until template selected", async () => {
    const user = userEvent.setup();
    render(
      <NewFromTemplateDialog
        slug="acme"
        templates={TEMPLATES}
        orgMembers={MEMBERS}
        customers={CUSTOMERS}
      >
        <button>Open NewFromDialog</button>
      </NewFromTemplateDialog>,
    );
    await user.click(screen.getByText("Open NewFromDialog"));
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
    await user.click(screen.getByText("Monthly Bookkeeping"));
    expect(screen.getByRole("button", { name: "Next" })).not.toBeDisabled();
  });

  it("advances to step 2 after selecting template and clicking Next", async () => {
    const user = userEvent.setup();
    render(
      <NewFromTemplateDialog
        slug="acme"
        templates={TEMPLATES}
        orgMembers={MEMBERS}
        customers={CUSTOMERS}
      >
        <button>Open NewFromDialog</button>
      </NewFromTemplateDialog>,
    );
    await user.click(screen.getByText("Open NewFromDialog"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByLabelText("Project name")).toBeInTheDocument();
  });

  it("Back button returns to step 1", async () => {
    const user = userEvent.setup();
    render(
      <NewFromTemplateDialog
        slug="acme"
        templates={TEMPLATES}
        orgMembers={MEMBERS}
        customers={CUSTOMERS}
      >
        <button>Open NewFromDialog</button>
      </NewFromTemplateDialog>,
    );
    await user.click(screen.getByText("Open NewFromDialog"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Back" }));
    expect(screen.getByText("Monthly Bookkeeping")).toBeInTheDocument();
  });

  it("calls instantiateTemplateAction and redirects on success", async () => {
    mockInstantiateTemplate.mockResolvedValue({
      success: true,
      projectId: "proj-new",
    });
    const user = userEvent.setup();
    render(
      <NewFromTemplateDialog
        slug="acme"
        templates={TEMPLATES}
        orgMembers={MEMBERS}
        customers={CUSTOMERS}
      >
        <button>Open NewFromDialog</button>
      </NewFromTemplateDialog>,
    );
    await user.click(screen.getByText("Open NewFromDialog"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(
      screen.getByRole("button", { name: "Create Project" }),
    );
    await waitFor(() => {
      expect(mockInstantiateTemplate).toHaveBeenCalledWith(
        "acme",
        "pt-1",
        expect.objectContaining({}),
      );
    });
    await waitFor(() => {
      expect(mockRouterPush).toHaveBeenCalledWith(
        "/org/acme/projects/proj-new",
      );
    });
  });

  it("shows error message on failure", async () => {
    mockInstantiateTemplate.mockResolvedValue({
      success: false,
      error: "Template is inactive",
    });
    const user = userEvent.setup();
    render(
      <NewFromTemplateDialog
        slug="acme"
        templates={TEMPLATES}
        orgMembers={MEMBERS}
        customers={CUSTOMERS}
      >
        <button>Open NewFromDialog</button>
      </NewFromTemplateDialog>,
    );
    await user.click(screen.getByText("Open NewFromDialog"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(
      screen.getByRole("button", { name: "Create Project" }),
    );
    await waitFor(() => {
      expect(screen.getByText("Template is inactive")).toBeInTheDocument();
    });
  });

  it("opens PrerequisiteModal when engagement prerequisites are not met", async () => {
    mockCheckEngagement.mockResolvedValue({
      passed: false,
      context: "PROJECT_CREATION",
      violations: [
        {
          code: "MISSING_FIELD",
          message: "VAT number is required",
          entityType: "CUSTOMER",
          entityId: "c-1",
          fieldSlug: "vat_number",
          groupName: null,
          resolution: "Enter VAT number",
        },
      ],
    });
    const user = userEvent.setup();
    render(
      <NewFromTemplateDialog
        slug="acme"
        templates={TEMPLATES}
        orgMembers={MEMBERS}
        customers={CUSTOMERS}
      >
        <button>Open Dialog prereq test</button>
      </NewFromTemplateDialog>,
    );
    await user.click(screen.getByText("Open Dialog prereq test"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    // Select customer
    const customerSelect = screen.getByLabelText(/customer/i);
    await user.selectOptions(customerSelect, "c-1");
    // Try to create
    await user.click(screen.getByRole("button", { name: "Create Project" }));
    await waitFor(() => {
      expect(screen.getByTestId("prerequisite-modal")).toBeInTheDocument();
    });
    expect(mockInstantiateTemplate).not.toHaveBeenCalled();
  });

  it("skips prerequisite check when no customer is selected", async () => {
    mockInstantiateTemplate.mockResolvedValue({ success: true, projectId: "proj-new" });
    const user = userEvent.setup();
    render(
      <NewFromTemplateDialog
        slug="acme"
        templates={TEMPLATES}
        orgMembers={MEMBERS}
        customers={CUSTOMERS}
      >
        <button>Open Dialog no customer</button>
      </NewFromTemplateDialog>,
    );
    await user.click(screen.getByText("Open Dialog no customer"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    // Do NOT select a customer — leave as None
    await user.click(screen.getByRole("button", { name: "Create Project" }));
    await waitFor(() => {
      expect(mockInstantiateTemplate).toHaveBeenCalled();
    });
    expect(mockCheckEngagement).not.toHaveBeenCalled();
  });
});
