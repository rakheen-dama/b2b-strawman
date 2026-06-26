import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import type { AiGateListItem } from "@/lib/api/ai";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...p
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...p}>
      {children}
    </a>
  ),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const noop = vi.fn().mockResolvedValue({ success: true });

function makeGate(overrides: Partial<AiGateListItem> = {}): AiGateListItem {
  return {
    id: "gate-1",
    gateType: "CREATE_TASK_FROM_CORRESPONDENCE",
    status: "PENDING",
    aiReasoning: "A task should be created from the client's settlement email.",
    createdAt: "2026-06-15T13:29:00Z",
    expiresAt: new Date(Date.now() + 65 * 60 * 1000).toISOString(),
    executionId: "exec-abcdef123456",
    ...overrides,
  };
}

describe("ExecutionGateCard — originating correspondence (586B.3)", () => {
  it("renders a link to the originating matter's Correspondence tab for a CREATE_TASK_FROM_CORRESPONDENCE gate", () => {
    render(
      <ExecutionGateCard
        gate={makeGate()}
        slug="acme"
        correspondenceOrigin={{ projectId: "matter-9", correspondenceId: "corr-7" }}
        onApprove={noop}
        onReject={noop}
      />
    );

    const link = screen.getByRole("link", { name: /view originating correspondence/i });
    expect(link).toHaveAttribute("href", "/org/acme/projects/matter-9?tab=correspondence");
  });

  it("does not render the originating-correspondence link for unrelated gate types", () => {
    render(
      <ExecutionGateCard
        gate={makeGate({ gateType: "DOCUMENT_SEND" })}
        slug="acme"
        correspondenceOrigin={{ projectId: "matter-9" }}
        onApprove={noop}
        onReject={noop}
      />
    );

    expect(
      screen.queryByRole("link", { name: /view originating correspondence/i })
    ).not.toBeInTheDocument();
  });

  it("omits the link when no originating correspondence reference is supplied", () => {
    render(<ExecutionGateCard gate={makeGate()} slug="acme" onApprove={noop} onReject={noop} />);

    expect(
      screen.queryByRole("link", { name: /view originating correspondence/i })
    ).not.toBeInTheDocument();
  });
});
