import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import TaxSettingsPage from "@/app/(app)/org/[slug]/settings/tax/page";

vi.mock("@/lib/auth", () => ({
  getAuthContext: vi.fn().mockResolvedValue({
    userId: "user_1",
    orgId: "org_1",
    orgSlug: "acme",
    orgRole: "org:owner",
    memberId: "member_1",
  }),
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn().mockResolvedValue({
      defaultCurrency: "USD",
      taxRegistrationNumber: "VAT-123",
      taxRegistrationLabel: "VAT Number",
      taxLabel: "VAT",
      taxInclusive: true,
    }),
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(message: string, status: number) {
      super(message);
      this.status = status;
    }
  },
}));

vi.mock("@/app/(app)/org/[slug]/settings/tax/actions", () => ({
  updateTaxSettings: vi.fn().mockResolvedValue({ success: true }),
}));

describe("TaxSettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    cleanup();
  });

  it("renders page heading", async () => {
    const page = await TaxSettingsPage({
      params: Promise.resolve({ slug: "acme" }),
    });
    render(page);
    expect(screen.getByText("Tax Settings")).toBeInTheDocument();
  });

  it("renders tax configuration form fields", async () => {
    const page = await TaxSettingsPage({
      params: Promise.resolve({ slug: "acme" }),
    });
    render(page);
    expect(screen.getByLabelText("Tax Registration Number")).toBeInTheDocument();
    expect(screen.getByLabelText("Registration Label")).toBeInTheDocument();
    expect(screen.getByLabelText("Tax Label")).toBeInTheDocument();
    expect(screen.getByLabelText("Tax-inclusive pricing")).toBeInTheDocument();
  });

  it("renders back link to settings", async () => {
    const page = await TaxSettingsPage({
      params: Promise.resolve({ slug: "acme" }),
    });
    render(page);
    expect(screen.getByText("Settings")).toBeInTheDocument();
  });
});
