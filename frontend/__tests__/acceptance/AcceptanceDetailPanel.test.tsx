import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AcceptanceDetailPanel } from "@/components/acceptance/AcceptanceDetailPanel";
import type { AcceptanceRequestResponse } from "@/lib/actions/acceptance-actions";

// Mock server-only (imported transitively via acceptance-actions -> api)
vi.mock("server-only", () => ({}));

const mockRemindAcceptance = vi.fn();
const mockRevokeAcceptance = vi.fn();
const mockDownloadCertificate = vi.fn();

vi.mock("@/lib/actions/acceptance-actions", async () => {
  const actual = await vi.importActual("@/lib/actions/acceptance-actions");
  return {
    ...actual,
    remindAcceptance: (...args: unknown[]) => mockRemindAcceptance(...args),
    revokeAcceptance: (...args: unknown[]) => mockRevokeAcceptance(...args),
    downloadCertificate: (...args: unknown[]) => mockDownloadCertificate(...args),
  };
});

function makeRequest(
  overrides: Partial<AcceptanceRequestResponse> = {},
): AcceptanceRequestResponse {
  return {
    id: "req-1",
    generatedDocumentId: "doc-1",
    portalContactId: "contact-1",
    customerId: "cust-1",
    status: "SENT",
    sentAt: "2026-02-20T10:00:00Z",
    viewedAt: null,
    acceptedAt: null,
    expiresAt: "2026-03-22T10:00:00Z",
    revokedAt: null,
    acceptorName: null,
    hasCertificate: false,
    certificateFileName: null,
    sentByMemberId: "member-1",
    revokedByMemberId: null,
    reminderCount: 0,
    lastRemindedAt: null,
    createdAt: "2026-02-20T10:00:00Z",
    updatedAt: "2026-02-20T10:00:00Z",
    contact: {
      id: "contact-1",
      displayName: "Jane Smith",
      email: "jane@client.com",
    },
    document: {
      id: "doc-1",
      fileName: "engagement-letter.pdf",
    },
    ...overrides,
  };
}

describe("AcceptanceDetailPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders recipient info", () => {
    render(
      <AcceptanceDetailPanel request={makeRequest()} isAdmin />,
    );

    expect(screen.getByText("Jane Smith")).toBeInTheDocument();
    expect(screen.getByText("jane@client.com")).toBeInTheDocument();
  });

  it("renders status timeline with sent date", () => {
    render(
      <AcceptanceDetailPanel request={makeRequest()} isAdmin />,
    );

    expect(screen.getByText("Sent")).toBeInTheDocument();
    expect(screen.getByText("Viewed")).toBeInTheDocument();
    expect(screen.getByText("Accepted")).toBeInTheDocument();
  });

  it("shows certificate download when accepted and has certificate", () => {
    render(
      <AcceptanceDetailPanel
        request={makeRequest({
          status: "ACCEPTED",
          acceptedAt: "2026-02-25T14:00:00Z",
          acceptorName: "Jane Smith",
          hasCertificate: true,
          certificateFileName: "acceptance-cert.pdf",
        })}
        isAdmin
      />,
    );

    expect(
      screen.getByRole("button", { name: /Download Certificate/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Accepted by/)).toBeInTheDocument();
  });

  it("shows remind button for sent status when admin", () => {
    render(
      <AcceptanceDetailPanel request={makeRequest({ status: "SENT" })} isAdmin />,
    );

    expect(
      screen.getByRole("button", { name: /Remind/i }),
    ).toBeInTheDocument();
  });

  it("shows revoke button for active status (SENT/VIEWED) when admin", () => {
    render(
      <AcceptanceDetailPanel
        request={makeRequest({ status: "VIEWED", viewedAt: "2026-02-21T10:00:00Z" })}
        isAdmin
      />,
    );

    expect(
      screen.getByRole("button", { name: /Revoke/i }),
    ).toBeInTheDocument();
  });

  it("hides actions for terminal status (ACCEPTED/EXPIRED/REVOKED)", () => {
    render(
      <AcceptanceDetailPanel
        request={makeRequest({
          status: "ACCEPTED",
          acceptedAt: "2026-02-25T14:00:00Z",
          acceptorName: "Jane Smith",
          hasCertificate: false,
        })}
        isAdmin
      />,
    );

    expect(
      screen.queryByRole("button", { name: /Remind/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Revoke/i }),
    ).not.toBeInTheDocument();
  });

  it("calls remindAcceptance and dispatches refresh event on remind", async () => {
    mockRemindAcceptance.mockResolvedValue({ success: true });
    const dispatchSpy = vi.spyOn(window, "dispatchEvent");

    const user = userEvent.setup();
    render(
      <AcceptanceDetailPanel request={makeRequest({ status: "SENT" })} isAdmin />,
    );

    await user.click(screen.getByRole("button", { name: /Remind/i }));

    await waitFor(() => {
      expect(mockRemindAcceptance).toHaveBeenCalledWith("req-1");
    });

    expect(dispatchSpy).toHaveBeenCalledWith(
      expect.objectContaining({ type: "acceptance-requests-refresh" }),
    );

    dispatchSpy.mockRestore();
  });

  it("shows reminder history when reminders have been sent", () => {
    render(
      <AcceptanceDetailPanel
        request={makeRequest({
          status: "SENT",
          reminderCount: 3,
          lastRemindedAt: "2026-02-24T10:00:00Z",
        })}
        isAdmin
      />,
    );

    expect(screen.getByText(/Reminded 3 times/)).toBeInTheDocument();
  });
});
