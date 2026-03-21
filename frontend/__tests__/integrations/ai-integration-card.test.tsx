import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntegrationCard } from "@/components/integrations/IntegrationCard";
import type { OrgIntegration } from "@/lib/types";

const mockUpsertIntegrationAction = vi.fn().mockResolvedValue({ success: true });
const mockToggleIntegrationAction = vi.fn().mockResolvedValue({ success: true });
const mockDeleteApiKeyAction = vi.fn().mockResolvedValue({ success: true });
const mockFetchAiModels = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/integrations/actions", () => ({
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
  tier: "PRO",
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
    render(
      <IntegrationCard {...defaultProps} integration={aiIntegration} />,
    );

    await waitFor(() => {
      expect(screen.getByText("Model")).toBeInTheDocument();
    });

    // The select trigger should show the current model
    const modelTrigger = screen.getByRole("combobox", { name: "Model" });
    expect(modelTrigger).toBeInTheDocument();
  });

  it("shows PRO badge on AI integration card for non-PRO tier", () => {
    render(
      <IntegrationCard {...defaultProps} tier="STARTER" integration={null} />,
    );

    expect(screen.getByText("PRO")).toBeInTheDocument();
  });

  it("hides PRO badge when org is already on PRO tier", () => {
    render(
      <IntegrationCard {...defaultProps} tier="PRO" integration={null} />,
    );

    expect(screen.queryByText("PRO")).not.toBeInTheDocument();
  });

  it("shows upgrade prompt and disables controls for STARTER tier", () => {
    render(
      <IntegrationCard
        {...defaultProps}
        tier="STARTER"
        integration={aiIntegration}
      />,
    );

    // Upgrade prompt text
    expect(
      screen.getByText("AI Assistant requires the PRO plan"),
    ).toBeInTheDocument();
    expect(screen.getByText("Upgrade to Pro")).toBeInTheDocument();

    // Provider selector should be disabled
    const providerTrigger = screen.getByRole("combobox", { name: "Provider" });
    expect(providerTrigger).toBeDisabled();

    // Toggle should be disabled
    const toggle = screen.getByRole("switch", { name: "Enabled" });
    expect(toggle).toBeDisabled();
  });

  it("calls upsert action with updated configJson on model change", async () => {
    const user = userEvent.setup();

    render(
      <IntegrationCard {...defaultProps} integration={aiIntegration} />,
    );

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
      expect(mockUpsertIntegrationAction).toHaveBeenCalledWith(
        "acme",
        "AI",
        {
          providerSlug: "anthropic",
          configJson: JSON.stringify({ model: "claude-opus-4-6" }),
        },
      );
    });
  });
});
