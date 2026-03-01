import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PrerequisiteModal } from "@/components/prerequisite/prerequisite-modal";
import type { PrerequisiteViolation } from "@/components/prerequisite/types";
import type { InlineFieldEditorField } from "@/components/prerequisite/inline-field-editor";
import type { PrerequisiteCheck } from "@/components/prerequisite/types";

vi.mock("@/lib/actions/prerequisite-actions", () => ({
  checkPrerequisitesAction: vi.fn(),
  updateEntityCustomFieldsAction: vi.fn(),
}));

import {
  checkPrerequisitesAction,
  updateEntityCustomFieldsAction,
} from "@/lib/actions/prerequisite-actions";

const mockCheck = vi.mocked(checkPrerequisitesAction);
const mockSave = vi.mocked(updateEntityCustomFieldsAction);

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

const fieldViolation: PrerequisiteViolation = {
  code: "MISSING_FIELD",
  message: "VAT number is required",
  entityType: "CUSTOMER",
  entityId: "cust-1",
  fieldSlug: "vat_number",
  groupName: "Billing",
  resolution: "Enter the VAT number",
};

const structuralViolation: PrerequisiteViolation = {
  code: "NO_PORTAL_CONTACT",
  message: "Customer must have a portal contact",
  entityType: "CUSTOMER",
  entityId: "cust-1",
  fieldSlug: null,
  groupName: null,
  resolution: "Add a portal contact to the customer",
};

const vatFieldDef: InlineFieldEditorField = {
  id: "field-vat",
  name: "VAT Number",
  slug: "vat_number",
  fieldType: "TEXT",
  description: "Company VAT number",
  required: true,
  options: null,
};

const defaultProps = {
  open: true,
  onOpenChange: vi.fn(),
  context: "INVOICE_GENERATION" as const,
  violations: [fieldViolation, structuralViolation],
  fieldDefinitions: { vat_number: vatFieldDef },
  initialFieldValues: {},
  entityType: "CUSTOMER" as const,
  entityId: "cust-1",
  slug: "test-org",
  onResolved: vi.fn(),
  onCancel: vi.fn(),
};

describe("PrerequisiteModal — display", () => {
  it("renders violations grouped by entity type", () => {
    render(<PrerequisiteModal {...defaultProps} />);

    expect(screen.getByText("Prerequisites: Invoice Generation")).toBeInTheDocument();
    expect(screen.getByText("Customer")).toBeInTheDocument();
    expect(screen.getByText("VAT number is required")).toBeInTheDocument();
    expect(
      screen.getByText("Customer must have a portal contact"),
    ).toBeInTheDocument();
  });

  it("renders inline editor for custom field violations", () => {
    render(<PrerequisiteModal {...defaultProps} />);

    // The VAT Number field should have an inline editor (text input)
    expect(screen.getByText("VAT Number")).toBeInTheDocument();
    const input = screen.getByRole("textbox");
    expect(input).toBeInTheDocument();
  });

  it("renders resolution text for structural violations", () => {
    render(
      <PrerequisiteModal
        {...defaultProps}
        violations={[structuralViolation]}
        fieldDefinitions={{}}
      />,
    );

    expect(
      screen.getByText("Add a portal contact to the customer"),
    ).toBeInTheDocument();
  });
});

describe("PrerequisiteModal — interaction", () => {
  it("saves fields and re-checks on Check & Continue", async () => {
    const user = userEvent.setup();
    const recheckResult: PrerequisiteCheck = {
      passed: false,
      context: "INVOICE_GENERATION",
      violations: [structuralViolation],
    };
    mockSave.mockResolvedValueOnce({ success: true });
    mockCheck.mockResolvedValueOnce(recheckResult);

    render(<PrerequisiteModal {...defaultProps} />);

    // Type a value into the VAT field
    const input = screen.getByRole("textbox");
    await user.type(input, "ZA123456");

    // Click Check & Continue
    const button = screen.getByRole("button", { name: /check & continue/i });
    await user.click(button);

    await waitFor(() => {
      expect(mockSave).toHaveBeenCalledWith(
        "test-org",
        "CUSTOMER",
        "cust-1",
        { vat_number: "ZA123456" },
      );
    });

    await waitFor(() => {
      expect(mockCheck).toHaveBeenCalledWith(
        "INVOICE_GENERATION",
        "CUSTOMER",
        "cust-1",
      );
    });
  });

  it("calls onResolved when re-check passes", async () => {
    const user = userEvent.setup();
    const passedResult: PrerequisiteCheck = {
      passed: true,
      context: "INVOICE_GENERATION",
      violations: [],
    };
    mockSave.mockResolvedValueOnce({ success: true });
    mockCheck.mockResolvedValueOnce(passedResult);

    const onResolved = vi.fn();
    render(
      <PrerequisiteModal
        {...defaultProps}
        onResolved={onResolved}
      />,
    );

    // Type a value to trigger save
    const input = screen.getByRole("textbox");
    await user.type(input, "ZA999");

    const button = screen.getByRole("button", { name: /check & continue/i });
    await user.click(button);

    await waitFor(() => {
      expect(onResolved).toHaveBeenCalled();
    });
  });

  it("updates violations when re-check still fails", async () => {
    const user = userEvent.setup();
    const newViolation: PrerequisiteViolation = {
      code: "MISSING_FIELD",
      message: "Email address is now required",
      entityType: "CUSTOMER",
      entityId: "cust-1",
      fieldSlug: "email",
      groupName: "Contact",
      resolution: "Add email",
    };
    const stillFailingResult: PrerequisiteCheck = {
      passed: false,
      context: "INVOICE_GENERATION",
      violations: [newViolation],
    };
    mockSave.mockResolvedValueOnce({ success: true });
    mockCheck.mockResolvedValueOnce(stillFailingResult);

    render(<PrerequisiteModal {...defaultProps} />);

    // Type a value to trigger save
    const input = screen.getByRole("textbox");
    await user.type(input, "ZA111");

    const button = screen.getByRole("button", { name: /check & continue/i });
    await user.click(button);

    await waitFor(() => {
      expect(
        screen.getByText("Email address is now required"),
      ).toBeInTheDocument();
    });
  });

  it("shows error and skips re-check when save fails", async () => {
    const user = userEvent.setup();
    mockSave.mockResolvedValueOnce({ success: false, error: "Server error" });

    render(<PrerequisiteModal {...defaultProps} />);

    // Type a value to trigger dirty state
    const input = screen.getByRole("textbox");
    await user.type(input, "BAD");

    const button = screen.getByRole("button", { name: /check & continue/i });
    await user.click(button);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Server error");
    });

    // Re-check should NOT have been called
    expect(mockCheck).not.toHaveBeenCalled();
  });

  it("shows generic error when save throws an exception", async () => {
    const user = userEvent.setup();
    mockSave.mockRejectedValueOnce(new Error("Network failure"));

    render(<PrerequisiteModal {...defaultProps} />);

    // Type a value to trigger dirty state
    const input = screen.getByRole("textbox");
    await user.type(input, "CRASH");

    const button = screen.getByRole("button", { name: /check & continue/i });
    await user.click(button);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "An unexpected error occurred.",
      );
    });
  });
});
