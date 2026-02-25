import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FieldGroupDialog } from "@/components/field-definitions/FieldGroupDialog";
import type { FieldDefinitionResponse, FieldGroupResponse } from "@/lib/types";

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

  it("renders dependency selector with same entity type groups", async () => {
    const user = userEvent.setup();

    const allGroups: FieldGroupResponse[] = [
      {
        id: "g-1",
        entityType: "PROJECT",
        name: "Base Group",
        slug: "base_group",
        description: null,
        packId: null,
        sortOrder: 0,
        active: true,
        autoApply: false,
        dependsOn: null,
        createdAt: "2025-01-15T10:00:00Z",
        updatedAt: "2025-01-15T10:00:00Z",
      },
      {
        id: "g-2",
        entityType: "PROJECT",
        name: "Another Group",
        slug: "another_group",
        description: null,
        packId: null,
        sortOrder: 1,
        active: true,
        autoApply: false,
        dependsOn: null,
        createdAt: "2025-01-15T10:00:00Z",
        updatedAt: "2025-01-15T10:00:00Z",
      },
    ];

    render(
      <FieldGroupDialog
        slug="acme"
        entityType="PROJECT"
        availableFields={makeAvailableFields()}
        allGroups={allGroups}
      >
        <button>Open Dep Dialog</button>
      </FieldGroupDialog>,
    );

    await user.click(screen.getByText("Open Dep Dialog"));

    // Dependencies section should be visible
    expect(screen.getByText("Dependencies")).toBeInTheDocument();
    // Should have at least two comboboxes (fields + dependencies)
    const comboboxes = screen.getAllByRole("combobox");
    expect(comboboxes.length).toBeGreaterThanOrEqual(2);
  });

  it("excludes self from dependency options", async () => {
    const user = userEvent.setup();

    const editGroup: FieldGroupResponse = {
      id: "g-self",
      entityType: "PROJECT",
      name: "Self Group",
      slug: "self_group",
      description: null,
      packId: null,
      sortOrder: 0,
      active: true,
      autoApply: false,
      dependsOn: null,
      createdAt: "2025-01-15T10:00:00Z",
      updatedAt: "2025-01-15T10:00:00Z",
    };

    const allGroups: FieldGroupResponse[] = [
      editGroup,
      {
        id: "g-other",
        entityType: "PROJECT",
        name: "Other Group",
        slug: "other_group",
        description: null,
        packId: null,
        sortOrder: 1,
        active: true,
        autoApply: false,
        dependsOn: null,
        createdAt: "2025-01-15T10:00:00Z",
        updatedAt: "2025-01-15T10:00:00Z",
      },
    ];

    render(
      <FieldGroupDialog
        slug="acme"
        entityType="PROJECT"
        group={editGroup}
        availableFields={makeAvailableFields()}
        allGroups={allGroups}
      >
        <button>Open Self Exclude Dialog</button>
      </FieldGroupDialog>,
    );

    await user.click(screen.getByText("Open Self Exclude Dialog"));

    // Click on the dependencies combobox to open it
    const depButtons = screen.getAllByRole("combobox");
    // The dependencies combobox is the second one (first is fields)
    const depsCombobox = depButtons.find((btn) =>
      btn.textContent?.includes("dependenc") || btn.textContent?.includes("Select dependencies"),
    );
    expect(depsCombobox).toBeDefined();
    await user.click(depsCombobox!);

    // Should show "Other Group" but NOT "Self Group"
    await waitFor(() => {
      expect(screen.getByText("Other Group")).toBeInTheDocument();
    });
    // The self group should not appear in the dependency list
    const listItems = screen.getAllByRole("option");
    const selfItem = listItems.find((item) => item.textContent?.includes("Self Group"));
    expect(selfItem).toBeUndefined();
  });
});
