import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { TemplateDetailResponse } from "@/lib/types";

const mockUpdateTemplateAction = vi.fn();

vi.mock("@/lib/auth", () => ({
  getAuthContext: vi.fn().mockResolvedValue({
    userId: "user_1",
    orgId: "org_1",
    orgSlug: "acme",
    orgRole: "org:owner",
    memberId: "member_1",
  }),
}));

vi.mock("@/lib/api", () => ({
  getTemplateDetail: vi.fn(),
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(message: string, status: number) {
      super(message);
      this.status = status;
    }
  },
}));

vi.mock(
  "@/app/(app)/org/[slug]/settings/templates/actions",
  () => ({
    updateTemplateAction: (...args: unknown[]) =>
      mockUpdateTemplateAction(...args),
    createTemplateAction: vi.fn(),
    fetchVariableMetadataAction: vi.fn(),
  }),
);

vi.mock("@/components/editor/DocumentEditor", () => ({
  DocumentEditor: ({
    content,
    onUpdate,
    editable,
  }: {
    content?: Record<string, unknown> | null;
    onUpdate?: (json: Record<string, unknown>) => void;
    editable?: boolean;
  }) => (
    <div data-testid="document-editor" data-editable={editable}>
      {content ? "Editor with content" : "Empty editor"}
      <button
        onClick={() =>
          onUpdate?.({ type: "doc", content: [{ type: "paragraph" }] })
        }
      >
        Simulate edit
      </button>
    </div>
  ),
}));

vi.mock("@/components/templates/TemplatePreviewDialog", () => ({
  TemplatePreviewDialog: () => (
    <button data-testid="preview-dialog">Preview</button>
  ),
}));

// Must import after mocks
import { TemplateEditorClient } from "@/components/templates/TemplateEditorClient";

function makeTemplate(
  overrides?: Partial<TemplateDetailResponse>,
): TemplateDetailResponse {
  return {
    id: "tpl-1",
    name: "Test Template",
    slug: "test-template",
    description: "A test template",
    category: "ENGAGEMENT_LETTER",
    primaryEntityType: "PROJECT",
    content: { type: "doc", content: [{ type: "paragraph" }] },
    legacyContent: null,
    css: null,
    source: "ORG_CUSTOM",
    sourceTemplateId: null,
    packId: null,
    packTemplateKey: null,
    requiredContextFields: null,
    active: true,
    sortOrder: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("TemplateEditorClient", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders with DocumentEditor and back link", () => {
    const template = makeTemplate();
    render(
      <TemplateEditorClient
        slug="acme"
        template={template}
        readOnly={false}
      />,
    );

    expect(screen.getByTestId("document-editor")).toBeInTheDocument();
    expect(screen.getByText("Templates")).toBeInTheDocument();
    expect(screen.getByText("Editor with content")).toBeInTheDocument();
  });

  it("settings panel toggles visibility", async () => {
    const user = userEvent.setup();
    const template = makeTemplate();
    render(
      <TemplateEditorClient
        slug="acme"
        template={template}
        readOnly={false}
      />,
    );

    // Settings panel should be hidden by default
    expect(screen.queryByLabelText("Description")).not.toBeInTheDocument();

    // Click settings toggle
    await user.click(screen.getByTestId("settings-toggle"));

    // Settings panel should now be visible
    expect(screen.getByLabelText("Description")).toBeInTheDocument();
    expect(screen.getByLabelText("Name")).toBeInTheDocument();

    // Click again to hide
    await user.click(screen.getByTestId("settings-toggle"));
    expect(screen.queryByLabelText("Description")).not.toBeInTheDocument();
  });

  it("save button calls updateTemplateAction with JSON content", async () => {
    const user = userEvent.setup();
    const template = makeTemplate();
    mockUpdateTemplateAction.mockResolvedValue({ success: true });

    render(
      <TemplateEditorClient
        slug="acme"
        template={template}
        readOnly={false}
      />,
    );

    // Simulate an edit in the editor
    await user.click(screen.getByText("Simulate edit"));

    // Click save
    await user.click(screen.getByText("Save"));

    await waitFor(() => {
      expect(mockUpdateTemplateAction).toHaveBeenCalledWith(
        "acme",
        "tpl-1",
        expect.objectContaining({
          name: "Test Template",
          content: { type: "doc", content: [{ type: "paragraph" }] },
        }),
      );
    });
  });

  it("shows legacy content banner when template has legacyContent", () => {
    const template = makeTemplate({
      legacyContent: "<html><body>Old content</body></html>",
    });

    render(
      <TemplateEditorClient
        slug="acme"
        template={template}
        readOnly={false}
      />,
    );

    expect(screen.getByText("Migration needed")).toBeInTheDocument();
    expect(
      screen.getByText(/migrated from legacy HTML content/),
    ).toBeInTheDocument();
  });
});
