import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Regression for the silent-defaults bug class: a failing GET /api/settings must
// PROPAGATE (so the route error boundary renders) instead of being swallowed and
// replaced by an editable form pre-filled with hardcoded defaults — which a Save
// would then persist over the org's real time-reminder policy.

vi.mock("server-only", () => ({}));

vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({
    capabilities: [],
    role: "owner",
    isAdmin: false,
    isOwner: true,
  }),
}));

const mockGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockGet(...args) },
}));

import TimeTrackingSettingsPage from "@/app/(app)/org/[slug]/settings/time-tracking/page";

describe("TimeTrackingSettingsPage — settings fetch failure", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    cleanup();
  });

  it("propagates a rejected GET /api/settings instead of rendering a defaulted form", async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === "/api/settings") {
        return Promise.reject(new Error("network"));
      }
      return Promise.resolve(null);
    });

    // The page render (a server component invocation) must reject so the settings
    // error boundary takes over — it must NOT resolve to an editable form.
    await expect(
      TimeTrackingSettingsPage({ params: Promise.resolve({ slug: "acme" }) })
    ).rejects.toThrow("network");
  });
});
