import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/home",
}));

// Mock next/link
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

// Mock api-client — single fn the test controls per call
const mockPortalGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (path: string) => mockPortalGet(path),
}));

// Mock portal context to enable only the information_requests module so
// only InfoRequestsCard renders. This keeps the test focused.
vi.mock("@/hooks/use-portal-context", () => ({
  useModules: () => ["information_requests"],
}));

// Mock terminology hook — RecentInvoicesCard uses it but is hidden behind
// a non-existent module gate; with information_requests-only modules it
// won't render. Still safer to provide a stub.
vi.mock("@/lib/terminology", () => ({
  useTerminology: () => ({ t: (k: string) => k }),
}));

import HomePage from "@/app/(authenticated)/home/page";

type Req = {
  id: string;
  status: string;
  totalItems: number;
  submittedItems: number;
};

function setRequestsList(list: Req[] | Error) {
  mockPortalGet.mockImplementation((path: string) => {
    if (path === "/portal/requests") {
      return list instanceof Error ? Promise.reject(list) : Promise.resolve(list);
    }
    // RecentInvoicesCard always renders — give it an empty list so it doesn't
    // throw, even though we don't assert on it.
    return Promise.resolve([]);
  });
}

async function renderHomeAndReadCount(): Promise<string> {
  render(<HomePage />);
  // Wait for the dashes ("--") loading placeholder to be replaced with a number
  // (or "0" for empty/error states).
  const tile = await waitFor(() => {
    const el = screen
      .getByText("Pending info requests")
      .closest("a")
      ?.querySelector("p.font-mono");
    if (!el) throw new Error("count element not found");
    if (el.textContent === "--") throw new Error("still loading");
    return el;
  });
  return tile.textContent ?? "";
}

describe("InfoRequestsCard — pending count semantics (GAP-L-92)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it("empty list renders 0", async () => {
    setRequestsList([]);
    expect(await renderHomeAndReadCount()).toBe("0");
  });

  it("counts SENT request with 0/3 submitted", async () => {
    setRequestsList([
      {
        id: "r1",
        status: "SENT",
        totalItems: 3,
        submittedItems: 0,
      },
    ]);
    expect(await renderHomeAndReadCount()).toBe("1");
  });

  it("counts IN_PROGRESS with 2/3 submitted (partial)", async () => {
    setRequestsList([
      {
        id: "r1",
        status: "IN_PROGRESS",
        totalItems: 3,
        submittedItems: 2,
      },
    ]);
    expect(await renderHomeAndReadCount()).toBe("1");
  });

  it("does NOT count IN_PROGRESS with 3/3 submitted (waiting on firm)", async () => {
    setRequestsList([
      {
        id: "r1",
        status: "IN_PROGRESS",
        totalItems: 3,
        submittedItems: 3,
      },
    ]);
    expect(await renderHomeAndReadCount()).toBe("0");
  });

  it("does NOT count COMPLETED requests", async () => {
    setRequestsList([
      {
        id: "r1",
        status: "COMPLETED",
        totalItems: 3,
        submittedItems: 3,
      },
      {
        id: "r2",
        status: "COMPLETED",
        totalItems: 3,
        submittedItems: 0,
      },
    ]);
    expect(await renderHomeAndReadCount()).toBe("0");
  });

  it("mixed scenario — Day 46 cycle-42: SENT 0/3 + IN_PROGRESS 2/2 + SENT 0/3 + COMPLETED → 2", async () => {
    setRequestsList([
      // REQ-0001 SENT 0/3 — counts
      { id: "REQ-0001", status: "SENT", totalItems: 3, submittedItems: 0 },
      // REQ-0004 SENT 0/3 — counts
      { id: "REQ-0004", status: "SENT", totalItems: 3, submittedItems: 0 },
      // REQ-0005 IN_PROGRESS 2/2 — Sipho done, does NOT count (the regression)
      {
        id: "REQ-0005",
        status: "IN_PROGRESS",
        totalItems: 2,
        submittedItems: 2,
      },
      // REQ-0002 COMPLETED — does NOT count
      {
        id: "REQ-0002",
        status: "COMPLETED",
        totalItems: 3,
        submittedItems: 0,
      },
    ]);
    expect(await renderHomeAndReadCount()).toBe("2");
  });

  it("network error renders 0", async () => {
    setRequestsList(new Error("boom"));
    expect(await renderHomeAndReadCount()).toBe("0");
  });
});
