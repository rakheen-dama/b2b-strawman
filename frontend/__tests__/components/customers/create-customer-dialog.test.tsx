import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateCustomerDialog } from "@/components/customers/create-customer-dialog";
import { TerminologyProvider } from "@/lib/terminology";
import type { IntakeFieldGroup } from "@/components/prerequisite/types";

const mockCreateCustomer = vi.fn();
const mockFetchIntakeFields = vi.fn();
const mockRouterPush = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockRouterPush, refresh: vi.fn() }),
}));

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

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

    await user.click(screen.getByText("New Customer"));
    await waitFor(() => {
      expect(screen.getByText("Create Customer")).toBeInTheDocument();
    });
    expect(screen.getByText(/Step 1 of 2/)).toBeInTheDocument();

    // Fill required fields before advancing
    await user.type(screen.getByLabelText("Name"), "Test Corp");
    await user.type(screen.getByLabelText("Email"), "test@corp.com");

    await user.click(screen.getByText("Next"));

    await waitFor(() => {
      expect(screen.getByText("Additional Information")).toBeInTheDocument();
    });
    expect(screen.getByText(/Step 2 of 2/)).toBeInTheDocument();
  });

  it("fetchesIntakeFieldsOnTypeSelection", async () => {
    mockFetchIntakeFields.mockResolvedValue({ groups: [makeGroup()] });
    const user = userEvent.setup();

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

    await user.click(screen.getByText("New Customer"));
    await waitFor(() => {
      expect(screen.getByText("Create Customer")).toBeInTheDocument();
    });

    // Fill required fields before advancing
    await user.type(screen.getByLabelText("Name"), "Test Corp");
    await user.type(screen.getByLabelText("Email"), "test@corp.com");

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

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

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
        })
      );
    });
  });

  it("backButtonReturnsToStep1", async () => {
    const user = userEvent.setup();

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

    await user.click(screen.getByText("New Customer"));
    await waitFor(() => expect(screen.getByText("Create Customer")).toBeInTheDocument());

    // Fill required fields before advancing
    await user.type(screen.getByLabelText("Name"), "Back Corp");
    await user.type(screen.getByLabelText("Email"), "back@corp.com");

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

  it("renders Address section with all 6 fields", async () => {
    const user = userEvent.setup();

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

    await user.click(screen.getByText("New Customer"));
    await waitFor(() => expect(screen.getByLabelText("Name")).toBeInTheDocument());

    expect(screen.getByLabelText(/address line 1/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/address line 2/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^city$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/state.*province/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/postal code/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^country\b/i)).toBeInTheDocument();
  });

  it("country dropdown contains ZA and US options", async () => {
    const user = userEvent.setup();

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

    await user.click(screen.getByText("New Customer"));
    const countrySelect = await screen.findByLabelText(/^country\b/i);
    // Native <select> renders <option> children
    expect(countrySelect.querySelector('option[value="ZA"]')).not.toBeNull();
    expect(countrySelect.querySelector('option[value="US"]')).not.toBeNull();
  });

  it("validates contactEmail format on Next", async () => {
    const user = userEvent.setup();

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

    await user.click(screen.getByText("New Customer"));
    await user.type(screen.getByLabelText("Name"), "Test Corp");
    await user.type(screen.getByLabelText("Email"), "test@corp.com");
    await user.type(screen.getByLabelText(/contact email/i), "not-an-email");

    await user.click(screen.getByText("Next"));

    await waitFor(() => {
      expect(screen.getAllByText(/invalid email address/i).length).toBeGreaterThan(0);
    });
    // Should still be on step 1
    expect(screen.getByText(/Step 1 of 2/)).toBeInTheDocument();
  });

  it("entity type select includes PTY_LTD option", async () => {
    const user = userEvent.setup();

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

    await user.click(screen.getByText("New Customer"));
    const entityTypeSelect = await screen.findByLabelText(/entity type/i);
    expect(entityTypeSelect.querySelector('option[value="PTY_LTD"]')).not.toBeNull();
    expect(entityTypeSelect.querySelector('option[value="TRUST"]')).not.toBeNull();
  });

  it("submits with promoted fields populated", async () => {
    const user = userEvent.setup();

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

    await user.click(screen.getByText("New Customer"));
    await user.type(screen.getByLabelText("Name"), "Acme Pty Ltd");
    await user.type(screen.getByLabelText("Email"), "billing@acme.com");
    await user.type(screen.getByLabelText(/address line 1/i), "100 Main St");
    await user.type(screen.getByLabelText(/^city$/i), "Cape Town");
    await user.selectOptions(screen.getByLabelText(/^country\b/i), "ZA");
    await user.type(screen.getByLabelText(/contact name/i), "Jane Doe");
    await user.type(screen.getByLabelText(/contact email/i), "jane@acme.com");
    await user.type(screen.getByLabelText(/registration number/i), "2024/123456/07");
    await user.selectOptions(screen.getByLabelText(/entity type/i), "PTY_LTD");

    await user.click(screen.getByText("Next"));
    await waitFor(() => expect(screen.getByText("Additional Information")).toBeInTheDocument());
    await user.click(screen.getByText("Create Customer"));

    await waitFor(() => {
      expect(mockCreateCustomer).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          name: "Acme Pty Ltd",
          email: "billing@acme.com",
          addressLine1: "100 Main St",
          city: "Cape Town",
          country: "ZA",
          contactName: "Jane Doe",
          contactEmail: "jane@acme.com",
          registrationNumber: "2024/123456/07",
          entityType: "PTY_LTD",
        })
      );
    });
  });

  it("submits with only required fields (backward compat)", async () => {
    const user = userEvent.setup();

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

    await user.click(screen.getByText("New Customer"));
    await user.type(screen.getByLabelText("Name"), "Plain Corp");
    await user.type(screen.getByLabelText("Email"), "plain@corp.com");

    await user.click(screen.getByText("Next"));
    await waitFor(() => expect(screen.getByText("Additional Information")).toBeInTheDocument());
    await user.click(screen.getByText("Create Customer"));

    await waitFor(() => {
      expect(mockCreateCustomer).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          name: "Plain Corp",
          email: "plain@corp.com",
        })
      );
    });
    // Promoted fields should be undefined (empty-string → undefined mapping)
    const call = mockCreateCustomer.mock.calls[0][1];
    expect(call.addressLine1).toBeUndefined();
    expect(call.contactEmail).toBeUndefined();
    expect(call.entityType).toBeUndefined();
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

    render(
      <TerminologyProvider verticalProfile={null}>
        <CreateCustomerDialog slug="acme" />
      </TerminologyProvider>
    );

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
        })
      );
    });
  });
});
