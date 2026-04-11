import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditRequestTemplateForm } from "@/components/information-requests/edit-request-template-form";
import { CreateRequestTemplateForm } from "@/components/information-requests/create-request-template-form";
import { RequestTemplateActions } from "@/components/information-requests/request-template-actions";
import { ResponseTypeBadge } from "@/components/information-requests/response-type-badge";
import { TemplateSourceBadge } from "@/components/information-requests/template-source-badge";
import type { RequestTemplateResponse } from "@/lib/api/information-requests";

const mockCreateTemplateAction = vi.fn();
const mockUpdateTemplateAction = vi.fn();
const mockDeactivateTemplateAction = vi.fn();
const mockDuplicateTemplateAction = vi.fn();
const mockPush = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock("@/app/(app)/org/[slug]/settings/request-templates/actions", () => ({
  createTemplateAction: (...args: unknown[]) => mockCreateTemplateAction(...args),
  updateTemplateAction: (...args: unknown[]) => mockUpdateTemplateAction(...args),
  deactivateTemplateAction: (...args: unknown[]) => mockDeactivateTemplateAction(...args),
  duplicateTemplateAction: (...args: unknown[]) => mockDuplicateTemplateAction(...args),
}));

function makeTemplate(overrides?: Partial<RequestTemplateResponse>): RequestTemplateResponse {
  return {
    id: "t1",
    name: "Tax Documents",
    description: "Documents needed for annual tax return",
    source: "CUSTOM",
    packId: null,
    active: true,
    items: [
      {
        id: "i1",
        templateId: "t1",
        name: "ID Copy",
        description: "Certified copy of identification",
        responseType: "FILE_UPLOAD",
        required: true,
        fileTypeHints: "PDF, JPG",
        sortOrder: 0,
        createdAt: "2026-01-01T00:00:00Z",
      },
      {
        id: "i2",
        templateId: "t1",
        name: "Tax Reference Number",
        description: null,
        responseType: "TEXT_RESPONSE",
        required: false,
        fileTypeHints: null,
        sortOrder: 1,
        createdAt: "2026-01-01T00:00:00Z",
      },
    ],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("RequestTemplates", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders ResponseTypeBadge with correct label for FILE_UPLOAD", () => {
    render(<ResponseTypeBadge responseType="FILE_UPLOAD" />);
    expect(screen.getByText("File Upload")).toBeInTheDocument();
  });

  it("renders ResponseTypeBadge with correct label for TEXT_RESPONSE", () => {
    render(<ResponseTypeBadge responseType="TEXT_RESPONSE" />);
    expect(screen.getByText("Text")).toBeInTheDocument();
  });

  it("renders TemplateSourceBadge for PLATFORM and CUSTOM", () => {
    const { unmount } = render(<TemplateSourceBadge source="PLATFORM" />);
    expect(screen.getByText("Platform")).toBeInTheDocument();
    unmount();

    render(<TemplateSourceBadge source="CUSTOM" />);
    expect(screen.getByText("Custom")).toBeInTheDocument();
  });

  it("renders edit form with template name and items", () => {
    const template = makeTemplate();
    render(<EditRequestTemplateForm slug="acme" template={template} />);

    expect(screen.getByDisplayValue("Tax Documents")).toBeInTheDocument();
    expect(screen.getByDisplayValue("ID Copy")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Tax Reference Number")).toBeInTheDocument();
  });

  it("adds a new item to the editor", async () => {
    const user = userEvent.setup();
    const template = makeTemplate({ items: [] });
    render(<EditRequestTemplateForm slug="acme" template={template} />);

    const addButton = screen.getByRole("button", { name: /add item/i });
    await user.click(addButton);

    // A new empty item input should appear
    expect(screen.getByPlaceholderText("Item name")).toBeInTheDocument();
  });

  it("removes an item from the editor", async () => {
    const user = userEvent.setup();
    const template = makeTemplate();
    render(<EditRequestTemplateForm slug="acme" template={template} />);

    // Should have 2 items initially
    expect(screen.getByDisplayValue("ID Copy")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Tax Reference Number")).toBeInTheDocument();

    // Click the first delete button
    const deleteButtons = screen.getAllByRole("button", { name: "" });
    // Find the Trash2 button — they have sr-only text or no visible text
    const trashButtons = deleteButtons.filter((btn) => {
      const svg = btn.querySelector("svg");
      return svg && btn.className.includes("text-red");
    });
    if (trashButtons.length > 0) {
      await user.click(trashButtons[0]);
    }

    // ID Copy should be removed
    expect(screen.queryByDisplayValue("ID Copy")).not.toBeInTheDocument();
  });

  it("saves template with updated name", async () => {
    const user = userEvent.setup();
    mockUpdateTemplateAction.mockResolvedValue({ success: true });
    const template = makeTemplate();
    render(<EditRequestTemplateForm slug="acme" template={template} />);

    const nameInput = screen.getByDisplayValue("Tax Documents");
    await user.clear(nameInput);
    await user.type(nameInput, "Updated Template");

    const saveButton = screen.getByRole("button", { name: /save changes/i });
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockUpdateTemplateAction).toHaveBeenCalledWith(
        "acme",
        "t1",
        expect.objectContaining({ name: "Updated Template" })
      );
    });
  });

  it("shows platform template as read-only with duplicate button", () => {
    const template = makeTemplate({ source: "PLATFORM" });
    render(<EditRequestTemplateForm slug="acme" template={template} />);

    // Should show the template name as text, not an input
    expect(screen.getByText("Tax Documents")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /duplicate to customize/i })).toBeInTheDocument();

    // Should NOT have editable inputs
    expect(screen.queryByDisplayValue("Tax Documents")).not.toBeInTheDocument();
  });

  it("duplicates a platform template", async () => {
    const user = userEvent.setup();
    mockDuplicateTemplateAction.mockResolvedValue({
      success: true,
      data: { id: "t2", name: "Tax Documents (Copy)" },
    });
    const template = makeTemplate({ source: "PLATFORM" });
    render(<EditRequestTemplateForm slug="acme" template={template} />);

    const duplicateButton = screen.getByRole("button", {
      name: /duplicate to customize/i,
    });
    await user.click(duplicateButton);

    await waitFor(() => {
      expect(mockDuplicateTemplateAction).toHaveBeenCalledWith("acme", "t1");
    });
  });

  it("creates a new template and redirects", async () => {
    const user = userEvent.setup();
    mockCreateTemplateAction.mockResolvedValue({
      success: true,
      data: { id: "t3" },
    });
    render(<CreateRequestTemplateForm slug="acme" />);

    const nameInput = screen.getByLabelText("Name");
    await user.type(nameInput, "New Template");

    const createButton = screen.getByRole("button", {
      name: /create template/i,
    });
    await user.click(createButton);

    await waitFor(() => {
      expect(mockCreateTemplateAction).toHaveBeenCalledWith("acme", {
        name: "New Template",
        description: undefined,
      });
    });

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith("/org/acme/settings/request-templates/t3");
    });
  });

  it("renders actions with edit and deactivate for custom templates", () => {
    render(
      <RequestTemplateActions
        slug="acme"
        templateId="t1"
        templateName="Tax Documents"
        source="CUSTOM"
      />
    );

    expect(screen.getByRole("button", { name: /edit tax documents/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /duplicate tax documents/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /deactivate tax documents/i })).toBeInTheDocument();
  });

  it("renders actions without edit/deactivate for platform templates", () => {
    render(
      <RequestTemplateActions
        slug="acme"
        templateId="t1"
        templateName="Platform Template"
        source="PLATFORM"
      />
    );

    expect(
      screen.queryByRole("button", { name: /edit platform template/i })
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /duplicate platform template/i })
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /deactivate platform template/i })
    ).not.toBeInTheDocument();
  });
});
