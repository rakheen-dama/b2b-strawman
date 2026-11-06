import { describe, it, expect, vi, afterEach } from "vitest";
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

// Only the trust tile (RecentInvoicesCard always mounts and gets []).
vi.mock("@/hooks/use-portal-context", () => ({
  useModules: () => ["trust_accounting"],
}));

import HomePage from "@/app/(authenticated)/home/page";

describe("Home trust tile — G89-F39", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows the movement's transaction date, not the day it was recorded", async () => {
    mockPortalGet.mockImplementation((path: string) =>
      path === "/portal/trust/movements?limit=1"
        ? Promise.resolve([
            {
              id: "m-1",
              type: "DEPOSIT",
              amount: 5000,
              currency: "ZAR",
              // Back-dated deposit: dated 1 Oct, recorded on 7 Nov.
              transactionDate: "2026-10-01",
              occurredAt: "2026-11-07T08:00:00Z",
              matterName: "Estate late M Dlamini",
              description: "Deposit",
            },
          ])
        : Promise.resolve([]),
    );

    render(<HomePage />);

    const tile = await waitFor(() => {
      const el = screen.getByText("Last trust movement").closest("a");
      if (!el?.textContent?.includes("R")) throw new Error("still loading");
      return el;
    });
    expect(tile.textContent).toContain("01 Oct 2026");
    expect(tile.textContent).not.toContain("Nov 2026");
  });
});
