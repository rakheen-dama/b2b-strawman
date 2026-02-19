import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RetentionPolicyTable } from "@/components/compliance/RetentionPolicyTable";
import type { RetentionPolicy } from "@/lib/types";

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/settings/compliance/actions", () => ({
  createRetentionPolicy: vi.fn().mockResolvedValue({ success: true }),
  updateRetentionPolicy: vi.fn().mockResolvedValue({ success: true }),
  deleteRetentionPolicy: vi.fn().mockResolvedValue({ success: true }),
  runRetentionCheck: vi.fn().mockResolvedValue({
    success: true,
    result: { checkedAt: "2026-02-19T10:00:00Z", flagged: {}, totalFlagged: 0 },
  }),
  executePurge: vi.fn().mockResolvedValue({ success: true }),
}));

const mockPolicies: RetentionPolicy[] = [
  {
    id: "pol-1",
    recordType: "CUSTOMER",
    retentionDays: 365,
    triggerEvent: "CUSTOMER_OFFBOARDED",
    action: "FLAG",
    active: true,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "pol-2",
    recordType: "AUDIT_EVENT",
    retentionDays: 730,
    triggerEvent: "RECORD_CREATED",
    action: "ANONYMIZE",
    active: false,
    createdAt: "2026-01-02T00:00:00Z",
    updatedAt: "2026-01-02T00:00:00Z",
  },
];

describe("RetentionPolicyTable", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders existing policies in the table", () => {
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    // Check retention days are visible as input values
    const inputs = screen.getAllByRole("spinbutton");
    expect(inputs[0]).toHaveValue(365);
    expect(inputs[1]).toHaveValue(730);
  });

  it("shows empty state when no policies exist", () => {
    render(<RetentionPolicyTable policies={[]} slug="acme" />);
    expect(screen.getByText(/No retention policies configured/)).toBeInTheDocument();
  });

  it("adds a new row when Add Row is clicked", async () => {
    const user = userEvent.setup();
    render(<RetentionPolicyTable policies={[]} slug="acme" />);
    await user.click(screen.getByRole("button", { name: /Add Row/i }));
    // New row should have a retention days input
    expect(screen.getByRole("spinbutton")).toBeInTheDocument();
  });

  it("shows Run Retention Check button", () => {
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    expect(screen.getByRole("button", { name: /Run Retention Check/i })).toBeInTheDocument();
  });
});
