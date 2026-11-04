import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AnonymizeCustomerDialog } from "@/components/customers/anonymize-customer-dialog";
import type {
  AnonymizationPreview,
  StandaloneAnonymizationResult,
} from "@/lib/types/data-protection";

const mockFetchAnonymizationPreview = vi.fn();
const mockExecuteAnonymization = vi.fn();

vi.mock("@/app/(app)/org/[slug]/clients/[id]/data-protection-actions", () => ({
  triggerDataExport: vi.fn(),
  fetchAnonymizationPreview: (...args: unknown[]) => mockFetchAnonymizationPreview(...args),
  executeAnonymization: (...args: unknown[]) => mockExecuteAnonymization(...args),
}));

const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: mockRefresh,
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// Shaped exactly like the flat backend record (datarequest/AnonymizationPreview.java).
const PREVIEW = {
  clientId: "c1",
  clientName: "Acme Corp",
  portalContacts: 2,
  projects: 3,
  documents: 12,
  timeEntries: 45,
  invoices: 8,
  comments: 23,
  customFieldValues: 6,
  financialRecordsRetained: 8,
  financialRetentionExpiresAt: "2031-03-19",
} satisfies AnonymizationPreview;

// Shaped exactly like DataAnonymizationService.AnonymizationResult.
const RESULT = {
  customerAnonymized: true,
  documentsDeleted: 12,
  commentsRedacted: 23,
  portalContactsAnonymized: 2,
  invoicesPreserved: 8,
} satisfies StandaloneAnonymizationResult;

function renderDialog() {
  return render(
    <AnonymizeCustomerDialog slug="acme" customerId="c1" customerName="Acme Corp">
      <button>Open Anonymize Dialog</button>
    </AnonymizeCustomerDialog>
  );
}

async function openAndContinue(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByText("Open Anonymize Dialog"));
  await waitFor(() => {
    expect(screen.getByRole("button", { name: "Continue" })).toBeInTheDocument();
  });
  await user.click(screen.getByRole("button", { name: "Continue" }));
}

describe("AnonymizeCustomerDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchAnonymizationPreview.mockResolvedValue({ success: true, data: PREVIEW });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders every preview count from the flat backend record (G88-F44)", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByText("Open Anonymize Dialog"));

    await waitFor(() => {
      expect(screen.getByText("Portal contacts: 2")).toBeInTheDocument();
    });
    expect(screen.getByText("Matters: 3")).toBeInTheDocument();
    expect(screen.getByText("Documents: 12")).toBeInTheDocument();
    expect(screen.getByText("Time entries: 45")).toBeInTheDocument();
    expect(screen.getByText("Fee notes: 8")).toBeInTheDocument();
    expect(screen.getByText("Comments: 23")).toBeInTheDocument();
    expect(screen.getByText("Custom field values: 6")).toBeInTheDocument();
    expect(screen.getByText(/8 fee notes will be preserved/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue" })).toBeEnabled();
  });

  it("confirm button disabled when input is empty", async () => {
    const user = userEvent.setup();
    renderDialog();
    await openAndContinue(user);

    const confirmButton = screen.getByRole("button", { name: /execute anonymization/i });
    expect(confirmButton).toBeDisabled();
  });

  it("confirm button disabled when input does not match customer name", async () => {
    const user = userEvent.setup();
    renderDialog();
    await openAndContinue(user);

    await user.type(screen.getByPlaceholderText("Acme Corp"), "Wrong Name");

    const confirmButton = screen.getByRole("button", { name: /execute anonymization/i });
    expect(confirmButton).toBeDisabled();
  });

  it("executes anonymization and renders the five completion counts from the flat result (G88-F44)", async () => {
    mockExecuteAnonymization.mockResolvedValue({ success: true, data: RESULT });
    const user = userEvent.setup();
    renderDialog();
    await openAndContinue(user);

    await user.type(screen.getByPlaceholderText("Acme Corp"), "Acme Corp");

    // Reason has a default value, so a matching name enables the button.
    const confirmButton = screen.getByRole("button", { name: /execute anonymization/i });
    expect(confirmButton).not.toBeDisabled();
    await user.click(confirmButton);

    await waitFor(() => {
      expect(screen.getByText("Anonymization Complete")).toBeInTheDocument();
    });

    expect(screen.getByText("Client anonymized: Yes")).toBeInTheDocument();
    expect(screen.getByText("Documents deleted: 12")).toBeInTheDocument();
    expect(screen.getByText("Comments redacted: 23")).toBeInTheDocument();
    expect(screen.getByText("Portal contacts anonymized: 2")).toBeInTheDocument();
    expect(screen.getByText("Fee notes preserved: 8")).toBeInTheDocument();

    expect(mockExecuteAnonymization).toHaveBeenCalledWith(
      "acme",
      "c1",
      "Acme Corp",
      "Data subject request"
    );
  });
});
