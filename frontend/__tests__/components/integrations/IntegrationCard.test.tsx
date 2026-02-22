import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntegrationCard } from "@/components/integrations/IntegrationCard";
import type { OrgIntegration } from "@/lib/types";

vi.mock("@/app/(app)/org/[slug]/settings/integrations/actions", () => ({
  upsertIntegrationAction: vi.fn().mockResolvedValue({ success: true }),
  setApiKeyAction: vi.fn().mockResolvedValue({ success: true }),
  deleteApiKeyAction: vi.fn().mockResolvedValue({ success: true }),
  toggleIntegrationAction: vi.fn().mockResolvedValue({ success: true }),
  testConnectionAction: vi.fn().mockResolvedValue({ success: true }),
}));

const defaultProps = {
  domain: "ACCOUNTING" as const,
  label: "Accounting",
  description: "Connect your accounting software for invoice sync",
  providers: ["xero", "quickbooks"],
  slug: "acme",
};

describe("IntegrationCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders not configured badge when integration is null", () => {
    render(<IntegrationCard {...defaultProps} integration={null} />);
    expect(screen.getByText("Not Configured")).toBeInTheDocument();
  });

  it("renders active badge when integration is enabled", () => {
    const integration: OrgIntegration = {
      domain: "ACCOUNTING",
      providerSlug: "xero",
      enabled: true,
      keySuffix: "ab12",
      configJson: null,
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(<IntegrationCard {...defaultProps} integration={integration} />);
    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("shows masked key suffix when key is configured", () => {
    const integration: OrgIntegration = {
      domain: "ACCOUNTING",
      providerSlug: "xero",
      enabled: false,
      keySuffix: "ab12",
      configJson: null,
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(<IntegrationCard {...defaultProps} integration={integration} />);
    expect(screen.getByText(/ab12/)).toBeInTheDocument();
  });

  it("opens set api key dialog when button is clicked", async () => {
    const user = userEvent.setup();
    const integration: OrgIntegration = {
      domain: "ACCOUNTING",
      providerSlug: "xero",
      enabled: false,
      keySuffix: null,
      configJson: null,
      updatedAt: "2026-01-01T00:00:00Z",
    };
    render(<IntegrationCard {...defaultProps} integration={integration} />);
    await user.click(screen.getByText("Set API Key"));
    expect(screen.getByText("Set API Key", { selector: "h2" })).toBeInTheDocument();
  });
});
