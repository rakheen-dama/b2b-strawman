import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FieldGroupDialog } from "@/components/field-definitions/FieldGroupDialog";
import type { FieldDefinitionResponse, FieldGroupResponse } from "@/lib/types";

const mockCreateFieldGroup = vi.fn();
const mockUpdateFieldGroup = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/custom-fields-actions", () => ({
  createFieldGroupAction: (...args: unknown[]) => mockCreateFieldGroup(...args),
  updateFieldGroupAction: (...args: unknown[]) => mockUpdateFieldGroup(...args),
}));

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
      visibilityCondition: null,
      requiredForContexts: [],
      portalVisibleDeadline: false,
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
      visibilityCondition: null,
      requiredForContexts: [],
      portalVisibleDeadline: false,
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
      <FieldGroupDialog slug="acme" entityType="PROJECT" availableFields={makeAvailableFields()}>
        <button>Open Group Dialog</button>
      </FieldGroupDialog>
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
      <FieldGroupDialog slug="acme" entityType="PROJECT" availableFields={makeAvailableFields()}>
        <button>Open Create Group Dialog</button>
      </FieldGroupDialog>
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
        })
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
        fieldDefinitionIds: [],
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
        fieldDefinitionIds: [],
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
      </FieldGroupDialog>
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
      fieldDefinitionIds: [],
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
        fieldDefinitionIds: [],
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
      </FieldGroupDialog>
    );

    await user.click(screen.getByText("Open Self Exclude Dialog"));

    // Click on the dependencies combobox to open it
    const depButtons = screen.getAllByRole("combobox");
    // The dependencies combobox is the second one (first is fields)
    const depsCombobox = depButtons.find(
      (btn) =>
        btn.textContent?.includes("dependenc") || btn.textContent?.includes("Select dependencies")
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

  describe("editing membership (G88-F3)", () => {
    const memberGroup: FieldGroupResponse = {
      id: "g-members",
      entityType: "PROJECT",
      name: "Member Group",
      slug: "member_group",
      description: null,
      packId: null,
      sortOrder: 0,
      active: true,
      autoApply: false,
      dependsOn: null,
      fieldDefinitionIds: ["fd-2"],
      createdAt: "2025-01-15T10:00:00Z",
      updatedAt: "2025-01-15T10:00:00Z",
    };

    it("pre-checks initialFieldIds when editing", async () => {
      const user = userEvent.setup();

      render(
        <FieldGroupDialog
          slug="acme"
          entityType="PROJECT"
          group={memberGroup}
          availableFields={makeAvailableFields()}
          initialFieldIds={memberGroup.fieldDefinitionIds}
        >
          <button>Open Prechecked Dialog</button>
        </FieldGroupDialog>
      );

      await user.click(screen.getByText("Open Prechecked Dialog"));

      expect(screen.getByText("1 field selected")).toBeInTheDocument();
      expect(screen.getByText("Priority Level")).toBeInTheDocument();
    });

    it("sends the edited membership on save", async () => {
      mockUpdateFieldGroup.mockResolvedValue({ success: true });
      const user = userEvent.setup();

      render(
        <FieldGroupDialog
          slug="acme"
          entityType="PROJECT"
          group={memberGroup}
          availableFields={makeAvailableFields()}
          initialFieldIds={memberGroup.fieldDefinitionIds}
        >
          <button>Open Save Membership Dialog</button>
        </FieldGroupDialog>
      );

      await user.click(screen.getByText("Open Save Membership Dialog"));
      await user.click(screen.getByRole("button", { name: "Save Changes" }));

      await waitFor(() => {
        expect(mockUpdateFieldGroup).toHaveBeenCalledWith(
          "acme",
          "g-members",
          expect.objectContaining({ fieldDefinitionIds: ["fd-2"] })
        );
      });
    });
  });

  // 634A follow-ups (PR #1733 review, carried into G89 Batch E)
  describe("634A follow-ups", () => {
    const depGroup: FieldGroupResponse = {
      id: "g-dep",
      entityType: "PROJECT",
      name: "Dependency Group",
      slug: "dependency_group",
      description: null,
      packId: null,
      sortOrder: 1,
      active: true,
      autoApply: false,
      dependsOn: null,
      fieldDefinitionIds: [],
      createdAt: "2025-01-15T10:00:00Z",
      updatedAt: "2025-01-15T10:00:00Z",
    };
    const editedGroup: FieldGroupResponse = {
      ...depGroup,
      id: "g-edit",
      name: "Edited Group",
      slug: "edited_group",
      sortOrder: 0,
      dependsOn: ["g-dep"],
      // fd-gone is a member whose definition is inactive (not offered in availableFields)
      fieldDefinitionIds: ["fd-1", "fd-gone"],
    };

    function renderEdit(label: string) {
      return render(
        <FieldGroupDialog
          slug="acme"
          entityType="PROJECT"
          group={editedGroup}
          availableFields={makeAvailableFields()}
          allGroups={[editedGroup, depGroup]}
          initialFieldIds={editedGroup.fieldDefinitionIds}
        >
          <button>{label}</button>
        </FieldGroupDialog>
      );
    }

    it("an inactive member field is listed and can be removed", async () => {
      mockUpdateFieldGroup.mockResolvedValue({ success: true });
      const user = userEvent.setup();
      renderEdit("Open Inactive Member Dialog");

      await user.click(screen.getByText("Open Inactive Member Dialog"));
      // fd-gone has no resolvable name: a distinct id-suffixed label, never a shared one
      await user.click(screen.getByRole("button", { name: "Remove Inactive field fd-gone" }));
      await user.click(screen.getByRole("button", { name: "Save Changes" }));

      await waitFor(() =>
        expect(mockUpdateFieldGroup).toHaveBeenCalledWith(
          "acme",
          "g-edit",
          expect.objectContaining({ fieldDefinitionIds: ["fd-1"] })
        )
      );
    });

    it("each inactive member is identified by name before it can be removed (PR #1752 review)", async () => {
      mockUpdateFieldGroup.mockResolvedValue({ success: true });
      const user = userEvent.setup();
      const twoInactive: FieldGroupResponse = {
        ...editedGroup,
        fieldDefinitionIds: ["fd-1", "fd-old-a", "fd-old-b"],
      };
      render(
        <FieldGroupDialog
          slug="acme"
          entityType="PROJECT"
          group={twoInactive}
          availableFields={makeAvailableFields()}
          allGroups={[twoInactive, depGroup]}
          initialFieldIds={twoInactive.fieldDefinitionIds}
          inactiveFieldNames={{ "fd-old-a": "Old Code", "fd-old-b": "Legacy Ref" }}
        >
          <button>Open Two Inactive Dialog</button>
        </FieldGroupDialog>
      );

      await user.click(screen.getByText("Open Two Inactive Dialog"));
      expect(screen.getByText("Old Code (inactive)")).toBeInTheDocument();
      expect(screen.getByText("Legacy Ref (inactive)")).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "Remove Legacy Ref (inactive)" }));
      await user.click(screen.getByRole("button", { name: "Save Changes" }));

      await waitFor(() =>
        expect(mockUpdateFieldGroup).toHaveBeenCalledWith(
          "acme",
          "g-edit",
          expect.objectContaining({ fieldDefinitionIds: ["fd-1", "fd-old-a"] })
        )
      );
    });

    it("removing the last dependency clears dependsOn on save", async () => {
      mockUpdateFieldGroup.mockResolvedValue({ success: true });
      const user = userEvent.setup();
      renderEdit("Open Clear Deps Dialog");

      await user.click(screen.getByText("Open Clear Deps Dialog"));
      await user.click(screen.getByRole("button", { name: "Remove Dependency Group" }));
      await user.click(screen.getByRole("button", { name: "Save Changes" }));

      await waitFor(() =>
        expect(mockUpdateFieldGroup).toHaveBeenCalledWith(
          "acme",
          "g-edit",
          expect.objectContaining({ dependsOn: [] })
        )
      );
    });

    it("the badge remove buttons have accessible names", async () => {
      const user = userEvent.setup();
      renderEdit("Open Named Buttons Dialog");

      await user.click(screen.getByText("Open Named Buttons Dialog"));

      expect(screen.getByRole("button", { name: "Remove Budget Code" })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Remove Dependency Group" })).toBeInTheDocument();
    });

    it("the Slug pattern is a valid regular expression under the v flag", async () => {
      const user = userEvent.setup();
      render(
        <FieldGroupDialog slug="acme" entityType="PROJECT" availableFields={makeAvailableFields()}>
          <button>Open Slug Pattern Dialog</button>
        </FieldGroupDialog>
      );
      await user.click(screen.getByText("Open Slug Pattern Dialog"));

      // Browsers compile the pattern attribute with the "v" flag; "[a-z0-9_-]" is invalid there
      // and logged a console error on every keystroke.
      const pattern = screen.getByLabelText("Slug").getAttribute("pattern")!;
      expect(() => new RegExp(`^(?:${pattern})$`, "v")).not.toThrow();
      expect(new RegExp(`^(?:${pattern})$`, "v").test("contact-and_address2")).toBe(true);
      expect(new RegExp(`^(?:${pattern})$`, "v").test("Bad Slug")).toBe(false);
    });
  });
});
