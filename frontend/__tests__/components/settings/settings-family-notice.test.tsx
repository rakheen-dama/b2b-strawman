import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: async () => ({
    capabilities: [],
    role: "member",
    isAdmin: false,
    isOwner: false,
  }),
}));
vi.mock("@/lib/api/settings", () => ({ getOrgSettings: async () => null }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/firm",
}));

import {
  renderSettingsFamilyPage,
  type SettingsTabLoaders,
} from "@/components/settings/settings-family-page";

// Phase 91 664A (K-12): the forbidden-tab notice is on theme tokens, so it follows light and dark
// without a hard-coded slate pair. Its text and role="status" are pinned in firm-family.test.tsx.

const loaders: SettingsTabLoaders<"firm"> = {
  general: async () => "general panel",
  roles: async () => "roles panel",
  "audit-trail": async () => "audit panel",
  "data-protection": async () => "data protection panel",
  "data-requests": async () => "data requests panel",
  compliance: async () => "compliance panel",
  features: async () => "features panel",
  subscription: async () => "subscription panel",
};

afterEach(() => {
  cleanup();
});

describe("settings forbidden-tab notice (K-12)", () => {
  it("uses the muted token pair and no raw slate or dark: classes", async () => {
    render(
      await renderSettingsFamilyPage("firm", loaders, {
        params: Promise.resolve({ slug: "acme" }),
        searchParams: Promise.resolve({ tab: "roles" }),
      })
    );
    const notice = screen.getByRole("status");
    expect(notice).toHaveTextContent(/don.t have access to Roles — showing General instead/);
    expect(notice).toHaveClass("bg-muted", "text-muted-foreground", "border-border");
    expect(notice.className).not.toMatch(/slate-|dark:/);
  });
});
