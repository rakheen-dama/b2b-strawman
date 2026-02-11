import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VisibilityToggle } from "@/components/documents/visibility-toggle";

// --- Mocks ---

const mockToggleVisibility = vi.fn();
const mockRefresh = vi.fn();

vi.mock("@/app/(app)/org/[slug]/customers/[id]/actions", () => ({
  toggleDocumentVisibility: (...args: unknown[]) => mockToggleVisibility(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: mockRefresh,
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

describe("VisibilityToggle", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockToggleVisibility.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
  });

  // Internal visibility renders Lock icon and "Internal" label
  it("renders Internal label with lock icon for INTERNAL visibility", () => {
    render(
      <VisibilityToggle
        documentId="doc1"
        visibility="INTERNAL"
        slug="acme"
        customerId="c1"
      />
    );

    const button = screen.getByRole("button", { name: /Internal/i });
    expect(button).toBeInTheDocument();
    expect(button).toHaveAttribute(
      "title",
      "Internal only. Click to share with customer."
    );
  });

  // Shared visibility renders Globe icon and "Shared" label
  it("renders Shared label with globe icon for SHARED visibility", () => {
    render(
      <VisibilityToggle
        documentId="doc1"
        visibility="SHARED"
        slug="acme"
        customerId="c1"
      />
    );

    const button = screen.getByRole("button", { name: /Shared/i });
    expect(button).toBeInTheDocument();
    expect(button).toHaveAttribute(
      "title",
      "Visible to customer portal. Click to make internal only."
    );
  });

  // Clicking Internal toggles to SHARED
  it("calls toggleDocumentVisibility with SHARED when clicking Internal toggle", async () => {
    const user = userEvent.setup();

    render(
      <VisibilityToggle
        documentId="doc1"
        visibility="INTERNAL"
        slug="acme"
        customerId="c1"
      />
    );

    await user.click(screen.getByRole("button", { name: /Internal/i }));

    await waitFor(() => {
      expect(mockToggleVisibility).toHaveBeenCalledWith("acme", "c1", "doc1", "SHARED");
    });
  });

  // Clicking Shared toggles to INTERNAL
  it("calls toggleDocumentVisibility with INTERNAL when clicking Shared toggle", async () => {
    const user = userEvent.setup();

    render(
      <VisibilityToggle
        documentId="doc1"
        visibility="SHARED"
        slug="acme"
        customerId="c1"
      />
    );

    await user.click(screen.getByRole("button", { name: /Shared/i }));

    await waitFor(() => {
      expect(mockToggleVisibility).toHaveBeenCalledWith("acme", "c1", "doc1", "INTERNAL");
    });
  });

  // Button is disabled when disabled prop is true
  it("disables the button when disabled prop is true", () => {
    render(
      <VisibilityToggle
        documentId="doc1"
        visibility="INTERNAL"
        slug="acme"
        customerId="c1"
        disabled={true}
      />
    );

    expect(screen.getByRole("button", { name: /Internal/i })).toBeDisabled();
  });

  // Successful toggle calls router.refresh
  it("calls router.refresh after successful toggle", async () => {
    const user = userEvent.setup();

    render(
      <VisibilityToggle
        documentId="doc1"
        visibility="INTERNAL"
        slug="acme"
        customerId="c1"
      />
    );

    await user.click(screen.getByRole("button", { name: /Internal/i }));

    await waitFor(() => {
      expect(mockRefresh).toHaveBeenCalled();
    });
  });
});
