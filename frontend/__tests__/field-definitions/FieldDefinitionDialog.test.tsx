import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FieldDefinitionDialog } from "@/components/field-definitions/FieldDefinitionDialog";

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

describe("FieldDefinitionDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders form fields when opened", async () => {
    const user = userEvent.setup();

    render(
      <FieldDefinitionDialog slug="acme" entityType="PROJECT">
        <button>Open Field Dialog</button>
      </FieldDefinitionDialog>,
    );

    await user.click(screen.getByText("Open Field Dialog"));

    expect(screen.getByLabelText("Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Slug")).toBeInTheDocument();
    expect(screen.getByLabelText("Field Type")).toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toBeInTheDocument();
    expect(screen.getByLabelText("Required field")).toBeInTheDocument();
    expect(screen.getByLabelText("Sort Order")).toBeInTheDocument();
  });

  it("shows options editor when DROPDOWN type is selected", async () => {
    const user = userEvent.setup();

    render(
      <FieldDefinitionDialog slug="acme" entityType="PROJECT">
        <button>Open Dropdown Field Dialog</button>
      </FieldDefinitionDialog>,
    );

    await user.click(screen.getByText("Open Dropdown Field Dialog"));

    // Select DROPDOWN from field type
    const fieldTypeSelect = screen.getByLabelText("Field Type");
    await user.selectOptions(fieldTypeSelect, "DROPDOWN");

    // Options editor should appear
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "Add Option" }),
      ).toBeInTheDocument();
    });
  });

  it("calls createFieldDefinitionAction on submit", async () => {
    mockCreateFieldDefinition.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <FieldDefinitionDialog slug="acme" entityType="PROJECT">
        <button>Open Create Field Dialog</button>
      </FieldDefinitionDialog>,
    );

    await user.click(screen.getByText("Open Create Field Dialog"));
    await user.type(screen.getByLabelText("Name"), "Test Field");
    await user.click(screen.getByRole("button", { name: "Create Field" }));

    await waitFor(() => {
      expect(mockCreateFieldDefinition).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          entityType: "PROJECT",
          name: "Test Field",
          fieldType: "TEXT",
          required: false,
          sortOrder: 0,
        }),
      );
    });
  });
});
