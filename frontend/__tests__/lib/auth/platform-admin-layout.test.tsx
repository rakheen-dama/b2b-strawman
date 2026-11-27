import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

// Mock next/navigation
const mockNotFound = vi.fn();
vi.mock("next/navigation", () => ({
  notFound: () => {
    mockNotFound();
    throw new Error("NEXT_NOT_FOUND");
  },
  usePathname: () => "/platform-admin/billing",
}));

// Mock @/lib/auth
const mockGetSessionIdentity = vi.fn();
vi.mock("@/lib/auth", () => ({
  getSessionIdentity: () => mockGetSessionIdentity(),
}));

import PlatformAdminLayout from "@/app/(app)/platform-admin/layout";

describe("PlatformAdminLayout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => cleanup());

  it("renders children when user has platform-admins group", async () => {
    mockGetSessionIdentity.mockResolvedValue({
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

  it("renders the children inside the one main#main-content landmark (K-98)", async () => {
    mockGetSessionIdentity.mockResolvedValue({ userId: "user_1", groups: ["platform-admins"] });

    const { container } = render(
      await PlatformAdminLayout({ children: <div data-testid="child">Admin Content</div> })
    );

    const mains = container.querySelectorAll("main");
    expect(mains).toHaveLength(1);
    expect(mains[0]).toHaveAttribute("id", "main-content");
    expect(within(mains[0]).getByTestId("child")).toBeInTheDocument();
    // The nav sits outside <main>, above it.
    expect(within(mains[0]).queryByRole("navigation")).toBeNull();
  });

  it("marks the current page's nav link with aria-current (K-98)", async () => {
    mockGetSessionIdentity.mockResolvedValue({ userId: "user_1", groups: ["platform-admins"] });

    render(await PlatformAdminLayout({ children: <div>Admin Content</div> }));

    const nav = screen.getByRole("navigation");
    expect(
      within(nav)
        .getAllByRole("link")
        .map((l) => l.textContent)
    ).toEqual(["Access Requests", "Billing", "Demo"]);
    expect(within(nav).getByRole("link", { name: "Billing" })).toHaveAttribute(
      "aria-current",
      "page"
    );
    expect(within(nav).getByRole("link", { name: "Billing" })).toHaveAttribute(
      "href",
      "/platform-admin/billing"
    );
    expect(within(nav).getByRole("link", { name: "Access Requests" })).not.toHaveAttribute(
      "aria-current"
    );
    expect(within(nav).getByRole("link", { name: "Demo" })).not.toHaveAttribute("aria-current");
  });

  it("calls notFound when user lacks platform-admins group", async () => {
    mockGetSessionIdentity.mockResolvedValue({
      userId: "user_1",
      groups: [],
    });

    await expect(
      PlatformAdminLayout({
        children: <div>Admin Content</div>,
      })
    ).rejects.toThrow("NEXT_NOT_FOUND");

    expect(mockNotFound).toHaveBeenCalled();
  });

  it("calls notFound when getSessionIdentity throws", async () => {
    mockGetSessionIdentity.mockRejectedValue(new Error("No auth"));

    await expect(
      PlatformAdminLayout({
        children: <div>Admin Content</div>,
      })
    ).rejects.toThrow("NEXT_NOT_FOUND");

    expect(mockNotFound).toHaveBeenCalled();
  });
});
