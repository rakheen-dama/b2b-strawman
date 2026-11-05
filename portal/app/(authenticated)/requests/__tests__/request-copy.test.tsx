import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { Suspense } from "react";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 89 633C (G88-F29, G88-F30): the portal request list / detail print client copy, never the
// backend enum ("SENT", "IN_PROGRESS", "SUBMITTED"), and a client-level request (no matter) gets a
// heading from its request number instead of an empty h1.

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

const clientLevel = {
  id: "r-7",
  requestNumber: "REQ-0007",
  status: "IN_PROGRESS",
  matterId: null,
  matterName: null,
  totalItems: 1,
  submittedItems: 1,
  acceptedItems: 0,
  rejectedItems: 0,
  sentAt: "2026-10-01T08:00:00Z",
  completedAt: null,
};

function fulfilled<T>(value: T): Promise<T> {
  // React 19 use() reads status/value synchronously from a tracked thenable.
  return Object.assign(Promise.resolve(value), { status: "fulfilled", value });
}

describe("portal request copy (Phase 89 633C, G88-F29/F30)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("detail h1 falls back to the request number for a client-level request (G88-F30)", async () => {
    mockPortalGet.mockResolvedValue({
      ...clientLevel,
      items: [
        {
          id: "i-1",
          name: "Certified ID copy",
          description: null,
          responseType: "FILE_UPLOAD",
          required: true,
          fileTypeHints: null,
          sortOrder: 0,
          status: "SUBMITTED",
          rejectionReason: null,
          documentId: "d-1",
          textResponse: null,
        },
      ],
    });

    render(
      <Suspense fallback={null}>
        <RequestDetailPage params={fulfilled({ id: "r-7" })} />
      </Suspense>,
    );

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Request REQ-0007",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("1/1 submitted • In progress")).toBeInTheDocument();
    // Item label (G88-F29).
    expect(screen.getByText("Submitted")).toBeInTheDocument();
    expect(screen.queryByText(/IN_PROGRESS|SUBMITTED/)).toBeNull();
  });

  it("request list shows the status label, not the enum (G88-F29)", async () => {
    mockPortalGet.mockResolvedValue([
      { ...clientLevel, status: "SENT", submittedItems: 0 },
    ]);

    render(<RequestsPage />);

    // D1 — SENT reads "Requested" (scenario P-0.9a).
    expect(await screen.findByText("Requested")).toBeInTheDocument();
    expect(screen.queryByText("SENT")).toBeNull();
    // D3 — the list title falls back to the request number too (G88-F30).
    expect(screen.getByText("Request REQ-0007")).toBeInTheDocument();
  });
});
