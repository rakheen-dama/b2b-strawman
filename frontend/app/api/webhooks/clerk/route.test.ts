import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@clerk/nextjs/webhooks", () => ({
  verifyWebhook: vi.fn(),
}));

vi.mock("@/lib/webhook-handlers", () => ({
  routeWebhookEvent: vi.fn(),
}));

import { POST } from "./route";
import { verifyWebhook } from "@clerk/nextjs/webhooks";
import { routeWebhookEvent } from "@/lib/webhook-handlers";
import { NextRequest } from "next/server";

const mockVerifyWebhook = vi.mocked(verifyWebhook);
const mockRouteWebhookEvent = vi.mocked(routeWebhookEvent);

function createMockRequest(headers?: Record<string, string>): NextRequest {
  return new NextRequest("http://localhost:3000/api/webhooks/clerk", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "svix-id": "msg_test123",
      "svix-timestamp": "1700000000",
      "svix-signature": "v1,test_signature",
      ...headers,
    },
    body: JSON.stringify({ type: "test", data: {} }),
  });
}

describe("POST /api/webhooks/clerk", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns 400 when signature verification fails", async () => {
    mockVerifyWebhook.mockRejectedValue(new Error("Invalid signature"));

    const response = await POST(createMockRequest());

    expect(response.status).toBe(400);
    expect(await response.text()).toBe("Webhook verification failed");
  });

  it("returns 200 and routes event when signature is valid", async () => {
    const mockEvent = {
      type: "organization.created" as const,
      object: "event" as const,
      data: { id: "org_123", name: "Test" },
      event_attributes: { http_request: { client_ip: "", user_agent: "" } },
    };
    mockVerifyWebhook.mockResolvedValue(mockEvent as never);
    mockRouteWebhookEvent.mockResolvedValue(undefined);

    const response = await POST(createMockRequest());

    expect(response.status).toBe(200);
    expect(mockRouteWebhookEvent).toHaveBeenCalledWith(mockEvent, "msg_test123");
  });

  it("extracts svix-id from headers and passes to router", async () => {
    mockVerifyWebhook.mockResolvedValue({
      type: "organization.updated" as const,
      object: "event" as const,
      data: { id: "org_456" },
      event_attributes: { http_request: { client_ip: "", user_agent: "" } },
    } as never);
    mockRouteWebhookEvent.mockResolvedValue(undefined);

    await POST(createMockRequest({ "svix-id": "msg_custom_id" }));

    expect(mockRouteWebhookEvent).toHaveBeenCalledWith(expect.anything(), "msg_custom_id");
  });
});
