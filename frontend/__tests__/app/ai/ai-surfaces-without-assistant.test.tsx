import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const OWNER_CAPS = {
  capabilities: ["AI_MANAGE", "AI_REVIEW", "AI_EXECUTE"],
  role: "owner",
  isAdmin: true,
  isOwner: true,
};
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn(async () => OWNER_CAPS),
}));

const EMPTY_PAGE = { content: [], page: { totalElements: 0, totalPages: 0, size: 20, number: 0 } };
vi.mock("@/lib/api/ai", () => ({
  getAiProfile: vi.fn(async () => ({
    practiceAreas: [],
    jurisdiction: "ZA",
    riskCalibration: "MODERATE",
    houseStyleNotes: null,
    ficaRequirements: null,
    feeEstimationNotes: null,
    preferredModel: "claude-sonnet-4-6",
    monthlyBudgetCents: null,
    coldStartCompleted: true,
  })),
  getAiCostSummary: vi.fn(async () => ({
    currentMonthSpentCents: 0,
    monthlyBudgetCents: null,
    invocationCount: 0,
    remainingBudgetCents: null,
    periodStart: "2026-10-01",
    periodEnd: "2026-10-31",
  })),
  getAiExecutions: vi.fn(async () => EMPTY_PAGE),
  getAiGates: vi.fn(async () => EMPTY_PAGE),
  getAiGate: vi.fn(),
}));

// Child client components are stubbed — this test proves the four PAGES still render (no assistant
// imports, no dead mounts), not the clients' internals (those have their own tests).
vi.mock("@/components/ai/ai-profile-form", () => ({
  AiProfileForm: () => <div data-testid="ai-profile-form" />,
}));
vi.mock("@/components/ai/ai-cost-summary", () => ({
  AiCostSummary: () => <div data-testid="ai-cost-summary" />,
}));
vi.mock("@/app/(app)/org/[slug]/settings/ai/history/history-client", () => ({
  ExecutionHistoryClient: () => <div data-testid="execution-history-client" />,
}));
vi.mock("@/app/(app)/org/[slug]/ai/reviews/reviews-client", () => ({
  AiReviewsClient: () => <div data-testid="ai-reviews-client" />,
}));
vi.mock("@/app/(app)/org/[slug]/settings/integrations/mcp/actions", () => ({
  getMcpStatusAction: vi.fn(async () => null),
  enableMcpAction: vi.fn(),
  revokeMcpAction: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/integrations/mcp/mcp-connector-card", () => ({
  McpConnectorCard: () => <div data-testid="mcp-connector-card" />,
}));

import AiSettingsPage from "@/app/(app)/org/[slug]/settings/ai/page";
import ExecutionHistoryPage from "@/app/(app)/org/[slug]/settings/ai/history/page";
import AiReviewsPage from "@/app/(app)/org/[slug]/ai/reviews/page";
import McpSettingsPage from "@/app/(app)/org/[slug]/settings/integrations/mcp/page";

const params = Promise.resolve({ slug: "acme" });

afterEach(() => {
  cleanup();
});

describe("AI surfaces kept after the assistant removal (Epic 601)", () => {
  it("renders Settings → AI", async () => {
    render(await AiSettingsPage({ params }));
    expect(screen.getByRole("heading", { level: 1, name: "AI Configuration" })).toBeInTheDocument();
    expect(screen.getByTestId("ai-profile-form")).toBeInTheDocument();
    expect(screen.getByTestId("ai-cost-summary")).toBeInTheDocument();
  });

  it("renders Settings → AI → Execution History", async () => {
    render(await ExecutionHistoryPage({ params, searchParams: Promise.resolve({}) }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Execution History" })
    ).toBeInTheDocument();
    expect(screen.getByTestId("execution-history-client")).toBeInTheDocument();
  });

  it("renders AI Reviews", async () => {
    render(await AiReviewsPage({ params }));
    expect(screen.getByRole("heading", { level: 1, name: "AI Reviews" })).toBeInTheDocument();
    expect(screen.getByTestId("ai-reviews-client")).toBeInTheDocument();
  });

  it("renders Settings → Integrations → Claude / MCP Connector", async () => {
    render(await McpSettingsPage({ params }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Claude / MCP Connector" })
    ).toBeInTheDocument();
    expect(screen.getByTestId("mcp-connector-card")).toBeInTheDocument();
  });
});
