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

  it("renders Required For multi-select checkboxes", async () => {
    const user = userEvent.setup();

    render(
      <FieldDefinitionDialog slug="acme" entityType="CUSTOMER">
        <button>Open Required For Dialog</button>
      </FieldDefinitionDialog>,
    );

    await user.click(screen.getByText("Open Required For Dialog"));

    expect(screen.getByLabelText("Customer Activation")).toBeInTheDocument();
    expect(screen.getByLabelText("Invoice Generation")).toBeInTheDocument();
    expect(screen.getByLabelText("Proposal Sending")).toBeInTheDocument();
    expect(screen.getByLabelText("Document Generation")).toBeInTheDocument();
    expect(screen.getByLabelText("Project Creation")).toBeInTheDocument();

    // Pack default note should NOT be shown for non-pack fields
    expect(screen.queryByText(/Set by field pack/)).not.toBeInTheDocument();
  });

  it("includes requiredForContexts in create payload", async () => {
    mockCreateFieldDefinition.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(
      <FieldDefinitionDialog slug="acme" entityType="CUSTOMER">
        <button>Open Contexts Create Dialog</button>
      </FieldDefinitionDialog>,
    );

    await user.click(screen.getByText("Open Contexts Create Dialog"));
    await user.type(screen.getByLabelText("Name"), "Tax Number");
    await user.click(screen.getByLabelText("Invoice Generation"));
    await user.click(screen.getByLabelText("Customer Activation"));
    await user.click(screen.getByRole("button", { name: "Create Field" }));

    await waitFor(() => {
      expect(mockCreateFieldDefinition).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          requiredForContexts: expect.arrayContaining([
            "INVOICE_GENERATION",
            "LIFECYCLE_ACTIVATION",
          ]),
        }),
      );
    });
  });

  it("shows pack default note for pack fields", async () => {
    const user = userEvent.setup();

    const packField: FieldDefinitionResponse = {
      id: "field-1",
      entityType: "CUSTOMER",
      name: "Address",
      slug: "address",
      fieldType: "TEXT",
      description: null,
      required: false,
      defaultValue: null,
      options: null,
      validation: null,
      sortOrder: 1,
      packId: "pack-common",
      packFieldKey: "address_line1",
      visibilityCondition: null,
      requiredForContexts: ["INVOICE_GENERATION"],
      active: true,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    };

    render(
      <FieldDefinitionDialog slug="acme" entityType="CUSTOMER" field={packField}>
        <button>Open Pack Field Dialog</button>
      </FieldDefinitionDialog>,
    );

    await user.click(screen.getByText("Open Pack Field Dialog"));

    expect(
      screen.getByText("Set by field pack — override by changing selections."),
    ).toBeInTheDocument();
  });
});
