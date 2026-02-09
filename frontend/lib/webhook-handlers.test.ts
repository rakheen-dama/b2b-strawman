import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  handleOrganizationCreated,
  handleOrganizationUpdated,
  handleOrganizationDeleted,
  handleMembershipCreated,
  handleMembershipUpdated,
  handleMembershipDeleted,
  handleSubscriptionCreated,
  handleSubscriptionUpdated,
  routeWebhookEvent,
} from "./webhook-handlers";

vi.mock("@/lib/internal-api", () => ({
  internalApiClient: vi.fn(),
  InternalApiError: class InternalApiError extends Error {
    constructor(
      public status: number,
      public statusText: string,
      public body?: string
    ) {
      super(`Internal API request failed: ${status} ${statusText}`);
      this.name = "InternalApiError";
    }
  },
}));

const mockGetUser = vi.fn();
vi.mock("@clerk/nextjs/server", () => ({
  clerkClient: vi.fn().mockResolvedValue({
    users: { getUser: (...args: unknown[]) => mockGetUser(...args) },
  }),
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

function membershipEventData(overrides = {}) {
  return {
    id: "orgmem_123",
    role: "org:member",
    organization: { id: "org_456" },
    public_user_data: { user_id: "user_789" },
    ...overrides,
  };
}

function mockClerkUser(overrides = {}) {
  return {
    id: "user_789",
    firstName: "Jane",
    lastName: "Doe",
    emailAddresses: [{ emailAddress: "jane@example.com" }],
    imageUrl: "https://img.clerk.com/avatar.jpg",
    ...overrides,
  };
}

function subscriptionEventData(overrides = {}) {
  return {
    id: "sub_123",
    organization_id: "org_456",
    plan: { slug: "pro" },
    ...overrides,
  };
}

// ─── Organization Handlers ───────────────────────────────────────────────────

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

    expect(mockInternalApiClient).toHaveBeenCalledWith("/internal/orgs/provision", {
      body: { clerkOrgId: "org_123", orgName: "Acme Corp" },
    });
  });

  it("handles 409 Conflict (already provisioned) gracefully", async () => {
    mockInternalApiClient.mockRejectedValue(new InternalApiError(409, "Conflict"));

    await expect(
      handleOrganizationCreated(orgCreatedData(), "msg_abc123")
    ).resolves.toBeUndefined();
  });

  it("logs error but does not throw on provisioning failure", async () => {
    mockInternalApiClient.mockRejectedValue(new InternalApiError(500, "Internal Server Error"));

    await expect(
      handleOrganizationCreated(orgCreatedData(), "msg_abc123")
    ).resolves.toBeUndefined();
  });

  it("logs error but does not throw on network failure", async () => {
    mockInternalApiClient.mockRejectedValue(new TypeError("fetch failed"));

    await expect(
      handleOrganizationCreated(orgCreatedData(), "msg_abc123")
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

    expect(mockInternalApiClient).toHaveBeenCalledWith("/internal/orgs/update", {
      method: "PUT",
      body: {
        clerkOrgId: "org_123",
        orgName: "Acme Corp v2",
        updatedAt: 1700000001000,
      },
    });
  });

  it("logs error but does not throw on update failure", async () => {
    mockInternalApiClient.mockRejectedValue(new InternalApiError(500, "Internal Server Error"));

    await expect(
      handleOrganizationUpdated(orgUpdatedData(), "msg_def456")
    ).resolves.toBeUndefined();
  });
});

describe("handleOrganizationDeleted", () => {
  it("does not throw (no-op for MVP)", async () => {
    const data = { id: "org_123", object: "organization" as const, slug: "acme", deleted: true };

    await expect(handleOrganizationDeleted(data as never, "msg_ghi789")).resolves.toBeUndefined();
  });
});

// ─── Membership Handlers ─────────────────────────────────────────────────────

describe("handleMembershipCreated", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches user from Clerk and calls sync endpoint", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser());
    mockInternalApiClient.mockResolvedValue({ memberId: "uuid-1", clerkUserId: "user_789", action: "created" });

    await handleMembershipCreated(membershipEventData(), "msg_mem1");

    expect(mockGetUser).toHaveBeenCalledWith("user_789");
    expect(mockInternalApiClient).toHaveBeenCalledWith("/internal/members/sync", {
      body: {
        clerkOrgId: "org_456",
        clerkUserId: "user_789",
        email: "jane@example.com",
        name: "Jane Doe",
        avatarUrl: "https://img.clerk.com/avatar.jpg",
        orgRole: "member",
      },
    });
  });

  it("strips 'org:' prefix from role", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser());
    mockInternalApiClient.mockResolvedValue({ memberId: "uuid-1", clerkUserId: "user_789", action: "created" });

    await handleMembershipCreated(membershipEventData({ role: "org:admin" }), "msg_mem2");

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/members/sync",
      expect.objectContaining({
        body: expect.objectContaining({ orgRole: "admin" }),
      })
    );
  });

  it("handles user with no last name", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser({ lastName: null }));
    mockInternalApiClient.mockResolvedValue({ memberId: "uuid-1", clerkUserId: "user_789", action: "created" });

    await handleMembershipCreated(membershipEventData(), "msg_mem3");

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/members/sync",
      expect.objectContaining({
        body: expect.objectContaining({ name: "Jane" }),
      })
    );
  });

  it("sends undefined name when user has no first or last name", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser({ firstName: null, lastName: null }));
    mockInternalApiClient.mockResolvedValue({ memberId: "uuid-1", clerkUserId: "user_789", action: "created" });

    await handleMembershipCreated(membershipEventData(), "msg_mem4");

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/members/sync",
      expect.objectContaining({
        body: expect.objectContaining({ name: undefined }),
      })
    );
  });

  it("skips sync when user has no email address", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser({ emailAddresses: [] }));

    await expect(
      handleMembershipCreated(membershipEventData(), "msg_mem_noemail")
    ).resolves.toBeUndefined();

    expect(mockInternalApiClient).not.toHaveBeenCalled();
  });

  it("logs error but does not throw on sync failure", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser());
    mockInternalApiClient.mockRejectedValue(new InternalApiError(500, "Internal Server Error"));

    await expect(
      handleMembershipCreated(membershipEventData(), "msg_mem5")
    ).resolves.toBeUndefined();
  });

  it("logs error but does not throw on Clerk API failure", async () => {
    mockGetUser.mockRejectedValue(new Error("Clerk API error"));

    await expect(
      handleMembershipCreated(membershipEventData(), "msg_mem6")
    ).resolves.toBeUndefined();
  });
});

describe("handleMembershipUpdated", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches user from Clerk and calls sync endpoint with updated role", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser());
    mockInternalApiClient.mockResolvedValue({ memberId: "uuid-1", clerkUserId: "user_789", action: "updated" });

    await handleMembershipUpdated(
      membershipEventData({ role: "org:admin" }),
      "msg_upd1"
    );

    expect(mockGetUser).toHaveBeenCalledWith("user_789");
    expect(mockInternalApiClient).toHaveBeenCalledWith("/internal/members/sync", {
      body: expect.objectContaining({ orgRole: "admin" }),
    });
  });

  it("logs error but does not throw on failure", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser());
    mockInternalApiClient.mockRejectedValue(new InternalApiError(500, "Internal Server Error"));

    await expect(
      handleMembershipUpdated(membershipEventData(), "msg_upd2")
    ).resolves.toBeUndefined();
  });
});

describe("handleMembershipDeleted", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls delete endpoint with correct URL", async () => {
    mockInternalApiClient.mockResolvedValue(undefined);

    await handleMembershipDeleted(membershipEventData(), "msg_del1");

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/members/user_789?clerkOrgId=org_456",
      { method: "DELETE" }
    );
  });

  it("handles 404 gracefully (member already deleted)", async () => {
    mockInternalApiClient.mockRejectedValue(new InternalApiError(404, "Not Found"));

    await expect(
      handleMembershipDeleted(membershipEventData(), "msg_del2")
    ).resolves.toBeUndefined();
  });

  it("logs error but does not throw on other failures", async () => {
    mockInternalApiClient.mockRejectedValue(new InternalApiError(500, "Internal Server Error"));

    await expect(
      handleMembershipDeleted(membershipEventData(), "msg_del3")
    ).resolves.toBeUndefined();
  });
});

// ─── Subscription Handlers ──────────────────────────────────────────────────

describe("handleSubscriptionCreated", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls plan-sync endpoint with correct payload", async () => {
    mockInternalApiClient.mockResolvedValue(undefined);

    await handleSubscriptionCreated(subscriptionEventData(), "msg_sub1");

    expect(mockInternalApiClient).toHaveBeenCalledWith("/internal/orgs/plan-sync", {
      body: { clerkOrgId: "org_456", planSlug: "pro" },
    });
  });

  it("logs error but does not throw on failure", async () => {
    mockInternalApiClient.mockRejectedValue(new InternalApiError(500, "Internal Server Error"));

    await expect(
      handleSubscriptionCreated(subscriptionEventData(), "msg_sub2")
    ).resolves.toBeUndefined();
  });

  it("logs error but does not throw on network failure", async () => {
    mockInternalApiClient.mockRejectedValue(new TypeError("fetch failed"));

    await expect(
      handleSubscriptionCreated(subscriptionEventData(), "msg_sub3")
    ).resolves.toBeUndefined();
  });
});

describe("handleSubscriptionUpdated", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls plan-sync endpoint with correct payload", async () => {
    mockInternalApiClient.mockResolvedValue(undefined);

    await handleSubscriptionUpdated(
      subscriptionEventData({ plan: { slug: "starter-monthly" } }),
      "msg_sub_upd1"
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith("/internal/orgs/plan-sync", {
      body: { clerkOrgId: "org_456", planSlug: "starter-monthly" },
    });
  });

  it("logs error but does not throw on failure", async () => {
    mockInternalApiClient.mockRejectedValue(new InternalApiError(500, "Internal Server Error"));

    await expect(
      handleSubscriptionUpdated(subscriptionEventData(), "msg_sub_upd2")
    ).resolves.toBeUndefined();
  });
});

// ─── Router ──────────────────────────────────────────────────────────────────

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
      "msg_xyz"
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/orgs/provision",
      expect.any(Object)
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
      "msg_xyz"
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith("/internal/orgs/update", expect.any(Object));
  });

  it("routes organizationMembership.created to member sync", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser());
    mockInternalApiClient.mockResolvedValue({ memberId: "uuid-1", clerkUserId: "user_789", action: "created" });

    await routeWebhookEvent(
      {
        type: "organizationMembership.created",
        object: "event",
        data: membershipEventData() as never,
        event_attributes: { http_request: { client_ip: "", user_agent: "" } },
      },
      "msg_mem_route1"
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/members/sync",
      expect.any(Object)
    );
  });

  it("routes organizationMembership.updated to member sync", async () => {
    mockGetUser.mockResolvedValue(mockClerkUser());
    mockInternalApiClient.mockResolvedValue({ memberId: "uuid-1", clerkUserId: "user_789", action: "updated" });

    await routeWebhookEvent(
      {
        type: "organizationMembership.updated",
        object: "event",
        data: membershipEventData({ role: "org:admin" }) as never,
        event_attributes: { http_request: { client_ip: "", user_agent: "" } },
      },
      "msg_mem_route2"
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/members/sync",
      expect.any(Object)
    );
  });

  it("routes organizationMembership.deleted to member delete", async () => {
    mockInternalApiClient.mockResolvedValue(undefined);

    await routeWebhookEvent(
      {
        type: "organizationMembership.deleted",
        object: "event",
        data: membershipEventData() as never,
        event_attributes: { http_request: { client_ip: "", user_agent: "" } },
      },
      "msg_mem_route3"
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/members/user_789?clerkOrgId=org_456",
      expect.objectContaining({ method: "DELETE" })
    );
  });

  it("routes subscription.created to plan-sync", async () => {
    mockInternalApiClient.mockResolvedValue(undefined);

    await routeWebhookEvent(
      {
        type: "subscription.created" as never,
        object: "event",
        data: subscriptionEventData() as never,
        event_attributes: { http_request: { client_ip: "", user_agent: "" } },
      },
      "msg_sub_route1"
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/orgs/plan-sync",
      expect.any(Object)
    );
  });

  it("routes subscription.updated to plan-sync", async () => {
    mockInternalApiClient.mockResolvedValue(undefined);

    await routeWebhookEvent(
      {
        type: "subscription.updated" as never,
        object: "event",
        data: subscriptionEventData({ plan: { slug: "starter" } }) as never,
        event_attributes: { http_request: { client_ip: "", user_agent: "" } },
      },
      "msg_sub_route2"
    );

    expect(mockInternalApiClient).toHaveBeenCalledWith(
      "/internal/orgs/plan-sync",
      expect.any(Object)
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
        "msg_unknown"
      )
    ).resolves.toBeUndefined();
  });
});
