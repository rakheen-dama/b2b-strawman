import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import { formatDateTime } from "@/lib/format";
import type { AiGateListItem } from "@/lib/api/ai";

afterEach(() => cleanup());

const noop = vi.fn().mockResolvedValue({ success: true });

function makeGate(overrides: Partial<AiGateListItem> = {}): AiGateListItem {
  return {
    id: "gate-1",
    gateType: "DOCUMENT_SEND",
    status: "PENDING",
    aiReasoning: "The drafted letter is ready to send to the client.",
    // Fixed createdAt — the hydration-sensitive value.
    createdAt: "2026-06-15T13:29:00Z",
    // Far in the future so the countdown is non-null.
    expiresAt: new Date(Date.now() + 65 * 60 * 1000).toISOString(),
    executionId: "exec-abcdef123456",
    ...overrides,
  };
}

describe("formatDateTime (AIVERIFY-009 timestamp determinism)", () => {
  // The bug: new Date(createdAt).toLocaleString() resolves the runtime's
  // default timezone, so the Node SSR runtime and the browser differ → a React
  // hydration mismatch. The fix pins both locale (en-ZA) and timeZone
  // (Africa/Johannesburg), so the string is invariant to process.env.TZ.
  function formatUnderTz(tz: string, iso: string): string {
    const original = process.env.TZ;
    try {
      process.env.TZ = tz;
      return formatDateTime(iso);
    } finally {
      process.env.TZ = original;
    }
  }

  it("renders the same string regardless of process timezone", () => {
    const iso = "2026-06-15T13:29:00Z";
    const utc = formatUnderTz("UTC", iso);
    const ny = formatUnderTz("America/New_York", iso);
    const jhb = formatUnderTz("Africa/Johannesburg", iso);
    expect(utc).toBe(ny);
    expect(utc).toBe(jhb);
    // 13:29 UTC == 15:29 in Africa/Johannesburg (UTC+2).
    expect(utc).toContain("15:29");
  });

  it("returns empty string for invalid/empty input", () => {
    expect(formatDateTime("")).toBe("");
    expect(formatDateTime("not-a-date")).toBe("");
  });
});

describe("ExecutionGateCard", () => {
  it("renders the deterministic timestamp (15:29 JHB), not the raw locale string", async () => {
    render(<ExecutionGateCard gate={makeGate()} onApprove={noop} onReject={noop} />);
    // The fixed createdAt (13:29 UTC) renders as 15:29 in Africa/Johannesburg.
    expect(await screen.findByText(formatDateTime("2026-06-15T13:29:00Z"))).toBeInTheDocument();
    expect(screen.getByText(formatDateTime("2026-06-15T13:29:00Z")).textContent).toContain("15:29");
  });

  it("shows the live countdown after mount for a PENDING gate", async () => {
    render(<ExecutionGateCard gate={makeGate()} onApprove={noop} onReject={noop} />);
    // Countdown is computed post-mount (useEffect), so it appears asynchronously.
    await waitFor(() => {
      expect(screen.getByText(/remaining/)).toBeInTheDocument();
    });
    expect(screen.getByText(/1h \d+m remaining/)).toBeInTheDocument();
  });

  it("does not render a countdown for non-PENDING gates", async () => {
    render(
      <ExecutionGateCard
        gate={makeGate({ status: "APPROVED" })}
        onApprove={noop}
        onReject={noop}
      />
    );
    // Let any post-mount effects settle, then assert no countdown.
    await screen.findByText(formatDateTime("2026-06-15T13:29:00Z"));
    expect(screen.queryByText(/remaining/)).not.toBeInTheDocument();
  });
});
