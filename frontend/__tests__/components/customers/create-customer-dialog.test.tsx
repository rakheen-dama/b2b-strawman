import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateCustomerDialog } from "@/components/customers/create-customer-dialog";
import type { IntakeFieldGroup } from "@/components/prerequisite/types";

const mockCreateCustomer = vi.fn();
const mockFetchIntakeFields = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/actions", () => ({
  createCustomer: (...args: unknown[]) => mockCreateCustomer(...args),
}));

vi.mock("@/app/(app)/org/[slug]/customers/intake-actions", () => ({
  fetchIntakeFields: (...args: unknown[]) => mockFetchIntakeFields(...args),
}));

function makeGroup(overrides: Partial<IntakeFieldGroup> = {}): IntakeFieldGroup {
  return {
    id: "group-1",
    name: "Billing Info",
    slug: "billing_info",
    fields: [
      {
        id: "f1",
        name: "VAT Number",
        slug: "vat_number",
        fieldType: "TEXT",
        required: true,
        description: null,
        options: null,
        defaultValue: null,
        requiredForContexts: [],
        visibilityCondition: null,
      },
    ],
    ...overrides,
  };
}

describe("CreateCustomerDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchIntakeFields.mockResolvedValue({ groups: [] });
    mockCreateCustomer.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  it("showsStep2AfterStep1Next", async () => {
    const user = userEvent.setup();

    render(<CreateCustomerDialog slug="acme" />);

    await user.click(screen.getByText("New Customer"));
    await waitFor(() => {
      expect(screen.getByText("Create Customer")).toBeInTheDocument();
    });
    expect(screen.getByText(/Step 1 of 2/)).toBeInTheDocument();

    await user.click(screen.getByText("Next"));

    await waitFor(() => {
      expect(screen.getByText("Additional Information")).toBeInTheDocument();
    });
    expect(screen.getByText(/Step 2 of 2/)).toBeInTheDocument();
  });

  it("fetchesIntakeFieldsOnTypeSelection", async () => {
    mockFetchIntakeFields.mockResolvedValue({ groups: [makeGroup()] });
    const user = userEvent.setup();

    render(<CreateCustomerDialog slug="acme" />);

    await user.click(screen.getByText("New Customer"));
    await waitFor(() => {
      expect(screen.getByText("Create Customer")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Next"));

    await waitFor(() => {
      expect(mockFetchIntakeFields).toHaveBeenCalledWith("CUSTOMER");
    });

    await waitFor(() => {
      expect(screen.getByText("Billing Info")).toBeInTheDocument();
    });
  });

  it("submitIncludesCustomFields", async () => {
    mockFetchIntakeFields.mockResolvedValue({ groups: [makeGroup()] });
    const user = userEvent.setup();

    render(<CreateCustomerDialog slug="acme" />);

    await user.click(screen.getByText("New Customer"));
    await waitFor(() => expect(screen.getByLabelText("Name")).toBeInTheDocument());

    // Fill Step 1
    await user.type(screen.getByLabelText("Name"), "Test Corp");
    await user.type(screen.getByLabelText("Email"), "test@corp.com");

    // Go to Step 2
    await user.click(screen.getByText("Next"));

    await waitFor(() => {
      expect(screen.getByText("Billing Info")).toBeInTheDocument();
    });

    // Fill custom field
    const vatInput = screen.getByLabelText(/vat number/i);
    await user.type(vatInput, "GB123456789");

    // Submit
    await user.click(screen.getByText("Create Customer"));

    await waitFor(() => {
      expect(mockCreateCustomer).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          name: "Test Corp",
          email: "test@corp.com",
          customFields: expect.objectContaining({ vat_number: "GB123456789" }),
        }),
      );
    });
  });

  it("backButtonReturnsToStep1", async () => {
    const user = userEvent.setup();

    render(<CreateCustomerDialog slug="acme" />);

    await user.click(screen.getByText("New Customer"));
    await waitFor(() => expect(screen.getByText("Create Customer")).toBeInTheDocument());

    // Go to Step 2
    await user.click(screen.getByText("Next"));
    await waitFor(() => expect(screen.getByText("Additional Information")).toBeInTheDocument());

    // Go back
    await user.click(screen.getByText("Back"));

    await waitFor(() => {
      expect(screen.getByText("Create Customer")).toBeInTheDocument();
    });
    expect(screen.getByLabelText("Name")).toBeInTheDocument();
  });

  it("skipForNow_collapsesOptionalFields", async () => {
    // Return groups with no required fields -> allRequiredFilled = true immediately
    mockFetchIntakeFields.mockResolvedValue({
      groups: [
        makeGroup({
          fields: [
            {
              id: "f1",
              name: "Extra Notes",
              slug: "extra_notes",
              fieldType: "TEXT",
              required: false,
              description: null,
              options: null,
              defaultValue: null,
              requiredForContexts: [],
              visibilityCondition: null,
            },
          ],
        }),
      ],
    });
    const user = userEvent.setup();

    render(<CreateCustomerDialog slug="acme" />);

    await user.click(screen.getByText("New Customer"));
    await waitFor(() => expect(screen.getByLabelText("Name")).toBeInTheDocument());

    await user.type(screen.getByLabelText("Name"), "Skip Corp");
    await user.type(screen.getByLabelText("Email"), "skip@corp.com");

    await user.click(screen.getByText("Next"));

    await waitFor(() => {
      expect(screen.getByText("Skip for now")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Skip for now"));

    await waitFor(() => {
      expect(mockCreateCustomer).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          name: "Skip Corp",
          email: "skip@corp.com",
        }),
      );
    });
  });
});
