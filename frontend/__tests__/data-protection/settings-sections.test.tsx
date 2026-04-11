import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
const mockRefresh = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh, push: vi.fn() }),
  usePathname: () => "/org/acme/settings/data-protection",
}));

// Mock server actions
const mockUpdateRetentionPolicy = vi.fn();
const mockEvaluateRetentionPolicies = vi.fn();
const mockExecuteRetentionPurge = vi.fn();
const mockCreateProcessingActivity = vi.fn();
const mockUpdateProcessingActivity = vi.fn();
const mockDeleteProcessingActivity = vi.fn();
const mockGeneratePaiaManual = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/data-protection/actions", () => ({
  updateRetentionPolicy: (...args: unknown[]) => mockUpdateRetentionPolicy(...args),
  evaluateRetentionPolicies: (...args: unknown[]) => mockEvaluateRetentionPolicies(...args),
  executeRetentionPurge: (...args: unknown[]) => mockExecuteRetentionPurge(...args),
  createProcessingActivity: (...args: unknown[]) => mockCreateProcessingActivity(...args),
  updateProcessingActivity: (...args: unknown[]) => mockUpdateProcessingActivity(...args),
  deleteProcessingActivity: (...args: unknown[]) => mockDeleteProcessingActivity(...args),
  generatePaiaManual: (...args: unknown[]) => mockGeneratePaiaManual(...args),
  fetchRetentionPolicies: vi.fn().mockResolvedValue([]),
  fetchProcessingActivities: vi.fn().mockResolvedValue([]),
}));

// Mock sonner
vi.mock("sonner", () => ({
  toast: {
    info: vi.fn(),
    error: vi.fn(),
    success: vi.fn(),
  },
}));

import { RetentionPoliciesTable } from "@/components/data-protection/retention-policies-table";
import { ProcessingRegisterTable } from "@/components/data-protection/processing-register-table";
import { PaiaManualSection } from "@/components/data-protection/paia-manual-section";
import type { RetentionPolicyExtended, ProcessingActivity } from "@/lib/types/data-protection";

// --- Test data ---

const basePolicy: RetentionPolicyExtended = {
  id: "pol-1",
  recordType: "CUSTOMER",
  retentionDays: 365,
  triggerEvent: "CUSTOMER_OFFBOARDED",
  action: "ANONYMIZE",
  active: true,
  description: null,
  lastEvaluatedAt: "2026-03-10T08:00:00Z",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-03-10T08:00:00Z",
};

const financialPolicy: RetentionPolicyExtended = {
  id: "pol-2",
  recordType: "DOCUMENT",
  retentionDays: 1825,
  triggerEvent: "RECORD_CREATED",
  action: "FLAG",
  active: true,
  description: null,
  lastEvaluatedAt: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const baseActivity: ProcessingActivity = {
  id: "act-1",
  category: "Client Management",
  description: "Processing client contact details for engagement delivery",
  legalBasis: "Legitimate interest",
  dataSubjects: "Clients",
  retentionPeriod: "5 years after contract end",
  recipients: "Cloud providers",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// --- Retention Policies Table ---

describe("RetentionPoliciesTable", () => {
  it("renders retention policies table with correct columns", () => {
    render(
      <RetentionPoliciesTable
        policies={[basePolicy, financialPolicy]}
        slug="acme"
        financialRetentionMonths={60}
      />
    );

    // Check header columns
    expect(screen.getByText("Entity Type")).toBeInTheDocument();
    expect(screen.getByText("Retention Period")).toBeInTheDocument();
    expect(screen.getByText("Action")).toBeInTheDocument();
    expect(screen.getByText("Enabled")).toBeInTheDocument();
    expect(screen.getByText("Last Evaluated")).toBeInTheDocument();

    // Check row data
    expect(screen.getByText("CUSTOMER")).toBeInTheDocument();
    expect(screen.getByText("DOCUMENT")).toBeInTheDocument();
  });

  it("calls updateRetentionPolicy on save", async () => {
    const user = userEvent.setup();
    mockUpdateRetentionPolicy.mockResolvedValue({ success: true });

    render(<RetentionPoliciesTable policies={[basePolicy]} slug="acme" />);

    // Change retention days using fireEvent for reliable number input updates
    const input = screen.getByLabelText(/retention days for CUSTOMER/i);
    fireEvent.change(input, { target: { value: "730" } });

    // Click save
    await user.click(screen.getByRole("button", { name: /save CUSTOMER policy/i }));

    await waitFor(() => {
      expect(mockUpdateRetentionPolicy).toHaveBeenCalledWith(
        "acme",
        "pol-1",
        expect.objectContaining({
          retentionDays: 730,
          enabled: true,
        })
      );
    });
  });

  it("rejects retention period below financial minimum for financial record types", async () => {
    const user = userEvent.setup();

    render(
      <RetentionPoliciesTable
        policies={[financialPolicy]}
        slug="acme"
        financialRetentionMonths={60}
      />
    );

    // Set retention days below 60 months * 30 = 1800 days
    const input = screen.getByLabelText(/retention days for DOCUMENT/i);
    fireEvent.change(input, { target: { value: "100" } });

    // Click save
    await user.click(screen.getByRole("button", { name: /save DOCUMENT policy/i }));

    // Should show validation error, not call the server action
    await waitFor(() => {
      expect(screen.getByText(/financial records require at least 1800 days/i)).toBeInTheDocument();
    });
    expect(mockUpdateRetentionPolicy).not.toHaveBeenCalled();
  });
});

// --- Processing Register Table ---

describe("ProcessingRegisterTable", () => {
  it("renders processing register table with seeded entries", () => {
    render(<ProcessingRegisterTable activities={[baseActivity]} slug="acme" />);

    // Check header columns
    expect(screen.getByText("Category")).toBeInTheDocument();
    expect(screen.getByText("Description")).toBeInTheDocument();
    expect(screen.getByText("Legal Basis")).toBeInTheDocument();
    expect(screen.getByText("Data Subjects")).toBeInTheDocument();
    expect(screen.getByText("Retention Period")).toBeInTheDocument();

    // Check row data
    expect(screen.getByText("Client Management")).toBeInTheDocument();
    expect(screen.getByText("Legitimate interest")).toBeInTheDocument();
    expect(screen.getByText("Clients")).toBeInTheDocument();
    expect(screen.getByText("5 years after contract end")).toBeInTheDocument();
  });
});

// --- PAIA Manual Section ---

describe("PaiaManualSection", () => {
  it("shows Generate button and jurisdiction warning when no jurisdiction set", () => {
    render(<PaiaManualSection slug="acme" jurisdiction={null} />);

    expect(screen.getByRole("button", { name: /generate paia manual/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /generate paia manual/i })).toBeDisabled();
    expect(screen.getByText(/jurisdiction required/i)).toBeInTheDocument();
  });

  it("enables Generate button when jurisdiction is set", () => {
    render(<PaiaManualSection slug="acme" jurisdiction="ZA" />);

    expect(screen.getByRole("button", { name: /generate paia manual/i })).toBeEnabled();
    expect(screen.queryByText(/jurisdiction required/i)).not.toBeInTheDocument();
  });
});
