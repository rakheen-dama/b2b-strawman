import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { TemplateDetailResponse } from "@/lib/types";

// --- Mocks ---

const mockUpdateTemplateAction = vi.fn();
const mockCreateTemplateAction = vi.fn();

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
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
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
    updateTemplateAction: (...args: unknown[]) => mockUpdateTemplateAction(...args),
    createTemplateAction: (...args: unknown[]) => mockCreateTemplateAction(...args),
    fetchVariableMetadataAction: vi.fn(),
  }),
);

// DocumentEditor mock — supports onUpdate simulation for both variable and clause insertions
vi.mock("@/components/editor/DocumentEditor", () => ({
  DocumentEditor: ({
    onUpdate,
    editable,
    entityType,
  }: {
    content?: Record<string, unknown> | null;
    onUpdate?: (json: Record<string, unknown>) => void;
    editable?: boolean;
    entityType?: string;
  }) => (
    <div data-testid="document-editor" data-editable={editable} data-entity-type={entityType}>
      <button
        onClick={() =>
          onUpdate?.({
            type: "doc",
            content: [
              {
                type: "paragraph",
                content: [{ type: "text", text: "Hello" }],
              },
            ],
          })
        }
      >
        Simulate text edit
      </button>
      <button
        onClick={() =>
          onUpdate?.({
            type: "doc",
            content: [
              {
                type: "variable",
                attrs: { key: "project.name" },
              },
            ],
          })
        }
      >
        Simulate variable insert
      </button>
      <button
        onClick={() =>
          onUpdate?.({
            type: "doc",
            content: [
              {
                type: "clauseBlock",
                attrs: {
                  clauseId: "clause-1",
                  slug: "payment-terms",
                  title: "Payment Terms",
                  required: false,
                },
              },
            ],
          })
        }
      >
        Simulate clause insert
      </button>
    </div>
  ),
}));

vi.mock("@/components/templates/TemplatePreviewDialog", () => ({
  TemplatePreviewDialog: () => <button data-testid="preview-dialog">Preview</button>,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}));

// Import after mocks
import { TemplateEditorClient } from "@/components/templates/TemplateEditorClient";
import { CreateTemplateForm } from "@/components/templates/CreateTemplateForm";

function makeTemplate(overrides?: Partial<TemplateDetailResponse>): TemplateDetailResponse {
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

describe("Template save integration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("save sends JSON content to updateTemplateAction", async () => {
    const user = userEvent.setup();
    const template = makeTemplate();
    mockUpdateTemplateAction.mockResolvedValue({ success: true });

    render(
      <TemplateEditorClient slug="acme" template={template} readOnly={false} />,
    );

    await user.click(screen.getByText("Simulate text edit"));
    await user.click(screen.getByText("Save"));

    await waitFor(() => {
      expect(mockUpdateTemplateAction).toHaveBeenCalledWith(
        "acme",
        "tpl-1",
        expect.objectContaining({
          content: {
            type: "doc",
            content: [{ type: "paragraph", content: [{ type: "text", text: "Hello" }] }],
          },
        }),
      );
    });
  });

  it("variable insertion appears in saved content", async () => {
    const user = userEvent.setup();
    const template = makeTemplate();
    mockUpdateTemplateAction.mockResolvedValue({ success: true });

    render(
      <TemplateEditorClient slug="acme" template={template} readOnly={false} />,
    );

    await user.click(screen.getByText("Simulate variable insert"));
    await user.click(screen.getByText("Save"));

    await waitFor(() => {
      expect(mockUpdateTemplateAction).toHaveBeenCalledWith(
        "acme",
        "tpl-1",
        expect.objectContaining({
          content: {
            type: "doc",
            content: [{ type: "variable", attrs: { key: "project.name" } }],
          },
        }),
      );
    });
  });

  it("clause block insertion appears in saved content", async () => {
    const user = userEvent.setup();
    const template = makeTemplate();
    mockUpdateTemplateAction.mockResolvedValue({ success: true });

    render(
      <TemplateEditorClient slug="acme" template={template} readOnly={false} />,
    );

    await user.click(screen.getByText("Simulate clause insert"));
    await user.click(screen.getByText("Save"));

    await waitFor(() => {
      expect(mockUpdateTemplateAction).toHaveBeenCalledWith(
        "acme",
        "tpl-1",
        expect.objectContaining({
          content: {
            type: "doc",
            content: [
              {
                type: "clauseBlock",
                attrs: {
                  clauseId: "clause-1",
                  slug: "payment-terms",
                  title: "Payment Terms",
                  required: false,
                },
              },
            ],
          },
        }),
      );
    });
  });

  it("new template creation calls createTemplateAction with JSON content from editor", async () => {
    const user = userEvent.setup();
    mockCreateTemplateAction.mockResolvedValue({
      success: true,
      data: { id: "new-tpl-1" },
    });

    render(<CreateTemplateForm slug="acme" />);

    // Fill in name (required)
    await user.type(screen.getByLabelText("Name"), "My New Template");

    // Simulate editor content update
    await user.click(screen.getByText("Simulate text edit"));

    // Click create
    await user.click(screen.getByText("Create Template"));

    await waitFor(() => {
      expect(mockCreateTemplateAction).toHaveBeenCalledWith(
        "acme",
        expect.objectContaining({
          name: "My New Template",
          content: {
            type: "doc",
            content: [{ type: "paragraph", content: [{ type: "text", text: "Hello" }] }],
          },
        }),
      );
    });
  });
});
