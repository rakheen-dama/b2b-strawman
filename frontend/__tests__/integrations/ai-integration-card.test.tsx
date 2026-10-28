import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntegrationCard } from "@/components/integrations/IntegrationCard";
import type { OrgIntegration } from "@/lib/types";

const mockUpsertIntegrationAction = vi.fn().mockResolvedValue({ success: true });
const mockToggleIntegrationAction = vi.fn().mockResolvedValue({ success: true });
const mockDeleteApiKeyAction = vi.fn().mockResolvedValue({ success: true });
const mockFetchAiModels = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/connections/_tabs/integrations-actions", () => ({
  upsertIntegrationAction: (...args: unknown[]) => mockUpsertIntegrationAction(...args),
  toggleIntegrationAction: (...args: unknown[]) => mockToggleIntegrationAction(...args),
  deleteApiKeyAction: (...args: unknown[]) => mockDeleteApiKeyAction(...args),
  fetchAiModels: (...args: unknown[]) => mockFetchAiModels(...args),
}));

const aiIntegration: OrgIntegration = {
  domain: "AI",
  providerSlug: "anthropic",
  enabled: true,
  keySuffix: "xk9z",
  configJson: JSON.stringify({ model: "claude-sonnet-4-6" }),
  updatedAt: "2026-03-21T00:00:00Z",
};

const aiModelsResponse = {
  models: [
    { id: "claude-sonnet-4-6", name: "Claude Sonnet 4.6", recommended: true },
    { id: "claude-opus-4-6", name: "Claude Opus 4.6", recommended: false },
    { id: "claude-haiku-4-5", name: "Claude Haiku 4.5", recommended: false },
  ],
};

const defaultProps = {
  domain: "AI" as const,
  label: "AI Assistant",
  description: "Enable AI-powered document drafting and analysis",
  providers: ["anthropic", "openai"],
  slug: "acme",
};

describe("AI IntegrationCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchAiModels.mockResolvedValue(aiModelsResponse);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders model selector dropdown with models from API", async () => {
    render(<IntegrationCard {...defaultProps} integration={aiIntegration} />);

    await waitFor(() => {
      expect(screen.getByText("Model")).toBeInTheDocument();
    });

    // The select trigger should show the current model
    const modelTrigger = screen.getByRole("combobox", { name: "Model" });
    expect(modelTrigger).toBeInTheDocument();
  });

  it("AI controls are enabled with no plan badge or upgrade prompt", () => {
    render(<IntegrationCard {...defaultProps} integration={aiIntegration} />);

    expect(screen.queryByText("PRO")).not.toBeInTheDocument();
    expect(screen.queryByText(/Upgrade/)).not.toBeInTheDocument();
    expect(screen.queryByText(/requires the PRO plan/)).not.toBeInTheDocument();

    expect(screen.getByRole("combobox", { name: "Provider" })).not.toBeDisabled();
    expect(screen.getByRole("switch", { name: "Enabled" })).not.toBeDisabled();
  });

  it("calls upsert action with updated configJson on model change", async () => {
    const user = userEvent.setup();

    render(<IntegrationCard {...defaultProps} integration={aiIntegration} />);

    // Wait for models to load
    await waitFor(() => {
      expect(screen.getByText("Model")).toBeInTheDocument();
    });

    // Click the model selector trigger
    const modelTrigger = screen.getByRole("combobox", { name: "Model" });
    await user.click(modelTrigger);

    // Select a different model
    await waitFor(() => {
      expect(screen.getByText("Claude Opus 4.6")).toBeInTheDocument();
    });
    await user.click(screen.getByText("Claude Opus 4.6"));

    // Verify upsert was called with the new model
    await waitFor(() => {
      expect(mockUpsertIntegrationAction).toHaveBeenCalledWith("acme", "AI", {
        providerSlug: "anthropic",
        configJson: JSON.stringify({ model: "claude-opus-4-6" }),
      });
    });
  });
});
