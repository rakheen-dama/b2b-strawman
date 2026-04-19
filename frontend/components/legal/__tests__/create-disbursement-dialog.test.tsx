import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock server actions BEFORE importing the component under test, since
// actions.ts uses `"use server"` + the upload actions touch server-only APIs.
const mockCreate = vi.fn();
const mockFetchProjects = vi.fn();
const mockFetchCustomers = vi.fn();

vi.mock("@/app/(app)/org/[slug]/legal/disbursements/actions", () => ({
  createDisbursementAction: (...args: unknown[]) => mockCreate(...args),
  fetchProjects: () => mockFetchProjects(),
  fetchCustomers: () => mockFetchCustomers(),
}));

vi.mock("@/app/(app)/org/[slug]/projects/[id]/actions", () => ({
  initiateUpload: vi.fn(),
  confirmUpload: vi.fn(),
  cancelUpload: vi.fn(),
}));

import { CreateDisbursementDialog } from "@/components/legal/create-disbursement-dialog";

const PROJECTS = [
  { id: "11111111-1111-1111-1111-111111111111", name: "Matter 2026/001" },
];
const CUSTOMERS = [
  { id: "22222222-2222-2222-2222-222222222222", name: "Acme Attorneys" },
];

describe("CreateDisbursementDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchProjects.mockResolvedValue(PROJECTS);
    mockFetchCustomers.mockResolvedValue(CUSTOMERS);
    mockCreate.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  async function openDialog() {
    const user = userEvent.setup();
    render(<CreateDisbursementDialog slug="test-org" />);
    await user.click(screen.getByTestId("create-disbursement-trigger"));
    await waitFor(() =>
      expect(screen.getByTestId("create-disbursement-dialog")).toBeInTheDocument()
    );
    return user;
  }

  it("renders the form fields when opened", async () => {
    await openDialog();

    // "New Disbursement" appears on both the trigger button and the dialog header
    expect(screen.getAllByText("New Disbursement").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByLabelText("Category")).toBeInTheDocument();
    expect(screen.getByLabelText("Description")).toBeInTheDocument();
    expect(screen.getByLabelText("Amount (ZAR, excl VAT)")).toBeInTheDocument();
    expect(screen.getByLabelText("VAT Treatment")).toBeInTheDocument();
    expect(screen.getByText("Payment Source")).toBeInTheDocument();
    expect(screen.getByLabelText("Incurred Date")).toBeInTheDocument();
    expect(screen.getByLabelText("Supplier")).toBeInTheDocument();
  });

  it("auto-seeds VAT treatment from category when user has not overridden", async () => {
    const user = await openDialog();

    const vatSelect = screen.getByLabelText("VAT Treatment") as HTMLSelectElement;
    // Default category OTHER → STANDARD_15
    expect(vatSelect.value).toBe("STANDARD_15");

    const categorySelect = screen.getByLabelText("Category") as HTMLSelectElement;
    await user.selectOptions(categorySelect, "SHERIFF_FEES");

    // SHERIFF_FEES → ZERO_RATED_PASS_THROUGH
    await waitFor(() => expect(vatSelect.value).toBe("ZERO_RATED_PASS_THROUGH"));
  });

  it("stops auto-seeding VAT once the user manually selects one", async () => {
    const user = await openDialog();

    const vatSelect = screen.getByLabelText("VAT Treatment") as HTMLSelectElement;
    const categorySelect = screen.getByLabelText("Category") as HTMLSelectElement;

    // User overrides to EXEMPT
    await user.selectOptions(vatSelect, "EXEMPT");
    expect(vatSelect.value).toBe("EXEMPT");

    // Changing category should not overwrite the manual selection
    await user.selectOptions(categorySelect, "SHERIFF_FEES");
    await waitFor(() => expect(vatSelect.value).toBe("EXEMPT"));
  });

  it("disables the Trust Account payment source until 488B", async () => {
    await openDialog();

    // Trust Account option is disabled (488B territory) — users should not
    // be able to pick it until the backend flow for trust-linked disbursements lands.
    const trustRadio = screen.getByRole("radio", { name: /Trust Account/i });
    expect(trustRadio).toBeDisabled();
    // Trust-link slot is not shown because OFFICE_ACCOUNT remains selected.
    expect(screen.queryByTestId("trust-link-slot")).not.toBeInTheDocument();
  });

  it("blocks submission when required fields are empty", async () => {
    const user = await openDialog();

    await user.click(screen.getByRole("button", { name: /Create Disbursement/i }));

    // Form-level validation must prevent the server action from being called
    // when matter/customer/description are all blank or invalid.
    await waitFor(() => {
      expect(mockCreate).not.toHaveBeenCalled();
    });
  });
});
