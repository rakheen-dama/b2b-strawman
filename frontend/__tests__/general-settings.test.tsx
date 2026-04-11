import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { GeneralSettingsForm } from "@/components/settings/general-settings-form";

const mockUpdateGeneralSettings = vi.fn();
const mockUpdateGeneralTaxSettings = vi.fn();
const mockUploadLogoAction = vi.fn();
const mockDeleteLogoAction = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/general/actions", () => ({
  updateGeneralSettings: (...args: unknown[]) => mockUpdateGeneralSettings(...args),
  updateGeneralTaxSettings: (...args: unknown[]) => mockUpdateGeneralTaxSettings(...args),
  uploadLogoAction: (...args: unknown[]) => mockUploadLogoAction(...args),
  deleteLogoAction: (...args: unknown[]) => mockDeleteLogoAction(...args),
}));

afterEach(() => {
  cleanup();
  mockUpdateGeneralSettings.mockReset();
  mockUpdateGeneralTaxSettings.mockReset();
  mockUploadLogoAction.mockReset();
  mockDeleteLogoAction.mockReset();
});

function renderForm(overrides: Partial<React.ComponentProps<typeof GeneralSettingsForm>> = {}) {
  const defaultProps: React.ComponentProps<typeof GeneralSettingsForm> = {
    slug: "acme",
    defaultCurrency: "ZAR",
    logoUrl: null,
    brandColor: "#1a2b3c",
    documentFooterText: "Footer text here",
    taxRegistrationNumber: "VAT-123",
    taxLabel: "VAT",
    taxInclusive: true,
    ...overrides,
  };
  return render(<GeneralSettingsForm {...defaultProps} />);
}

describe("GeneralSettingsForm", () => {
  it("renders with existing org settings values pre-filled", () => {
    renderForm();

    // Brand color (text input, not the native color picker)
    const brandColorInput = screen.getByLabelText("Brand Color");
    expect(brandColorInput).toHaveValue("#1a2b3c");

    // Footer text
    expect(screen.getByDisplayValue("Footer text here")).toBeInTheDocument();

    // Tax registration number
    expect(screen.getByDisplayValue("VAT-123")).toBeInTheDocument();

    // Tax label
    expect(screen.getByDisplayValue("VAT")).toBeInTheDocument();
  });

  it("calls server actions with correct payload on save", async () => {
    mockUpdateGeneralSettings.mockResolvedValue({ success: true });
    mockUpdateGeneralTaxSettings.mockResolvedValue({ success: true });

    renderForm();

    fireEvent.click(screen.getByRole("button", { name: /Save Settings/i }));

    await waitFor(() => {
      expect(mockUpdateGeneralSettings).toHaveBeenCalledWith("acme", {
        defaultCurrency: "ZAR",
        brandColor: "#1a2b3c",
        documentFooterText: "Footer text here",
      });
      expect(mockUpdateGeneralTaxSettings).toHaveBeenCalledWith("acme", {
        taxRegistrationNumber: "VAT-123",
        taxLabel: "VAT",
        taxInclusive: true,
      });
    });
  });

  it("renders currency select with correct options", () => {
    renderForm();

    // The select trigger should show the current currency
    expect(screen.getByText("ZAR — South African Rand")).toBeInTheDocument();
  });

  it("displays logo preview when URL is present", () => {
    renderForm({ logoUrl: "https://example.com/logo.png" });

    const logoImage = screen.getByAltText("Organization logo");
    expect(logoImage).toBeInTheDocument();
    expect(logoImage).toHaveAttribute("src", "https://example.com/logo.png");
  });
});
