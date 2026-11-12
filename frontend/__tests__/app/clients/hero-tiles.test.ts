import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { isValidElement, type ReactElement } from "react";
import { formatCurrency, formatDateInSA } from "@/lib/format";
import type { HeroTile } from "@/components/shared/detail-hero";
import { unserializableProps } from "@/__tests__/helpers/rsc-props";

// Phase 90 642B — client hero tiles (arch §15.3.5, ADR-373, rulings §6): each read once, only under
// its gate; gate fails / 403 / 404 → omitted; other failures → state "error"; a real zero → the
// formatted zero; Outstanding never sums across currencies; tiles carry only data (OQ #26).

vi.mock("server-only", () => ({}));

const { mockMatterRows, mockFica, mockDebtor, mockAccounts, mockLedger, MockApiError } = vi.hoisted(
  () => {
    class MockApiError extends Error {
      constructor(
        public status: number,
        message: string
      ) {
        super(message);
        this.name = "ApiError";
      }
    }
    return {
      mockMatterRows: vi.fn(),
      mockFica: vi.fn(),
      mockDebtor: vi.fn(),
      mockAccounts: vi.fn(),
      mockLedger: vi.fn(),
      MockApiError,
    };
  }
);

vi.mock("@/lib/api/matters", () => ({ listMatterRows: mockMatterRows }));
vi.mock("@/lib/api/clients", () => ({ getClientFicaStatus: mockFica }));
vi.mock("@/lib/api/collections", () => ({ getDebtorDetail: mockDebtor }));
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: mockAccounts,
}));
vi.mock("@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions", () => ({
  fetchClientLedger: mockLedger,
}));
// The real settle(): its deps are the API client's ApiError and the expired-session funnel.
vi.mock("@/lib/api/client", () => ({ ApiError: MockApiError }));
vi.mock("@/lib/auth/expiry-server", () => ({ rethrowOnExpiredSession: vi.fn() }));

import {
  buildClientHeroTiles,
  chaseHistory,
  loadClientHeroData,
} from "@/app/(app)/org/[slug]/clients/[id]/hero-tiles";
import type { HomeCaps } from "@/lib/dashboard-home";

const CLIENT = "c-1";
const OPTS = { trustEnabled: true, chasePage: 0 };
const ADMIN: HomeCaps = { capabilities: [], isAdmin: true, isOwner: false };
const OWNER: HomeCaps = { capabilities: [], isAdmin: false, isOwner: true };
/** The built-in member role (tenant V1 baseline): VIEW_TRUST, no INVOICING. */
const MEMBER: HomeCaps = {
  capabilities: ["VIEW_LEGAL", "VIEW_TRUST", "MANAGE_DISBURSEMENTS"],
  isAdmin: false,
  isOwner: false,
};

/** What GET /api/matters?customerId=&status=ACTIVE returns: ACTIVE rows the caller can see. */
const ACTIVE_ROWS = [
  { id: "m1", status: "ACTIVE" },
  { id: "m2", status: "ACTIVE" },
];
const ACCOUNTS = [
  { id: "acc-2", accountName: "Investment", isPrimary: false, status: "ACTIVE" },
  { id: "acc-1", accountName: "Main Trust", isPrimary: true, status: "ACTIVE" },
];
const LEDGER = { clientId: CLIENT, balance: 50000 };
const row = (currency: string, balanceDue: number, daysOverdue = 0) => ({
  invoiceId: `i-${currency}-${balanceDue}`,
  invoiceNumber: "FN-1",
  total: balanceDue + 100,
  balanceDue,
  currency,
  dueDate: "2026-10-01",
  daysOverdue,
});
const ACTIVITIES = { content: [], page: { totalElements: 0, totalPages: 0, size: 20, number: 0 } };
const DEBTOR = {
  clientId: CLIENT,
  clientName: "Acme",
  collectionsExempt: false,
  outstandingInvoices: [row("ZAR", 1000, 12), row("ZAR", 2450), row("USD", 999, 30)],
  activities: ACTIVITIES,
};
const FICA = {
  clientId: CLIENT,
  status: "DONE",
  lastVerifiedAt: "2026-05-01T23:30:00Z",
  requestId: null,
  checklistInstanceId: "chk-1",
};

async function tilesFor(
  caps: HomeCaps,
  currency: string | null = "ZAR",
  opts = OPTS
): Promise<HeroTile[]> {
  return buildClientHeroTiles(await loadClientHeroData(CLIENT, caps, opts), currency);
}
const ids = (tiles: HeroTile[]) => tiles.map((t) => t.id);
const tile = (tiles: HeroTile[], id: string) => tiles.find((t) => t.id === id) as HeroTile;
/** A ReactNode value/hint as rendered text (and its DOM, for icon checks). */
function renderNode(node: unknown): HTMLElement {
  const { container } = render(node as ReactElement);
  return container;
}

describe("client hero tiles (642B)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockMatterRows.mockResolvedValue(ACTIVE_ROWS);
    mockDebtor.mockResolvedValue(DEBTOR);
    mockAccounts.mockResolvedValue(ACCOUNTS);
    mockLedger.mockResolvedValue(LEDGER);
    mockFica.mockResolvedValue(FICA);
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it.each([
    ["admin", ADMIN],
    ["owner", OWNER],
  ])("%s: four tiles in order, each read once", async (_, caps) => {
    const tiles = await tilesFor(caps);

    expect(tiles.map((t) => t.label)).toEqual([
      "Open matters",
      "Trust balance",
      "Outstanding",
      "FICA",
    ]);
    expect(tiles.every((t) => t.state === "ready" && !t.href)).toBe(true);
    for (const read of [mockMatterRows, mockDebtor, mockAccounts, mockLedger, mockFica]) {
      expect(read).toHaveBeenCalledTimes(1);
    }
    expect(mockDebtor).toHaveBeenCalledWith(CLIENT, { page: 0 });
    expect(mockFica).toHaveBeenCalledWith(CLIENT);
  });

  describe("Open matters (R1: ACTIVE, visible, server-filtered)", () => {
    it("asks the matters list for this client's ACTIVE matters and counts the rows", async () => {
      const tiles = await tilesFor(ADMIN);

      expect(mockMatterRows).toHaveBeenCalledTimes(1);
      expect(mockMatterRows).toHaveBeenCalledWith({ customerId: CLIENT, status: "ACTIVE" });
      expect(tile(tiles, "openMatters").value).toBe("2");
    });

    it("member: the count is whatever the visibility-filtered list returned (ADR-346)", async () => {
      mockMatterRows.mockResolvedValue([{ id: "m1", status: "ACTIVE" }]);
      const tiles = await tilesFor(MEMBER);

      expect(mockMatterRows).toHaveBeenCalledWith({ customerId: CLIENT, status: "ACTIVE" });
      expect(tile(tiles, "openMatters").value).toBe("1");
    });

    it("0 is a real value — never omitted", async () => {
      mockMatterRows.mockResolvedValue([]);
      const tiles = await tilesFor(MEMBER);

      expect(tile(tiles, "openMatters")).toMatchObject({ value: "0", state: "ready" });
    });

    it("read failed → state error", async () => {
      mockMatterRows.mockRejectedValue(new Error("boom"));
      const tiles = await tilesFor(ADMIN);

      expect(tile(tiles, "openMatters")).toEqual({
        id: "openMatters",
        label: "Open matters",
        value: null,
        state: "error",
      });
    });
  });

  describe("Outstanding (default currency only — rulings §6, R3)", () => {
    it("mixed currency: sums the default-currency rows only and notes the excluded one", async () => {
      const tiles = await tilesFor(ADMIN);
      const outstanding = tile(tiles, "outstanding");

      // 1000 + 2450 ZAR (balanceDue, not total); the USD 999 is never added.
      expect(outstanding.value).toBe(formatCurrency(3450, "ZAR"));
      // One hint line: the exclusion caveat wins over the overdue count.
      expect(outstanding.hint).toBe("Excludes 1 fee note in other currencies");
    });

    it("plural exclusion copy", async () => {
      mockDebtor.mockResolvedValue({
        ...DEBTOR,
        outstandingInvoices: [row("ZAR", 500), row("USD", 10), row("EUR", 20)],
      });
      const tiles = await tilesFor(ADMIN);

      expect(tile(tiles, "outstanding").value).toBe(formatCurrency(500, "ZAR"));
      expect(tile(tiles, "outstanding").hint).toBe("Excludes 2 fee notes in other currencies");
    });

    it("single currency: the shared overdue hint, counting default-currency overdue rows", async () => {
      mockDebtor.mockResolvedValue({
        ...DEBTOR,
        outstandingInvoices: [row("ZAR", 1000, 12), row("ZAR", 2450)],
      });
      const tiles = await tilesFor(ADMIN);
      const outstanding = tile(tiles, "outstanding");

      expect(outstanding.value).toBe(formatCurrency(3450, "ZAR"));
      expect(isValidElement(outstanding.hint)).toBe(true);
      const hint = renderNode(outstanding.hint);
      expect(hint).toHaveTextContent(/^1 overdue$/);
      expect(hint.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
      expect(screen.queryByText(/Excludes/)).not.toBeInTheDocument();
    });

    it("nothing outstanding: a formatted zero and '0 overdue'", async () => {
      mockDebtor.mockResolvedValue({ ...DEBTOR, outstandingInvoices: [] });
      const tiles = await tilesFor(OWNER);

      expect(tile(tiles, "outstanding")).toMatchObject({
        value: formatCurrency(0, "ZAR"),
        hint: "0 overdue",
        state: "ready",
      });
    });

    it("no INVOICING (member): omitted, the debtor never read, Chase history hidden", async () => {
      const data = await loadClientHeroData(CLIENT, MEMBER, OPTS);

      expect(ids(buildClientHeroTiles(data, "ZAR"))).toEqual(["openMatters", "trust", "fica"]);
      expect(mockDebtor).not.toHaveBeenCalled();
      expect(chaseHistory(data)).toBeNull();
    });

    it("INVOICING without admin: present, and the Chase-history card gets the same read", async () => {
      const data = await loadClientHeroData(
        CLIENT,
        { capabilities: ["INVOICING"], isAdmin: false, isOwner: false },
        { ...OPTS, chasePage: 2 }
      );

      expect(ids(buildClientHeroTiles(data, "ZAR"))).toContain("outstanding");
      expect(mockDebtor).toHaveBeenCalledTimes(1);
      expect(mockDebtor).toHaveBeenCalledWith(CLIENT, { page: 2 });
      expect(chaseHistory(data)).toEqual(DEBTOR);
    });

    it("debtor read failed → state error; Chase history hidden", async () => {
      mockDebtor.mockRejectedValue(new Error("boom"));
      const data = await loadClientHeroData(CLIENT, ADMIN, OPTS);

      expect(tile(buildClientHeroTiles(data, "ZAR"), "outstanding").state).toBe("error");
      expect(chaseHistory(data)).toBeNull();
    });

    it("settings read failed (no default currency) → Outstanding state error", async () => {
      const tiles = await tilesFor(ADMIN, null);

      expect(tile(tiles, "outstanding")).toMatchObject({ value: null, state: "error" });
    });
  });

  describe("Trust balance (primary account only — OQ #11)", () => {
    it("reads the primary account's ledger; account name as hint", async () => {
      const tiles = await tilesFor(MEMBER);

      expect(mockLedger).toHaveBeenCalledWith("acc-1", CLIENT);
      expect(tile(tiles, "trust")).toMatchObject({
        value: formatCurrency(50000, "ZAR"),
        hint: "Main Trust",
        state: "ready",
      });
    });

    it("no primary: falls back to the first account", async () => {
      mockAccounts.mockResolvedValue([ACCOUNTS[0]]);
      const tiles = await tilesFor(ADMIN);

      expect(mockLedger).toHaveBeenCalledWith("acc-2", CLIENT);
      expect(tile(tiles, "trust").hint).toBe("Investment");
    });

    it("no ledger card (backend's synthesised zero) → R 0,00", async () => {
      mockLedger.mockResolvedValue({ clientId: CLIENT, balance: 0 });
      const tiles = await tilesFor(ADMIN);

      expect(tile(tiles, "trust")).toMatchObject({
        value: formatCurrency(0, "ZAR"),
        state: "ready",
      });
    });

    it("ledger 404 → R 0,00 (not omitted)", async () => {
      mockLedger.mockRejectedValue(new MockApiError(404, "Not found"));
      const tiles = await tilesFor(ADMIN);

      expect(tile(tiles, "trust")).toMatchObject({
        value: formatCurrency(0, "ZAR"),
        hint: "Main Trust",
        state: "ready",
      });
    });

    it("no trust account → omitted and the ledger never read", async () => {
      mockAccounts.mockResolvedValue([]);
      const tiles = await tilesFor(ADMIN);

      expect(ids(tiles)).not.toContain("trust");
      expect(mockLedger).not.toHaveBeenCalled();
    });

    it("VIEW_TRUST absent → omitted without a request", async () => {
      const tiles = await tilesFor({ capabilities: ["INVOICING"], isAdmin: false, isOwner: false });

      expect(ids(tiles)).toEqual(["openMatters", "outstanding", "fica"]);
      expect(mockAccounts).not.toHaveBeenCalled();
      expect(mockLedger).not.toHaveBeenCalled();
    });

    it("trust_accounting off → omitted without a request", async () => {
      const tiles = await tilesFor(ADMIN, "ZAR", { ...OPTS, trustEnabled: false });

      expect(ids(tiles)).toEqual(["openMatters", "outstanding", "fica"]);
      expect(mockAccounts).not.toHaveBeenCalled();
    });

    it("ledger read failed (500) → state error", async () => {
      mockLedger.mockRejectedValue(new MockApiError(500, "boom"));
      const tiles = await tilesFor(ADMIN);

      expect(tile(tiles, "trust")).toMatchObject({ value: null, state: "error" });
    });
  });

  describe("FICA (label + icon, never colour alone)", () => {
    it("DONE: icon + 'Done', completion date hint on the SA calendar", async () => {
      const fica = tile(await tilesFor(ADMIN), "fica");

      const value = renderNode(fica.value);
      expect(value).toHaveTextContent(/^Done$/);
      const icon = value.querySelector("svg");
      expect(icon).toHaveAttribute("aria-hidden", "true");
      expect(icon).toHaveClass("text-success");
      expect(fica.hint).toBe(`Completed ${formatDateInSA("2026-05-01T23:30:00Z")}`);
    });

    it("DONE by a FICA request: 'Verified <date>'", async () => {
      mockFica.mockResolvedValue({ ...FICA, checklistInstanceId: null, requestId: "r-1" });
      const fica = tile(await tilesFor(ADMIN), "fica");

      expect(fica.hint).toBe(`Verified ${formatDateInSA("2026-05-01T23:30:00Z")}`);
    });

    it.each([
      ["IN_PROGRESS", "In progress", "text-warning"],
      ["NOT_STARTED", "Not started", "text-danger"],
    ])("%s → '%s', no hint", async (status, label, colour) => {
      mockFica.mockResolvedValue({
        ...FICA,
        status,
        lastVerifiedAt: null,
        checklistInstanceId: null,
      });
      const fica = tile(await tilesFor(MEMBER), "fica");

      const value = renderNode(fica.value);
      expect(value).toHaveTextContent(new RegExp(`^${label}$`));
      expect(value.querySelector("svg")).toHaveClass(colour);
      expect(fica.hint).toBeUndefined();
    });

    it("read failed → state error", async () => {
      mockFica.mockRejectedValue(new Error("boom"));
      const tiles = await tilesFor(ADMIN);

      expect(tile(tiles, "fica")).toEqual({
        id: "fica",
        label: "FICA",
        value: null,
        state: "error",
      });
    });
  });

  it("OQ #26: tiles carry only data across the RSC boundary", async () => {
    mockDebtor.mockResolvedValue({ ...DEBTOR, outstandingInvoices: [row("ZAR", 10, 3)] });
    const tiles = await tilesFor(ADMIN);

    const slots = new Set(["value", "hint"]);
    expect(
      tiles.flatMap((t, i) =>
        unserializableProps(t as unknown as Record<string, unknown>, slots).map(
          (p) => `tiles[${i}].${p}`
        )
      )
    ).toEqual([]);
    // Non-vacuous: the FICA value and the overdue hint are pre-rendered elements.
    expect(isValidElement(tile(tiles, "fica").value)).toBe(true);
    expect(isValidElement(tile(tiles, "outstanding").hint)).toBe(true);
  });
});
