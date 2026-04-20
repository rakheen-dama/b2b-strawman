import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) =>
    (
      <a href={href} {...props}>
        {children}
      </a>
    ),
}));

const mockLogout = vi.fn();
vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    jwt: "test-jwt",
    customer: {
      id: "c",
      name: "Jane Client",
      email: "jane@example.com",
      orgId: "org_1",
    },
    logout: mockLogout,
  }),
}));

vi.mock("@/hooks/use-portal-context", () => ({
  useBranding: () => ({
    orgName: "Acme",
    logoUrl: null,
    brandColor: "#3B82F6",
    footerText: null,
    isLoading: false,
  }),
}));

import { PortalTopbar } from "@/components/portal-topbar";

describe("PortalTopbar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    cleanup();
  });

  it("invokes onHamburgerClick when the hamburger button is clicked", async () => {
    const onHamburgerClick = vi.fn();
    const user = userEvent.setup();
    render(<PortalTopbar onHamburgerClick={onHamburgerClick} />);
    await user.click(
      screen.getByRole("button", { name: /open navigation menu/i }),
    );
    expect(onHamburgerClick).toHaveBeenCalledTimes(1);
  });

  it("opens the user menu on click and exposes a Profile link to /profile", async () => {
    const user = userEvent.setup();
    render(<PortalTopbar onHamburgerClick={() => {}} />);
    await user.click(screen.getByRole("button", { name: /user menu/i }));
    const profileLink = await screen.findByRole("menuitem", {
      name: /profile/i,
    });
    expect(profileLink).toHaveAttribute("href", "/profile");
  });

  it("invokes useAuth().logout when the Logout menu item is selected", async () => {
    const user = userEvent.setup();
    render(<PortalTopbar onHamburgerClick={() => {}} />);
    await user.click(screen.getByRole("button", { name: /user menu/i }));
    const logoutItem = await screen.findByRole("menuitem", {
      name: /logout/i,
    });
    await user.click(logoutItem);
    expect(mockLogout).toHaveBeenCalledTimes(1);
  });
});
