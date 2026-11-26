import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RetentionPolicyTable } from "@/components/compliance/RetentionPolicyTable";
import {
  createRetentionPolicy,
  deleteRetentionPolicy,
  updateRetentionPolicy,
} from "@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions";
import type { RetentionPolicy } from "@/lib/types";

// DataTable calls useRouter(); vitest has no app router mounted.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
}));

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions", () => ({
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
    vi.clearAllMocks();
    vi.unstubAllGlobals();
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

  it("renders the empty state as the shared inline EmptyState, with no table (665A)", () => {
    render(<RetentionPolicyTable policies={[]} slug="acme" />);
    const empty = screen
      .getByText("No retention policies configured.")
      .closest('[data-slot="empty-state"]');
    expect(empty).not.toBeNull();
    expect(empty).toHaveAttribute("data-variant", "inline");
    expect(within(empty as HTMLElement).getByText("Click “Add Row” to create one.")).toBeVisible();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("renders inside a Retention Policies section with Add Row in its header (665A)", () => {
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    const section = screen
      .getByRole("heading", { level: 2, name: "Retention Policies" })
      .closest('[data-slot="settings-section"]') as HTMLElement;
    expect(section).not.toBeNull();
    expect(within(section).getByRole("table")).toBeInTheDocument();
    expect(
      within(section)
        .getAllByRole("columnheader")
        .map((h) => h.textContent)
    ).toEqual(["Record Type", "Trigger Event", "Retention Days", "Action", "Active", "Actions"]);
    expect(within(section).getAllByRole("row")).toHaveLength(3); // header + 2 policies
    const addRow = within(section).getByRole("button", { name: /Add Row/i });
    expect(addRow.closest('[data-slot="card-action"]')).not.toBeNull();
    // Per-row save: no section footer.
    expect(section.querySelector('[data-slot="card-footer"]')).toBeNull();
    expect(within(section).getByRole("button", { name: /Run Retention Check/i })).toBeVisible();
  });

  it("saves an existing row with its current values", async () => {
    const user = userEvent.setup();
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    await user.click(screen.getAllByRole("button", { name: "Save" })[0]);
    await waitFor(() =>
      expect(updateRetentionPolicy).toHaveBeenCalledWith("acme", "pol-1", {
        retentionDays: 365,
        action: "FLAG",
        active: true,
      })
    );
    expect(createRetentionPolicy).not.toHaveBeenCalled();
  });

  it("saves an edited value from the same row (no stale closure)", async () => {
    const user = userEvent.setup();
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    fireEvent.change(screen.getAllByRole("spinbutton")[1], { target: { value: "800" } });
    await user.click(screen.getAllByRole("button", { name: "Save" })[1]);
    await waitFor(() =>
      expect(updateRetentionPolicy).toHaveBeenCalledWith("acme", "pol-2", {
        retentionDays: 800,
        action: "ANONYMIZE",
        active: false,
      })
    );
  });

  it("deletes a row after window.confirm and removes it", async () => {
    const user = userEvent.setup();
    const confirm = vi.fn(() => true);
    vi.stubGlobal("confirm", confirm);
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    await user.click(screen.getAllByRole("button", { name: "Delete" })[0]);
    expect(confirm).toHaveBeenCalledWith("Delete this retention policy?");
    await waitFor(() => expect(deleteRetentionPolicy).toHaveBeenCalledWith("acme", "pol-1"));
    await waitFor(() => expect(screen.getAllByRole("spinbutton")).toHaveLength(1));
    expect(screen.getByRole("spinbutton")).toHaveValue(730);
  });

  it("does nothing when the delete confirm is cancelled", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "confirm",
      vi.fn(() => false)
    );
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    await user.click(screen.getAllByRole("button", { name: "Delete" })[0]);
    expect(deleteRetentionPolicy).not.toHaveBeenCalled();
    expect(screen.getAllByRole("spinbutton")).toHaveLength(2);
  });

  it("creates a new row with the defaults on Save", async () => {
    const user = userEvent.setup();
    render(<RetentionPolicyTable policies={[]} slug="acme" />);
    await user.click(screen.getByRole("button", { name: /Add Row/i }));
    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() =>
      expect(createRetentionPolicy).toHaveBeenCalledWith("acme", {
        recordType: "CUSTOMER",
        retentionDays: 365,
        triggerEvent: "CUSTOMER_OFFBOARDED",
        action: "FLAG",
      })
    );
    expect(updateRetentionPolicy).not.toHaveBeenCalled();
  });

  it("does not remount a row's input while it is edited (stable row ids and columns)", () => {
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    const before = screen.getAllByRole("spinbutton")[0];
    fireEvent.change(before, { target: { value: "400" } });
    const after = screen.getAllByRole("spinbutton")[0];
    expect(after).toBe(before);
    expect(after).toHaveValue(400);
  });

  it("adds a new row when Add Row is clicked", async () => {
    const user = userEvent.setup();
    render(<RetentionPolicyTable policies={[]} slug="acme" />);
    await user.click(screen.getByRole("button", { name: /Add Row/i }));
    // New row should have a retention days input
    expect(screen.getByRole("spinbutton")).toBeInTheDocument();
  });

  it("labels record-type and trigger-event options with legal words; values stay wire enums (628A)", () => {
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    const [recordTypeSelect, triggerSelect] = screen.getAllByRole("combobox");
    const options = (select: HTMLElement) =>
      Array.from((select as HTMLSelectElement).options).map((o) => [o.value, o.textContent]);
    expect(options(recordTypeSelect)).toEqual([
      ["CUSTOMER", "Client"],
      ["AUDIT_EVENT", "Audit Event"],
      ["DOCUMENT", "Document"],
      ["COMMENT", "Comment"],
    ]);
    expect(options(triggerSelect)).toEqual([
      ["CUSTOMER_OFFBOARDED", "Client Offboarded"],
      ["RECORD_CREATED", "Record Created"],
    ]);
    expect((recordTypeSelect as HTMLSelectElement).value).toBe("CUSTOMER");
    expect((triggerSelect as HTMLSelectElement).value).toBe("CUSTOMER_OFFBOARDED");
    expect(screen.queryByRole("option", { name: /customer/i })).not.toBeInTheDocument();
  });

  it("shows Run Retention Check button", () => {
    render(<RetentionPolicyTable policies={mockPolicies} slug="acme" />);
    expect(screen.getByRole("button", { name: /Run Retention Check/i })).toBeInTheDocument();
  });
});
