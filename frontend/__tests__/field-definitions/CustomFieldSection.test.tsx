import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CustomFieldSection } from "@/components/field-definitions/CustomFieldSection";
import type {
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
} from "@/lib/types";

const mockUpdateEntityCustomFields = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/custom-fields/actions",
  () => ({
    updateEntityCustomFieldsAction: (...args: unknown[]) =>
      mockUpdateEntityCustomFields(...args),
  }),
);

// --- Test Data ---

function makeFieldDef(
  overrides: Partial<FieldDefinitionResponse>,
): FieldDefinitionResponse {
  return {
    id: "fd-1",
    entityType: "PROJECT",
    name: "Test Field",
    slug: "test_field",
    fieldType: "TEXT",
    description: null,
    required: false,
    defaultValue: null,
    options: null,
    validation: null,
    sortOrder: 0,
    packId: null,
    packFieldKey: null,
    active: true,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}

const textField = makeFieldDef({
  id: "fd-text",
  name: "Case Number",
  slug: "case_number",
  fieldType: "TEXT",
});

const dropdownField = makeFieldDef({
  id: "fd-dropdown",
  name: "Court",
  slug: "court",
  fieldType: "DROPDOWN",
  options: [
    { value: "high_court", label: "High Court" },
    { value: "magistrate", label: "Magistrate Court" },
  ],
});

const booleanField = makeFieldDef({
  id: "fd-boolean",
  name: "Is Urgent",
  slug: "is_urgent",
  fieldType: "BOOLEAN",
});

const currencyField = makeFieldDef({
  id: "fd-currency",
  name: "Budget",
  slug: "budget",
  fieldType: "CURRENCY",
});

const numberField = makeFieldDef({
  id: "fd-number",
  name: "Estimated Hours",
  slug: "estimated_hours",
  fieldType: "NUMBER",
  validation: { min: 0, max: 1000 },
});

const requiredField = makeFieldDef({
  id: "fd-required",
  name: "Priority Level",
  slug: "priority_level",
  fieldType: "TEXT",
  required: true,
});

const allFields = [
  textField,
  dropdownField,
  booleanField,
  currencyField,
  numberField,
  requiredField,
];

const testGroup: FieldGroupResponse = {
  id: "grp-1",
  entityType: "PROJECT",
  name: "Litigation Fields",
  slug: "litigation_fields",
  description: null,
  packId: null,
  sortOrder: 0,
  active: true,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const testGroupMembers: FieldGroupMemberResponse[] = allFields.map(
  (f, i) => ({
    id: `gm-${i}`,
    fieldGroupId: "grp-1",
    fieldDefinitionId: f.id,
    sortOrder: i,
  }),
);

const defaultProps = {
  entityType: "PROJECT" as const,
  entityId: "proj-1",
  customFields: {},
  appliedFieldGroups: ["grp-1"],
  editable: true,
  slug: "acme",
  fieldDefinitions: allFields,
  fieldGroups: [testGroup],
  groupMembers: { "grp-1": testGroupMembers },
};

describe("CustomFieldSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders TEXT field as Input", () => {
    render(<CustomFieldSection {...defaultProps} />);

    expect(screen.getByLabelText(/Case Number/)).toBeInTheDocument();
    const input = screen.getByLabelText(/Case Number/) as HTMLInputElement;
    // HTML inputs default to "text" when no type attribute is set
    expect(input.tagName).toBe("INPUT");
    expect(input.type).toBe("text");
  });

  it("renders DROPDOWN field as Select with options", () => {
    render(<CustomFieldSection {...defaultProps} />);

    const select = screen.getByLabelText(/Court/);
    expect(select.tagName).toBe("SELECT");

    // Check options
    const options = select.querySelectorAll("option");
    // 3 = "Select..." + 2 options
    expect(options.length).toBe(3);
    expect(options[1].textContent).toBe("High Court");
    expect(options[2].textContent).toBe("Magistrate Court");
  });

  it("renders BOOLEAN field as Checkbox", () => {
    render(<CustomFieldSection {...defaultProps} />);

    const checkbox = screen.getByRole("checkbox");
    expect(checkbox).toBeInTheDocument();
    expect(checkbox).not.toBeChecked();
  });

  it("renders CURRENCY field with amount and currency inputs", () => {
    render(<CustomFieldSection {...defaultProps} />);

    expect(screen.getByPlaceholderText("Amount")).toBeInTheDocument();
    expect(screen.getByLabelText("Currency")).toBeInTheDocument();
  });

  it("shows validation error for required field when empty on save", async () => {
    const user = userEvent.setup();
    mockUpdateEntityCustomFields.mockResolvedValue({ success: true });

    render(<CustomFieldSection {...defaultProps} />);

    // Click save without filling required field
    await user.click(screen.getByRole("button", { name: /Save Custom Fields/i }));

    // Should show error for required field
    await waitFor(() => {
      expect(screen.getByText("Priority Level is required")).toBeInTheDocument();
    });

    // Should NOT have called the action
    expect(mockUpdateEntityCustomFields).not.toHaveBeenCalled();
  });

  it("calls updateEntityCustomFieldsAction on save with valid data", async () => {
    const user = userEvent.setup();
    mockUpdateEntityCustomFields.mockResolvedValue({ success: true });

    render(
      <CustomFieldSection
        {...defaultProps}
        customFields={{ priority_level: "high" }}
      />,
    );

    // Fill in a text field
    const caseInput = screen.getByLabelText(/Case Number/);
    await user.type(caseInput, "2025/12345");

    await user.click(screen.getByRole("button", { name: /Save Custom Fields/i }));

    await waitFor(() => {
      expect(mockUpdateEntityCustomFields).toHaveBeenCalledWith(
        "acme",
        "PROJECT",
        "proj-1",
        expect.objectContaining({
          case_number: "2025/12345",
          priority_level: "high",
        }),
      );
    });
  });

  it("renders read-only values when editable is false", () => {
    render(
      <CustomFieldSection
        {...defaultProps}
        editable={false}
        customFields={{
          case_number: "2025/99999",
          is_urgent: true,
        }}
      />,
    );

    // Should show formatted values, not inputs
    expect(screen.getByText("2025/99999")).toBeInTheDocument();
    expect(screen.getByText("Yes")).toBeInTheDocument();

    // No save button
    expect(
      screen.queryByRole("button", { name: /Save Custom Fields/i }),
    ).not.toBeInTheDocument();
  });

  it("shows empty state when no field groups are applied", () => {
    render(
      <CustomFieldSection {...defaultProps} appliedFieldGroups={[]} />,
    );

    expect(screen.getByText("No custom fields configured")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Custom fields let you track additional information specific to your workflow.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Configure Fields" }),
    ).toHaveAttribute("href", "/org/acme/settings/custom-fields");
  });

  it("renders group name as card header", () => {
    render(<CustomFieldSection {...defaultProps} />);

    expect(screen.getByText("Litigation Fields")).toBeInTheDocument();
  });

  it("shows success message after saving", async () => {
    const user = userEvent.setup();
    mockUpdateEntityCustomFields.mockResolvedValue({ success: true });

    render(
      <CustomFieldSection
        {...defaultProps}
        customFields={{ priority_level: "high" }}
      />,
    );

    await user.click(screen.getByRole("button", { name: /Save Custom Fields/i }));

    await waitFor(() => {
      expect(screen.getByText("Saved successfully")).toBeInTheDocument();
    });
  });

  it("shows error message when save fails", async () => {
    const user = userEvent.setup();
    mockUpdateEntityCustomFields.mockResolvedValue({
      success: false,
      error: "Validation failed on server",
    });

    render(
      <CustomFieldSection
        {...defaultProps}
        customFields={{ priority_level: "high" }}
      />,
    );

    await user.click(screen.getByRole("button", { name: /Save Custom Fields/i }));

    await waitFor(() => {
      expect(screen.getByText("Validation failed on server")).toBeInTheDocument();
    });
  });
});
