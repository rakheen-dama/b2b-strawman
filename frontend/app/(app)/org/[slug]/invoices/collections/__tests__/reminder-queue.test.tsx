import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { ReminderQueue, type ReminderPreview } from "../reminder-queue";
import type { AiGateListItem } from "@/lib/api/ai";

const mockBatchApproveGatesAction = vi.fn();
const mockRejectReminderGateAction = vi.fn();

vi.mock("@/app/(app)/org/[slug]/invoices/collections/actions", () => ({
  batchApproveGatesAction: (...args: unknown[]) => mockBatchApproveGatesAction(...args),
  rejectReminderGateAction: (...args: unknown[]) => mockRejectReminderGateAction(...args),
}));

// Stub DOMPurify so the wiring is provable in happy-dom: the component must call
// sanitize with the raw bodyHtml, and what renders must be the sanitizer's OUTPUT
// (the real strip-unsafe-markup behavior is DOMPurify's own, exercised in the browser).
const mockSanitize = vi.fn((html: string) => html.replace("<script>alert(1)</script>", ""));

vi.mock("dompurify", () => ({
  default: { sanitize: (html: string) => mockSanitize(html) },
}));

afterEach(() => {
  cleanup();
});

beforeEach(() => {
  mockBatchApproveGatesAction.mockReset();
  mockRejectReminderGateAction.mockReset();
  mockSanitize.mockClear();
});

function makeGate(overrides: Partial<AiGateListItem> = {}): AiGateListItem {
  return {
    id: "gate-1",
    gateType: "SEND_COLLECTION_REMINDER",
    status: "PENDING",
    aiReasoning: "Invoice INV-001 is 30 days overdue.",
    createdAt: "2026-07-01T09:00:00Z",
    expiresAt: new Date(Date.now() + 65 * 60 * 1000).toISOString(),
    executionId: "exec-abcdef123456",
    ...overrides,
  };
}

function makePreview(overrides: Partial<ReminderPreview> = {}): ReminderPreview {
  return {
    subject: "Reminder: invoice overdue",
    bodyHtml: "<p>Please settle your outstanding invoice.</p>",
    bodyText: "Please settle your outstanding invoice.",
    stage: "STAGE_2",
    invoiceId: "inv-1",
    customerId: "cust-1",
    ...overrides,
  };
}

function renderQueue() {
  const gates = [
    makeGate({ id: "gate-a" }),
    makeGate({ id: "gate-b" }),
    makeGate({ id: "gate-c" }),
  ];
  const previews: Record<string, ReminderPreview> = {
    "gate-a": makePreview({ subject: "Reminder A" }),
    "gate-b": makePreview({ subject: "Reminder B" }),
    "gate-c": makePreview({ subject: "Reminder C" }),
  };
  return render(<ReminderQueue slug="acme" gates={gates} previews={previews} />);
}

describe("ReminderQueue — multi-select + batch approve", () => {
  it("enables the approve button and calls the batch action with exactly the selected ids", async () => {
    mockBatchApproveGatesAction.mockResolvedValue({ success: true, results: [] });
    renderQueue();

    // Approve button is disabled with nothing selected.
    const approveButton = screen.getByRole("button", { name: /Approve selected \(0\)/ });
    expect(approveButton).toBeDisabled();

    const checkboxes = screen.getAllByRole("checkbox");
    expect(checkboxes).toHaveLength(3);

    // Select the first two of three reminders.
    fireEvent.click(checkboxes[0]);
    fireEvent.click(checkboxes[1]);

    const enabledButton = screen.getByRole("button", { name: /Approve selected \(2\)/ });
    expect(enabledButton).not.toBeDisabled();

    fireEvent.click(enabledButton);

    await waitFor(() => {
      expect(mockBatchApproveGatesAction).toHaveBeenCalledTimes(1);
    });
    const [slugArg, idsArg] = mockBatchApproveGatesAction.mock.calls[0];
    expect(slugArg).toBe("acme");
    expect(idsArg).toHaveLength(2);
    expect(idsArg).toEqual(expect.arrayContaining(["gate-a", "gate-b"]));
    expect(idsArg).not.toContain("gate-c");
  });

  it("renders per-gate dispositions without dropping the rest of the queue", async () => {
    mockBatchApproveGatesAction.mockResolvedValue({
      success: true,
      results: [
        { gateId: "gate-a", outcome: "APPROVED_EXECUTED", error: null },
        { gateId: "gate-b", outcome: "FAILED", error: "gate not PENDING (EXPIRED)" },
      ],
    });
    renderQueue();

    const checkboxes = screen.getAllByRole("checkbox");
    fireEvent.click(checkboxes[0]);
    fireEvent.click(checkboxes[1]);
    fireEvent.click(screen.getByRole("button", { name: /Approve selected \(2\)/ }));

    await waitFor(() => {
      // Both outcomes surfaced in the batch-result summary.
      expect(screen.getAllByText("Approved").length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText("Failed").length).toBeGreaterThanOrEqual(1);
    });

    // The failure reason is shown to the approver.
    expect(screen.getByText(/gate not PENDING \(EXPIRED\)/)).toBeInTheDocument();

    // The unprocessed third reminder is still in the queue.
    expect(screen.getByText("Reminder C")).toBeInTheDocument();
  });

  it("shows an empty state when there are no pending reminders", () => {
    render(<ReminderQueue slug="acme" gates={[]} previews={{}} />);
    expect(screen.getByText(/No reminders awaiting approval/)).toBeInTheDocument();
  });

  it("runs the bodyHtml through client-side sanitization and renders the sanitizer's output", () => {
    const rawHtml = "<p>Please settle your outstanding invoice.</p><script>alert(1)</script>";
    const gates = [makeGate({ id: "gate-a" })];
    const previews: Record<string, ReminderPreview> = {
      "gate-a": makePreview({
        subject: "Reminder A",
        bodyHtml: rawHtml,
      }),
    };
    const { container } = render(<ReminderQueue slug="acme" gates={gates} previews={previews} />);

    // sanitize must not run for collapsed cards (also proves the SSR-unreachable claim).
    expect(mockSanitize).not.toHaveBeenCalled();

    // Expand the reminder card so the bodyHtml preview mounts and DOMPurify.sanitize runs.
    fireEvent.click(screen.getByRole("button", { name: /Reminder A/ }));

    // The component passed the RAW html to the sanitizer...
    expect(mockSanitize).toHaveBeenCalledWith(rawHtml);
    // ...and what renders is the sanitizer's OUTPUT: script gone, safe markup kept.
    expect(screen.getByText("Please settle your outstanding invoice.")).toBeInTheDocument();
    expect(container.querySelector("script")).toBeNull();
  });
});
