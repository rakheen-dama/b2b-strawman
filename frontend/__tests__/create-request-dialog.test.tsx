import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

const mockCreateRequest = vi.fn();
const mockSendRequest = vi.fn();
const mockFetchTemplates = vi.fn();
const mockFetchContacts = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/[id]/request-actions", () => ({
  createRequestAction: (...args: unknown[]) => mockCreateRequest(...args),
  sendRequestAction: (...args: unknown[]) => mockSendRequest(...args),
  fetchActiveTemplatesAction: (...args: unknown[]) => mockFetchTemplates(...args),
  fetchPortalContactsAction: (...args: unknown[]) => mockFetchContacts(...args),
}));

import { CreateRequestDialog } from "@/components/information-requests/create-request-dialog";

const TEMPLATE_FIXTURE = {
  id: "tmpl-1",
  name: "FICA Onboarding Pack",
  description: null,
  active: true,
  items: [
    {
      id: "ti-1",
      name: "ID document",
      description: null,
      responseType: "FILE_UPLOAD" as const,
      required: true,
      fileTypeHints: null,
      sortOrder: 0,
    },
  ],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const CONTACT_FIXTURE = {
  id: "contact-1",
  displayName: "Sipho Dlamini",
  email: "sipho@example.com",
  active: true,
};

function setupHappyMocks() {
  mockFetchTemplates.mockResolvedValue({ success: true, data: [TEMPLATE_FIXTURE] });
  mockFetchContacts.mockResolvedValue({ success: true, data: [CONTACT_FIXTURE] });
  mockCreateRequest.mockResolvedValue({
    success: true,
    data: { id: "req-new", requestNumber: "REQ-0042" },
  });
  mockSendRequest.mockResolvedValue({ success: true, data: { id: "req-new" } });
}

async function openDialog() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: /new request/i }));
  // wait for the loading spinner to clear (template + contact fetch)
  await waitFor(() => {
    expect(screen.getByLabelText(/template/i)).toBeInTheDocument();
  });
  return user;
}

describe("CreateRequestDialog (GAP-L-67) — ad-hoc items", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupHappyMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows the items section by default (ad-hoc is default template)", async () => {
    render(
      <CreateRequestDialog
        slug="test-org"
        customerId="cust-1"
        customerName="Mathebula Trust"
      />
    );

    await openDialog();

    expect(screen.getByTestId("ad-hoc-items-section")).toBeInTheDocument();
    expect(screen.getByText(/add at least one item before sending/i)).toBeInTheDocument();
  });

  it("Add Item button appends a blank row, Trash button removes it", async () => {
    render(
      <CreateRequestDialog
        slug="test-org"
        customerId="cust-1"
        customerName="Mathebula Trust"
      />
    );

    const user = await openDialog();

    await user.click(screen.getByTestId("add-ad-hoc-item-button"));
    expect(screen.getByTestId("ad-hoc-item-row-0")).toBeInTheDocument();
    expect(screen.getByTestId("ad-hoc-item-name-0")).toBeInTheDocument();

    await user.click(screen.getByTestId("add-ad-hoc-item-button"));
    expect(screen.getByTestId("ad-hoc-item-row-1")).toBeInTheDocument();

    await user.click(screen.getByTestId("ad-hoc-item-remove-1"));
    expect(screen.queryByTestId("ad-hoc-item-row-1")).not.toBeInTheDocument();
    expect(screen.getByTestId("ad-hoc-item-row-0")).toBeInTheDocument();
  });

  it("Send Now is disabled when ad-hoc has 0 items", async () => {
    render(
      <CreateRequestDialog
        slug="test-org"
        customerId="cust-1"
        customerName="Mathebula Trust"
      />
    );

    await openDialog();

    expect(screen.getByTestId("create-request-send-now")).toBeDisabled();
  });

  it("Send Now is disabled when an item name is blank", async () => {
    render(
      <CreateRequestDialog
        slug="test-org"
        customerId="cust-1"
        customerName="Mathebula Trust"
      />
    );

    const user = await openDialog();
    await user.click(screen.getByTestId("add-ad-hoc-item-button"));

    expect(screen.getByTestId("create-request-send-now")).toBeDisabled();

    await user.type(screen.getByTestId("ad-hoc-item-name-0"), "Latest medical reports");
    expect(screen.getByTestId("create-request-send-now")).toBeEnabled();
  });

  it("Send Now submits createRequestAction with the items array (sortOrder, trimmed names)", async () => {
    render(
      <CreateRequestDialog
        slug="test-org"
        customerId="cust-1"
        customerName="Mathebula Trust"
      />
    );

    const user = await openDialog();
    await user.click(screen.getByTestId("add-ad-hoc-item-button"));
    await user.click(screen.getByTestId("add-ad-hoc-item-button"));

    await user.type(
      screen.getByTestId("ad-hoc-item-name-0"),
      "  Latest specialist medical reports  "
    );
    await user.type(
      screen.getByTestId("ad-hoc-item-name-1"),
      "Independent expert assessment"
    );

    await user.click(screen.getByTestId("create-request-send-now"));

    await waitFor(() => {
      expect(mockCreateRequest).toHaveBeenCalledTimes(1);
    });

    const [, , payload] = mockCreateRequest.mock.calls[0] as [
      string,
      string,
      Record<string, unknown>,
    ];
    expect(payload.requestTemplateId).toBeNull();
    expect(payload.portalContactId).toBe(CONTACT_FIXTURE.id);
    expect(payload.items).toEqual([
      {
        name: "Latest specialist medical reports",
        description: undefined,
        responseType: "FILE_UPLOAD",
        required: true,
        sortOrder: 0,
      },
      {
        name: "Independent expert assessment",
        description: undefined,
        responseType: "FILE_UPLOAD",
        required: true,
        sortOrder: 1,
      },
    ]);

    // Send Now flow also calls sendRequestAction afterwards
    await waitFor(() => {
      expect(mockSendRequest).toHaveBeenCalledTimes(1);
    });
  });

  it("template path hides the items section and omits items from payload", async () => {
    render(
      <CreateRequestDialog
        slug="test-org"
        customerId="cust-1"
        customerName="Mathebula Trust"
      />
    );

    const user = await openDialog();

    // Switch from ad-hoc to a real template
    await user.click(screen.getByLabelText(/template/i));
    const templateOption = await screen.findByRole("option", {
      name: /FICA Onboarding Pack/i,
    });
    await user.click(templateOption);

    // Items section should be gone
    expect(screen.queryByTestId("ad-hoc-items-section")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("create-request-send-now"));

    await waitFor(() => {
      expect(mockCreateRequest).toHaveBeenCalledTimes(1);
    });

    const [, , payload] = mockCreateRequest.mock.calls[0] as [
      string,
      string,
      Record<string, unknown>,
    ];
    expect(payload.requestTemplateId).toBe(TEMPLATE_FIXTURE.id);
    expect(payload.items).toBeUndefined();
  });

  it("Save as Draft allows ad-hoc with 0 items (existing behaviour)", async () => {
    render(
      <CreateRequestDialog
        slug="test-org"
        customerId="cust-1"
        customerName="Mathebula Trust"
      />
    );

    const user = await openDialog();

    // Save as Draft button is enabled even with 0 items
    const draftButton = screen.getByRole("button", { name: /save as draft/i });
    expect(draftButton).toBeEnabled();

    await user.click(draftButton);

    await waitFor(() => {
      expect(mockCreateRequest).toHaveBeenCalledTimes(1);
    });
    const [, , payload] = mockCreateRequest.mock.calls[0] as [
      string,
      string,
      Record<string, unknown>,
    ];
    expect(payload.items).toBeUndefined();
    expect(mockSendRequest).not.toHaveBeenCalled();
  });
});
