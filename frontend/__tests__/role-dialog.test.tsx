import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock motion/react (Dialog/AlertDialog use motion internally)
vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) => {
      const { initial, animate, transition, ...rest } = props;
      return <div {...rest}>{children}</div>;
    },
  },
}));

// Mock server actions
const mockCreateRoleAction = vi.fn();
const mockUpdateRoleAction = vi.fn();
const mockDeleteRoleAction = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/roles-actions", () => ({
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

    render(<RoleDialog slug="acme" open={true} onOpenChange={onOpenChange} />);

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

    render(<RoleDialog slug="acme" role={role} open={true} onOpenChange={() => {}} />);

    expect(screen.getByText("Edit Role")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Bookkeeper")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Can manage invoices")).toBeInTheDocument();

    // Financial Visibility and Billing should be checked
    expect(screen.getByRole("checkbox", { name: /Financial Visibility/i })).toHaveAttribute(
      "data-state",
      "checked"
    );
    expect(screen.getByRole("checkbox", { name: /Billing/i })).toHaveAttribute(
      "data-state",
      "checked"
    );

    // Matter Management should not be checked
    expect(screen.getByRole("checkbox", { name: /Matter Management/i })).toHaveAttribute(
      "data-state",
      "unchecked"
    );
  });

  it("updates capabilities in edit mode", async () => {
    const user = userEvent.setup();
    const role = makeRole();
    mockUpdateRoleAction.mockResolvedValue({ success: true });
    const onOpenChange = vi.fn();

    render(<RoleDialog slug="acme" role={role} open={true} onOpenChange={onOpenChange} />);

    // Add Matter Management capability
    await user.click(screen.getByLabelText("Matter Management"));

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

describe("RoleDialog — K-102: bounded to the viewport", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  // `ui/dialog.tsx` puts DialogContent's className on the card, the first child of the Radix
  // content (the motion.div carries data-slot="dialog-content").
  const card = () =>
    document.querySelector<HTMLElement>('[data-slot="dialog-content"] > div') as HTMLElement;
  const body = () => card().querySelector<HTMLElement>("form > .overflow-y-auto") as HTMLElement;

  it.each([
    ["create", undefined, "Create Role", "Create"],
    ["edit", makeRole(), "Edit Role", "Save"],
  ])(
    "%s: caps the height and scrolls only the body; title and footer stay outside it",
    (_, role, title, submit) => {
      render(<RoleDialog slug="acme" role={role} open={true} onOpenChange={vi.fn()} />);

      expect(card()).toHaveClass("flex", "max-h-[90vh]", "flex-col");
      expect(card()).not.toHaveClass("grid");
      const form = card().querySelector("form") as HTMLFormElement;
      expect(form).toHaveClass("flex", "min-h-0", "flex-1", "flex-col");
      expect(body()).not.toBeNull();
      expect(body()).toHaveClass("min-h-0", "flex-1", "overflow-y-auto");

      // The fields and the capability checkboxes live in the scrolling body…
      expect(body().contains(screen.getByLabelText("Name"))).toBe(true);
      expect(body().contains(screen.getByText("Capabilities"))).toBe(true);
      expect(body().contains(screen.getByTestId("role-capability-FINANCIAL_VISIBILITY"))).toBe(
        true
      );
      // …the title, Cancel, the submit and Close do not.
      expect(body().contains(screen.getByText(title))).toBe(false);
      expect(body().contains(screen.getByRole("button", { name: "Cancel" }))).toBe(false);
      expect(body().contains(screen.getByRole("button", { name: submit }))).toBe(false);
      expect(body().contains(screen.getByRole("button", { name: "Close" }))).toBe(false);
      // The submit stays a real submit inside the form (happy-dom wraps <form> in a proxy, so
      // compare by attribute rather than identity).
      const submitButton = screen.getByRole("button", { name: submit });
      expect(submitButton).toHaveAttribute("type", "submit");
      expect(submitButton.closest("form")).not.toBeNull();
    }
  );
});

describe("DeleteRoleDialog", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows member count in the warning", () => {
    const role = makeRole({ memberCount: 5 });

    render(<DeleteRoleDialog slug="acme" role={role} open={true} onOpenChange={() => {}} />);

    expect(screen.getByText("Delete Role")).toBeInTheDocument();
    expect(screen.getByText("5 members")).toBeInTheDocument();
  });

  it("disables delete button when members are assigned", () => {
    const role = makeRole({ memberCount: 3 });

    render(<DeleteRoleDialog slug="acme" role={role} open={true} onOpenChange={() => {}} />);

    const deleteButton = screen.getByRole("button", { name: "Delete" });
    expect(deleteButton).toBeDisabled();
  });
});
