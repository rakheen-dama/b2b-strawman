import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FieldDefinitionDialog } from "@/components/field-definitions/FieldDefinitionDialog";
import type { FieldDefinitionResponse } from "@/lib/types";

const mockCreateFieldDefinition = vi.fn();
const mockUpdateFieldDefinition = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/custom-fields/actions",
  () => ({
    createFieldDefinitionAction: (...args: unknown[]) =>
      mockCreateFieldDefinition(...args),
    updateFieldDefinitionAction: (...args: unknown[]) =>
      mockUpdateFieldDefinition(...args),
  }),
);

function makeFields(): FieldDefinitionResponse[] {
  return [
    {
      id: "fd-vis-1",
      entityType: "PROJECT",
      name: "Status Field",
      slug: "status_field",
      fieldType: "DROPDOWN",
      description: null,
      required: false,
      defaultValue: null,
      options: [
        { value: "active", label: "Active" },
        { value: "inactive", label: "Inactive" },
      ],
      validation: null,
      sortOrder: 1,
      packId: null,
      packFieldKey: null,
      visibilityCondition: null,
      active: true,
      createdAt: "2025-01-15T10:00:00Z",
      updatedAt: "2025-01-15T10:00:00Z",
    },
    {
      id: "fd-vis-2",
      entityType: "PROJECT",
      name: "Region",
      slug: "region",
      fieldType: "TEXT",
      description: null,
      required: false,
      defaultValue: null,
      options: null,
      validation: null,
      sortOrder: 2,
      packId: null,
      packFieldKey: null,
      visibilityCondition: null,
      active: true,
      createdAt: "2025-01-15T10:00:00Z",
      updatedAt: "2025-01-15T10:00:00Z",
    },
    {
      id: "fd-vis-3",
      entityType: "PROJECT",
      name: "Notes",
      slug: "notes",
      fieldType: "TEXT",
      description: null,
      required: false,
      defaultValue: null,
      options: null,
      validation: null,
      sortOrder: 3,
      packId: null,
      packFieldKey: null,
      visibilityCondition: null,
      active: true,
      createdAt: "2025-01-15T10:00:00Z",
      updatedAt: "2025-01-15T10:00:00Z",
    },
  ];
}

describe("Visibility Condition Editor", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders controlling field options excluding self", async () => {
    const user = userEvent.setup();
    const allFields = makeFields();
    // Editing the first field (fd-vis-1), so the dropdown should show the other 2
    const editingField = allFields[0];

    render(
      <FieldDefinitionDialog
        slug="acme"
        entityType="PROJECT"
        field={editingField}
        allFieldsForType={allFields}
      >
        <button>Open Vis Editor Dialog</button>
      </FieldDefinitionDialog>,
    );

    await user.click(screen.getByText("Open Vis Editor Dialog"));

    // Check "Show conditionally"
    const checkbox = screen.getByLabelText("Show conditionally");
    await user.click(checkbox);

    // The controlling field dropdown should appear
    const fieldSelect = screen.getByLabelText("Show this field when");
    expect(fieldSelect).toBeInTheDocument();

    // Should have 2 options (Region and Notes) plus the placeholder
    const options = fieldSelect.querySelectorAll("option");
    expect(options).toHaveLength(3); // placeholder + Region + Notes

    // Should NOT contain the current field (Status Field)
    const optionTexts = Array.from(options).map((o) => o.textContent);
    expect(optionTexts).not.toContain("Status Field");
    expect(optionTexts).toContain("Region");
    expect(optionTexts).toContain("Notes");
  });

  it("submits eq condition with createFieldDefinitionAction", async () => {
    mockCreateFieldDefinition.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const allFields = makeFields();

    render(
      <FieldDefinitionDialog
        slug="acme"
        entityType="PROJECT"
        allFieldsForType={allFields}
      >
        <button>Open Create Vis Dialog</button>
      </FieldDefinitionDialog>,
    );

    await user.click(screen.getByText("Open Create Vis Dialog"));

    // Fill required name field
    await user.type(screen.getByLabelText("Name"), "Conditional Field");

    // Check "Show conditionally"
    await user.click(screen.getByLabelText("Show conditionally"));

    // Select controlling field
    const fieldSelect = screen.getByLabelText("Show this field when");
    await user.selectOptions(fieldSelect, "region");

    // Operator defaults to "eq", leave it

    // Enter value
    const valueInput = screen.getByLabelText("Value");
    await user.type(valueInput, "US");

    // Submit
    await user.click(screen.getByRole("button", { name: "Create Field" }));

    await waitFor(() => {
      expect(mockCreateFieldDefinition).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          entityType: "PROJECT",
          name: "Conditional Field",
          visibilityCondition: {
            dependsOnSlug: "region",
            operator: "eq",
            value: "US",
          },
        }),
      );
    });
  });

  it("clears condition on edit and submits with null", async () => {
    mockUpdateFieldDefinition.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const allFields = makeFields();
    const editingField: FieldDefinitionResponse = {
      ...allFields[2],
      visibilityCondition: {
        dependsOnSlug: "region",
        operator: "eq",
        value: "US",
      },
    };

    render(
      <FieldDefinitionDialog
        slug="acme"
        entityType="PROJECT"
        field={editingField}
        allFieldsForType={allFields}
      >
        <button>Open Clear Vis Dialog</button>
      </FieldDefinitionDialog>,
    );

    await user.click(screen.getByText("Open Clear Vis Dialog"));

    // The "Show conditionally" checkbox should be checked
    const checkbox = screen.getByLabelText("Show conditionally");
    expect(checkbox).toBeChecked();

    // Click "Clear condition"
    await user.click(screen.getByRole("button", { name: "Clear condition" }));

    // Checkbox should now be unchecked
    expect(checkbox).not.toBeChecked();

    // Submit
    await user.click(screen.getByRole("button", { name: "Save Changes" }));

    await waitFor(() => {
      expect(mockUpdateFieldDefinition).toHaveBeenCalledWith(
        "acme",
        "fd-vis-3",
        expect.objectContaining({
          visibilityCondition: null,
        }),
      );
    });
  });
});
