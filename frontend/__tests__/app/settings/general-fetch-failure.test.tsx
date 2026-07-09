import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Regression for the silent-defaults bug class (PUT flavor): a failing
// GET /api/settings must PROPAGATE so the route error boundary renders, instead
// of being swallowed and replaced by an editable form pre-filled with hardcoded
// defaults — a Save on the general page issues PUT /api/settings which overwrites
// currency, brandColor and documentFooterText unconditionally.

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

import GeneralSettingsPage from "@/app/(app)/org/[slug]/settings/general/page";

describe("GeneralSettingsPage — settings fetch failure", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    cleanup();
  });

  it("propagates a rejected GET /api/settings instead of rendering a defaulted form", async () => {
    mockGet.mockImplementation((url: string) => {
      // The documents fetch is a display-only degrade (its own try/catch) — resolve it.
      if (url.startsWith("/api/documents")) {
        return Promise.resolve([]);
      }
      if (url === "/api/settings") {
        return Promise.reject(new Error("network"));
      }
      return Promise.resolve(null);
    });

    await expect(
      GeneralSettingsPage({ params: Promise.resolve({ slug: "acme" }) })
    ).rejects.toThrow("network");
  });
});
