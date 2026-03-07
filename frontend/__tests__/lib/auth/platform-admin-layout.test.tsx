import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";

// Mock next/navigation
const mockNotFound = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    mockNotFound();
    throw new Error("NEXT_NOT_FOUND");
  },
}));

// Mock @/lib/auth
const mockGetAuthContext = vi.fn();
vi.mock("@/lib/auth", () => ({
  getAuthContext: () => mockGetAuthContext(),
}));

import PlatformAdminLayout from "@/app/(app)/platform-admin/layout";

describe("PlatformAdminLayout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders children when user has platform-admins group", async () => {
    mockGetAuthContext.mockResolvedValue({
      orgId: "org_1",
      orgSlug: "acme",
      orgRole: "org:owner",
      userId: "user_1",
      groups: ["platform-admins"],
    });

    const Layout = await PlatformAdminLayout({
      children: <div data-testid="child">Admin Content</div>,
    });

    render(Layout);
    expect(screen.getByTestId("child")).toBeInTheDocument();
    expect(mockNotFound).not.toHaveBeenCalled();
  });

  it("calls notFound when user lacks platform-admins group", async () => {
    mockGetAuthContext.mockResolvedValue({
      orgId: "org_1",
      orgSlug: "acme",
      orgRole: "org:owner",
      userId: "user_1",
      groups: [],
    });

    await expect(
      PlatformAdminLayout({
        children: <div>Admin Content</div>,
      }),
    ).rejects.toThrow("NEXT_NOT_FOUND");

    expect(mockNotFound).toHaveBeenCalled();
  });

  it("calls notFound when getAuthContext throws", async () => {
    mockGetAuthContext.mockRejectedValue(new Error("No auth"));

    await expect(
      PlatformAdminLayout({
        children: <div>Admin Content</div>,
      }),
    ).rejects.toThrow("NEXT_NOT_FOUND");

    expect(mockNotFound).toHaveBeenCalled();
  });
});
