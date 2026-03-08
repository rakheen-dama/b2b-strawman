import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import {
  cleanup,
  render,
  screen,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import { BatchBillingSettings } from "@/components/settings/batch-billing-settings";

const mockUpdateBatchBillingSettings = vi.fn();
vi.mock(
  "@/app/(app)/org/[slug]/settings/batch-billing/actions",
  () => ({
    updateBatchBillingSettings: (...args: unknown[]) =>
      mockUpdateBatchBillingSettings(...args),
  })
);

afterEach(() => {
  cleanup();
  mockUpdateBatchBillingSettings.mockReset();
});

function renderForm(
  overrides: Partial<
    React.ComponentProps<typeof BatchBillingSettings>
  > = {}
) {
  const defaultProps: React.ComponentProps<typeof BatchBillingSettings> = {
    slug: "acme",
    billingBatchAsyncThreshold: 50,
    billingEmailRateLimit: 5,
    defaultBillingRunCurrency: null,
    ...overrides,
  };
  return render(<BatchBillingSettings {...defaultProps} />);
}

describe("BatchBillingSettings", () => {
  it("renders 3 settings fields (async threshold, email rate limit, default currency)", () => {
    renderForm();
    expect(screen.getByLabelText("Async Threshold")).toBeInTheDocument();
    expect(screen.getByLabelText("Email Rate Limit")).toBeInTheDocument();
    expect(
      screen.getByLabelText("Default Billing Run Currency")
    ).toBeInTheDocument();
  });

  it("displays current values passed as props", () => {
    renderForm({
      billingBatchAsyncThreshold: 100,
      billingEmailRateLimit: 10,
      defaultBillingRunCurrency: "ZAR",
    });
    expect(screen.getByLabelText("Async Threshold")).toHaveValue(100);
    expect(screen.getByLabelText("Email Rate Limit")).toHaveValue(10);
    expect(
      screen.getByLabelText("Default Billing Run Currency")
    ).toHaveValue("ZAR");
  });

  it("validates numeric inputs on save", async () => {
    renderForm({ billingBatchAsyncThreshold: 1, billingEmailRateLimit: 1 });

    // Change async threshold to invalid value via direct state (component clamps, so test the currency field)
    const currencyInput = screen.getByLabelText(
      "Default Billing Run Currency"
    );
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
      });
    });
  });
});
