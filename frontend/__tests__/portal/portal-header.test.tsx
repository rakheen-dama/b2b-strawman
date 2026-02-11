import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PortalHeader } from "@/components/portal/portal-header";

// --- Mocks ---

const mockPush = vi.fn();
const mockClearPortalAuth = vi.fn();
const mockGetPortalCustomerName = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  usePathname: () => "/portal/projects",
}));

vi.mock("@/lib/portal-api", () => ({
  clearPortalAuth: () => mockClearPortalAuth(),
  getPortalCustomerName: () => mockGetPortalCustomerName(),
}));

describe("PortalHeader", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders DocTeams Portal branding and navigation", () => {
    mockGetPortalCustomerName.mockReturnValue("Acme Corp");
    render(<PortalHeader />);

    expect(screen.getByText("DocTeams Portal")).toBeInTheDocument();
    expect(screen.getByText("Projects")).toBeInTheDocument();
    expect(screen.getByText("Documents")).toBeInTheDocument();
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
  });

  it("renders without Clerk components", () => {
    mockGetPortalCustomerName.mockReturnValue(null);
    render(<PortalHeader />);

    // Should not have any Clerk-related elements
    expect(screen.queryByText("Sign in")).not.toBeInTheDocument();
    expect(screen.getByText("DocTeams Portal")).toBeInTheDocument();
  });

  it("clears auth and redirects on sign out click", async () => {
    mockGetPortalCustomerName.mockReturnValue("Test Customer");
    const user = userEvent.setup();
    render(<PortalHeader />);

    await user.click(screen.getByRole("button", { name: /Sign out/i }));

    expect(mockClearPortalAuth).toHaveBeenCalled();
    expect(mockPush).toHaveBeenCalledWith("/portal");
  });
});
