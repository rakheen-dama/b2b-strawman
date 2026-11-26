import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 91 665C: the Settings › Matters tab loaders and the six Matters sub-pages render their
// existing h1 through PageHeader in every branch that has one (so each page keeps exactly one
// level-1 heading), header actions keep their gates, list tabs show the page-level EmptyState or a
// DataTable in an untitled section, and Naming / Request settings keep their per-card save.

vi.mock("server-only", () => ({}));
const h = vi.hoisted(() => ({
  mockCaps: vi.fn(),
  mockApiGet: vi.fn(),
  mockGetTags: vi.fn(),
  mockGetProjectTemplates: vi.fn(),
  mockGetProjectTemplate: vi.fn(),
  mockListRequestTemplates: vi.fn(),
  mockGetRequestTemplate: vi.fn(),
  mockGetChecklistDetail: vi.fn(),
  mockUpdateNaming: vi.fn(),
  mockUpdateReminders: vi.fn(),
  mockRedirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT ${url}`);
  }),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => h.mockCaps() }));
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => h.mockApiGet(...args) },
  getTags: () => h.mockGetTags(),
  getFieldDefinitions: vi.fn().mockResolvedValue([]),
}));
vi.mock("@/lib/api/templates", () => ({
  getProjectTemplates: () => h.mockGetProjectTemplates(),
  getProjectTemplate: (id: string) => h.mockGetProjectTemplate(id),
}));
vi.mock("@/lib/api/information-requests", () => ({
  listRequestTemplates: () => h.mockListRequestTemplates(),
  getRequestTemplate: (id: string) => h.mockGetRequestTemplate(id),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/checklists/queries", () => ({
  getChecklistTemplateDetail: (id: string) => h.mockGetChecklistDetail(id),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/naming-actions", () => ({
  updateProjectNamingPattern: (...args: unknown[]) => h.mockUpdateNaming(...args),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/request-settings-actions", () => ({
  updateRequestReminderSettings: (...args: unknown[]) => h.mockUpdateReminders(...args),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/templates-actions", () => ({
  deleteTemplateAction: vi.fn(),
  duplicateTemplateAction: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/tags-actions", () => ({
  createTagAction: vi.fn(),
  updateTagAction: vi.fn(),
  deleteTagAction: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  redirect: (url: string) => h.mockRedirect(url),
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

// Components with their own tests: here only the frame around them is pinned.
vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/custom-fields-content", () => ({
  CustomFieldsContent: () => <div data-testid="custom-fields-content-mount" />,
}));
vi.mock("@/components/compliance/ChecklistTemplateActions", () => ({
  ChecklistTemplateActions: ({ templateName }: { templateName: string }) => (
    <button type="button">Clone {templateName}</button>
  ),
}));
vi.mock("@/components/information-requests/request-template-actions", () => ({
  RequestTemplateActions: ({ templateName }: { templateName: string }) => (
    <button type="button">Duplicate {templateName}</button>
  ),
}));
vi.mock("@/components/templates/TemplateEditor", () => ({
  TemplateEditor: () => <div data-testid="template-editor-mount" />,
}));
vi.mock("@/components/compliance/CreateChecklistTemplateForm", () => ({
  CreateChecklistTemplateForm: () => <div data-testid="create-checklist-form-mount" />,
}));
vi.mock("@/components/compliance/EditChecklistTemplateForm", () => ({
  EditChecklistTemplateForm: () => <div data-testid="edit-checklist-form-mount" />,
}));
vi.mock("@/components/information-requests/create-request-template-form", () => ({
  CreateRequestTemplateForm: () => <div data-testid="create-request-form-mount" />,
}));
vi.mock("@/components/information-requests/edit-request-template-form", () => ({
  EditRequestTemplateForm: () => <div data-testid="edit-request-form-mount" />,
}));

import { renderTemplatesTab } from "@/app/(app)/org/[slug]/settings/matters/_tabs/templates";
import { renderNamingTab } from "@/app/(app)/org/[slug]/settings/matters/_tabs/naming";
import { renderCustomFieldsTab } from "@/app/(app)/org/[slug]/settings/matters/_tabs/custom-fields";
import { renderTagsTab } from "@/app/(app)/org/[slug]/settings/matters/_tabs/tags";
import { renderChecklistsTab } from "@/app/(app)/org/[slug]/settings/matters/_tabs/checklists";
import { renderRequestTemplatesTab } from "@/app/(app)/org/[slug]/settings/matters/_tabs/request-templates";
import { renderRequestSettingsTab } from "@/app/(app)/org/[slug]/settings/matters/_tabs/request-settings";
import TemplateEditorPage from "@/app/(app)/org/[slug]/settings/matters/templates/[id]/page";
import NewChecklistTemplatePage from "@/app/(app)/org/[slug]/settings/matters/checklists/new/page";
import ChecklistTemplateDetailPage from "@/app/(app)/org/[slug]/settings/matters/checklists/[id]/page";
import EditChecklistTemplatePage from "@/app/(app)/org/[slug]/settings/matters/checklists/[id]/edit/page";
import NewRequestTemplatePage from "@/app/(app)/org/[slug]/settings/matters/request-templates/new/page";
import EditRequestTemplatePage from "@/app/(app)/org/[slug]/settings/matters/request-templates/[id]/page";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };
const TAB = { slug: "acme", searchParams: {} };

function expectOneH1(text: string | RegExp) {
  const h1s = screen.getAllByRole("heading", { level: 1 });
  expect(h1s).toHaveLength(1);
  expect(h1s[0]).toHaveTextContent(typeof text === "string" ? new RegExp(`^${text}$`) : text);
  return h1s[0];
}

const TEMPLATE = {
  id: "pt-1",
  name: "Litigation",
  namePattern: "{name}",
  description: null,
  billableDefault: true,
  source: "SEEDER",
  sourceMatterId: null,
  active: true,
  taskCount: 3,
  tagCount: 1,
  tasks: [],
  tags: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const CHECKLIST = {
  id: "cl-1",
  name: "FICA Individual",
  slug: "fica-individual",
  description: "Onboarding checks",
  clientType: "INDIVIDUAL",
  source: "ORG_CUSTOM",
  packId: null,
  active: true,
  autoInstantiate: true,
  sortOrder: 0,
  items: [
    {
      id: "it-2",
      name: "Proof of address",
      description: null,
      sortOrder: 2,
      required: false,
      requiresDocument: false,
      requiredDocumentLabel: null,
    },
    {
      id: "it-1",
      name: "ID document",
      description: "Certified copy",
      sortOrder: 1,
      required: true,
      requiresDocument: true,
      requiredDocumentLabel: "ID copy",
    },
  ],
};

const REQUEST_TEMPLATE = {
  id: "rt-1",
  name: "Annual docs",
  description: null,
  source: "CUSTOM",
  packId: null,
  active: true,
  items: [],
};

beforeEach(() => {
  h.mockApiGet.mockResolvedValue({ matterNamingPattern: "", defaultRequestReminderDays: 7 });
  h.mockGetTags.mockResolvedValue([]);
  h.mockGetProjectTemplates.mockResolvedValue([]);
  h.mockListRequestTemplates.mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Matters tab loaders: one h1 through PageHeader per branch", () => {
  it.each([
    ["Naming", renderNamingTab, "Matter Naming"],
    ["Checklists", renderChecklistsTab, "Checklists"],
    ["Request templates", renderRequestTemplatesTab, "Request Templates"],
    ["Request settings", renderRequestSettingsTab, "Request Settings"],
  ])("%s permission branch: one h1 and the permission line", async (_tab, loader, title) => {
    h.mockCaps.mockResolvedValue(MEMBER);
    render(await loader(TAB));

    const h1 = expectOneH1(title);
    expect(h1.closest('[data-slot="page-header"]')).not.toBeNull();
    expect(screen.getByText(/You do not have permission/)).toBeInTheDocument();
  });

  it("Custom fields permission branch keeps the HelpTip inside the h1", async () => {
    h.mockCaps.mockResolvedValue(MEMBER);
    render(await renderCustomFieldsTab(TAB));

    const h1 = expectOneH1("Custom Fields");
    expect(within(h1).getByTestId("help-tip-fields.types")).toBeInTheDocument();
    expect(screen.queryByTestId("custom-fields-content-mount")).toBeNull();
  });

  it("Custom fields owner: h1 + HelpTip, description, content mounted", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockApiGet.mockResolvedValue([]);
    render(await renderCustomFieldsTab(TAB));

    const h1 = expectOneH1("Custom Fields");
    expect(within(h1).getByTestId("help-tip-fields.types")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Define custom fields and groups for matters, tasks, clients, and fee notes."
      )
    ).toHaveAttribute("data-slot", "page-header-description");
    expect(screen.getByTestId("custom-fields-content-mount")).toBeInTheDocument();
  });
});

describe("Matters › Templates", () => {
  it("owner, no templates: 'Create Template' in the header and the page-level empty state", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderTemplatesTab(TAB));

    expectOneH1("Matter Templates");
    const actions = document.querySelector('[data-slot="page-header-actions"]') as HTMLElement;
    expect(within(actions).getByRole("link", { name: "Create Template" })).toHaveAttribute(
      "href",
      "/org/acme/settings/matters/templates/new"
    );
    const empty = document.querySelector('[data-slot="empty-state"]');
    expect(empty).toHaveAttribute("data-variant", "default");
    expect(
      screen.getByRole("heading", { level: 2, name: "No matter templates yet." })
    ).toBeVisible();
    expect(screen.queryByRole("table")).toBeNull();
    expect(document.querySelector('[data-slot="settings-section"]')).toBeNull();
  });

  it("member: no 'Create Template' and no row actions; rows in a section", async () => {
    h.mockCaps.mockResolvedValue(MEMBER);
    h.mockGetProjectTemplates.mockResolvedValue([TEMPLATE]);
    render(await renderTemplatesTab(TAB));

    expectOneH1("Matter Templates");
    expect(screen.queryByRole("link", { name: "Create Template" })).toBeNull();
    const section = document.querySelector('[data-slot="settings-section"]') as HTMLElement;
    const table = within(section).getByRole("table");
    expect(within(table).getByRole("cell", { name: "Litigation" })).toBeInTheDocument();
    expect(within(table).queryByRole("columnheader", { name: "Actions" })).toBeNull();
    expect(within(table).getByText("Pack")).toBeInTheDocument();
  });
});

describe("Matters › Tags", () => {
  it("owner: h1 + HelpTip, 'Add Tag' in the header, empty state's CTA is 'Add tag' (K-111)", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderTagsTab(TAB));

    const h1 = expectOneH1("Tags");
    expect(within(h1).getByTestId("help-tip-tags.overview")).toBeInTheDocument();
    const actions = document.querySelector('[data-slot="page-header-actions"]') as HTMLElement;
    expect(within(actions).getByRole("button", { name: "Add Tag" })).toBeInTheDocument();
    // The header button comes first in the DOM (tags.spec `.first()`).
    const addButtons = screen.getAllByRole("button", { name: /Add Tag/i });
    expect(addButtons).toHaveLength(2);
    expect(addButtons[0]).toHaveTextContent(/^Add Tag$/);
    expect(addButtons[1]).toHaveTextContent(/^Add tag$/);
  });

  it("member: no 'Add Tag' anywhere; rows in a table without Actions", async () => {
    h.mockCaps.mockResolvedValue(MEMBER);
    h.mockGetTags.mockResolvedValue([
      {
        id: "t-1",
        name: "Urgent",
        slug: "urgent",
        color: "#3B82F6",
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      },
    ]);
    render(await renderTagsTab(TAB));

    expectOneH1("Tags");
    expect(screen.queryByRole("button", { name: /Add Tag/i })).toBeNull();
    expect(screen.getByRole("cell", { name: "Urgent" })).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: "Actions" })).toBeNull();
    // K-15: blue-500 now takes near-black text (≈5.7:1), not white (3.68:1).
    expect(screen.getByText("Urgent").style.color).toBe("var(--slate-950)");
  });
});

describe("Matters › Checklists and Request templates", () => {
  it("checklists empty: page-level EmptyState with today's copy, no table", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockApiGet.mockResolvedValue([]);
    render(await renderChecklistsTab(TAB));

    expectOneH1("Checklists");
    expect(screen.getByRole("link", { name: "Create Template" })).toHaveAttribute(
      "href",
      "/org/acme/settings/matters/checklists/new"
    );
    expect(
      screen.getByRole("heading", { level: 2, name: "No checklist templates yet" })
    ).toBeVisible();
    expect(
      screen.getByText("Create your first checklist template to streamline client onboarding.")
    ).toBeInTheDocument();
    expect(document.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "default"
    );
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("checklists rows: DataTable rows keep checklist-template-row, name links to the detail", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockApiGet.mockResolvedValue([CHECKLIST]);
    render(await renderChecklistsTab(TAB));

    const rows = screen.getAllByTestId("checklist-template-row");
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByRole("link", { name: "FICA Individual" })).toHaveAttribute(
      "href",
      "/org/acme/settings/matters/checklists/cl-1"
    );
    expect(within(rows[0]).getByText("Yes")).toHaveClass("text-success-foreground");
    expect(within(rows[0]).getByRole("button", { name: "Clone FICA Individual" })).toBeVisible();
  });

  it("request templates empty: 'New Template' in the header and today's empty copy", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderRequestTemplatesTab(TAB));

    expectOneH1("Request Templates");
    expect(screen.getByRole("link", { name: "New Template" })).toHaveAttribute(
      "href",
      "/org/acme/settings/matters/request-templates/new"
    );
    expect(
      screen.getByRole("heading", { level: 2, name: "No request templates yet" })
    ).toBeVisible();
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("request templates rows keep request-template-row", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockListRequestTemplates.mockResolvedValue([REQUEST_TEMPLATE]);
    render(await renderRequestTemplatesTab(TAB));

    const row = screen.getByTestId("request-template-row");
    expect(within(row).getByRole("link", { name: "Annual docs" })).toHaveAttribute(
      "href",
      "/org/acme/settings/matters/request-templates/rt-1"
    );
    expect(within(row).getByText("Active")).toHaveClass("text-success-foreground");
  });
});

describe("Per-card save (Naming, Request settings)", () => {
  it("Naming: 'Save Settings' in the card footer, same action args, message in the footer", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockApiGet.mockResolvedValue({ matterNamingPattern: "{name}" });
    h.mockUpdateNaming.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(await renderNamingTab(TAB));

    expectOneH1("Matter Naming");
    expect(screen.getByRole("heading", { level: 2, name: "Naming Pattern" })).toBeVisible();
    const footer = document.querySelector('[data-slot="card-footer"]') as HTMLElement;
    const save = within(footer).getByRole("button", { name: "Save Settings" });
    expect(document.querySelectorAll('[data-slot="settings-action-bar"]')).toHaveLength(0);

    await user.click(save);
    expect(h.mockUpdateNaming).toHaveBeenCalledWith("acme", "{name}");
    expect(await within(footer).findByText("Matter naming pattern updated.")).toHaveClass(
      "text-success-foreground"
    );
  });

  it("Naming: an empty pattern is still sent as null", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockUpdateNaming.mockResolvedValue({ success: false, error: "Nope" });
    const user = userEvent.setup();
    render(await renderNamingTab(TAB));

    await user.click(screen.getByRole("button", { name: "Save Settings" }));
    expect(h.mockUpdateNaming).toHaveBeenCalledWith("acme", null);
    const footer = document.querySelector('[data-slot="card-footer"]') as HTMLElement;
    expect(await within(footer).findByText("Nope")).toHaveClass("text-destructive");
  });

  it("Request settings: 'Save Settings' in the footer; the error line in the footer", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockUpdateReminders.mockResolvedValue({ success: false, error: "Not available" });
    const user = userEvent.setup();
    render(await renderRequestSettingsTab(TAB));

    expectOneH1("Request Settings");
    expect(screen.getByRole("heading", { level: 2, name: "Request Reminders" })).toBeVisible();
    const footer = document.querySelector('[data-slot="card-footer"]') as HTMLElement;
    await user.click(within(footer).getByRole("button", { name: "Save Settings" }));
    expect(h.mockUpdateReminders).toHaveBeenCalledWith("acme", 7);
    expect(await within(footer).findByText("Not available")).toHaveClass("text-destructive");
  });
});

const params = <T extends Record<string, string>>(p: T) => ({ params: Promise.resolve(p) });

describe("Matters sub-pages: one h1 through PageHeader, form in one section", () => {
  it("templates/new: h1 'New Matter Template', back link, editor inside a section", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockListRequestTemplates.mockResolvedValue([]);
    render(await TemplateEditorPage(params({ slug: "acme", id: "new" })));

    expectOneH1("New Matter Template");
    expect(screen.getByRole("link", { name: "Matter Templates" })).toHaveAttribute(
      "href",
      "/org/acme/settings/matters?tab=templates"
    );
    const editor = screen.getByTestId("template-editor-mount");
    expect(editor.closest('[data-slot="settings-section"]')).not.toBeNull();
  });

  it("templates/[id]: h1 is the template name; a member is redirected", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockGetProjectTemplate.mockResolvedValue(TEMPLATE);
    render(await TemplateEditorPage(params({ slug: "acme", id: "pt-1" })));
    expectOneH1("Litigation");

    cleanup();
    h.mockCaps.mockResolvedValue(MEMBER);
    await expect(TemplateEditorPage(params({ slug: "acme", id: "pt-1" }))).rejects.toThrow(
      "NEXT_REDIRECT /org/acme/settings/matters?tab=templates"
    );
  });

  it("checklists/new: h1 + description, form inside a section", async () => {
    render(await NewChecklistTemplatePage(params({ slug: "acme" })));

    expectOneH1("New Checklist Template");
    expect(screen.getByText("Create a checklist template for client onboarding.")).toBeVisible();
    const form = screen.getByTestId("create-checklist-form-mount");
    expect(form.closest('[data-slot="settings-section"]')).not.toBeNull();
  });

  it("checklists/[id] owner: name h1, Edit in the header, badges in the toolbar, items table", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockGetChecklistDetail.mockResolvedValue(CHECKLIST);
    render(await ChecklistTemplateDetailPage(params({ slug: "acme", id: "cl-1" })));

    expectOneH1("FICA Individual");
    const actions = document.querySelector('[data-slot="page-header-actions"]') as HTMLElement;
    expect(within(actions).getByRole("link", { name: "Edit" })).toHaveAttribute(
      "href",
      "/org/acme/settings/matters/checklists/cl-1/edit"
    );
    const toolbar = document.querySelector('[data-slot="page-header-toolbar"]') as HTMLElement;
    expect(within(toolbar).getByText("Custom")).toBeInTheDocument();
    expect(within(toolbar).getByText("Auto-instantiate")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { level: 2, name: "Checklist Items (2)" })
    ).toBeInTheDocument();
    const rows = screen.getAllByTestId("checklist-item-row");
    expect(rows.map((r) => within(r).getAllByRole("cell")[1].textContent)).toEqual([
      "ID document",
      "Proof of address",
    ]);
    expect(within(rows[0]).getAllByRole("cell")[0]).toHaveTextContent("1");
    expect(within(rows[0]).getByTestId("checklist-item-required")).toHaveTextContent("Required");
    expect(within(rows[1]).getByText("Optional")).toHaveClass("text-muted-foreground");
  });

  it("checklists/[id] member: no Edit; empty items show the inline empty line", async () => {
    h.mockCaps.mockResolvedValue(MEMBER);
    h.mockGetChecklistDetail.mockResolvedValue({ ...CHECKLIST, items: [] });
    render(await ChecklistTemplateDetailPage(params({ slug: "acme", id: "cl-1" })));

    expectOneH1("FICA Individual");
    expect(screen.queryByRole("link", { name: "Edit" })).toBeNull();
    expect(screen.getByText("No items in this template.")).toBeInTheDocument();
    expect(document.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "inline"
    );
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("checklists/[id] not found: one h1 'Template Not Found'", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockGetChecklistDetail.mockRejectedValue({ status: 404 });
    render(await ChecklistTemplateDetailPage(params({ slug: "acme", id: "nope" })));
    expectOneH1("Template Not Found");
  });

  it("checklists/[id]/edit: h1 + description, form in a section; not-found keeps one h1", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockGetChecklistDetail.mockResolvedValue(CHECKLIST);
    render(await EditChecklistTemplatePage(params({ slug: "acme", id: "cl-1" })));
    expectOneH1("Edit Checklist Template");
    expect(
      screen.getByTestId("edit-checklist-form-mount").closest('[data-slot="settings-section"]')
    ).not.toBeNull();

    cleanup();
    h.mockGetChecklistDetail.mockRejectedValue({ status: 404 });
    render(await EditChecklistTemplatePage(params({ slug: "acme", id: "nope" })));
    expectOneH1("Template Not Found");
  });

  it("request-templates/new: one h1 in both the owner and the member branch", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await NewRequestTemplatePage(params({ slug: "acme" })));
    expectOneH1("New Request Template");
    expect(
      screen.getByTestId("create-request-form-mount").closest('[data-slot="settings-section"]')
    ).not.toBeNull();

    cleanup();
    h.mockCaps.mockResolvedValue(MEMBER);
    render(await NewRequestTemplatePage(params({ slug: "acme" })));
    expectOneH1("New Request Template");
    expect(screen.getByText(/You do not have permission to create/)).toBeInTheDocument();
  });

  it("request-templates/[id]: h1 'Edit Template' with the source badge beside it, not inside", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockGetRequestTemplate.mockResolvedValue(REQUEST_TEMPLATE);
    render(await EditRequestTemplatePage(params({ slug: "acme", id: "rt-1" })));

    expectOneH1("Edit Template");
    const toolbar = document.querySelector('[data-slot="page-header-toolbar"]') as HTMLElement;
    expect(toolbar).not.toBeNull();
    expect(
      screen.getByTestId("edit-request-form-mount").closest('[data-slot="settings-section"]')
    ).not.toBeNull();
  });
});
