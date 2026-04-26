import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { KycIntegrationCard } from "@/components/integrations/KycIntegrationCard";
import type { OrgIntegration } from "@/lib/types";

const mockUpsertIntegrationAction = vi.fn().mockResolvedValue({ success: true });
const mockDeleteApiKeyAction = vi.fn().mockResolvedValue({ success: true });
const mockToggleIntegrationAction = vi.fn().mockResolvedValue({ success: true });

vi.mock("@/app/(app)/org/[slug]/settings/integrations/actions", () => ({
  upsertIntegrationAction: (...args: unknown[]) => mockUpsertIntegrationAction(...args),
  setApiKeyAction: vi.fn().mockResolvedValue({ success: true }),
  deleteApiKeyAction: (...args: unknown[]) => mockDeleteApiKeyAction(...args),
  toggleIntegrationAction: (...args: unknown[]) => mockToggleIntegrationAction(...args),
  testConnectionAction: vi.fn().mockResolvedValue({ success: true }),
}));

describe("KycIntegrationCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders 'Not Configured' when integration is null", () => {
    render(<KycIntegrationCard integration={null} slug="acme" />);
    expect(screen.getByText("Not Configured")).toBeInTheDocument();
    expect(screen.getByText("KYC Verification")).toBeInTheDocument();
    expect(screen.getByText("Configure")).toBeInTheDocument();
    // Remove button should not be visible when not configured
    expect(screen.queryByText("Remove")).not.toBeInTheDocument();
  });

  it("renders provider name and 'Configured' badge when integration is present and enabled", () => {
    const integration: OrgIntegration = {
      domain: "KYC_VERIFICATION",
      providerSlug: "verifynow",
      enabled: true,
      keySuffix: "ab12",
      configJson: null,
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(<KycIntegrationCard integration={integration} slug="acme" />);
    expect(screen.getByText("Configured")).toBeInTheDocument();
    expect(screen.getByText("VerifyNow")).toBeInTheDocument();
    expect(screen.getByText("Remove")).toBeInTheDocument();
  });

  it("configuration dialog renders with provider selector and API key field", async () => {
    const user = userEvent.setup();
    render(<KycIntegrationCard integration={null} slug="acme" />);

    // Open configuration dialog
    await user.click(screen.getByText("Configure"));

    // Dialog should be open with provider selector and API key field
    expect(screen.getByText("Configure KYC Verification")).toBeInTheDocument();
    expect(screen.getByLabelText("Provider")).toBeInTheDocument();
    expect(screen.getByLabelText("API Key")).toBeInTheDocument();
  });

  it("clicking 'Remove' calls remove actions", async () => {
    const user = userEvent.setup();
    const integration: OrgIntegration = {
      domain: "KYC_VERIFICATION",
      providerSlug: "verifynow",
      enabled: true,
      keySuffix: "ab12",
      configJson: null,
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(<KycIntegrationCard integration={integration} slug="acme" />);

    await user.click(screen.getByText("Remove"));

    expect(mockDeleteApiKeyAction).toHaveBeenCalledWith("acme", "KYC_VERIFICATION");
    expect(mockUpsertIntegrationAction).toHaveBeenCalledWith("acme", "KYC_VERIFICATION", {
      providerSlug: "",
    });
  });

  it("renders Enable Switch unchecked when provider configured but enabled=false, and toggling calls toggleIntegrationAction with enabled=true", async () => {
    const user = userEvent.setup();
    const integration: OrgIntegration = {
      domain: "KYC_VERIFICATION",
      providerSlug: "verifynow",
      enabled: false,
      keySuffix: "ab12",
      configJson: null,
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(<KycIntegrationCard integration={integration} slug="acme" />);

    const toggle = screen.getByRole("switch", { name: "Enabled" });
    expect(toggle).toBeInTheDocument();
    expect(toggle).toHaveAttribute("aria-checked", "false");

    await user.click(toggle);

    expect(mockToggleIntegrationAction).toHaveBeenCalledWith("acme", "KYC_VERIFICATION", {
      enabled: true,
    });
  });

  it("hides the Enable Switch when no provider is configured", () => {
    render(<KycIntegrationCard integration={null} slug="acme" />);
    expect(screen.queryByRole("switch", { name: "Enabled" })).not.toBeInTheDocument();
  });
});
