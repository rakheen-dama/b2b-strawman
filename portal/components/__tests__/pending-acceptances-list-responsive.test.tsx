import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    className,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    className?: string;
    [key: string]: unknown;
  }) => (
    <a href={href} className={className} {...props}>
      {children}
    </a>
  ),
}));

// Mock api-client
const mockPortalGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
}));

import { PendingAcceptancesList } from "@/components/pending-acceptances-list";

describe("PendingAcceptancesList responsive layout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("each row uses stacked-on-mobile, inline-on-sm layout", async () => {
    mockPortalGet.mockResolvedValue([
      {
        id: "acc-1",
        documentTitle: "Service Agreement 2026",
        requestToken: "token-abc-123",
        sentAt: "2026-02-20T10:00:00Z",
        expiresAt: "2026-03-20T10:00:00Z",
        status: "SENT",
      },
    ]);

    render(<PendingAcceptancesList />);

    await waitFor(() => {
      expect(screen.getByText("Service Agreement 2026")).toBeInTheDocument();
    });

    // The row wrapper should flip from flex-col to flex-row at sm breakpoint.
    const row = screen.getByText("Service Agreement 2026").closest(
      'div[class*="flex-col"]',
    ) as HTMLElement | null;
    expect(row).not.toBeNull();
    expect(row!.className).toContain("flex-col");
    expect(row!.className).toContain("sm:flex-row");
  });

  it("Review & Accept link is full-width on mobile and auto on sm+", async () => {
    mockPortalGet.mockResolvedValue([
      {
        id: "acc-1",
        documentTitle: "Service Agreement 2026",
        requestToken: "token-abc-123",
        sentAt: "2026-02-20T10:00:00Z",
        expiresAt: "2026-03-20T10:00:00Z",
        status: "SENT",
      },
    ]);

    render(<PendingAcceptancesList />);

    await waitFor(() => {
      expect(screen.getByText("Review & Accept")).toBeInTheDocument();
    });

    const link = screen.getByText("Review & Accept").closest("a");
    expect(link).not.toBeNull();
    expect(link!.className).toContain("w-full");
    expect(link!.className).toContain("sm:w-auto");
    expect(link!.className).toContain("min-h-11");
  });
});
