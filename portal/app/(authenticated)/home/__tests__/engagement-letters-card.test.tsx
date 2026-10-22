import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/home",
}));

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const mockPortalGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (path: string) => mockPortalGet(path),
}));

// Only document_acceptance enabled → the engagement-letters tile renders (page.test.tsx
// mocks information_requests at module level, so this tile needs its own file).
vi.mock("@/hooks/use-portal-context", () => ({
  useModules: () => ["document_acceptance"],
}));

import HomePage from "@/app/(authenticated)/home/page";

// Phase 87 S10 (617A, OQ #5): there is no /acceptance page. The Home tile keeps its
// count shape, is titled "Engagement letters", and opens the list on /matters.
describe("Home — engagement letters tile", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPortalGet.mockImplementation((path: string) =>
      path === "/portal/acceptance-requests/pending"
        ? Promise.resolve([
            {
              id: "acc-1",
              documentTitle: "Engagement Letter — Litigation",
              requestToken: "token-1",
              sentAt: "2026-02-20T10:00:00Z",
              expiresAt: "2026-03-20T10:00:00Z",
              status: "SENT",
            },
          ])
        : Promise.resolve([]),
    );
  });

  afterEach(() => {
    cleanup();
  });

  it("is titled 'Engagement letters' and links to the list on /matters", async () => {
    render(<HomePage />);
    const link = screen.getByText("Engagement letters").closest("a");
    expect(link).toHaveAttribute("href", "/matters#engagement-letters");
    expect(screen.queryByText("Pending acceptances")).not.toBeInTheDocument();
    expect(
      document.querySelector('a[href="/acceptance"]'),
    ).not.toBeInTheDocument();
    await waitFor(() => {
      expect(link?.querySelector("p.font-mono")?.textContent).toBe("1");
    });
  });
});
