import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { SWRConfig } from "swr";

// --- Mocks (before component imports) ---

vi.mock("server-only", () => ({}));

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

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/org/acme/matters/p1",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("@/app/(app)/org/[slug]/matters/actions", () => ({
  completeProject: vi.fn(),
  archiveProject: vi.fn(),
  reopenProject: vi.fn(),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  deleteProject: vi.fn(),
  fetchActiveCustomers: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/matters/[id]/matter-closure-actions", () => ({
  evaluateClosureAction: vi.fn(),
  closeMatterAction: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/matters/[id]/matter-reopen-actions", () => ({
  reopenMatterAction: vi.fn(),
}));

// --- Imports after mocks ---

import { MatterHeaderCard } from "@/components/projects/matter-header-card";
import { OrgProfileProvider } from "@/lib/org-profile";
import { CapabilityProvider } from "@/lib/capabilities";
import type { HeaderField } from "@/lib/constants/header-field-slugs";
import type { MatterStatus } from "@/lib/types";

afterEach(() => cleanup());

// --- Helpers ---

/**
 * Renders the header with the REAL closure / reopen / lifecycle components under the org-profile
 * and capability providers, so the Close Matter assertions prove the real gating chain
 * (matter_closure module + CLOSE_MATTER capability + status).
 */
function renderCard(status: MatterStatus, headerFields: HeaderField[] = []) {
  return render(
    <SWRConfig value={{ provider: () => new Map() }}>
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["matter_closure"]}
        terminologyNamespace={null}
      >
        <CapabilityProvider
          capabilities={["CLOSE_MATTER"]}
          role="owner"
          isAdmin={true}
          isOwner={true}
        >
          <MatterHeaderCard
            projectId="p1"
            projectName="Dlamini v Road Accident Fund"
            projectStatus={status}
            workType="LITIGATION"
            referenceNumber="RAF-2026-001"
            closedAt={status === "CLOSED" ? "2026-10-20T10:00:00Z" : null}
            customers={[{ id: "c1", name: "Sipho Ndlovu" }]}
            slug="acme"
            isAdmin={true}
            headerFields={headerFields}
          />
        </CapabilityProvider>
      </OrgProfileProvider>
    </SWRConfig>
  );
}

const RAF_FIELDS: HeaderField[] = [
  { slug: "case_number", label: "Case Number", value: "1234/2026" },
  { slug: "court_name", label: "Court", value: "Gauteng Local Division" },
  { slug: "opposing_party", label: "Opposing Party", value: "Road Accident Fund" },
];

// --- Tests ---

describe("MatterHeaderCard facts strip (G-B9)", () => {
  it("renders case number, court and opposing party in order as a dl under the title", () => {
    renderCard("ACTIVE", RAF_FIELDS);

    const strip = screen.getByTestId("matter-header-fields");
    expect(strip.tagName).toBe("DL");

    const text = strip.textContent ?? "";
    expect(text.indexOf("1234/2026")).toBeGreaterThan(-1);
    expect(text.indexOf("1234/2026")).toBeLessThan(text.indexOf("Gauteng Local Division"));
    expect(text.indexOf("Gauteng Local Division")).toBeLessThan(text.indexOf("Road Accident Fund"));
  });

  it("prefixes only the case number visibly; the other terms are screen-reader labels", () => {
    renderCard("ACTIVE", RAF_FIELDS);

    const caseNumber = screen.getByTestId("matter-header-field-case_number");
    const caseTerm = within(caseNumber).getByText("Case no.");
    expect(caseTerm.closest("dt")).not.toBeNull();
    expect(caseTerm).not.toHaveClass("sr-only");
    expect(within(caseNumber).getByText("1234/2026").tagName).toBe("DD");

    const court = screen.getByTestId("matter-header-field-court_name");
    const courtTerm = within(court).getByText("Court");
    expect(courtTerm.closest("dt")).not.toBeNull();
    expect(courtTerm).toHaveClass("sr-only");

    const opposing = screen.getByTestId("matter-header-field-opposing_party");
    expect(within(opposing).getByText("Opposing Party")).toHaveClass("sr-only");
  });

  it("separates the facts with decorative dots hidden from screen readers", () => {
    renderCard("ACTIVE", RAF_FIELDS);

    const strip = screen.getByTestId("matter-header-fields");
    const separators = within(strip).getAllByText("·");
    expect(separators).toHaveLength(2);
    separators.forEach((sep) => expect(sep).toHaveAttribute("aria-hidden", "true"));

    // The visible strip (screen-reader-only terms removed) reads in field order.
    const visible = strip.cloneNode(true) as HTMLElement;
    visible.querySelectorAll(".sr-only").forEach((node) => node.remove());
    expect(visible.textContent).toMatch(
      /^Case no\.\s*1234\/2026\s*·\s*Gauteng Local Division\s*·\s*Road Accident Fund$/
    );
  });

  it("renders no strip when no header field is set", () => {
    renderCard("ACTIVE", []);
    expect(screen.queryByTestId("matter-header-fields")).not.toBeInTheDocument();
  });
});

describe("MatterHeaderCard lifecycle actions (G-D2)", () => {
  it.each(["ACTIVE", "COMPLETED"] as const)("offers Close Matter on a %s matter", (status) => {
    renderCard(status);
    expect(screen.getByTestId("close-matter-btn")).toBeInTheDocument();
  });

  it("offers Close Matter and keeps Archive on a COMPLETED matter", () => {
    renderCard("COMPLETED");
    const actions = screen.getByTestId("header-lifecycle-actions");
    expect(within(actions).getByTestId("close-matter-btn")).toBeInTheDocument();
    expect(within(actions).getByTestId("archive-project-btn")).toBeInTheDocument();
  });

  it.each(["CLOSED", "ARCHIVED"] as const)(
    "does not offer Close Matter on a %s matter",
    (status) => {
      renderCard(status);
      expect(screen.queryByTestId("close-matter-btn")).not.toBeInTheDocument();
    }
  );

  it("offers Reopen on a CLOSED matter only", () => {
    renderCard("CLOSED");
    expect(screen.getByTestId("reopen-matter-btn")).toBeInTheDocument();
    cleanup();
    renderCard("COMPLETED");
    expect(screen.queryByTestId("reopen-matter-btn")).not.toBeInTheDocument();
  });
});
