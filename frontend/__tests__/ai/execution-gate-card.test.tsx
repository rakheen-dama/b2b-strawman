import { execFileSync } from "node:child_process";
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
  // (Africa/Johannesburg), so the rendered string is invariant to the runtime
  // timezone.

  it("renders a fixed instant in Africa/Johannesburg regardless of locale defaults", () => {
    // 13:29 UTC == 15:29 in Africa/Johannesburg (UTC+2).
    const out = formatDateTime("2026-06-15T13:29:00Z");
    expect(out).toContain("15:29");
    expect(out).toContain("15 Jun 2026");
  });

  // Node caches the timezone at process startup, so mutating process.env.TZ
  // in-process is a no-op for Intl-backed methods. The only honest way to prove
  // TZ-invariance is to run the formatter in fresh subprocesses started under
  // different TZ env values and assert identical output. (Skipped in jsdom/CI
  // shells without a node binary on PATH — but present locally.)
  it("produces the same string across processes started under different TZ", () => {
    const iso = "2026-06-15T13:29:00Z";
    const opts = JSON.stringify({
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      timeZone: "Africa/Johannesburg",
    });
    // Inline the exact Intl call formatDateTime makes (the shared package is TS;
    // we replicate the runtime expression rather than transpile-import it here).
    const script = `process.stdout.write(new Date(${JSON.stringify(iso)}).toLocaleString("en-ZA", ${opts}))`;
    const run = (tz: string) =>
      execFileSync(process.execPath, ["-e", script], {
        env: { ...process.env, TZ: tz },
        encoding: "utf8",
      });

    const utc = run("UTC");
    const ny = run("America/New_York");
    const auckland = run("Pacific/Auckland");

    expect(utc).toContain("15:29"); // pinned JHB offset applied
    expect(ny).toBe(utc);
    expect(auckland).toBe(utc);
    expect(formatDateTime(iso)).toBe(utc);
  });

  it("returns empty string for invalid/empty input", () => {
    expect(formatDateTime("")).toBe("");
    expect(formatDateTime("not-a-date")).toBe("");
  });
});

describe("ExecutionGateCard", () => {
  it("renders the deterministic timestamp (15:29 JHB), not the raw locale string", async () => {
    render(<ExecutionGateCard gate={makeGate()} slug="acme" onApprove={noop} onReject={noop} />);
    // The fixed createdAt (13:29 UTC) renders as 15:29 in Africa/Johannesburg.
    expect(await screen.findByText(formatDateTime("2026-06-15T13:29:00Z"))).toBeInTheDocument();
    expect(screen.getByText(formatDateTime("2026-06-15T13:29:00Z")).textContent).toContain("15:29");
  });

  it("shows the live countdown after mount for a PENDING gate", async () => {
    render(<ExecutionGateCard gate={makeGate()} slug="acme" onApprove={noop} onReject={noop} />);
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
        slug="acme"
        onApprove={noop}
        onReject={noop}
      />
    );
    // Let any post-mount effects settle, then assert no countdown.
    await screen.findByText(formatDateTime("2026-06-15T13:29:00Z"));
    expect(screen.queryByText(/remaining/)).not.toBeInTheDocument();
  });
});
