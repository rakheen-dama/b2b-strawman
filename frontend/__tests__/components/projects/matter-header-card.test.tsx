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
import type { HeroTile } from "@/components/shared/detail-hero";
import { OrgProfileProvider } from "@/lib/org-profile";
import { CapabilityProvider } from "@/lib/capabilities";
import type { HeaderField } from "@/lib/constants/header-field-slugs";
import type { MatterStatus } from "@/lib/types";

afterEach(() => cleanup());

// --- Helpers ---

type CardOverrides = Partial<React.ComponentProps<typeof MatterHeaderCard>>;

/**
 * Renders the header with the REAL closure / reopen / lifecycle components under the org-profile
 * and capability providers, so the Close Matter assertions prove the real gating chain
 * (matter_closure module + CLOSE_MATTER capability + status). The hero is the real DetailHero
 * with real compact StatCards (641B).
 */
function renderCard(
  status: MatterStatus,
  headerFields: HeaderField[] = [],
  overrides: CardOverrides = {}
) {
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
            {...overrides}
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

// --- 641B: composed on DetailHero (ADR-373, §15.6.6 selectors) ---

const TILES: HeroTile[] = [
  {
    id: "wip",
    label: "Unbilled WIP",
    value: "R 12 500,00",
    hint: "8,5h · 6 entries",
    state: "ready",
  },
  { id: "trust", label: "Trust balance", value: "R 50 000,00", state: "ready" },
  {
    id: "outstanding",
    label: "Outstanding",
    value: "R 3 450,00",
    hint: "0 overdue",
    state: "ready",
  },
  { id: "court", label: "Next court date", value: "1 Dec 2026", hint: "Pre-trial", state: "ready" },
];

function tilesContainer(): HTMLElement | null {
  return screen.getByTestId("matter-header-card").querySelector('[data-slot="detail-hero-tiles"]');
}

describe("MatterHeaderCard on DetailHero (641B)", () => {
  it("is the DetailHero root with the matter name as the only h1", () => {
    renderCard("ACTIVE");

    const card = screen.getByTestId("matter-header-card");
    expect(card).toHaveAttribute("data-slot", "detail-hero");
    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toBe(screen.getByTestId("matter-name"));
    expect(headings[0]).toHaveTextContent("Dlamini v Road Accident Fund");
    expect(card).toContainElement(headings[0]);
  });

  it("keeps the status badge and work type; closed date only on a CLOSED matter", () => {
    renderCard("ACTIVE");
    expect(screen.getByTestId("project-status-badge")).toHaveTextContent("Active");
    expect(screen.getByText("LITIGATION")).toBeInTheDocument();
    expect(screen.queryByTestId("project-closed-at")).not.toBeInTheDocument();
    cleanup();

    renderCard("CLOSED");
    expect(screen.getByTestId("project-status-badge")).toHaveTextContent("Closed");
    // 2026-10-20T10:00Z is 20 Oct on the SA calendar (formatDateInSA — hydration-safe).
    expect(screen.getByTestId("project-closed-at")).toHaveTextContent("Closed 20 Oct 2026");
  });

  it("renders the reference number as the identifier chip, and none without one", () => {
    renderCard("ACTIVE");
    const chip = screen.getByText("RAF-2026-001");
    expect(chip.tagName).toBe("CODE");
    expect(chip).toHaveAttribute("data-slot", "detail-hero-identifier");
    cleanup();

    renderCard("ACTIVE", [], { referenceNumber: null });
    expect(screen.getByTestId("matter-header-card").querySelector("code")).toBeNull();
  });

  it("subtitle: client link, then fee earner and opened date when passed", () => {
    renderCard("ACTIVE", [], {
      feeEarnerName: "Thandi Mokoena",
      openedAt: "2026-03-04T21:30:00Z",
    });

    const subtitle = screen
      .getByTestId("matter-header-card")
      .querySelector('[data-slot="detail-hero-subtitle"]') as HTMLElement;
    expect(subtitle).not.toBeNull();
    const client = within(subtitle).getByRole("link", { name: "Sipho Ndlovu" });
    expect(client).toHaveAttribute("href", "/org/acme/clients/c1");
    // 21:30Z on 4 Mar is 23:30 on 4 Mar SAST — the SA calendar day, whatever the runtime zone.
    expect(subtitle.textContent).toMatch(
      /^Sipho Ndlovu\s*·\s*Thandi Mokoena\s*·\s*Opened 4 Mar 2026$/
    );
    within(subtitle)
      .getAllByText("·")
      .forEach((sep) => expect(sep).toHaveAttribute("aria-hidden", "true"));
  });

  it("subtitle: 'Internal Matter' without a client; no fee earner / opened parts when absent", () => {
    renderCard("ACTIVE", [], { customers: [] });

    const subtitle = screen
      .getByTestId("matter-header-card")
      .querySelector('[data-slot="detail-hero-subtitle"]') as HTMLElement;
    expect(subtitle).toHaveTextContent(/^Internal Matter$/);
    expect(within(subtitle).queryByText("·")).not.toBeInTheDocument();
    expect(screen.queryByText(/Opened/)).not.toBeInTheDocument();
  });

  it("keeps the facts strip and lifecycle actions inside the hero root", () => {
    renderCard("ACTIVE", RAF_FIELDS);

    const card = screen.getByTestId("matter-header-card");
    const strip = within(card).getByTestId("matter-header-fields");
    expect(
      within(strip).getByTestId("matter-header-field-case_number").querySelector("dt")
    ).toHaveTextContent("Case no.");
    const actions = within(card).getByTestId("header-lifecycle-actions");
    expect(within(actions).getByTestId("close-matter-btn")).toBeInTheDocument();
  });

  it("renders no tile strip when no tiles are passed", () => {
    renderCard("ACTIVE");
    expect(tilesContainer()).toBeNull();
  });

  it("renders four tiles as compact StatCards, in order", () => {
    renderCard("ACTIVE", [], { tiles: TILES });

    const strip = tilesContainer() as HTMLElement;
    expect(strip).not.toBeNull();
    const groups = within(strip).getAllByRole("group");
    expect(groups).toHaveLength(4);
    expect(within(strip).getByRole("group", { name: "Unbilled WIP" })).toHaveTextContent(
      "R 12 500,00"
    );
    expect(groups[0]).toHaveAccessibleName("Unbilled WIP");
    expect(groups[1]).toHaveAccessibleName("Trust balance");
    expect(groups[2]).toHaveAccessibleName("Outstanding");
    expect(groups[3]).toHaveAccessibleName("Next court date");
    // Not links: the matter tiles carry no href (641B).
    expect(within(strip).queryByRole("link")).not.toBeInTheDocument();
  });

  it("renders three tiles when one was omitted by its gate", () => {
    renderCard("ACTIVE", [], { tiles: TILES.filter((t) => t.id !== "wip") });

    const groups = within(tilesContainer() as HTMLElement).getAllByRole("group");
    expect(groups).toHaveLength(3);
    expect(screen.queryByText("Unbilled WIP")).not.toBeInTheDocument();
  });

  it("renders an error tile as '—' with \"Couldn't load\"", () => {
    renderCard("ACTIVE", [], {
      tiles: [{ id: "outstanding", label: "Outstanding", value: null, state: "error" }],
    });

    const group = within(tilesContainer() as HTMLElement).getByRole("group", {
      name: "Outstanding",
    });
    expect(within(group).getByRole("status")).toHaveTextContent("Couldn't load");
  });
});
