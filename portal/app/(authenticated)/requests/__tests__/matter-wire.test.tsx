import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { Suspense } from "react";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 87 S3 Batch 5 (ADR-337, Epic 609G): guards the portal callers of the 609F-renamed
// portal API (PR #1603). PortalRequestListResponse / PortalRequestDetailResponse renamed
// projectId/projectName → matterId/matterName; a stale read renders a blank title SILENTLY.

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/requests",
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
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
  portalPost: vi.fn(),
  portalFetch: vi.fn(),
}));

import RequestsPage from "@/app/(authenticated)/requests/page";
import RequestDetailPage from "@/app/(authenticated)/requests/[id]/page";

const request = {
  id: "r-1",
  requestNumber: "REQ-0001",
  status: "SENT",
  matterId: "m-1",
  matterName: "Estate Late J Mokoena",
  totalItems: 2,
  submittedItems: 0,
  acceptedItems: 0,
  rejectedItems: 0,
  sentAt: "2026-10-01T08:00:00Z",
  completedAt: null,
};

function fulfilled<T>(value: T): Promise<T> {
  // React 19 use() reads status/value synchronously from a tracked thenable.
  return Object.assign(Promise.resolve(value), { status: "fulfilled", value });
}

describe("portal requests on the matter wire (Phase 87 S3, 609G)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("request list renders matterName", async () => {
    mockPortalGet.mockResolvedValue([request]);

    render(<RequestsPage />);

    expect(
      await screen.findByText("Estate Late J Mokoena"),
    ).toBeInTheDocument();
    expect(mockPortalGet).toHaveBeenCalledWith("/portal/requests");
  });

  it("request detail heading renders matterName", async () => {
    mockPortalGet.mockResolvedValue({ ...request, items: [] });

    render(
      <Suspense fallback={null}>
        <RequestDetailPage params={fulfilled({ id: "r-1" })} />
      </Suspense>,
    );

    expect(
      await screen.findByRole("heading", { name: "Estate Late J Mokoena" }),
    ).toBeInTheDocument();
    expect(mockPortalGet).toHaveBeenCalledWith("/portal/requests/r-1");
  });
});
