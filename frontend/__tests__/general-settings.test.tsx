import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { GeneralSettingsForm } from "@/components/settings/general-settings-form";
import { renderGeneralTab } from "@/app/(app)/org/[slug]/settings/firm/_tabs/general";
import type { LetterheadSettings } from "@/lib/types";

const mockUpdateGeneralSettings = vi.fn();
const mockUpdateGeneralTaxSettings = vi.fn();
const mockUploadLogoAction = vi.fn();
const mockDeleteLogoAction = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/general-actions", () => ({
  updateGeneralSettings: (...args: unknown[]) => mockUpdateGeneralSettings(...args),
  updateGeneralTaxSettings: (...args: unknown[]) => mockUpdateGeneralTaxSettings(...args),
  uploadLogoAction: (...args: unknown[]) => mockUploadLogoAction(...args),
  deleteLogoAction: (...args: unknown[]) => mockDeleteLogoAction(...args),
  updateLetterheadSettingsAction: vi.fn(),
}));

// ---- renderGeneralTab (Firm › General loader) server deps — used by the 626C mount describe ----
vi.mock("server-only", () => ({}));
const { mockCaps, mockApiGet } = vi.hoisted(() => ({ mockCaps: vi.fn(), mockApiGet: vi.fn() }));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));
vi.mock("@/lib/api", () => ({ api: { get: (...args: unknown[]) => mockApiGet(...args) } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/general-portal-actions", () => ({
  updatePortalDigestCadence: vi.fn(),
}));
vi.mock("@/components/documents/org-document-upload", () => ({
  OrgDocumentUpload: () => <button type="button">Upload Document</button>,
}));
// The letterhead form has its own test file; here we only pin where and with what it is mounted.
vi.mock("@/components/settings/letterhead-settings-form", () => ({
  LetterheadSettingsForm: ({
    slug,
    letterhead,
  }: {
    slug: string;
    letterhead: LetterheadSettings;
  }) => (
    <section data-testid="letterhead-form-mount">{`${slug}|${letterhead.city}|${letterhead.signatoryName}`}</section>
  ),
}));

afterEach(() => {
  cleanup();
  mockUpdateGeneralSettings.mockReset();
  mockUpdateGeneralTaxSettings.mockReset();
  mockUploadLogoAction.mockReset();
  mockDeleteLogoAction.mockReset();
  mockCaps.mockReset();
  mockApiGet.mockReset();
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

describe("renderGeneralTab — letterhead mount (626C)", () => {
  const LETTERHEAD: LetterheadSettings = {
    addressLine1: "12 Rissik Street",
    addressLine2: null,
    addressLine3: null,
    city: "Johannesburg",
    postalCode: "2001",
    country: "South Africa",
    phone: "+27 11 555 0100",
    email: "info@mathebula.law",
    website: null,
    bankName: "FNB",
    bankBranchCode: "250655",
    bankAccountNumber: "62012345678",
    bankAccountType: "Cheque",
    paymentReferenceNote: null,
    signatoryName: "Thandi Mathebula",
    signatoryDesignation: "Director",
  };

  const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
  const ADMIN = { capabilities: [], role: "admin", isAdmin: true, isOwner: false };
  const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };

  function routeGets(settings: Record<string, unknown>) {
    mockApiGet.mockImplementation((url: string) => {
      if (url === "/api/documents?scope=ORG") return Promise.resolve([]);
      if (url === "/api/settings") return Promise.resolve(settings);
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });
  }

  const BASE_SETTINGS = {
    defaultCurrency: "ZAR",
    brandColor: "#1a2b3c",
    portalDigestCadence: "WEEKLY",
  };

  beforeEach(() => {
    routeGets({ ...BASE_SETTINGS, letterhead: LETTERHEAD });
  });

  it("owner: mounts the letterhead form with the settings' letterhead, after Save Settings", async () => {
    mockCaps.mockResolvedValue(OWNER);

    render(await renderGeneralTab({ slug: "acme", searchParams: {} }));

    const mount = screen.getByTestId("letterhead-form-mount");
    expect(mount).toHaveTextContent("acme|Johannesburg|Thandi Mathebula");
    const saveSettings = screen.getByRole("button", { name: "Save Settings" });
    expect(saveSettings.compareDocumentPosition(mount) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING
    );
    // One read: the letterhead comes from GET /api/settings, never a second endpoint.
    expect(mockApiGet).not.toHaveBeenCalledWith("/api/settings/letterhead");
    expect(mockApiGet.mock.calls.filter(([url]) => url === "/api/settings")).toHaveLength(1);
    expect(screen.getByText(/branding and letterhead/)).toBeInTheDocument();
  });

  it("admin: mounts the letterhead form", async () => {
    mockCaps.mockResolvedValue(ADMIN);

    render(await renderGeneralTab({ slug: "acme", searchParams: {} }));

    expect(screen.getByTestId("letterhead-form-mount")).toBeInTheDocument();
  });

  it("member: does not mount the letterhead form and does not read settings", async () => {
    mockCaps.mockResolvedValue(MEMBER);

    render(await renderGeneralTab({ slug: "acme", searchParams: {} }));

    expect(screen.queryByTestId("letterhead-form-mount")).not.toBeInTheDocument();
    expect(mockApiGet).not.toHaveBeenCalledWith("/api/settings");
  });

  it("absent letterhead: renders no letterhead form (never a blank full-replace form)", async () => {
    mockCaps.mockResolvedValue(OWNER);
    routeGets({ ...BASE_SETTINGS });

    render(await renderGeneralTab({ slug: "acme", searchParams: {} }));

    expect(screen.getByRole("button", { name: "Save Settings" })).toBeInTheDocument();
    expect(screen.queryByTestId("letterhead-form-mount")).not.toBeInTheDocument();
  });
});
