import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import IntegrationsSettingsPage from "@/app/(app)/org/[slug]/settings/integrations/page";

vi.mock("@/lib/api/integrations", () => ({
  listIntegrations: vi.fn().mockResolvedValue([]),
  listProviders: vi.fn().mockResolvedValue({}),
}));

vi.mock("@/app/(app)/org/[slug]/settings/integrations/actions", () => ({
  upsertIntegrationAction: vi.fn().mockResolvedValue({ success: true }),
  setApiKeyAction: vi.fn().mockResolvedValue({ success: true }),
  deleteApiKeyAction: vi.fn().mockResolvedValue({ success: true }),
  toggleIntegrationAction: vi.fn().mockResolvedValue({ success: true }),
  testConnectionAction: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock("@/lib/actions/email", () => ({
  getEmailStats: vi.fn().mockResolvedValue({ success: true, data: { sent24h: 0, bounced7d: 0, failed7d: 0, rateLimited7d: 0, currentHourUsage: 0, hourlyLimit: 100, providerSlug: null } }),
  sendTestEmail: vi.fn().mockResolvedValue({ success: true }),
}));

describe("IntegrationsSettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders page heading", async () => {
    const page = await IntegrationsSettingsPage({
      params: Promise.resolve({ slug: "acme" }),
    });
    render(page);
    expect(screen.getByText("Integrations")).toBeInTheDocument();
  });

  it("renders 5 domain cards", async () => {
    const page = await IntegrationsSettingsPage({
      params: Promise.resolve({ slug: "acme" }),
    });
    render(page);
    expect(screen.getByText("Accounting")).toBeInTheDocument();
    expect(screen.getByText("AI Assistant")).toBeInTheDocument();
    expect(screen.getByText("Document Signing")).toBeInTheDocument();
    expect(screen.getByText("Email Delivery")).toBeInTheDocument();
    expect(screen.getByText("Payment Gateway")).toBeInTheDocument();
  });

  it("renders back link to settings", async () => {
    const page = await IntegrationsSettingsPage({
      params: Promise.resolve({ slug: "acme" }),
    });
    render(page);
    expect(screen.getByText("Settings")).toBeInTheDocument();
  });
});
