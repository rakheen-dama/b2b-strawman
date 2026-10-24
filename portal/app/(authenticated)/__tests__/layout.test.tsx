import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

const mockReplace = vi.fn();
let mockPathname = "/invoices/123";
let mockAuthed = false;
let mockLastOrg: string | null = null;

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: mockReplace,
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  usePathname: () => mockPathname,
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({
    isAuthenticated: mockAuthed,
    isLoading: false,
    jwt: mockAuthed ? "jwt" : null,
    customer: null,
    logout: vi.fn(),
  }),
}));

vi.mock("@/hooks/use-portal-context", () => ({
  PortalContextProvider: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}));
vi.mock("@/components/portal-topbar", () => ({ PortalTopbar: () => null }));
vi.mock("@/components/portal-sidebar", () => ({
  PortalSidebar: () => null,
  PortalSidebarMobile: () => null,
}));
vi.mock("@/components/portal-footer", () => ({ PortalFooter: () => null }));

// The layout must not consult the localStorage hint (Phase 88 U11, R-2). The
// mock exists so the "hint present" case would catch a regression.
vi.mock("@/lib/auth", () => ({ getLastOrgId: () => mockLastOrg }));

import AuthenticatedLayout from "@/app/(authenticated)/layout";

/** Sets the incoming URL the layout reads and the pathname mock. */
function visit(url: string) {
  window.history.replaceState({}, "", url);
  mockPathname = new URL(url, "http://localhost").pathname;
}

function loginTarget(): URL {
  expect(mockReplace).toHaveBeenCalledTimes(1);
  const arg = mockReplace.mock.calls[0][0] as string;
  const url = new URL(arg, "http://localhost");
  expect(url.pathname).toBe("/login");
  return url;
}

describe("AuthenticatedLayout — unauthenticated redirect (Phase 88 U11)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthed = false;
    mockLastOrg = null;
    localStorage.clear();
  });

  afterEach(() => {
    cleanup();
    window.history.replaceState({}, "", "/");
  });

  it("forwards the incoming orgId to /login with redirectTo", () => {
    visit("/invoices/123?orgId=acme-legal");

    render(<AuthenticatedLayout>child</AuthenticatedLayout>);

    const target = loginTarget();
    expect(target.searchParams.get("orgId")).toBe("acme-legal");
    expect(target.searchParams.get("redirectTo")).toBe("/invoices/123");
  });

  it("adds no orgId when the incoming URL has none, even with a hint", () => {
    mockLastOrg = "hint-org";
    localStorage.setItem("portal_last_org_id", "hint-org");
    visit("/matters/42");

    render(<AuthenticatedLayout>child</AuthenticatedLayout>);

    const target = loginTarget();
    expect(target.searchParams.has("orgId")).toBe(false);
    expect(target.searchParams.get("redirectTo")).toBe("/matters/42");
  });

  it("keeps other query params in redirectTo and strips orgId from it", () => {
    visit("/settings/notifications?unsubscribe=1&orgId=x");

    render(<AuthenticatedLayout>child</AuthenticatedLayout>);

    const target = loginTarget();
    expect(target.searchParams.get("orgId")).toBe("x");
    expect(target.searchParams.get("redirectTo")).toBe(
      "/settings/notifications?unsubscribe=1",
    );
  });

  it("does not redirect an authenticated user and renders children", () => {
    mockAuthed = true;
    visit("/invoices/123?orgId=acme-legal");

    render(<AuthenticatedLayout>child content</AuthenticatedLayout>);

    expect(mockReplace).not.toHaveBeenCalled();
    expect(screen.getByText("child content")).toBeInTheDocument();
  });
});
