import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  handleOrganizationCreated,
  handleOrganizationUpdated,
  handleOrganizationDeleted,
  routeWebhookEvent,
} from "./webhook-handlers";

vi.mock("@/lib/internal-api", () => ({
  internalApiClient: vi.fn(),
  InternalApiError: class InternalApiError extends Error {
    constructor(
      public status: number,
      public statusText: string,
      public body?: string,
    ) {
      super(`Internal API request failed: ${status} ${statusText}`);
      this.name = "InternalApiError";
    }
  },
}));

import { internalApiClient, InternalApiError } from "@/lib/internal-api";
const mockInternalApiClient = vi.mocked(internalApiClient);

function orgCreatedData(overrides = {}) {
  return {
    id: "org_123",
    name: "Acme Corp",
    slug: "acme",
    created_at: 1700000000000,
    updated_at: 1700000000000,
    ...overrides,
  };
}

function orgUpdatedData(overrides = {}) {
  return {
    id: "org_123",
    name: "Acme Corp v2",
    slug: "acme",
    created_at: 1700000000000,
    updated_at: 1700000001000,
    ...overrides,
  };
}

describe("handleOrganizationCreated", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls provisioning endpoint with correct payload", async () => {
    mockInternalApiClient.mockResolvedValue({
      clerkOrgId: "org_123",
      schemaName: "tenant_abc123def456",
      status: "COMPLETED",
    });

    await handleOrganizationCreated(orgCreatedData(), "msg_abc123");

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/orgs/provision",
      {
        body: { clerkOrgId: "org_123", orgName: "Acme Corp" },
      },
    );
  });

  it("handles 409 Conflict (already provisioned) gracefully", async () => {
    mockInternalApiClient.mockRejectedValue(
      new InternalApiError(409, "Conflict"),
    );

    await expect(
      handleOrganizationCreated(orgCreatedData(), "msg_abc123"),
    ).resolves.toBeUndefined();
  });

  it("logs error but does not throw on provisioning failure", async () => {
    mockInternalApiClient.mockRejectedValue(
      new InternalApiError(500, "Internal Server Error"),
    );

    await expect(
      handleOrganizationCreated(orgCreatedData(), "msg_abc123"),
    ).resolves.toBeUndefined();
  });

  it("logs error but does not throw on network failure", async () => {
    mockInternalApiClient.mockRejectedValue(
      new TypeError("fetch failed"),
    );

    await expect(
      handleOrganizationCreated(orgCreatedData(), "msg_abc123"),
    ).resolves.toBeUndefined();
  });
});

describe("handleOrganizationUpdated", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls update endpoint with correct payload including updatedAt", async () => {
    mockInternalApiClient.mockResolvedValue(undefined);

    await handleOrganizationUpdated(orgUpdatedData(), "msg_def456");

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/orgs/update",
      {
        method: "PUT",
        body: {
          clerkOrgId: "org_123",
          orgName: "Acme Corp v2",
          updatedAt: 1700000001000,
        },
      },
    );
  });

  it("logs error but does not throw on update failure", async () => {
    mockInternalApiClient.mockRejectedValue(
      new InternalApiError(500, "Internal Server Error"),
    );

    await expect(
      handleOrganizationUpdated(orgUpdatedData(), "msg_def456"),
    ).resolves.toBeUndefined();
  });
});

describe("handleOrganizationDeleted", () => {
  it("does not throw (no-op for MVP)", async () => {
    const data = { id: "org_123", object: "organization" as const, slug: "acme", deleted: true };

    await expect(
      handleOrganizationDeleted(data as never, "msg_ghi789"),
    ).resolves.toBeUndefined();
  });
});

describe("routeWebhookEvent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("routes organization.created to provisioning", async () => {
    mockInternalApiClient.mockResolvedValue({
      clerkOrgId: "org_123",
      schemaName: "tenant_abc",
      status: "COMPLETED",
    });

    await routeWebhookEvent(
      {
        type: "organization.created",
        object: "event",
        data: orgCreatedData(),
        event_attributes: { http_request: { client_ip: "", user_agent: "" } },
      },
      "msg_xyz",
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/orgs/provision",
      expect.any(Object),
    );
  });

  it("routes organization.updated to update endpoint", async () => {
    mockInternalApiClient.mockResolvedValue(undefined);

    await routeWebhookEvent(
      {
        type: "organization.updated",
        object: "event",
        data: orgUpdatedData(),
        event_attributes: { http_request: { client_ip: "", user_agent: "" } },
      },
      "msg_xyz",
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/orgs/update",
      expect.any(Object),
    );
  });

  it("handles unknown event types without throwing", async () => {
    await expect(
      routeWebhookEvent(
        {
          type: "user.created" as never,
          object: "event",
          data: { id: "user_123" } as never,
          event_attributes: { http_request: { client_ip: "", user_agent: "" } },
        },
        "msg_unknown",
      ),
    ).resolves.toBeUndefined();
  });
});
