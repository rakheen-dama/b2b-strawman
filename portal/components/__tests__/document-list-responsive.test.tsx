import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render } from "@testing-library/react";

// Mock api-client (DocumentList triggers presign-download on button click — not used here,
// but the module is imported, so we stub it for consistency).
const mockPortalGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
}));

import { DocumentList } from "@/components/document-list";

const docs = [
  {
    id: "doc-1",
    fileName: "report.pdf",
    contentType: "application/pdf",
    size: 1024,
    scope: "PROJECT",
    status: "ACTIVE",
    createdAt: "2026-02-01T10:00:00Z",
  },
  {
    id: "doc-2",
    fileName: "photo.jpg",
    contentType: "image/jpeg",
    size: 2048,
    scope: "PROJECT",
    status: "ACTIVE",
    createdAt: "2026-01-15T08:00:00Z",
  },
];

describe("DocumentList responsive layout (regression)", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders both mobile card container and desktop table container", () => {
    const { container } = render(<DocumentList documents={docs} />);

    const mobileContainer = container.querySelector(
      "div.flex.flex-col.gap-3.md\\:hidden",
    );
    const desktopContainer = container.querySelector(
      "div.hidden.md\\:block",
    );

    expect(mobileContainer).not.toBeNull();
    expect(desktopContainer).not.toBeNull();
  });

  it("mobile container has md:hidden class", () => {
    const { container } = render(<DocumentList documents={docs} />);
    const mobileContainer = container.querySelector(
      "div.flex.flex-col.gap-3.md\\:hidden",
    );
    expect(mobileContainer?.className).toContain("md:hidden");
  });

  it("desktop container has `hidden md:block` classes", () => {
    const { container } = render(<DocumentList documents={docs} />);
    const desktopContainer = container.querySelector("div.hidden.md\\:block");
    expect(desktopContainer?.className).toContain("hidden");
    expect(desktopContainer?.className).toContain("md:block");
  });

  it("desktop container renders a table element", () => {
    const { container } = render(<DocumentList documents={docs} />);
    const desktopContainer = container.querySelector("div.hidden.md\\:block");
    expect(desktopContainer?.querySelector("table")).not.toBeNull();
  });

  it("empty state renders no mobile/desktop variant containers", () => {
    const { container } = render(<DocumentList documents={[]} />);

    const mobileContainer = container.querySelector(
      "div.flex.flex-col.gap-3.md\\:hidden",
    );
    const desktopContainer = container.querySelector(
      "div.hidden.md\\:block",
    );

    expect(mobileContainer).toBeNull();
    expect(desktopContainer).toBeNull();
  });
});
