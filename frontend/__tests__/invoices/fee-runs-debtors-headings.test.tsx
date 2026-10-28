import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 88 628B (628.4, D11, R-6): the page h1s follow the sidebar words — "Fee runs" (both
// branches, plus the page's own sub-nav, empty state, new-run and detail headings) and "Debtors".
// The settings tab stays "Collections"; buttons and wizard copy are a follow-up.

vi.mock("server-only", () => ({}));

const { mockCaps, mockModuleEnabled, mockListBillingRuns, mockGetDebtors } = vi.hoisted(() => ({
  mockCaps: vi.fn(),
  mockModuleEnabled: vi.fn(),
  mockListBillingRuns: vi.fn(),
  mockGetDebtors: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: mockCaps }));
vi.mock("@/lib/api/settings", () => ({ isModuleEnabledServer: mockModuleEnabled }));
vi.mock("@/lib/api/billing-runs", () => ({
  listBillingRuns: mockListBillingRuns,
  getBillingRun: vi.fn(),
  getItems: vi.fn(),
}));
vi.mock("@/lib/api/collections", () => ({ getDebtors: mockGetDebtors }));
vi.mock("@/lib/api/ai", () => ({
  getAiGates: vi.fn().mockResolvedValue({ content: [] }),
  getAiGate: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/invoices/collections/collections-client", () => ({
  CollectionsClient: () => <div data-testid="collections-client" />,
}));
vi.mock("@/components/billing-runs/billing-run-wizard", () => ({
  BillingRunWizard: () => <div data-testid="billing-run-wizard" />,
}));
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/invoices/billing-runs",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import BillingRunsPage from "@/app/(app)/org/[slug]/invoices/billing-runs/page";
import NewBillingRunPage from "@/app/(app)/org/[slug]/invoices/billing-runs/new/page";
import BillingRunDetailPage from "@/app/(app)/org/[slug]/invoices/billing-runs/[id]/page";
import CollectionsPage from "@/app/(app)/org/[slug]/invoices/collections/page";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: ["INVOICING"], role: "member", isAdmin: false, isOwner: false };
const params = Promise.resolve({ slug: "acme" });

function heading() {
  return screen.getByRole("heading", { level: 1 });
}

describe("Fee runs and Debtors page headings (628B)", () => {
  beforeEach(() => {
    mockModuleEnabled.mockResolvedValue(true);
    mockListBillingRuns.mockResolvedValue({ content: [] });
    mockGetDebtors.mockResolvedValue({ content: [] });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('titles the fee runs list "Fee runs" with a "Fee runs" sub-nav tab and empty state', async () => {
    mockCaps.mockResolvedValue(OWNER);
    render(await BillingRunsPage({ params }));
    expect(heading()).toHaveTextContent("Fee runs");
    expect(screen.getByRole("link", { name: "Fee runs" })).toHaveAttribute(
      "href",
      "/org/acme/invoices/billing-runs"
    );
    expect(screen.getByText("No fee runs yet")).toBeInTheDocument();
    expect(screen.queryByText("Billing Runs")).not.toBeInTheDocument();
  });

  it('titles the fee runs list "Fee runs" for a non-admin too', async () => {
    mockCaps.mockResolvedValue(MEMBER);
    render(await BillingRunsPage({ params }));
    expect(heading()).toHaveTextContent("Fee runs");
  });

  it('titles the new-run page "New fee run" on both branches', async () => {
    mockCaps.mockResolvedValue(OWNER);
    render(await NewBillingRunPage({ params, searchParams: Promise.resolve({}) }));
    expect(heading()).toHaveTextContent("New fee run");
    cleanup();
    mockCaps.mockResolvedValue(MEMBER);
    render(await NewBillingRunPage({ params, searchParams: Promise.resolve({}) }));
    expect(heading()).toHaveTextContent("New fee run");
  });

  it('titles the run detail "Fee run" for a non-admin', async () => {
    mockCaps.mockResolvedValue(MEMBER);
    render(await BillingRunDetailPage({ params: Promise.resolve({ slug: "acme", id: "run-1" }) }));
    expect(heading()).toHaveTextContent("Fee run");
  });

  it('titles the collections page "Debtors"', async () => {
    mockCaps.mockResolvedValue(OWNER);
    render(await CollectionsPage({ params }));
    expect(heading()).toHaveTextContent("Debtors");
    expect(screen.queryByRole("heading", { name: "Collections" })).not.toBeInTheDocument();
  });
});
