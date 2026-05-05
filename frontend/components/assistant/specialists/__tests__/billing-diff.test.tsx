import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock the API module BEFORE importing the component-under-test
vi.mock("@/lib/api/assistant-specialists", () => ({
  approveInvocation: vi.fn(),
  rejectInvocation: vi.fn(),
}));

import { BillingDiff } from "@/components/assistant/specialists/billing-diff";
import {
  approveInvocation,
  rejectInvocation,
} from "@/lib/api/assistant-specialists";

const approveInvocationMock = approveInvocation as unknown as ReturnType<typeof vi.fn>;
const rejectInvocationMock = rejectInvocation as unknown as ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

const POLISH_EDITS = [
  {
    timeEntryId: "te-1",
    beforeText: "call w/ J re property",
    afterText: "Telephone consultation with client regarding property transfer",
  },
  {
    timeEntryId: "te-2",
    beforeText: "draft letter",
    afterText: "Drafted formal correspondence to opposing counsel",
  },
];

describe("<BillingDiff> polish mode", () => {
  it("renders per-row before/after diff rows", () => {
    render(
      <BillingDiff
        invocationId="inv-1"
        kind="BillingPolishPayload"
        invoiceId="invoice-1"
        edits={POLISH_EDITS}
      />
    );

    const rows = screen.getAllByTestId("diff-row");
    expect(rows).toHaveLength(2);

    // Check before/after text is visible
    expect(screen.getByText("call w/ J re property")).toBeInTheDocument();
    expect(
      screen.getByText("Telephone consultation with client regarding property transfer")
    ).toBeInTheDocument();
    expect(screen.getByText("draft letter")).toBeInTheDocument();
    expect(
      screen.getByText("Drafted formal correspondence to opposing counsel")
    ).toBeInTheDocument();
  });

  it("calls approveInvocation with accepted edits on approve all", async () => {
    const user = userEvent.setup();
    const onApproved = vi.fn();
    approveInvocationMock.mockResolvedValueOnce({
      id: "inv-1",
      status: "APPROVED",
      appliedAt: "2026-05-05T10:00:00Z",
    });

    render(
      <BillingDiff
        invocationId="inv-1"
        kind="BillingPolishPayload"
        invoiceId="invoice-1"
        edits={POLISH_EDITS}
        onApproved={onApproved}
      />
    );

    const approveBtn = screen.getByTestId("approve-all-btn");
    await user.click(approveBtn);

    await waitFor(() => expect(approveInvocationMock).toHaveBeenCalledTimes(1));
    expect(approveInvocationMock).toHaveBeenCalledWith("inv-1", {
      kind: "BillingPolishPayload",
      invoiceId: "invoice-1",
      edits: [
        {
          timeEntryId: "te-1",
          afterText: "Telephone consultation with client regarding property transfer",
        },
        {
          timeEntryId: "te-2",
          afterText: "Drafted formal correspondence to opposing counsel",
        },
      ],
    });
    await waitFor(() => expect(onApproved).toHaveBeenCalledTimes(1));
  });

  it("excludes rejected rows from appliedOutput", async () => {
    const user = userEvent.setup();
    approveInvocationMock.mockResolvedValueOnce({
      id: "inv-1",
      status: "APPROVED",
      appliedAt: "2026-05-05T10:00:00Z",
    });

    render(
      <BillingDiff
        invocationId="inv-1"
        kind="BillingPolishPayload"
        invoiceId="invoice-1"
        edits={POLISH_EDITS}
      />
    );

    // Reject the first row
    const rows = screen.getAllByTestId("diff-row");
    const rejectBtn = within(rows[0]).getByLabelText("Reject");
    await user.click(rejectBtn);

    // Approve all — rejected row should be excluded
    await user.click(screen.getByTestId("approve-all-btn"));

    await waitFor(() => expect(approveInvocationMock).toHaveBeenCalledTimes(1));
    const calledPayload = approveInvocationMock.mock.calls[0][1];
    expect(calledPayload.edits).toHaveLength(1);
    expect(calledPayload.edits[0].timeEntryId).toBe("te-2");
  });

  it("uses edited text when a row is in edit mode", async () => {
    const user = userEvent.setup();
    approveInvocationMock.mockResolvedValueOnce({
      id: "inv-1",
      status: "APPROVED",
      appliedAt: "2026-05-05T10:00:00Z",
    });

    render(
      <BillingDiff
        invocationId="inv-1"
        kind="BillingPolishPayload"
        invoiceId="invoice-1"
        edits={POLISH_EDITS}
      />
    );

    // Click edit on first row
    const rows = screen.getAllByTestId("diff-row");
    const editBtn = within(rows[0]).getByLabelText("Edit");
    await user.click(editBtn);

    // Should show textarea
    const textarea = within(rows[0]).getByTestId("edit-textarea");
    await user.clear(textarea);
    await user.type(textarea, "Custom edited text");

    // Approve
    await user.click(screen.getByTestId("approve-all-btn"));

    await waitFor(() => expect(approveInvocationMock).toHaveBeenCalledTimes(1));
    const calledPayload = approveInvocationMock.mock.calls[0][1];
    expect(calledPayload.edits[0].afterText).toBe("Custom edited text");
  });

  it("calls rejectInvocation with reason on reject", async () => {
    const user = userEvent.setup();
    const onRejected = vi.fn();
    rejectInvocationMock.mockResolvedValueOnce({
      id: "inv-1",
      status: "REJECTED",
    });

    render(
      <BillingDiff
        invocationId="inv-1"
        kind="BillingPolishPayload"
        invoiceId="invoice-1"
        edits={POLISH_EDITS}
        onRejected={onRejected}
      />
    );

    // Open reject form
    await user.click(screen.getByTestId("reject-all-btn"));
    expect(screen.getByTestId("reject-form")).toBeInTheDocument();

    // Type reason and confirm
    const reasonInput = screen.getByTestId("reject-reason");
    await user.type(reasonInput, "Misread two entries");
    await user.click(screen.getByRole("button", { name: /confirm reject/i }));

    await waitFor(() => expect(rejectInvocationMock).toHaveBeenCalledTimes(1));
    expect(rejectInvocationMock).toHaveBeenCalledWith("inv-1", "Misread two entries");
    await waitFor(() => expect(onRejected).toHaveBeenCalledTimes(1));
  });
});

describe("<BillingDiff> grouping mode", () => {
  const GROUPS = [
    {
      description: "Research and document preparation",
      hours: 4.5,
      sourceTimeEntryIds: ["te-1", "te-2", "te-3"],
    },
    {
      description: "Client communication",
      hours: 1.5,
      sourceTimeEntryIds: ["te-4"],
    },
  ];

  it("renders group rows with description, hours, and entry count", () => {
    render(
      <BillingDiff
        invocationId="inv-2"
        kind="BillingGroupingPayload"
        invoiceId="invoice-2"
        groups={GROUPS}
      />
    );

    const groupRows = screen.getAllByTestId("group-row");
    expect(groupRows).toHaveLength(2);
    expect(screen.getByText("Research and document preparation")).toBeInTheDocument();
    expect(screen.getByText(/4\.5h/)).toBeInTheDocument();
    expect(screen.getByText(/3 entries/)).toBeInTheDocument();
    expect(screen.getByText("Client communication")).toBeInTheDocument();
    expect(screen.getByText(/1 entries/)).toBeInTheDocument();
  });

  it("approves grouping payload as-is", async () => {
    const user = userEvent.setup();
    const onApproved = vi.fn();
    approveInvocationMock.mockResolvedValueOnce({
      id: "inv-2",
      status: "APPROVED",
      appliedAt: "2026-05-05T10:00:00Z",
    });

    render(
      <BillingDiff
        invocationId="inv-2"
        kind="BillingGroupingPayload"
        invoiceId="invoice-2"
        groups={GROUPS}
        onApproved={onApproved}
      />
    );

    await user.click(screen.getByTestId("approve-all-btn"));

    await waitFor(() => expect(approveInvocationMock).toHaveBeenCalledTimes(1));
    expect(approveInvocationMock).toHaveBeenCalledWith("inv-2", {
      kind: "BillingGroupingPayload",
      invoiceId: "invoice-2",
      groups: GROUPS,
    });
    await waitFor(() => expect(onApproved).toHaveBeenCalledTimes(1));
  });
});
