import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { mockSetPaletteOpen } = vi.hoisted(() => ({ mockSetPaletteOpen: vi.fn() }));

vi.mock("@/hooks/use-mobile", () => ({ useIsMobile: () => false }));

vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/matters"),
  useRouter: vi.fn(() => ({ push: vi.fn() })),
}));

vi.mock("@/components/command-palette-provider", () => ({
  useCommandPalette: () => ({ open: false, setOpen: mockSetPaletteOpen }),
}));

// The bell polls the API and the user menus read auth; stand-ins mark their slots.
vi.mock("@/components/notifications/notification-bell", () => ({
  NotificationBell: ({ orgSlug }: { orgSlug: string }) => (
    <div data-testid="notification-bell-slot" data-org-slug={orgSlug} />
  ),
}));
vi.mock("@/components/auth-header-controls", () => ({
  AuthHeaderControls: () => <div data-testid="auth-header-controls-slot" />,
}));

import { SidebarProvider } from "@/components/ui/sidebar";
import { AppHeader } from "@/components/shell/app-header";

afterEach(() => {
  cleanup();
  mockSetPaletteOpen.mockClear();
});

function renderHeader() {
  return render(
    <SidebarProvider>
      <AppHeader slug="test-org" orgName="Mathebula & Partners" />
    </SidebarProvider>
  );
}

describe("AppHeader", () => {
  it("renders one banner holding the single Toggle menu trigger and the breadcrumb trail", () => {
    renderHeader();
    const banner = screen.getByRole("banner");
    expect(within(banner).getAllByRole("button", { name: "Toggle menu" })).toHaveLength(1);
    expect(screen.getAllByRole("button", { name: "Toggle menu" })).toHaveLength(1);
    const crumbs = within(banner).getByRole("navigation", { name: "Breadcrumb" });
    expect(within(crumbs).getByRole("link", { name: "Mathebula & Partners" })).toHaveAttribute(
      "href",
      "/org/test-org/dashboard"
    );
    expect(within(crumbs).getByText("Matters")).toBeInTheDocument();
  });

  it('names the palette trigger "Search, Command K" and shows the ⌘K hint — never a bare "Search" button', () => {
    renderHeader();
    const search = screen.getByRole("button", { name: "Search, Command K" });
    expect(search).toHaveTextContent("Search");
    expect(search.querySelector("kbd")).toHaveTextContent("⌘K");
    expect(search).toHaveAttribute("aria-keyshortcuts", "Meta+K Control+K");
    // day-07-14-work-trust.spec.ts:226 / day-30-billing.spec.ts:240 click an exact "Search" button.
    expect(screen.queryByRole("button", { name: "Search" })).not.toBeInTheDocument();
  });

  it("opens the command palette from the Search button", async () => {
    renderHeader();
    await userEvent.setup().click(screen.getByRole("button", { name: "Search, Command K" }));
    expect(mockSetPaletteOpen).toHaveBeenCalledWith(true);
  });

  it("hosts the notification bell (for this org) and the user menu controls", () => {
    renderHeader();
    const banner = screen.getByRole("banner");
    expect(within(banner).getByTestId("notification-bell-slot")).toHaveAttribute(
      "data-org-slug",
      "test-org"
    );
    expect(within(banner).getByTestId("auth-header-controls-slot")).toBeInTheDocument();
  });

  it("toggles the desktop sidebar from the Toggle menu trigger (collapse cookie written)", async () => {
    renderHeader();
    await userEvent.setup().click(screen.getByRole("button", { name: "Toggle menu" }));
    expect(document.cookie).toContain("sidebar_state=false");
  });
});
