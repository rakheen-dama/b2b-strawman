import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NewFromTemplateDialog } from "@/components/templates/NewFromTemplateDialog";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { OrgMember, Client } from "@/lib/types";
import { SUGGESTED_WORK_TYPES } from "@/lib/constants/work-types";

const mockInstantiateTemplate = vi.fn();
const mockCheckEngagement = vi.fn();
const mockRouterPush = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/templates-actions", () => ({
  saveAsTemplateAction: vi.fn(),
  deleteTemplateAction: vi.fn(),
  duplicateTemplateAction: vi.fn(),
  createProjectTemplateAction: vi.fn(),
  updateProjectTemplateAction: vi.fn(),
  updateRequiredCustomerFieldsAction: vi.fn(),
  instantiateTemplateAction: (...args: unknown[]) => mockInstantiateTemplate(...args),
}));

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
    sourceMatterId: null,
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

const CUSTOMERS: Client[] = [
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
      </NewFromTemplateDialog>
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
      </NewFromTemplateDialog>
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
      </NewFromTemplateDialog>
    );
    await user.click(screen.getByText("Open NewFromDialog"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByLabelText("Matter name")).toBeInTheDocument();
    expect(screen.getByLabelText(/Reference Number/)).toHaveAttribute(
      "placeholder",
      "e.g. RAF-2026-001"
    );
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
      </NewFromTemplateDialog>
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
      </NewFromTemplateDialog>
    );
    await user.click(screen.getByText("Open NewFromDialog"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Create Matter" }));
    await waitFor(() => {
      expect(mockInstantiateTemplate).toHaveBeenCalledWith(
        "acme",
        "pt-1",
        expect.objectContaining({})
      );
    });
    await waitFor(() => {
      expect(mockRouterPush).toHaveBeenCalledWith("/org/acme/matters/proj-new");
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
      </NewFromTemplateDialog>
    );
    await user.click(screen.getByText("Open NewFromDialog"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Create Matter" }));
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
      </NewFromTemplateDialog>
    );
    await user.click(screen.getByText("Open Dialog prereq test"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    // Select client
    const clientSelect = screen.getByLabelText(/client/i);
    await user.selectOptions(clientSelect, "c-1");
    // Try to create
    await user.click(screen.getByRole("button", { name: "Create Matter" }));
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
      </NewFromTemplateDialog>
    );
    await user.click(screen.getByText("Open Dialog no customer"));
    await user.click(screen.getByText("Monthly Bookkeeping"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    // Do NOT select a customer — leave as None
    await user.click(screen.getByRole("button", { name: "Create Matter" }));
    await waitFor(() => {
      expect(mockInstantiateTemplate).toHaveBeenCalled();
    });
    expect(mockCheckEngagement).not.toHaveBeenCalled();
  });
});

describe("NewFromTemplateDialog work type prefill", () => {
  const WORK_TYPE_TEMPLATES: ProjectTemplateResponse[] = [
    { ...TEMPLATES[0], id: "pt-conv", name: "Property Transfer", workType: "CONVEYANCING" },
    { ...TEMPLATES[0], id: "pt-est", name: "Deceased Estate", workType: "ESTATES" },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  function renderDialog() {
    render(
      <NewFromTemplateDialog
        slug="acme"
        templates={WORK_TYPE_TEMPLATES}
        orgMembers={MEMBERS}
        customers={CUSTOMERS}
      >
        <button>Open work type dialog</button>
      </NewFromTemplateDialog>
    );
  }

  it("prefills Work Type from the selected template and submits it", async () => {
    mockInstantiateTemplate.mockResolvedValue({ success: true, projectId: "proj-new" });
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByText("Open work type dialog"));
    await user.click(screen.getByText("Property Transfer"));
    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(screen.getByLabelText(/work type/i)).toHaveValue("CONVEYANCING");

    await user.click(screen.getByRole("button", { name: "Create Matter" }));
    await waitFor(() => {
      expect(mockInstantiateTemplate).toHaveBeenCalledWith(
        "acme",
        "pt-conv",
        expect.objectContaining({ workType: "CONVEYANCING" })
      );
    });
  });

  it("does not overwrite a Work Type the user typed when the template changes", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByText("Open work type dialog"));
    await user.click(screen.getByText("Property Transfer"));
    await user.click(screen.getByRole("button", { name: "Next" }));

    const workTypeInput = screen.getByLabelText(/work type/i);
    await user.clear(workTypeInput);
    await user.type(workTypeInput, "LITIGATION");

    await user.click(screen.getByRole("button", { name: "Back" }));
    await user.click(screen.getByText("Deceased Estate"));
    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(screen.getByLabelText(/work type/i)).toHaveValue("LITIGATION");
  });

  it("follows the template while the user has not edited Work Type", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByText("Open work type dialog"));
    await user.click(screen.getByText("Property Transfer"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Back" }));
    await user.click(screen.getByText("Deceased Estate"));
    await user.click(screen.getByRole("button", { name: "Next" }));

    expect(screen.getByLabelText(/work type/i)).toHaveValue("ESTATES");
  });
  it("shows the inherited template work type when Work Type is left blank", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByText("Open work type dialog"));
    await user.click(screen.getByText("Property Transfer"));
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.queryByText("Inherits CONVEYANCING from the template")).not.toBeInTheDocument();

    const workTypeInput = screen.getByLabelText(/work type/i);
    await user.clear(workTypeInput);
    expect(workTypeInput).toHaveAttribute("placeholder", "CONVEYANCING");
    expect(screen.getByText("Inherits CONVEYANCING from the template")).toBeInTheDocument();
  });

  it("offers the canonical legal matter types as Work Type suggestions", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByText("Open work type dialog"));
    await user.click(screen.getByText("Property Transfer"));
    await user.click(screen.getByRole("button", { name: "Next" }));

    const workTypeInput = screen.getByLabelText(/work type/i);
    const listId = workTypeInput.getAttribute("list");
    expect(listId).toBeTruthy();
    const options = document.getElementById(listId!)?.querySelectorAll("option") ?? [];
    expect(Array.from(options, (o) => o.value)).toEqual([...SUGGESTED_WORK_TYPES]);
  });
});
