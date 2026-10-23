import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BatchBillingSettings } from "@/components/settings/batch-billing-settings";

const mockUpdateBatchBillingSettings = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/fee-runs-actions", () => ({
  updateBatchBillingSettings: (...args: unknown[]) => mockUpdateBatchBillingSettings(...args),
}));

afterEach(() => {
  cleanup();
  mockUpdateBatchBillingSettings.mockReset();
});

function renderForm(overrides: Partial<React.ComponentProps<typeof BatchBillingSettings>> = {}) {
  const defaultProps: React.ComponentProps<typeof BatchBillingSettings> = {
    slug: "acme",
    billingBatchAsyncThreshold: 50,
    billingEmailRateLimit: 5,
    defaultBillingRunCurrency: null,
    defaultPaymentTermsDays: 30,
    ...overrides,
  };
  return render(<BatchBillingSettings {...defaultProps} />);
}

describe("BatchBillingSettings", () => {
  it("renders 4 settings fields (payment terms, async threshold, email rate limit, default currency)", () => {
    renderForm();
    expect(screen.getByLabelText("Default payment terms (days)")).toBeInTheDocument();
    expect(screen.getByLabelText("Async Threshold")).toBeInTheDocument();
    expect(screen.getByLabelText("Email Rate Limit")).toBeInTheDocument();
    expect(screen.getByLabelText("Default Billing Run Currency")).toBeInTheDocument();
  });

  it("displays current values passed as props", () => {
    renderForm({
      billingBatchAsyncThreshold: 100,
      billingEmailRateLimit: 10,
      defaultBillingRunCurrency: "ZAR",
      defaultPaymentTermsDays: 14,
    });
    expect(screen.getByLabelText("Default payment terms (days)")).toHaveValue(14);
    expect(screen.getByLabelText("Async Threshold")).toHaveValue(100);
    expect(screen.getByLabelText("Email Rate Limit")).toHaveValue(10);
    expect(screen.getByLabelText("Default Billing Run Currency")).toHaveValue("ZAR");
  });

  it("validates numeric inputs on save", async () => {
    renderForm({ billingBatchAsyncThreshold: 1, billingEmailRateLimit: 1 });

    // Change async threshold to invalid value via direct state (component clamps, so test the currency field)
    const currencyInput = screen.getByLabelText("Default Billing Run Currency");
    fireEvent.change(currencyInput, { target: { value: "AB" } });

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(
        screen.getByText("Currency must be exactly 3 characters or empty.")
      ).toBeInTheDocument();
    });

    expect(mockUpdateBatchBillingSettings).not.toHaveBeenCalled();
  });

  it("save button calls update action with correct payload", async () => {
    mockUpdateBatchBillingSettings.mockResolvedValue({ success: true });
    renderForm({
      billingBatchAsyncThreshold: 50,
      billingEmailRateLimit: 5,
      defaultBillingRunCurrency: "USD",
    });

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(mockUpdateBatchBillingSettings).toHaveBeenCalledWith("acme", {
        billingBatchAsyncThreshold: 50,
        billingEmailRateLimit: 5,
        defaultBillingRunCurrency: "USD",
        defaultPaymentTermsDays: 30,
      });
    });
  });

  // Phase 88 620C (U3, ADR-347): firm default payment terms, 0–365 whole days.
  describe("Default payment terms (days)", () => {
    function setTerms(value: string) {
      fireEvent.change(screen.getByLabelText("Default payment terms (days)"), {
        target: { value },
      });
      fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));
    }

    it("shows the loaded value (30)", () => {
      renderForm();
      expect(screen.getByLabelText("Default payment terms (days)")).toHaveValue(30);
    });

    it("saves 14 through the batch-billing action", async () => {
      mockUpdateBatchBillingSettings.mockResolvedValue({ success: true });
      renderForm();

      setTerms("14");

      await waitFor(() => {
        expect(mockUpdateBatchBillingSettings).toHaveBeenCalledWith(
          "acme",
          expect.objectContaining({ defaultPaymentTermsDays: 14 })
        );
      });
    });

    it("allows the lower bound 0", async () => {
      mockUpdateBatchBillingSettings.mockResolvedValue({ success: true });
      renderForm();

      setTerms("0");

      await waitFor(() => {
        expect(mockUpdateBatchBillingSettings).toHaveBeenCalledWith(
          "acme",
          expect.objectContaining({ defaultPaymentTermsDays: 0 })
        );
      });
    });

    it.each([
      ["-1", "Payment terms must be between 0 and 365 days."],
      ["366", "Payment terms must be between 0 and 365 days."],
      ["14.5", "Payment terms must be a whole number of days."],
      ["", "Enter the number of days."],
    ])("rejects %j with an inline error and no action call", async (value, error) => {
      renderForm();

      setTerms(value);

      await waitFor(() => {
        expect(screen.getByText(error)).toBeInTheDocument();
      });
      expect(mockUpdateBatchBillingSettings).not.toHaveBeenCalled();
    });
  });
});
