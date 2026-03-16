import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { vi } from "vitest";

// --- Mocks ---

vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/projects"),
}));

vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.ComponentProps<"div">) => (
      <div {...props}>{children}</div>
    ),
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}));

import { usePathname } from "next/navigation";
import { TerminologyProvider } from "@/lib/terminology";
import { CapabilityProvider } from "@/lib/capabilities";
import { NavZone } from "@/components/nav-zone";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { TerminologyHeading } from "@/components/terminology-heading";
import { FolderOpen } from "lucide-react";
import type { NavGroup } from "@/lib/nav-items";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

// ---- Test 1: Sidebar nav labels ----

describe("364B: terminology in sidebar nav", () => {
  it("renders Engagements instead of Projects with accounting-za profile", () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");

    const deliveryZone: NavGroup = {
      id: "delivery",
      label: "Delivery",
      defaultExpanded: true,
      items: [
        {
          label: "Projects",
          href: (slug) => `/org/${slug}/projects`,
          icon: FolderOpen,
        },
      ],
    };

    render(
      <CapabilityProvider
        capabilities={[]}
        role="owner"
        isAdmin={false}
        isOwner={true}
      >
        <TerminologyProvider verticalProfile="accounting-za">
          <NavZone zone={deliveryZone} slug="test-org" />
        </TerminologyProvider>
      </CapabilityProvider>,
    );

    expect(screen.getByText("Engagements")).toBeInTheDocument();
    expect(screen.queryByText("Projects")).not.toBeInTheDocument();
  });
});

// ---- Test 2: Breadcrumbs ----

describe("364B: terminology in breadcrumbs", () => {
  it("renders Engagements instead of Projects in breadcrumb for /projects route", () => {
    mockUsePathname.mockReturnValue("/org/test-org/projects");

    render(
      <TerminologyProvider verticalProfile="accounting-za">
        <Breadcrumbs slug="test-org" />
      </TerminologyProvider>,
    );

    expect(screen.getByText("Engagements")).toBeInTheDocument();
    expect(screen.queryByText("Projects")).not.toBeInTheDocument();
  });
});

// ---- Test 3: Page heading via TerminologyHeading ----

describe("364B: terminology in page heading component", () => {
  it("renders Engagements instead of Projects for term=Projects with accounting-za profile", () => {
    render(
      <TerminologyProvider verticalProfile="accounting-za">
        <h1>
          <TerminologyHeading term="Projects" />
        </h1>
      </TerminologyProvider>,
    );

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Engagements");
  });
});
