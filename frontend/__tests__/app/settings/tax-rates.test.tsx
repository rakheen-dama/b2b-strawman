import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TaxRateTable } from "@/app/(app)/org/[slug]/settings/fees/_tabs/tax-rate-table";
import type { TaxRateResponse } from "@/lib/types";

const mockCreateTaxRate = vi.fn();
const mockUpdateTaxRate = vi.fn();
const mockDeactivateTaxRate = vi.fn();

// DataTable calls useRouter (Phase 91 665D: the tax-rate list moved onto DataTable).
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn() }),
}));

vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/tax-actions", () => ({
  createTaxRate: (...args: unknown[]) => mockCreateTaxRate(...args),
  updateTaxRate: (...args: unknown[]) => mockUpdateTaxRate(...args),
  deactivateTaxRate: (...args: unknown[]) => mockDeactivateTaxRate(...args),
}));

const vatRate: TaxRateResponse = {
  id: "rate-1",
  name: "VAT",
  rate: 15.0,
  isDefault: true,
  isExempt: false,
  active: true,
  sortOrder: 0,
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const gstRate: TaxRateResponse = {
  id: "rate-2",
  name: "GST",
  rate: 10.0,
  isDefault: false,
  isExempt: false,
  active: true,
  sortOrder: 1,
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const exemptRate: TaxRateResponse = {
  id: "rate-3",
  name: "Zero Rated",
  rate: 0,
  isDefault: false,
  isExempt: true,
  active: false,
  sortOrder: 2,
  createdAt: "2025-01-15T10:00:00Z",
  updatedAt: "2025-01-15T10:00:00Z",
};

const sampleRates: TaxRateResponse[] = [vatRate, gstRate, exemptRate];

describe("TaxRateTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders tax rates in table", () => {
    render(<TaxRateTable slug="acme" taxRates={sampleRates} />);

    expect(screen.getByText("VAT")).toBeInTheDocument();
    expect(screen.getByText("GST")).toBeInTheDocument();
    expect(screen.getByText("Zero Rated")).toBeInTheDocument();
    expect(screen.getByText("15.00%")).toBeInTheDocument();
    expect(screen.getByText("10.00%")).toBeInTheDocument();
    expect(screen.getByText("0.00%")).toBeInTheDocument();
  });

  it("shows empty state when no tax rates", () => {
    render(<TaxRateTable slug="acme" taxRates={[]} />);

    expect(screen.getByText("No tax rates yet")).toBeInTheDocument();
    expect(
      screen.getByText("Create your first tax rate to apply taxes to fee notes.")
    ).toBeInTheDocument();
  });

  it("displays default badge on default tax rate", () => {
    render(<TaxRateTable slug="acme" taxRates={sampleRates} />);

    const defaultBadge = screen.getByText("Default", {
      selector: '[data-variant="success"]',
    });
    expect(defaultBadge).toBeInTheDocument();
  });

  it("displays exempt badge on exempt tax rate", () => {
    render(<TaxRateTable slug="acme" taxRates={sampleRates} />);

    expect(screen.getByText("Exempt")).toBeInTheDocument();
  });

  it("shows Add Tax Rate button", () => {
    render(<TaxRateTable slug="acme" taxRates={sampleRates} />);

    expect(screen.getAllByRole("button", { name: /Add Tax Rate/i }).length).toBeGreaterThanOrEqual(
      1
    );
  });

  it("shows deactivate button only for active non-default rates", () => {
    render(<TaxRateTable slug="acme" taxRates={sampleRates} />);

    // GST is active + non-default → should have deactivate button
    expect(screen.getByRole("button", { name: /Deactivate GST/i })).toBeInTheDocument();

    // VAT is default → no deactivate button
    expect(screen.queryByRole("button", { name: /Deactivate VAT/i })).not.toBeInTheDocument();

    // Zero Rated is inactive → no deactivate button
    expect(
      screen.queryByRole("button", { name: /Deactivate Zero Rated/i })
    ).not.toBeInTheDocument();
  });

  it("deactivate dialog shows confirmation and handles error", async () => {
    const user = userEvent.setup();
    mockDeactivateTaxRate.mockResolvedValue({
      success: false,
      error: "Tax rate is referenced by 3 draft invoice line(s)",
    });

    render(<TaxRateTable slug="acme" taxRates={[gstRate]} />);

    const deactivateBtn = screen.getByRole("button", {
      name: /Deactivate GST/i,
    });
    await user.click(deactivateBtn);

    // Confirmation dialog should appear
    expect(screen.getByText("Deactivate Tax Rate")).toBeInTheDocument();
    expect(screen.getByText(/Are you sure you want to deactivate/)).toBeInTheDocument();

    // Click deactivate in dialog
    const confirmBtn = screen.getByRole("button", { name: /^Deactivate$/i });
    await user.click(confirmBtn);

    // Error should be shown
    expect(
      await screen.findByText("Tax rate is referenced by 3 draft invoice line(s)")
    ).toBeInTheDocument();
  });

  // Phase 91 665D: the list sits in the "Tax Rates" section on DataTable client mode.
  it("renders one tax-rate-row per rate inside the 'Tax Rates' h2 section", () => {
    render(<TaxRateTable slug="acme" taxRates={sampleRates} />);

    const heading = screen.getByRole("heading", { level: 2, name: "Tax Rates" });
    const section = heading.closest('[data-slot="settings-section"]') as HTMLElement;
    expect(section).not.toBeNull();
    const rows = within(section).getAllByTestId("tax-rate-row");
    expect(rows).toHaveLength(3);

    const vatRow = rows[0];
    expect(within(vatRow).getByTestId("tax-rate-name")).toHaveTextContent(/^VAT$/);
    expect(within(vatRow).getByTestId("tax-rate-value")).toHaveTextContent(/^15\.00%$/);
    expect(within(vatRow).getByTestId("tax-rate-default")).toHaveTextContent(/^Default$/);
    // The Name cell holds only the name and the optional Exempt badge (e2e `cell` exact match).
    expect(within(rows[2]).getByRole("cell", { name: "Zero Rated Exempt" })).toBeInTheDocument();
    // One "Add Tax Rate" in the section header when rates exist.
    expect(within(section).getAllByRole("button", { name: "Add Tax Rate" })).toHaveLength(1);
  });

  it("empty: no table, the compact EmptyState heading and two Add Tax Rate buttons", () => {
    render(<TaxRateTable slug="acme" taxRates={[]} />);

    expect(screen.queryByRole("table")).toBeNull();
    expect(screen.getByRole("heading", { name: "No tax rates yet" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Add Tax Rate" })).toHaveLength(2);
  });

  it("Deactivate keeps the destructive colour in dark mode (D-55 pin)", () => {
    render(<TaxRateTable slug="acme" taxRates={[gstRate]} />);

    expect(screen.getByRole("button", { name: "Deactivate GST" })).toHaveClass(
      "dark:text-destructive"
    );
  });
});
