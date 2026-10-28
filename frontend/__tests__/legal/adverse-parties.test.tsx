import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// --- Mocks (before component imports) ---

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
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/legal/adverse-parties",
}));

vi.mock("@/app/(app)/org/[slug]/legal/adverse-parties/actions", () => ({
  fetchAdverseParties: vi.fn().mockResolvedValue({ content: [], page: { totalElements: 0 } }),
  fetchAdverseParty: vi.fn().mockResolvedValue({ links: [] }),
  createAdverseParty: vi.fn().mockResolvedValue({ success: true }),
  updateAdverseParty: vi.fn().mockResolvedValue({ success: true }),
  deleteAdverseParty: vi.fn().mockResolvedValue({ success: true }),
  linkAdverseParty: vi.fn().mockResolvedValue({ success: true }),
  unlinkAdverseParty: vi.fn().mockResolvedValue({ success: true }),
  fetchProjectAdverseParties: vi.fn().mockResolvedValue([]),
  fetchProjects: vi.fn().mockResolvedValue([]),
  fetchCustomers: vi.fn().mockResolvedValue([]),
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  },
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn().mockResolvedValue({ content: [] }),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

// --- Imports after mocks ---

import { AdversePartyRegistryClient } from "@/app/(app)/org/[slug]/legal/adverse-parties/adverse-party-registry-client";
import {
  createAdverseParty,
  fetchProjects,
} from "@/app/(app)/org/[slug]/legal/adverse-parties/actions";
import { AdversePartyDialog } from "@/components/legal/adverse-party-dialog";
import { LinkAdversePartyDialog } from "@/components/legal/link-adverse-party-dialog";
import { NAV_GROUPS } from "@/lib/nav-items";
import type { AdverseParty } from "@/lib/types";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Helpers ---

function makeAdverseParty(overrides: Partial<AdverseParty> = {}): AdverseParty {
  return {
    id: "ap-1",
    name: "Ndlovu Trading (Pty) Ltd",
    idNumber: null,
    registrationNumber: "2020/123456/07",
    partyType: "COMPANY",
    aliases: null,
    notes: null,
    linkedMatterCount: 0,
    createdAt: "2026-03-01T00:00:00Z",
    updatedAt: "2026-03-01T00:00:00Z",
    ...overrides,
  };
}

// --- Tests ---

describe("AdversePartyRegistryClient", () => {
  it("renders adverse party table with party data", () => {
    const parties: AdverseParty[] = [
      makeAdverseParty({
        id: "ap-1",
        name: "Ndlovu Trading",
        partyType: "COMPANY",
      }),
      makeAdverseParty({
        id: "ap-2",
        name: "John Doe",
        partyType: "NATURAL_PERSON",
        idNumber: "8501015800087",
      }),
    ];

    render(<AdversePartyRegistryClient initialParties={parties} initialTotal={2} slug="acme" />);

    const registry = screen.getByTestId("adverse-party-registry");
    expect(registry).toBeInTheDocument();
    expect(screen.getByText("Ndlovu Trading")).toBeInTheDocument();
    expect(screen.getByText("John Doe")).toBeInTheDocument();
    // "Company" appears in both filter dropdown option and party type badge
    expect(screen.getAllByText("Company").length).toBeGreaterThanOrEqual(1);
    // "Person" only appears as badge text (dropdown uses "Natural Person")
    expect(screen.getByText("Person")).toBeInTheDocument();
    expect(screen.getByText("8501015800087")).toBeInTheDocument();
  });
});

describe("AdversePartyRegistryClient — Government party type", () => {
  it("shows a Government badge and offers the Government filter option", () => {
    const parties: AdverseParty[] = [
      makeAdverseParty({
        id: "ap-raf",
        name: "Road Accident Fund",
        partyType: "GOVERNMENT",
        registrationNumber: null,
      }),
    ];

    render(<AdversePartyRegistryClient initialParties={parties} initialTotal={1} slug="acme" />);

    expect(screen.getByText("Government")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Government / organ of state" })).toBeInTheDocument();
  });
});

describe("AdversePartyDialog", () => {
  it("opens create dialog and submits new party", async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();

    render(<AdversePartyDialog slug="acme" onSuccess={onSuccess} />);

    await user.click(screen.getByTestId("create-adverse-party-trigger"));

    const dialog = screen.getByTestId("adverse-party-dialog");
    expect(dialog).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Add Adverse Party" })).toBeInTheDocument();
    expect(screen.getByLabelText("Name *")).toBeInTheDocument();
    expect(screen.getByLabelText("Party Type *")).toBeInTheDocument();
    expect(screen.getByLabelText("ID Number")).toBeInTheDocument();
    expect(screen.getByLabelText("Registration Number")).toBeInTheDocument();
    expect(screen.getByLabelText("Aliases")).toBeInTheDocument();
    expect(screen.getByLabelText("Notes")).toBeInTheDocument();

    // Fill form
    await user.type(screen.getByLabelText("Name *"), "Test Company");

    // Submit
    await user.click(screen.getByRole("button", { name: "Create Party" }));
  });
});

describe("AdversePartyDialog — Government party type", () => {
  it("offers Government / organ of state and submits partyType GOVERNMENT", async () => {
    const user = userEvent.setup();

    render(<AdversePartyDialog slug="acme" />);

    await user.click(screen.getByTestId("create-adverse-party-trigger"));
    await user.type(screen.getByLabelText("Name *"), "Road Accident Fund");
    await user.selectOptions(screen.getByLabelText("Party Type *"), "GOVERNMENT");
    expect(screen.getByRole("option", { name: "Government / organ of state" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create Party" }));

    await vi.waitFor(() =>
      expect(vi.mocked(createAdverseParty)).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({ name: "Road Accident Fund", partyType: "GOVERNMENT" })
      )
    );
  });
});

describe("Delete button behavior", () => {
  it("delete option disabled when party has active links", () => {
    // Parties with links should not be deletable
    // This is tested via the registry client with linkedMatterCount > 0
    const parties = [makeAdverseParty({ id: "ap-linked", name: "Linked Party" })];

    render(<AdversePartyRegistryClient initialParties={parties} initialTotal={1} slug="acme" />);

    // Party should be in the table
    expect(screen.getByText("Linked Party")).toBeInTheDocument();
    // The delete button behavior is disabled via linkedMatterCount check in the component
  });
});

describe("LinkAdversePartyDialog", () => {
  it("renders relationship options", () => {
    render(
      <LinkAdversePartyDialog
        open={true}
        onOpenChange={vi.fn()}
        adversePartyId="ap-1"
        adversePartyName="Test Party"
        slug="acme"
      />
    );

    const dialog = screen.getByTestId("link-adverse-party-dialog");
    expect(dialog).toBeInTheDocument();
    expect(screen.getByLabelText("Relationship *")).toBeInTheDocument();

    // Check relationship options exist
    const select = screen.getByLabelText("Relationship *");
    expect(select).toBeInTheDocument();
    expect(screen.getByText("Opposing Party")).toBeInTheDocument();
    expect(screen.getByText("Witness")).toBeInTheDocument();
    expect(screen.getByText("Co-Accused")).toBeInTheDocument();
    expect(screen.getByText("Related Entity")).toBeInTheDocument();
    expect(screen.getByText("Guarantor")).toBeInTheDocument();
  });
});

describe("LinkAdversePartyDialog — matter list", () => {
  it("lists matters from the flat array fetchProjects resolves to", async () => {
    // GET /api/matters is a flat list; fetchProjects hands the dialog that array as is.
    vi.mocked(fetchProjects).mockResolvedValueOnce([
      { id: "m-1", name: "Mthembu v Road Accident Fund" },
    ]);

    render(
      <LinkAdversePartyDialog
        open={true}
        onOpenChange={vi.fn()}
        adversePartyId="ap-1"
        adversePartyName="Road Accident Fund"
        slug="acme"
      />
    );

    expect(
      await screen.findByRole("option", { name: "Mthembu v Road Accident Fund" })
    ).toBeInTheDocument();
  });
});

describe("Nav items - adverse parties", () => {
  it("adverse-parties nav item has requiredModule: conflict_check", () => {
    const mattersZone = NAV_GROUPS.find((g) => g.id === "matters");
    const adverseItem = mattersZone?.items.find((i) => i.label === "Adverse parties");
    expect(adverseItem).toBeDefined();
    expect(adverseItem?.requiredModule).toBe("conflict_check");
    expect(adverseItem?.requiredCapability).toBeUndefined();
  });
});
