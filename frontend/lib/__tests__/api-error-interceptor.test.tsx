import { describe, it, expect } from "vitest";
import { classifyError } from "@/lib/error-handler";

/**
 * Tests for API error interception of subscription-related 403s.
 *
 * The server-side `isSubscriptionError()` in `lib/api/client.ts` is server-only,
 * so we test the client-side `classifyError()` from `lib/error-handler.ts` which
 * performs the same detection via the `detail.type` field on error objects.
 */

function makeApiError(
  status: number,
  detailType?: string
): { status: number; detail?: { type: string } } {
  if (detailType) {
    return { status, detail: { type: detailType } };
  }
  return { status };
}

describe("API Error Interceptor — subscription 403 detection", () => {
  it("classifies 403 with subscription_required type", () => {
    const error = makeApiError(403, "subscription_required");
    const result = classifyError(error);
    expect(result.category).toBe("subscriptionRequired");
    expect(result.action).toBe("subscribeBilling");
    expect(result.retryable).toBe(false);
  });

  it("classifies 403 with subscription_locked type", () => {
    const error = makeApiError(403, "subscription_locked");
    const result = classifyError(error);
    expect(result.category).toBe("subscriptionLocked");
    expect(result.action).toBe("subscribeBilling");
    expect(result.retryable).toBe(false);
  });

  it("does NOT intercept 403 without a detail type field", () => {
    const error = makeApiError(403);
    const result = classifyError(error);
    expect(result.category).toBe("forbidden");
    expect(result.action).toBe("contactAdmin");
  });

  it("does NOT intercept 403 with a non-subscription type", () => {
    const error = makeApiError(403, "forbidden");
    const result = classifyError(error);
    expect(result.category).toBe("forbidden");
    expect(result.action).toBe("contactAdmin");
  });
});
