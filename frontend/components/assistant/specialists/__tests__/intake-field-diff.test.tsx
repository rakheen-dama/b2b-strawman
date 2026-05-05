import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock the API module BEFORE importing the component-under-test
vi.mock("@/lib/api/assistant-specialists", () => ({
  approveInvocation: vi.fn(),
  rejectInvocation: vi.fn(),
}));

import { IntakeFieldDiff } from "@/components/assistant/specialists/intake-field-diff";
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

const BASE_PROPS = {
  invocationId: "inv-intake-1",
  contextEntityType: "customer",
  contextEntityId: "cust-123",
  extractionPath: "TEXT" as const,
  popiaFlaggedFields: ["id_passport_number"],
  validationFlags: [] as string[],
};

const PROPOSED_FIELDS: Record<string, unknown> = {
  full_name: "John Doe",
  id_passport_number: "9001015009087",
  email_address: "john@example.com",
};

const CURRENT_FIELDS: Record<string, unknown> = {
  full_name: "J. Doe",
  id_passport_number: "",
  email_address: "",
};

describe("<IntakeFieldDiff> per-field diff accept/reject round-trip", () => {
  it("accepts some rows, rejects others, and sends only accepted fields on approve", async () => {
    const user = userEvent.setup();
    const onApproved = vi.fn();
    approveInvocationMock.mockResolvedValueOnce({
      id: "inv-intake-1",
      status: "APPROVED",
      appliedAt: "2026-05-05T10:00:00Z",
    });

    render(
      <IntakeFieldDiff
        {...BASE_PROPS}
        proposedFields={PROPOSED_FIELDS}
        currentFields={CURRENT_FIELDS}
        onApproved={onApproved}
      />
    );

    const rows = screen.getAllByTestId("diff-row");
    expect(rows).toHaveLength(3);

    // Reject the id_passport_number row
    const idRow = rows.find(
      (r) => r.getAttribute("data-field-slug") === "id_passport_number"
    )!;
    const rejectBtn = within(idRow).getByLabelText("Reject");
    await user.click(rejectBtn);

    // Approve all
    await user.click(screen.getByTestId("approve-all-btn"));

    await waitFor(() => expect(approveInvocationMock).toHaveBeenCalledTimes(1));
    const calledPayload = approveInvocationMock.mock.calls[0][1];

    // Only accepted fields should be in proposedFields
    expect(calledPayload.proposedFields).toHaveProperty("full_name");
    expect(calledPayload.proposedFields).toHaveProperty("email_address");
    expect(calledPayload.proposedFields).not.toHaveProperty("id_passport_number");
    expect(calledPayload.kind).toBe("IntakeExtractionPayload");

    await waitFor(() => expect(onApproved).toHaveBeenCalledTimes(1));
  });
});

describe("<IntakeFieldDiff> empty state", () => {
  it("renders empty state message when proposedFields is empty", () => {
    render(
      <IntakeFieldDiff
        {...BASE_PROPS}
        proposedFields={{}}
        currentFields={{}}
      />
    );

    expect(screen.getByText("No documents to extract from")).toBeInTheDocument();
    expect(screen.queryByTestId("diff-row")).not.toBeInTheDocument();
  });
});

describe("<IntakeFieldDiff> VISION/TEXT badge rendering", () => {
  it("renders purple VISION badge when extractionPath is VISION", () => {
    render(
      <IntakeFieldDiff
        {...BASE_PROPS}
        extractionPath="VISION"
        proposedFields={PROPOSED_FIELDS}
        currentFields={CURRENT_FIELDS}
      />
    );

    const badge = screen.getByTestId("extraction-path-badge");
    expect(badge).toHaveTextContent("VISION");
    expect(badge.className).toContain("bg-purple-100");
  });

  it("renders grey TEXT badge when extractionPath is TEXT", () => {
    render(
      <IntakeFieldDiff
        {...BASE_PROPS}
        extractionPath="TEXT"
        proposedFields={PROPOSED_FIELDS}
        currentFields={CURRENT_FIELDS}
      />
    );

    const badge = screen.getByTestId("extraction-path-badge");
    expect(badge).toHaveTextContent("TEXT");
    expect(badge.className).toContain("bg-slate-100");
  });

  it("renders POPIA badge on flagged fields", () => {
    render(
      <IntakeFieldDiff
        {...BASE_PROPS}
        proposedFields={PROPOSED_FIELDS}
        currentFields={CURRENT_FIELDS}
      />
    );

    const popiaBadges = screen.getAllByTestId("popia-badge");
    expect(popiaBadges).toHaveLength(1);
    expect(popiaBadges[0]).toHaveTextContent("POPIA");
  });
});
