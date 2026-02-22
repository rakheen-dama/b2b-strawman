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

  it("renders 4 domain cards", async () => {
    const page = await IntegrationsSettingsPage({
      params: Promise.resolve({ slug: "acme" }),
    });
    render(page);
    expect(screen.getByText("Accounting")).toBeInTheDocument();
    expect(screen.getByText("AI Assistant")).toBeInTheDocument();
    expect(screen.getByText("Document Signing")).toBeInTheDocument();
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
