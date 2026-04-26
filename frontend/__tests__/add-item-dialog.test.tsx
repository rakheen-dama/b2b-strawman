import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

const mockAddItem = vi.fn();
const mockToastSuccess = vi.fn();
const mockToastError = vi.fn();

vi.mock("@/app/(app)/org/[slug]/information-requests/[id]/actions", () => ({
  addItemAction: (...args: unknown[]) => mockAddItem(...args),
}));

vi.mock("sonner", () => ({
  toast: {
    success: (...args: unknown[]) => mockToastSuccess(...args),
    error: (...args: unknown[]) => mockToastError(...args),
  },
}));

import { AddItemDialog } from "@/components/information-requests/add-item-dialog";
import type { InformationRequestResponse } from "@/lib/api/information-requests";

function makeUpdatedRequest(): InformationRequestResponse {
  return {
    id: "req-1",
    requestNumber: "REQ-0001",
    customerId: "cust-1",
    customerName: "Acme Corp",
    projectId: null,
    projectName: null,
    portalContactId: "contact-1",
    portalContactName: "John Smith",
    portalContactEmail: "john@acme.com",
    status: "DRAFT",
    reminderIntervalDays: 5,
    dueDate: null,
    sentAt: null,
    completedAt: null,
    totalItems: 1,
    submittedItems: 0,
    acceptedItems: 0,
    rejectedItems: 0,
    items: [
      {
        id: "item-new",
        name: "Proof of address",
        description: null,
        responseType: "FILE_UPLOAD",
        required: true,
        fileTypeHints: null,
        sortOrder: 0,
        status: "PENDING",
        documentId: null,
        documentFileName: null,
        textResponse: null,
        rejectionReason: null,
        submittedAt: null,
        reviewedAt: null,
      },
    ],
    createdAt: "2026-04-25T10:00:00Z",
  };
}

describe("AddItemDialog (GAP-L-67)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders form fields when opened", async () => {
    const user = userEvent.setup();
    render(<AddItemDialog slug="test-org" requestId="req-1" />);

    await user.click(screen.getByRole("button", { name: /add item/i }));

    expect(screen.getByTestId("add-item-name-input")).toBeInTheDocument();
    expect(screen.getByTestId("add-item-description-input")).toBeInTheDocument();
    expect(screen.getByTestId("add-item-response-type")).toBeInTheDocument();
    expect(screen.getByTestId("add-item-required-input")).toBeInTheDocument();
  });

  it("does not call action when name is empty", async () => {
    const user = userEvent.setup();
    render(<AddItemDialog slug="test-org" requestId="req-1" />);

    await user.click(screen.getByRole("button", { name: /add item/i }));

    // The submit button is disabled when name is empty
    const dialog = screen.getByRole("dialog");
    const buttons = dialog.querySelectorAll("button");
    const submitButton = Array.from(buttons).find(
      (b) => b.textContent === "Add Item" || b.textContent?.includes("Adding")
    );
    expect(submitButton).toBeDisabled();
    expect(mockAddItem).not.toHaveBeenCalled();
  });

  it("submits with valid input and calls addItemAction with correct payload", async () => {
    const user = userEvent.setup();
    mockAddItem.mockResolvedValue({ success: true, data: makeUpdatedRequest() });
    render(<AddItemDialog slug="test-org" requestId="req-1" />);

    await user.click(screen.getByRole("button", { name: /add item/i }));

    await user.type(screen.getByTestId("add-item-name-input"), "Proof of address");
    await user.type(screen.getByTestId("add-item-description-input"), "Recent utility bill");

    // Submit (the dialog's submit button is the second "Add Item" button after the trigger)
    const dialog = screen.getByRole("dialog");
    const submitButton = dialog.querySelector(
      "button:not([type='button'][aria-label]):last-of-type"
    );
    // Fall back to more direct lookup — find the submit button by text inside the dialog footer
    const dialogButtons = Array.from(dialog.querySelectorAll("button"));
    const submit = dialogButtons.find((b) => b.textContent?.trim() === "Add Item");
    expect(submit).toBeDefined();
    await user.click(submit!);

    await waitFor(() => {
      expect(mockAddItem).toHaveBeenCalledTimes(1);
    });
    expect(mockAddItem).toHaveBeenCalledWith("test-org", "req-1", {
      name: "Proof of address",
      description: "Recent utility bill",
      responseType: "FILE_UPLOAD",
      required: true,
    });
    // unused locator silences ts-unused warning
    void submitButton;
  });

  it("calls onSuccess with returned data on success", async () => {
    const user = userEvent.setup();
    const updated = makeUpdatedRequest();
    mockAddItem.mockResolvedValue({ success: true, data: updated });
    const onSuccess = vi.fn();
    render(<AddItemDialog slug="test-org" requestId="req-1" onSuccess={onSuccess} />);

    await user.click(screen.getByRole("button", { name: /add item/i }));
    await user.type(screen.getByTestId("add-item-name-input"), "Proof of address");

    const dialog = screen.getByRole("dialog");
    const dialogButtons = Array.from(dialog.querySelectorAll("button"));
    const submit = dialogButtons.find((b) => b.textContent?.trim() === "Add Item");
    await user.click(submit!);

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledWith(updated);
    });
    expect(mockToastSuccess).toHaveBeenCalled();
  });

  it("shows error and keeps dialog open when action fails", async () => {
    const user = userEvent.setup();
    mockAddItem.mockResolvedValue({ success: false, error: "Server unavailable" });
    render(<AddItemDialog slug="test-org" requestId="req-1" />);

    await user.click(screen.getByRole("button", { name: /add item/i }));
    await user.type(screen.getByTestId("add-item-name-input"), "Proof of address");

    const dialog = screen.getByRole("dialog");
    const dialogButtons = Array.from(dialog.querySelectorAll("button"));
    const submit = dialogButtons.find((b) => b.textContent?.trim() === "Add Item");
    await user.click(submit!);

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith("Server unavailable");
    });
    // Dialog still mounted
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("Server unavailable")).toBeInTheDocument();
  });

  it("toggles responseType between FILE_UPLOAD and TEXT_RESPONSE", async () => {
    const user = userEvent.setup();
    mockAddItem.mockResolvedValue({ success: true, data: makeUpdatedRequest() });
    render(<AddItemDialog slug="test-org" requestId="req-1" />);

    await user.click(screen.getByRole("button", { name: /add item/i }));
    await user.type(screen.getByTestId("add-item-name-input"), "Notes");

    // Open the select and pick TEXT_RESPONSE
    const trigger = screen.getByTestId("add-item-response-type");
    await user.click(trigger);
    const textOption = await screen.findByRole("option", { name: /text response/i });
    await user.click(textOption);

    const dialog = screen.getByRole("dialog");
    const dialogButtons = Array.from(dialog.querySelectorAll("button"));
    const submit = dialogButtons.find((b) => b.textContent?.trim() === "Add Item");
    await user.click(submit!);

    await waitFor(() => {
      expect(mockAddItem).toHaveBeenCalledWith(
        "test-org",
        "req-1",
        expect.objectContaining({ responseType: "TEXT_RESPONSE" })
      );
    });
  });
});
