import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import type { Project } from "@/lib/types";

// Mock server-only before any imports that pull it in
vi.mock("server-only", () => ({}));

// Mock Clerk auth
const mockAuth = vi.fn();
vi.mock("@clerk/nextjs/server", () => ({
  auth: () => mockAuth(),
}));

// Mock API client
const mockApiGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockApiGet(...args) },
  handleApiError: vi.fn(),
  getFieldDefinitions: vi.fn().mockResolvedValue([]),
  getViews: vi.fn().mockResolvedValue([]),
  getSavedView: vi.fn().mockResolvedValue(null),
  getTags: vi.fn().mockResolvedValue([]),
}));

// Mock templates API (has "server-only" import)
vi.mock("@/lib/api/templates", () => ({
  getProjectTemplates: vi.fn().mockResolvedValue([]),
}));

// Import page after mocks are set up
import ProjectsPage from "./page";

// Mock NewFromTemplateDialog (client component with hooks)
vi.mock("@/components/templates/NewFromTemplateDialog", () => ({
  NewFromTemplateDialog: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock ViewSelectorClient (client component with hooks)
vi.mock("@/components/views/ViewSelectorClient", () => ({
  ViewSelectorClient: () => <div data-testid="view-selector-client" />,
}));

// Mock view-actions
vi.mock("./view-actions", () => ({
  createSavedViewAction: vi.fn(),
}));

// Mock CreateProjectDialog (client component with dialog portal)
vi.mock("@/components/projects/create-project-dialog", () => ({
  CreateProjectDialog: ({ slug }: { slug: string }) => (
    <button data-testid="create-project-dialog" data-slug={slug}>
      New Project
    </button>
  ),
}));

// UpgradePrompt no longer used (inline upgrade banner in page)

// Mock next/link
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

const makeProject = (overrides: Partial<Project> = {}): Project => ({
  id: "proj-1",
  name: "Test Project",
  description: "A test project",
  createdBy: "user-1",
  createdAt: "2026-01-15T10:00:00Z",
  updatedAt: "2026-01-15T10:00:00Z",
  projectRole: null,
  ...overrides,
});

const params = Promise.resolve({ slug: "acme" });
const searchParams = Promise.resolve({} as Record<string, string | string[] | undefined>);

afterEach(() => cleanup());

beforeEach(() => {
  vi.clearAllMocks();
});

describe("ProjectsPage", () => {
  describe("New Project button", () => {
    it("renders for org:member role", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:member", has: () => false });
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getAllByTestId("create-project-dialog")).toHaveLength(1);
    });

    it("renders for org:admin role", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:admin", has: () => false });
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getAllByTestId("create-project-dialog")).toHaveLength(1);
    });

    it("renders for org:owner role", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:owner", has: () => false });
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getAllByTestId("create-project-dialog")).toHaveLength(1);
    });
  });

  describe("role badges on project cards", () => {
    it("shows Lead badge when projectRole is lead", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:member", has: () => false });
      mockApiGet.mockResolvedValue([makeProject({ projectRole: "lead" })]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByText("Lead")).toBeInTheDocument();
    });

    it("shows Member badge when projectRole is member", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:member", has: () => false });
      mockApiGet.mockResolvedValue([makeProject({ projectRole: "member" })]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByText("Member")).toBeInTheDocument();
    });

    it("shows no role badge when projectRole is null", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:admin", has: () => false });
      mockApiGet.mockResolvedValue([makeProject({ projectRole: null })]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.queryByText("Lead")).not.toBeInTheDocument();
      expect(screen.queryByText("Member")).not.toBeInTheDocument();
    });
  });

  describe("empty state", () => {
    it("shows member-specific message for org:member", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:member", has: () => false });
      mockApiGet.mockResolvedValue([]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByText(/not on any projects yet/)).toBeInTheDocument();
    });

    it("shows admin message for org:admin", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:admin", has: () => false });
      mockApiGet.mockResolvedValue([]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByText("Create your first project to get started.")).toBeInTheDocument();
    });

    it("shows New Project button in empty state for members", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:member", has: () => false });
      mockApiGet.mockResolvedValue([]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      // Header button + empty state button = 2
      expect(screen.getAllByTestId("create-project-dialog")).toHaveLength(2);
    });
  });

  describe("plan-aware feature gating", () => {
    it("shows upgrade prompt for starter orgs", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:member", has: () => false });
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.getByTestId("upgrade-prompt")).toBeInTheDocument();
    });

    it("hides upgrade prompt for pro orgs", async () => {
      mockAuth.mockResolvedValue({ orgId: "org_1", orgSlug: "acme", userId: "user_1", orgRole: "org:member", has: () => true });
      mockApiGet.mockResolvedValue([makeProject()]);

      const jsx = await ProjectsPage({ params, searchParams });
      render(jsx);

      expect(screen.queryByTestId("upgrade-prompt")).not.toBeInTheDocument();
    });
  });
});
