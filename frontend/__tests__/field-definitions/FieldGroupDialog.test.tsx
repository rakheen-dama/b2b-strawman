import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FieldGroupDialog } from "@/components/field-definitions/FieldGroupDialog";
import type { FieldDefinitionResponse } from "@/lib/types";

const mockCreateFieldGroup = vi.fn();
const mockUpdateFieldGroup = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/custom-fields/actions",
  () => ({
    createFieldGroupAction: (...args: unknown[]) =>
      mockCreateFieldGroup(...args),
    updateFieldGroupAction: (...args: unknown[]) =>
      mockUpdateFieldGroup(...args),
  }),
);

function makeAvailableFields(): FieldDefinitionResponse[] {
  return [
    {
      id: "fd-1",
      entityType: "PROJECT",
      name: "Budget Code",
      slug: "budget_code",
      fieldType: "TEXT",
      description: null,
      required: false,
      defaultValue: null,
      options: null,
      validation: null,
      sortOrder: 1,
      packId: null,
      packFieldKey: null,
      active: true,
      createdAt: "2025-01-15T10:00:00Z",
      updatedAt: "2025-01-15T10:00:00Z",
    },
    {
      id: "fd-2",
      entityType: "PROJECT",
      name: "Priority Level",
      slug: "priority_level",
      fieldType: "DROPDOWN",
      description: null,
      required: false,
      defaultValue: null,
      options: null,
      validation: null,
      sortOrder: 2,
      packId: null,
      packFieldKey: null,
      active: true,
      createdAt: "2025-01-15T10:00:00Z",
      updatedAt: "2025-01-15T10:00:00Z",
    },
  ];
}

describe("FieldGroupDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders form fields when opened", async () => {
    const user = userEvent.setup();

    render(
      <FieldGroupDialog
        slug="acme"
        entityType="PROJECT"
        availableFields={makeAvailableFields()}
      >
        <button>Open Group Dialog</button>
      </FieldGroupDialog>,
    );

    await user.click(screen.getByText("Open Group Dialog"));

    expect(screen.getByLabelText("Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Slug")).toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toBeInTheDocument();
    expect(screen.getByLabelText("Sort Order")).toBeInTheDocument();
    expect(screen.getByText("Fields")).toBeInTheDocument();
  });

  it("calls createFieldGroupAction on submit", async () => {
    mockCreateFieldGroup.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <FieldGroupDialog
        slug="acme"
        entityType="PROJECT"
        availableFields={makeAvailableFields()}
      >
        <button>Open Create Group Dialog</button>
      </FieldGroupDialog>,
    );

    await user.click(screen.getByText("Open Create Group Dialog"));
    await user.type(screen.getByLabelText("Name"), "Test Group");
    await user.click(screen.getByRole("button", { name: "Create Group" }));

    await waitFor(() => {
      expect(mockCreateFieldGroup).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          entityType: "PROJECT",
          name: "Test Group",
          sortOrder: 0,
          fieldDefinitionIds: [],
        }),
      );
    });
  });
});
