import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { PaymentIntegrationCard } from "@/components/integrations/PaymentIntegrationCard";
import type { OrgIntegration } from "@/lib/types";

vi.mock("@/app/(app)/org/[slug]/settings/integrations/actions", () => ({
  upsertIntegrationAction: vi.fn().mockResolvedValue({ success: true }),
  setApiKeyAction: vi.fn().mockResolvedValue({ success: true }),
  deleteApiKeyAction: vi.fn().mockResolvedValue({ success: true }),
  toggleIntegrationAction: vi.fn().mockResolvedValue({ success: true }),
  testConnectionAction: vi.fn().mockResolvedValue({
    success: true,
    data: { success: true, providerName: "stripe", errorMessage: null },
  }),
}));

const defaultProps = {
  providers: ["stripe", "payfast"],
  slug: "acme",
};

describe("PaymentIntegrationCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders Manual Payments Only badge when integration is null", () => {
    render(<PaymentIntegrationCard {...defaultProps} integration={null} />);
    expect(screen.getByText("Manual Payments Only")).toBeInTheDocument();
  });

  it("shows Stripe fields when provider is stripe", () => {
    const integration: OrgIntegration = {
      domain: "PAYMENT",
      providerSlug: "stripe",
      enabled: false,
      keySuffix: null,
      configJson: null,
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(
      <PaymentIntegrationCard {...defaultProps} integration={integration} />,
    );
    expect(
      screen.getByText(/Secret Key \(sk_live_\.\.\. or sk_test_\.\.\.\)/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Webhook Signing Secret \(whsec_\.\.\.\)/),
    ).toBeInTheDocument();
    expect(screen.getByText("Webhook URL")).toBeInTheDocument();
  });

  it("shows PayFast fields when provider is payfast", () => {
    const integration: OrgIntegration = {
      domain: "PAYMENT",
      providerSlug: "payfast",
      enabled: false,
      keySuffix: null,
      configJson: JSON.stringify({
        merchantId: "",
        merchantKey: "",
        sandbox: false,
      }),
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(
      <PaymentIntegrationCard {...defaultProps} integration={integration} />,
    );
    expect(screen.getByText("Merchant ID")).toBeInTheDocument();
    expect(screen.getByText("Merchant Key")).toBeInTheDocument();
    expect(
      screen.getByText(/Use PayFast Sandbox for testing/),
    ).toBeInTheDocument();
    expect(screen.getByText(/ITN Callback URL/)).toBeInTheDocument();
  });

  it("shows advisory note for PayFast instead of test connection button", () => {
    const integration: OrgIntegration = {
      domain: "PAYMENT",
      providerSlug: "payfast",
      enabled: true,
      keySuffix: "xyz9",
      configJson: JSON.stringify({
        merchantId: "123",
        merchantKey: "abc",
        sandbox: true,
      }),
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(
      <PaymentIntegrationCard {...defaultProps} integration={integration} />,
    );
    expect(
      screen.getByText(/Send a test payment to verify/),
    ).toBeInTheDocument();
    expect(screen.queryByText("Test Connection")).not.toBeInTheDocument();
  });

  it("shows test connection button for Stripe when enabled with key", () => {
    const integration: OrgIntegration = {
      domain: "PAYMENT",
      providerSlug: "stripe",
      enabled: true,
      keySuffix: "ab12",
      configJson: null,
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(
      <PaymentIntegrationCard {...defaultProps} integration={integration} />,
    );
    expect(screen.getByText("Test Connection")).toBeInTheDocument();
  });

  it("renders PayFast merchant key as password input", () => {
    const integration: OrgIntegration = {
      domain: "PAYMENT",
      providerSlug: "payfast",
      enabled: false,
      keySuffix: null,
      configJson: JSON.stringify({ merchantId: "", merchantKey: "", sandbox: false }),
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(
      <PaymentIntegrationCard {...defaultProps} integration={integration} />,
    );
    const merchantKeyInput = screen.getByPlaceholderText("Enter your Merchant Key");
    expect(merchantKeyInput).toHaveAttribute("type", "password");
  });

  it("renders Stripe webhook signing secret as password input", () => {
    const integration: OrgIntegration = {
      domain: "PAYMENT",
      providerSlug: "stripe",
      enabled: false,
      keySuffix: null,
      configJson: null,
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(
      <PaymentIntegrationCard {...defaultProps} integration={integration} />,
    );
    const webhookInput = screen.getByPlaceholderText("whsec_...");
    expect(webhookInput).toHaveAttribute("type", "password");
  });
});
