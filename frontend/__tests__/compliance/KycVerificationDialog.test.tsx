import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChecklistInstanceItemRow } from "@/components/compliance/ChecklistInstanceItemRow";
import { KycVerificationDialog } from "@/components/compliance/KycVerificationDialog";
import type { ChecklistInstanceItemResponse } from "@/lib/types";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    refresh: vi.fn(),
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// Mock kyc-actions
const mockVerifyKycAction = vi.fn();
vi.mock(
  "@/app/(app)/org/[slug]/customers/[id]/kyc-actions",
  () => ({
    verifyKycAction: (...args: unknown[]) => mockVerifyKycAction(...args),
    getKycStatusAction: vi.fn().mockResolvedValue({ configured: true, provider: "verifynow" }),
    getKycResultAction: vi.fn().mockResolvedValue({ success: true, data: null }),
  }),
);

const baseItem: ChecklistInstanceItemResponse = {
  id: "item-1",
  instanceId: "inst-1",
  templateItemId: "ti-1",
  name: "Verify identity document",
  description: "Check passport or ID",
  sortOrder: 1,
  required: true,
  requiresDocument: false,
  requiredDocumentLabel: null,
  status: "PENDING",
  completedAt: null,
  completedBy: null,
  completedByName: null,
  notes: null,
  documentId: null,
  dependsOnItemId: null,
  createdAt: "2026-02-18T10:00:00Z",
  updatedAt: "2026-02-18T10:00:00Z",
};

const mockOnComplete = vi.fn().mockResolvedValue(undefined);
const mockOnSkip = vi.fn().mockResolvedValue(undefined);
const mockOnReopen = vi.fn().mockResolvedValue(undefined);

describe("KycVerificationDialog — ChecklistInstanceItemRow integration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows 'Verify Now' button when kycConfigured=true, item is PENDING, and has verificationProvider", () => {
    const kycItem: ChecklistInstanceItemResponse = {
      ...baseItem,
      verificationProvider: "verifynow",
    };
    render(
      <ChecklistInstanceItemRow
        item={kycItem}
        instanceItems={[kycItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
        kycConfigured={true}
        customerName="John Doe"
        customerId="cust-1"
        slug="acme"
      />,
    );
    expect(screen.getByText("Verify Now")).toBeInTheDocument();
  });

  it("hides 'Verify Now' button when kycConfigured=false", () => {
    const kycItem: ChecklistInstanceItemResponse = {
      ...baseItem,
      verificationProvider: "verifynow",
    };
    render(
      <ChecklistInstanceItemRow
        item={kycItem}
        instanceItems={[kycItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
        kycConfigured={false}
        customerName="John Doe"
        customerId="cust-1"
        slug="acme"
      />,
    );
    expect(screen.queryByText("Verify Now")).not.toBeInTheDocument();
  });

  it("hides 'Verify Now' button when item has no verificationProvider", () => {
    render(
      <ChecklistInstanceItemRow
        item={baseItem}
        instanceItems={[baseItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
        kycConfigured={true}
        customerName="John Doe"
        customerId="cust-1"
        slug="acme"
      />,
    );
    expect(screen.queryByText("Verify Now")).not.toBeInTheDocument();
  });

  it("hides 'Verify Now' button when item status is COMPLETED", () => {
    const completedItem: ChecklistInstanceItemResponse = {
      ...baseItem,
      status: "COMPLETED",
      completedAt: "2026-02-18T12:00:00Z",
      verificationProvider: "verifynow",
    };
    render(
      <ChecklistInstanceItemRow
        item={completedItem}
        instanceItems={[completedItem]}
        onComplete={mockOnComplete}
        onSkip={mockOnSkip}
        onReopen={mockOnReopen}
        isAdmin={true}
        kycConfigured={true}
        customerName="John Doe"
        customerId="cust-1"
        slug="acme"
      />,
    );
    expect(screen.queryByText("Verify Now")).not.toBeInTheDocument();
  });

  it("dialog shows POPIA consent notice and Verify button disabled until consent checked", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();

    render(
      <KycVerificationDialog
        open={true}
        onOpenChange={onOpenChange}
        slug="acme"
        customerId="a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        checklistInstanceItemId="b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e"
        customerName="John Doe"
      />,
    );

    // POPIA consent text should be visible
    expect(
      screen.getByText(/explicit written consent for identity verification/),
    ).toBeInTheDocument();

    // Fill required fields so only consent blocks submission
    const idInput = screen.getByPlaceholderText("Enter ID number");
    const nameInput = screen.getByPlaceholderText("Enter full name");
    await user.type(idInput, "8501015009087");
    await user.type(nameInput, "John Doe");

    // Verify button should exist
    const verifyButton = screen.getByRole("button", { name: "Verify" });
    expect(verifyButton).toBeInTheDocument();
  });

  it("shows VERIFIED result with green success banner after form submission", async () => {
    mockVerifyKycAction.mockResolvedValue({
      success: true,
      data: {
        status: "VERIFIED",
        providerName: "verifynow",
        providerReference: "REF-12345",
        reasonCode: null,
        reasonDescription: null,
        verifiedAt: "2026-02-18T12:00:00Z",
        checklistItemUpdated: true,
      },
    });

    const user = userEvent.setup();
    const onOpenChange = vi.fn();

    render(
      <KycVerificationDialog
        open={true}
        onOpenChange={onOpenChange}
        slug="acme"
        customerId="a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        checklistInstanceItemId="b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e"
        customerName="John Doe"
      />,
    );

    // Fill required fields
    const idInput = screen.getByPlaceholderText("Enter ID number");
    const nameInput = screen.getByPlaceholderText("Enter full name");
    await user.type(idInput, "8501015009087");
    await user.type(nameInput, "John Doe");

    // Check consent checkbox
    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);

    // Submit form by clicking the Verify button
    const verifyButton = screen.getByRole("button", { name: "Verify" });
    await user.click(verifyButton);

    // Wait for the server action to be called and result to render
    await waitFor(() => {
      expect(mockVerifyKycAction).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(screen.getByText("Identity verified")).toBeInTheDocument();
    });
    expect(screen.getByText(/REF-12345/)).toBeInTheDocument();
  });

  it("shows NOT_VERIFIED result with destructive banner and reason after form submission", async () => {
    mockVerifyKycAction.mockResolvedValue({
      success: true,
      data: {
        status: "NOT_VERIFIED",
        providerName: "verifynow",
        providerReference: null,
        reasonCode: "ID_MISMATCH",
        reasonDescription: "The ID number does not match the name provided.",
        verifiedAt: null,
        checklistItemUpdated: false,
      },
    });

    const user = userEvent.setup();
    const onOpenChange = vi.fn();

    render(
      <KycVerificationDialog
        open={true}
        onOpenChange={onOpenChange}
        slug="acme"
        customerId="a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
        checklistInstanceItemId="b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e"
        customerName="John Doe"
      />,
    );

    // Fill required fields
    const idInput = screen.getByPlaceholderText("Enter ID number");
    const nameInput = screen.getByPlaceholderText("Enter full name");
    await user.type(idInput, "0000000000000");
    await user.type(nameInput, "John Doe");

    // Check consent checkbox
    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);

    // Submit form
    const verifyButton = screen.getByRole("button", { name: "Verify" });
    await user.click(verifyButton);

    // Wait for the server action to be called
    await waitFor(() => {
      expect(mockVerifyKycAction).toHaveBeenCalled();
    });

    // Wait for result
    await waitFor(() => {
      expect(screen.getByText("Identity not verified")).toBeInTheDocument();
    });
    expect(
      screen.getByText("The ID number does not match the name provided."),
    ).toBeInTheDocument();
  });
});
