import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import { ClosureHistorySection } from "@/components/projects/closure-history-section";
import type { ClosureLogEntry } from "@/lib/api/matter-closure";

const mockFetchClosureLog = vi.fn();

vi.mock("@/lib/actions/matter-closure", () => ({
  fetchClosureLog: (...args: unknown[]) => mockFetchClosureLog(...args),
}));

vi.mock("@/components/audit/audit-timeline-tab", () => ({
  AuditTimelineTab: () => <div data-testid="audit-timeline-stub" />,
}));

const CLOSED_BY_UUID = "0768ccd3-8ebd-4d35-880f-ecb0bcf9f0d8";

function makeEntry(overrides: Partial<ClosureLogEntry> = {}): ClosureLogEntry {
  return {
    id: "log-1",
    projectId: "proj-1",
    closedBy: CLOSED_BY_UUID,
    closedByName: null,
    closedAt: "2026-06-01T10:00:00Z",
    reason: "CONCLUDED",
    notes: null,
    gateReport: {},
    overrideUsed: false,
    overrideJustification: null,
    closureLetterDocumentId: null,
    reopenedAt: null,
    reopenedBy: null,
    reopenedByName: null,
    reopenNotes: null,
    ...overrides,
  };
}

describe("ClosureHistorySection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("LZKC-014: renders the resolved member name, not the raw UUID, when closedByName is present", async () => {
    // Unique projectId per test — SWR caches by key across tests.
    mockFetchClosureLog.mockResolvedValue([
      makeEntry({ id: "log-a", projectId: "proj-a", closedByName: "Thandi Mathebula" }),
    ]);
    render(<ClosureHistorySection projectId="proj-a" />);

    expect(await screen.findByText("Closed by Thandi Mathebula")).toBeInTheDocument();
    expect(screen.queryByText(new RegExp(CLOSED_BY_UUID))).not.toBeInTheDocument();
  });

  it("falls back to the raw member id when closedByName is absent", async () => {
    mockFetchClosureLog.mockResolvedValue([
      makeEntry({ id: "log-b", projectId: "proj-b", closedByName: null }),
    ]);
    render(<ClosureHistorySection projectId="proj-b" />);

    expect(await screen.findByText(`Closed by ${CLOSED_BY_UUID}`)).toBeInTheDocument();
  });
});
