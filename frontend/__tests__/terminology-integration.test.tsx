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
import { OrgProfileProvider } from "@/lib/org-profile";
import { NavZone } from "@/components/nav-zone";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { TerminologyHeading } from "@/components/terminology-heading";
import { TerminologyText } from "@/components/terminology-text";
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
      <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
        <CapabilityProvider
          capabilities={[]}
          role="owner"
          isAdmin={false}
          isOwner={true}
        >
          <TerminologyProvider verticalProfile="accounting-za">
            <NavZone zone={deliveryZone} slug="test-org" />
          </TerminologyProvider>
        </CapabilityProvider>
      </OrgProfileProvider>,
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

  it("renders Clients instead of Customers in breadcrumb for /customers route", () => {
    mockUsePathname.mockReturnValue("/org/test-org/customers");

    render(
      <TerminologyProvider verticalProfile="accounting-za">
        <Breadcrumbs slug="test-org" />
      </TerminologyProvider>,
    );

    expect(screen.getByText("Clients")).toBeInTheDocument();
    expect(screen.queryByText("Customers")).not.toBeInTheDocument();
  });

  it("renders Engagement Letters instead of Proposals in breadcrumb for /proposals route", () => {
    mockUsePathname.mockReturnValue("/org/test-org/proposals");

    render(
      <TerminologyProvider verticalProfile="accounting-za">
        <Breadcrumbs slug="test-org" />
      </TerminologyProvider>,
    );

    expect(screen.getByText("Engagement Letters")).toBeInTheDocument();
    expect(screen.queryByText("Proposals")).not.toBeInTheDocument();
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

// ---- Test 4: TerminologyHeading with count prop ----

describe("364B: terminology heading with count", () => {
  it("renders '3 engagements' for count=3 with accounting-za profile", () => {
    render(
      <TerminologyProvider verticalProfile="accounting-za">
        <TerminologyHeading count={3} term="projects" singularTerm="project" />
      </TerminologyProvider>,
    );

    expect(screen.getByText("3 engagements")).toBeInTheDocument();
  });

  it("renders '1 engagement' for count=1 with accounting-za profile", () => {
    render(
      <TerminologyProvider verticalProfile="accounting-za">
        <TerminologyHeading count={1} term="projects" singularTerm="project" />
      </TerminologyProvider>,
    );

    expect(screen.getByText("1 engagement")).toBeInTheDocument();
  });

  it("renders '3 projects' for count=3 with null profile", () => {
    render(
      <TerminologyProvider verticalProfile={null}>
        <TerminologyHeading count={3} term="projects" singularTerm="project" />
      </TerminologyProvider>,
    );

    expect(screen.getByText("3 projects")).toBeInTheDocument();
  });
});

// ---- Test 5: TerminologyText with template placeholders ----

describe("364B: terminology text template replacement", () => {
  it("replaces {proposals} in template with 'engagement letters' for accounting-za", () => {
    render(
      <TerminologyProvider verticalProfile="accounting-za">
        <TerminologyText template="No {proposals} yet" />
      </TerminologyProvider>,
    );

    expect(screen.getByText("No engagement letters yet")).toBeInTheDocument();
  });

  it("replaces {proposal} in template with 'engagement letter' for accounting-za", () => {
    render(
      <TerminologyProvider verticalProfile="accounting-za">
        <TerminologyText template="Create a {proposal} to get started." />
      </TerminologyProvider>,
    );

    expect(screen.getByText("Create a engagement letter to get started.")).toBeInTheDocument();
  });

  it("passes through template unchanged with null profile", () => {
    render(
      <TerminologyProvider verticalProfile={null}>
        <TerminologyText template="No {proposals} yet" />
      </TerminologyProvider>,
    );

    expect(screen.getByText("No proposals yet")).toBeInTheDocument();
  });
});
