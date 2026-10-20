import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 87 S3 Batch 5 (ADR-337, Epic 609F): guards the staff-frontend callers of the
// out-of-batch records 609F renamed so WireVocabularyContractTest could drop its scope list.
// Jackson drops unknown body properties, so a stale key fails SILENTLY:
//   - PUT /api/settings with projectNamingPattern → 200, pattern never saved
//   - NotificationResponse.referenceProjectId → deep-link disappears (renders null)
//   - POST /api/ai/skills/matter-intake with customerId → null client id
// plus the loud ones (400/500) so a regression is caught before the e2e stack:
//   - POST /api/kyc/verify with customerId → 400 "clientId is required"
//   - POST /api/ai/skills/{fica-verification,contract-review,drafting} with customerId/projectId

vi.mock("server-only", () => ({}));

const { mockGet, mockPost, mockPut, mockPush, apiMock } = vi.hoisted(() => {
  const mockGet = vi.fn();
  const mockPost = vi.fn();
  const mockPut = vi.fn();
  const mockPush = vi.fn();
  const apiMock = {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    patch: vi.fn(),
    delete: vi.fn(),
  };
  return { mockGet, mockPost, mockPut, mockPush, apiMock };
});

vi.mock("@/lib/api", () => ({
  api: apiMock,
  ApiError: class ApiError extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  },
}));
vi.mock("@/lib/api/client", () => ({ api: apiMock }));
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi
    .fn()
    .mockResolvedValue({ isAdmin: true, isOwner: true, capabilities: [] }),
}));
vi.mock("@/lib/auth", () => ({
  getAuthContext: vi.fn().mockResolvedValue({ orgSlug: "acme" }),
}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));
vi.mock("@/lib/actions/notifications", () => ({
  markNotificationRead: vi.fn().mockResolvedValue({ success: true }),
}));

import { updateProjectNamingPattern } from "@/app/(app)/org/[slug]/settings/project-naming/actions";
import { verifyKycAction } from "@/app/(app)/org/[slug]/clients/[id]/kyc-actions";
import {
  invokeFicaVerification,
  invokeMatterIntake,
  invokeContractReview,
  invokeDrafting,
} from "@/lib/api/ai";
import { NotificationItem } from "@/components/notifications/notification-item";
import type { Notification } from "@/lib/actions/notifications";

describe("staff-frontend straggler wire bodies (Phase 87 S3, 609F)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({ defaultCurrency: "ZAR" });
    mockPost.mockResolvedValue({});
    mockPut.mockResolvedValue({});
  });

  afterEach(() => {
    cleanup();
  });

  it("updateProjectNamingPattern PUTs matterNamingPattern (never projectNamingPattern)", async () => {
    const result = await updateProjectNamingPattern("acme", "{year}-{seq}");
    expect(result.success).toBe(true);
    const [path, body] = mockPut.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/api/settings");
    expect(body.matterNamingPattern).toBe("{year}-{seq}");
    expect(body).not.toHaveProperty("projectNamingPattern");
  });

  it("verifyKycAction sends clientId (never customerId)", async () => {
    await verifyKycAction("acme", {
      customerId: "c-1",
      checklistInstanceItemId: "item-1",
      idNumber: "9001015009087",
      fullName: "Thandi Mokoena",
      consentAcknowledged: true,
    });
    const [path, body] = mockPost.mock.calls[0] as [string, Record<string, unknown>];
    expect(path).toBe("/api/kyc/verify");
    expect(body.clientId).toBe("c-1");
    expect(body.checklistInstanceItemId).toBe("item-1");
    expect(body).not.toHaveProperty("customerId");
  });

  it("AI skill bodies use clientId / matterId (never customerId / projectId)", async () => {
    await invokeFicaVerification("c-1");
    await invokeMatterIntake("c-2", "New matter");
    await invokeContractReview("doc-1", "m-1");
    await invokeDrafting("tpl-1", "m-2");

    const calls = mockPost.mock.calls as [string, Record<string, unknown>][];
    expect(calls.map(([path]) => path)).toEqual([
      "/api/ai/skills/fica-verification",
      "/api/ai/skills/matter-intake",
      "/api/ai/skills/contract-review",
      "/api/ai/skills/drafting",
    ]);
    expect(calls[0][1]).toEqual({ clientId: "c-1" });
    expect(calls[1][1]).toEqual({ clientId: "c-2", description: "New matter" });
    expect(calls[2][1]).toEqual({ documentId: "doc-1", matterId: "m-1" });
    expect(calls[3][1]).toEqual({ templateId: "tpl-1", matterId: "m-2" });
  });

  it("notification deep-link is built from referenceMatterId", async () => {
    const notification: Notification = {
      id: "n1",
      type: "DOCUMENT_SHARED",
      title: "Charlie uploaded requirements.pdf",
      body: null,
      referenceEntityType: "DOCUMENT",
      referenceEntityId: "d1",
      referenceMatterId: "m-1",
      isRead: false,
      createdAt: "2026-10-20T10:00:00Z",
    };
    render(<NotificationItem notification={notification} orgSlug="acme" />);

    await userEvent.setup().click(screen.getByRole("button"));

    expect(mockPush).toHaveBeenCalledWith("/org/acme/projects/m-1?tab=documents");
  });
});
