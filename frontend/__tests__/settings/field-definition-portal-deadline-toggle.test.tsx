import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FieldDefinitionDialog } from "@/components/field-definitions/FieldDefinitionDialog";
import type { FieldDefinitionResponse } from "@/lib/types";

const mockCreateFieldDefinition = vi.fn();
const mockUpdateFieldDefinition = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/custom-fields/actions", () => ({
  createFieldDefinitionAction: (...args: unknown[]) =>
    mockCreateFieldDefinition(...args),
  updateFieldDefinitionAction: (...args: unknown[]) =>
    mockUpdateFieldDefinition(...args),
  fetchFieldUsageAction: () => Promise.resolve({ templates: [], clauses: [] }),
}));

function makeDateField(
  overrides: Partial<FieldDefinitionResponse> = {},
): FieldDefinitionResponse {
  return {
    id: "fd-pd-1",
    entityType: "PROJECT",
    name: "Filing Date",
    slug: "filing_date",
    fieldType: "DATE",
    description: null,
    required: false,
    defaultValue: null,
    options: null,
    validation: null,
    sortOrder: 1,
    packId: null,
    packFieldKey: null,
    visibilityCondition: null,
    requiredForContexts: [],
    active: true,
    createdAt: "2025-01-15T10:00:00Z",
    updatedAt: "2025-01-15T10:00:00Z",
    portalVisibleDeadline: false,
    ...overrides,
  };
}

describe("FieldDefinition portalVisibleDeadline toggle", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("is hidden for TEXT fields and visible for DATE fields", async () => {
    const user = userEvent.setup();
    render(
      <FieldDefinitionDialog slug="acme" entityType="PROJECT">
        <button>Open portal deadline toggle dialog</button>
      </FieldDefinitionDialog>,
    );
    await user.click(
      screen.getByText("Open portal deadline toggle dialog"),
    );

    // Default fieldType is TEXT — toggle hidden.
    expect(
      screen.queryByLabelText(/Surface this date on portal as a deadline/i),
    ).not.toBeInTheDocument();

    // Switch to DATE — toggle appears.
    const fieldTypeSelect = screen.getByLabelText(
      "Field Type",
    ) as HTMLSelectElement;
    await user.selectOptions(fieldTypeSelect, "DATE");
    expect(
      screen.getByLabelText(/Surface this date on portal as a deadline/i),
    ).toBeInTheDocument();
  });

  it("persists portalVisibleDeadline: true via updateFieldDefinitionAction", async () => {
    mockUpdateFieldDefinition.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const field = makeDateField();
    render(
      <FieldDefinitionDialog
        slug="acme"
        entityType="PROJECT"
        field={field}
      >
        <button>Edit portal deadline field</button>
      </FieldDefinitionDialog>,
    );
    await user.click(screen.getByText("Edit portal deadline field"));

    const checkbox = screen.getByLabelText(
      /Surface this date on portal as a deadline/i,
    );
    expect(checkbox).not.toBeChecked();

    await user.click(checkbox);
    await user.click(
      screen.getByRole("button", { name: /Save Changes/i }),
    );

    await waitFor(() => {
      expect(mockUpdateFieldDefinition).toHaveBeenCalledWith(
        "acme",
        "fd-pd-1",
        expect.objectContaining({ portalVisibleDeadline: true }),
      );
    });
  });

  it("submits portalVisibleDeadline: false (not undefined) when the toggle is left unchecked on a DATE field", async () => {
    mockUpdateFieldDefinition.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const field = makeDateField();
    render(
      <FieldDefinitionDialog
        slug="acme"
        entityType="PROJECT"
        field={field}
      >
        <button>Edit portal deadline field (unchecked)</button>
      </FieldDefinitionDialog>,
    );
    await user.click(
      screen.getByText("Edit portal deadline field (unchecked)"),
    );

    const checkbox = screen.getByLabelText(
      /Surface this date on portal as a deadline/i,
    );
    expect(checkbox).not.toBeChecked();

    // Do NOT click the checkbox — submit as-is.
    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(mockUpdateFieldDefinition).toHaveBeenCalledTimes(1);
    });
    const payload = mockUpdateFieldDefinition.mock.calls[0][2];
    expect(payload.portalVisibleDeadline).toBe(false);
  });

  it("submits portalVisibleDeadline: false when fieldType is not DATE, even if the state was previously toggled on", async () => {
    mockUpdateFieldDefinition.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    const field = makeDateField();
    render(
      <FieldDefinitionDialog
        slug="acme"
        entityType="PROJECT"
        field={field}
      >
        <button>Edit portal deadline field (type-switch)</button>
      </FieldDefinitionDialog>,
    );
    await user.click(
      screen.getByText("Edit portal deadline field (type-switch)"),
    );

    // Toggle on while DATE.
    const checkbox = screen.getByLabelText(
      /Surface this date on portal as a deadline/i,
    );
    await user.click(checkbox);
    expect(checkbox).toBeChecked();

    // Switch field type to TEXT — toggle hidden, but state may still be true.
    const fieldTypeSelect = screen.getByLabelText(
      "Field Type",
    ) as HTMLSelectElement;
    await user.selectOptions(fieldTypeSelect, "TEXT");
    expect(
      screen.queryByLabelText(/Surface this date on portal as a deadline/i),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Save Changes/i }));

    await waitFor(() => {
      expect(mockUpdateFieldDefinition).toHaveBeenCalledTimes(1);
    });
    const payload = mockUpdateFieldDefinition.mock.calls[0][2];
    expect(payload.portalVisibleDeadline).toBe(false);
  });
});
