import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock motion/react (Dialog/AlertDialog use motion internally)
vi.mock("motion/react", () => ({
  motion: {
    div: ({
      children,
      ...props
    }: React.PropsWithChildren<Record<string, unknown>>) => {
      const { initial, animate, transition, ...rest } = props;
      return <div {...rest}>{children}</div>;
    },
  },
}));

// Mock server actions
const mockCreateRoleAction = vi.fn();
const mockUpdateRoleAction = vi.fn();
const mockDeleteRoleAction = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/roles/actions", () => ({
  createRoleAction: (...args: unknown[]) => mockCreateRoleAction(...args),
  updateRoleAction: (...args: unknown[]) => mockUpdateRoleAction(...args),
  deleteRoleAction: (...args: unknown[]) => mockDeleteRoleAction(...args),
}));

import { RoleDialog } from "@/components/roles/role-dialog";
import { DeleteRoleDialog } from "@/components/roles/delete-role-dialog";
import type { OrgRole } from "@/lib/api/org-roles";

function makeRole(overrides: Partial<OrgRole> = {}): OrgRole {
  return {
    id: "role-1",
    name: "Bookkeeper",
    slug: "bookkeeper",
    description: "Can manage invoices",
    capabilities: ["FINANCIAL_VISIBILITY", "INVOICING"],
    isSystem: false,
    memberCount: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("RoleDialog", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("submits valid data in create mode", async () => {
    const user = userEvent.setup();
    mockCreateRoleAction.mockResolvedValue({ success: true });
    const onOpenChange = vi.fn();

    render(
      <RoleDialog slug="acme" open={true} onOpenChange={onOpenChange} />,
    );

    expect(screen.getByText("Create Role")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Name"), "Analyst");
    await user.click(screen.getByLabelText("Financial Visibility"));

    await user.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => {
      expect(mockCreateRoleAction).toHaveBeenCalledWith("acme", {
        name: "Analyst",
        description: undefined,
        capabilities: ["FINANCIAL_VISIBILITY"],
      });
    });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("pre-fills existing data in edit mode", async () => {
    const role = makeRole();

    render(
      <RoleDialog
        slug="acme"
        role={role}
        open={true}
        onOpenChange={() => {}}
      />,
    );

    expect(screen.getByText("Edit Role")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Bookkeeper")).toBeInTheDocument();
    expect(
      screen.getByDisplayValue("Can manage invoices"),
    ).toBeInTheDocument();

    // Financial Visibility and Invoicing should be checked
    const checkboxes = screen.getAllByRole("checkbox");
    const financialCb = checkboxes[0]; // FINANCIAL_VISIBILITY is first
    const invoicingCb = checkboxes[1]; // INVOICING is second
    expect(financialCb).toHaveAttribute("data-state", "checked");
    expect(invoicingCb).toHaveAttribute("data-state", "checked");

    // Project Management should not be checked
    const projectCb = checkboxes[2]; // PROJECT_MANAGEMENT is third
    expect(projectCb).toHaveAttribute("data-state", "unchecked");
  });

  it("updates capabilities in edit mode", async () => {
    const user = userEvent.setup();
    const role = makeRole();
    mockUpdateRoleAction.mockResolvedValue({ success: true });
    const onOpenChange = vi.fn();

    render(
      <RoleDialog
        slug="acme"
        role={role}
        open={true}
        onOpenChange={onOpenChange}
      />,
    );

    // Add Project Management capability
    await user.click(screen.getByLabelText("Project Management"));

    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => {
      expect(mockUpdateRoleAction).toHaveBeenCalledWith("acme", "role-1", {
        name: "Bookkeeper",
        description: "Can manage invoices",
        capabilities: expect.arrayContaining([
          "FINANCIAL_VISIBILITY",
          "INVOICING",
          "PROJECT_MANAGEMENT",
        ]),
      });
    });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });
});

describe("DeleteRoleDialog", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows member count in the warning", () => {
    const role = makeRole({ memberCount: 5 });

    render(
      <DeleteRoleDialog
        slug="acme"
        role={role}
        open={true}
        onOpenChange={() => {}}
      />,
    );

    expect(screen.getByText("Delete Role")).toBeInTheDocument();
    expect(screen.getByText("5 members")).toBeInTheDocument();
  });

  it("disables delete button when members are assigned", () => {
    const role = makeRole({ memberCount: 3 });

    render(
      <DeleteRoleDialog
        slug="acme"
        role={role}
        open={true}
        onOpenChange={() => {}}
      />,
    );

    const deleteButton = screen.getByRole("button", { name: "Delete" });
    expect(deleteButton).toBeDisabled();
  });
});
