import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 91 665E: the four Settings › Documents tab loaders render their h1 through PageHeader in
// every branch (K-124: Acceptance's h1 is the tab label), so each page keeps exactly one level-1
// heading; Templates gets one section per category (K-117 labels, label order, "Firm" badge) and a
// Branding section with its per-card Save in the footer; Clauses keeps its cards in one section;
// Acceptance keeps its per-card Save; Email keeps its inner tabs.

vi.mock("server-only", () => ({}));
const h = vi.hoisted(() => ({
  mockCaps: vi.fn(),
  mockGetTemplates: vi.fn(),
  mockGetOrgSettings: vi.fn(),
  mockApiGet: vi.fn(),
  mockGetClauses: vi.fn(),
  mockGetClauseCategories: vi.fn(),
  mockUpdateAcceptance: vi.fn(),
  mockSaveBranding: vi.fn(),
  mockGetEmailStats: vi.fn(),
  mockGetDeliveryLog: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => h.mockCaps() }));
vi.mock("@/lib/api", () => ({
  getTemplates: () => h.mockGetTemplates(),
  getOrgSettings: () => h.mockGetOrgSettings(),
  api: { get: (...args: unknown[]) => h.mockApiGet(...args) },
}));
vi.mock("@/lib/actions/clause-actions", () => ({
  getClauses: (...args: unknown[]) => h.mockGetClauses(...args),
  getClauseCategories: () => h.mockGetClauseCategories(),
  cloneClause: vi.fn(),
  deactivateClause: vi.fn(),
  createClause: vi.fn(),
  updateClause: vi.fn(),
  deleteClause: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/documents/_tabs/acceptance-actions", () => ({
  updateAcceptanceSettings: (...args: unknown[]) => h.mockUpdateAcceptance(...args),
}));
vi.mock("@/app/(app)/org/[slug]/settings/documents/_tabs/template-crud-actions", () => ({
  cloneTemplateAction: vi.fn(),
  deactivateTemplateAction: vi.fn(),
  resetTemplateAction: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/documents/_tabs/template-support-actions", () => ({
  uploadLogoAction: vi.fn(),
  deleteLogoAction: vi.fn(),
  saveBrandingAction: (...args: unknown[]) => h.mockSaveBranding(...args),
  uploadDocxTemplateAction: vi.fn(),
}));
vi.mock("@/lib/actions/email", () => ({
  getEmailStats: () => h.mockGetEmailStats(),
  getDeliveryLog: (...args: unknown[]) => h.mockGetDeliveryLog(...args),
}));
// Clauses: the category groups start expanded; the Tiptap body only mounts on "Expand clause".
vi.mock("@tiptap/react", () => ({
  useEditor: vi.fn(() => null),
  EditorContent: vi.fn(() => null),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn() }),
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
vi.mock("@/components/help-tip", () => ({
  HelpTip: ({ code }: { code: string }) => <span data-testid={`help-tip-${code}`} />,
}));

import { renderTemplatesTab } from "@/app/(app)/org/[slug]/settings/documents/_tabs/templates";
import { renderClausesTab } from "@/app/(app)/org/[slug]/settings/documents/_tabs/clauses";
import { renderAcceptanceTab } from "@/app/(app)/org/[slug]/settings/documents/_tabs/acceptance";
import { renderEmailTab } from "@/app/(app)/org/[slug]/settings/documents/_tabs/email";
import type { TemplateListResponse } from "@/lib/types";
import type { Clause } from "@/lib/actions/clause-actions";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };
const TAB = { slug: "acme", searchParams: {} };

const SETTINGS = {
  defaultCurrency: "ZAR",
  brandColor: "#0f766e",
  documentFooterText: "Footer",
  logoUrl: null,
  acceptanceExpiryDays: 30,
};

function template(overrides: Partial<TemplateListResponse>): TemplateListResponse {
  return {
    id: "tpl",
    name: "Template",
    slug: "template",
    description: "",
    category: "ENGAGEMENT_LETTER",
    primaryEntityType: "PROJECT",
    source: "PLATFORM",
    sourceTemplateId: null,
    active: true,
    sortOrder: 0,
    format: "TIPTAP",
    docxFileName: null,
    docxFileSize: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

// Key order (CLOSURE, ENGAGEMENT_LETTER, OTHER, PROJECT_SUMMARY, REPORT) differs from label order:
// PROJECT_SUMMARY's label "Matter Summary" sorts before "Other".
const TEMPLATES: TemplateListResponse[] = [
  template({ id: "t-summary", name: "Matter Summary", category: "PROJECT_SUMMARY" }),
  template({ id: "t-report", name: "Trust Report", category: "REPORT" }),
  template({ id: "t-closure", name: "Closure Letter", category: "CLOSURE" }),
  template({
    id: "t-firm",
    name: "Firm Letterhead",
    category: "OTHER",
    primaryEntityType: "ORGANIZATION" as TemplateListResponse["primaryEntityType"],
  }),
  template({ id: "t-el", name: "Engagement Letter — General", category: "ENGAGEMENT_LETTER" }),
  template({
    id: "t-sow",
    name: "Custom SOW",
    category: "ENGAGEMENT_LETTER",
    source: "ORG_CUSTOM",
  }),
];

const CLAUSE: Clause = {
  id: "c-1",
  title: "Standard NDA",
  slug: "standard-nda",
  description: "Non-disclosure agreement clause",
  body: { type: "doc", content: [] },
  legacyBody: null,
  category: "Confidentiality",
  source: "SYSTEM",
  sourceClauseId: null,
  packId: "common-pack",
  active: true,
  sortOrder: 1,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

function expectOneH1(text: string) {
  const h1s = screen.getAllByRole("heading", { level: 1 });
  expect(h1s).toHaveLength(1);
  expect(h1s[0]).toHaveTextContent(new RegExp(`^${text}$`));
  expect(h1s[0].closest('[data-slot="page-header"]')).not.toBeNull();
  return h1s[0];
}

function sections() {
  return Array.from(document.querySelectorAll<HTMLElement>('[data-slot="settings-section"]'));
}

function sectionTitled(name: string) {
  const heading = screen.getByRole("heading", { level: 2, name });
  const section = heading.closest<HTMLElement>('[data-slot="settings-section"]');
  expect(section).not.toBeNull();
  return section!;
}

beforeEach(() => {
  h.mockGetTemplates.mockResolvedValue(TEMPLATES);
  h.mockGetOrgSettings.mockResolvedValue(SETTINGS);
  h.mockApiGet.mockResolvedValue(SETTINGS);
  h.mockGetClauses.mockResolvedValue([CLAUSE]);
  h.mockGetClauseCategories.mockResolvedValue(["Confidentiality"]);
  h.mockGetEmailStats.mockResolvedValue({
    success: true,
    data: {
      sent24h: 4,
      bounced7d: 0,
      failed7d: 0,
      rateLimited7d: 0,
      currentHourUsage: 4,
      hourlyLimit: 50,
      providerSlug: "smtp",
    },
  });
  h.mockGetDeliveryLog.mockResolvedValue({
    success: true,
    data: { content: [], page: { totalElements: 0, totalPages: 0, size: 20, number: 0 } },
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

type Loader = (tab: typeof TAB) => Promise<React.ReactNode>;
const LOADERS: Array<[string, Loader, string]> = [
  ["Templates", renderTemplatesTab, "Templates"],
  ["Clauses", renderClausesTab, "Clause Library"],
  ["Acceptance", renderAcceptanceTab, "Acceptance"],
  ["Email", () => renderEmailTab(), "Email"],
];

describe("Documents tab loaders: one h1 through PageHeader in every branch", () => {
  it.each(LOADERS)("%s owner branch: one h1", async (_t, load, h1) => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await load(TAB));

    expectOneH1(h1);
    expect(screen.queryByText(/You do not have permission/)).toBeNull();
  });

  it.each(LOADERS)("%s member branch: one h1", async (_t, load, h1) => {
    h.mockCaps.mockResolvedValue(MEMBER);
    render(await load(TAB));

    expectOneH1(h1);
  });

  it("Templates: the HelpTip sits inside the h1, which starts with 'Templates'", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderTemplatesTab(TAB));

    const h1 = expectOneH1("Templates");
    expect(within(h1).getByTestId("help-tip-templates.tiptapVsWord")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Manage document templates for engagement letters, statements of work, and more."
      )
    ).toHaveAttribute("data-slot", "page-header-description");
  });

  it("Clauses: the description is the header's, unchanged", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderClausesTab(TAB));

    expect(
      screen.getByText(
        "Manage reusable clauses for document generation. System clauses can be cloned and customized."
      )
    ).toHaveAttribute("data-slot", "page-header-description");
  });

  it.each([
    [
      "Acceptance",
      renderAcceptanceTab as Loader,
      "You do not have permission to manage acceptance settings. Only admins and owners can access this page.",
    ],
    [
      "Email",
      (() => renderEmailTab()) as Loader,
      "You do not have permission to manage email settings. Only admins and owners can access this page.",
    ],
  ])("%s member branch: the permission line and no section", async (_t, load, sentence) => {
    h.mockCaps.mockResolvedValue(MEMBER);
    render(await load(TAB));

    expect(screen.getByText(sentence)).toBeInTheDocument();
    expect(sections()).toHaveLength(0);
  });

  it("Acceptance: the page h1 is the tab label (K-124); the card keeps 'Document Acceptance'", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderAcceptanceTab(TAB));

    expectOneH1("Acceptance");
    expect(
      screen.getByText("Configure default expiry period for document acceptance requests.")
    ).toHaveAttribute("data-slot", "page-header-description");
    expect(sectionTitled("Document Acceptance")).toBeInTheDocument();
  });
});

describe("Templates: one section per category (K-117) and the Branding section", () => {
  it("titles each category section with its label as an h2, in label order", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderTemplatesTab(TAB));

    const categoryTitles = sections()
      .map((s) => within(s).queryByRole("heading", { level: 2 })?.textContent)
      .filter((t) => t !== "Branding");
    expect(categoryTitles).toEqual([
      "Closure",
      "Engagement Letter",
      "Matter Summary",
      "Other",
      "Report",
    ]);
    expect(screen.queryByText("CLOSURE")).toBeNull();
    expect(screen.queryByText("REPORT")).toBeNull();
  });

  it("puts each category's templates in its own table, one template-list-item row each", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderTemplatesTab(TAB));

    const engagement = sectionTitled("Engagement Letter");
    expect(within(engagement).getAllByRole("table")).toHaveLength(1);
    const rows = within(engagement).getAllByTestId("template-list-item");
    expect(rows).toHaveLength(2);
    expect(
      within(rows[0]).getByRole("link", { name: "Engagement Letter — General" })
    ).toHaveAttribute("href", "/org/acme/settings/documents/templates/t-el/edit");
    expect(rows[0]).toHaveTextContent("Platform");
    expect(rows[0]).toHaveTextContent("Active");
    expect(rows[1]).toHaveTextContent("Custom");
    // The actions menu trigger stays the row's last button (template-management.spec.ts:125).
    const buttons = within(rows[1]).getAllByRole("button");
    expect(buttons[buttons.length - 1]).toHaveAttribute("aria-haspopup", "menu");
  });

  it("aligns the columns: every category table has the same header set", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderTemplatesTab(TAB));

    const headerSets = screen.getAllByRole("table").map((table) =>
      within(table)
        .getAllByRole("columnheader")
        .map((th) => th.textContent)
    );
    expect(headerSets).toHaveLength(5);
    for (const set of headerSets) {
      expect(set).toEqual(["Name", "Format", "Entity Type", "Source", "Status", "Actions"]);
    }
  });

  it("labels an ORGANIZATION template's entity badge 'Firm'", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderTemplatesTab(TAB));

    const row = within(sectionTitled("Other")).getByTestId("template-list-item");
    expect(within(row).getByText("Firm")).toBeInTheDocument();
    expect(row).not.toHaveTextContent("ORGANIZATION");
    expect(within(sectionTitled("Report")).getByText("Matter")).toBeInTheDocument();
  });

  it("Branding: an h2 section with 'Save Branding' in its footer and the saved message there", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockSaveBranding.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(await renderTemplatesTab(TAB));

    const branding = sectionTitled("Branding");
    expect(
      within(branding).getByText("Customize your organization's branding for generated documents.")
    ).toBeInTheDocument();
    const footer = branding.querySelector<HTMLElement>('[data-slot="card-footer"]');
    expect(footer).not.toBeNull();
    const save = within(footer!).getByRole("button", { name: "Save Branding" });

    await user.click(save);
    expect(h.mockSaveBranding).toHaveBeenCalledWith("acme", "#0f766e", "Footer");
    expect(await within(footer!).findByText("Branding settings saved.")).toBeInTheDocument();
  });

  it("member: read-only list — no New Template, no Actions column, no Branding", async () => {
    h.mockCaps.mockResolvedValue(MEMBER);
    render(await renderTemplatesTab(TAB));

    expect(screen.getAllByTestId("template-list-item")).toHaveLength(6);
    expect(screen.queryByRole("link", { name: "New Template" })).toBeNull();
    expect(screen.queryByRole("columnheader", { name: "Actions" })).toBeNull();
    expect(screen.queryByRole("heading", { name: "Branding" })).toBeNull();
  });

  it("no templates: the page-level EmptyState and no table; Branding still renders for an owner", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockGetTemplates.mockResolvedValue([]);
    render(await renderTemplatesTab(TAB));

    expect(screen.getByRole("heading", { level: 2, name: "No templates yet" })).toBeVisible();
    expect(screen.queryByRole("table")).toBeNull();
    expect(sections()).toHaveLength(1);
    expect(sectionTitled("Branding")).toBeInTheDocument();
  });
});

describe("Clauses, Email", () => {
  it("Clauses: the category groups and clause cards sit in one untitled section", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderClausesTab(TAB));

    const all = sections();
    expect(all).toHaveLength(1);
    const list = all[0];
    expect(list.querySelector('[data-slot="card-header"]')).toBeNull();
    expect(within(list).getByTestId("clause-category")).toHaveTextContent("Confidentiality");
    expect(within(list).getByRole("heading", { level: 2, name: "Confidentiality" })).toBeVisible();
    expect(within(list).getByTestId("clause-row")).toHaveTextContent("Standard NDA");
    expect(within(list).getByRole("button", { name: "Clause actions" })).toBeInTheDocument();
  });

  it("Email: the inner Overview / Delivery Log tabs stay; statistics are an h3 section", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderEmailTab());

    expect(screen.getByRole("tab", { name: "Overview" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "Delivery Log" })).toBeInTheDocument();
    const stats = await screen.findByRole("heading", { level: 3, name: "Delivery Statistics" });
    expect(stats.closest('[data-slot="settings-section"]')).not.toBeNull();
  });
});
