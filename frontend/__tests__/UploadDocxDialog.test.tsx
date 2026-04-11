import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UploadDocxDialog } from "@/app/(app)/org/[slug]/settings/templates/UploadDocxDialog";

const mockUploadDocxTemplate = vi.fn();
const mockPush = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/templates/template-crud-actions", () => ({
  createTemplateAction: vi.fn(),
  updateTemplateAction: vi.fn(),
  cloneTemplateAction: vi.fn(),
  resetTemplateAction: vi.fn(),
  deactivateTemplateAction: vi.fn(),
  previewTemplateAction: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/settings/templates/template-support-actions", () => ({
  uploadDocxTemplateAction: (...args: unknown[]) => mockUploadDocxTemplate(...args),
  uploadLogoAction: vi.fn(),
  deleteLogoAction: vi.fn(),
  saveBrandingAction: vi.fn(),
  fetchRequiredFieldPacksAction: vi.fn(),
  fetchVariableMetadataAction: vi.fn(),
  fetchProjectsForPicker: vi.fn(),
  fetchCustomersForPicker: vi.fn(),
  fetchInvoicesForPicker: vi.fn(),
  getDocxFieldsAction: vi.fn(),
  downloadDocxTemplateAction: vi.fn(),
  replaceDocxFileAction: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/settings/templates/template-generation-actions", () => ({
  generateDocumentAction: vi.fn(),
  fetchGeneratedDocumentsAction: vi.fn(),
  deleteGeneratedDocumentAction: vi.fn(),
  downloadGeneratedDocumentAction: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, refresh: vi.fn() }),
}));

const DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

function renderDialog() {
  return render(
    <UploadDocxDialog slug="test-org">
      <button>Open Upload DOCX 325A</button>
    </UploadDocxDialog>
  );
}

describe("UploadDocxDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("opens dialog when trigger is clicked", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Upload DOCX 325A"));

    await waitFor(() => {
      expect(screen.getByText("Upload Word Template")).toBeInTheDocument();
    });

    expect(screen.getByLabelText("Name")).toBeInTheDocument();
    expect(screen.getByLabelText("Category")).toBeInTheDocument();
    expect(screen.getByLabelText("Entity Type")).toBeInTheDocument();
  });

  it("shows validation error for non-.docx file", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Upload DOCX 325A"));
    await waitFor(() => {
      expect(screen.getByText("Upload Word Template")).toBeInTheDocument();
    });

    const pdfFile = new File(["fake pdf"], "doc.pdf", {
      type: "application/pdf",
    });

    const input = screen.getByTestId("docx-file-input");

    // Use fireEvent.change to bypass happy-dom stack overflow with userEvent.upload
    fireEvent.change(input, { target: { files: [pdfFile] } });

    await waitFor(() => {
      expect(screen.getByText("Only .docx files are accepted.")).toBeInTheDocument();
    });
  });

  it("shows validation error for file exceeding 10MB", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Upload DOCX 325A"));
    await waitFor(() => {
      expect(screen.getByText("Upload Word Template")).toBeInTheDocument();
    });

    const bigFile = new File(["x"], "big.docx", { type: DOCX_MIME });
    Object.defineProperty(bigFile, "size", { value: 11 * 1024 * 1024 });

    const input = screen.getByTestId("docx-file-input");
    fireEvent.change(input, { target: { files: [bigFile] } });

    await waitFor(() => {
      expect(screen.getByText("File size exceeds 10 MB.")).toBeInTheDocument();
    });
  });

  it("calls uploadDocxTemplateAction on submit with valid data", async () => {
    mockUploadDocxTemplate.mockResolvedValue({
      success: true,
      data: { id: "tmpl-123" },
    });

    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Upload DOCX 325A"));
    await waitFor(() => {
      expect(screen.getByText("Upload Word Template")).toBeInTheDocument();
    });

    const docxFile = new File(["fake docx content"], "template.docx", {
      type: DOCX_MIME,
    });

    const input = screen.getByTestId("docx-file-input");
    fireEvent.change(input, { target: { files: [docxFile] } });

    // Name should be auto-populated from filename
    await waitFor(() => {
      expect(screen.getByLabelText("Name")).toHaveValue("template");
    });

    await user.click(screen.getByText("Upload Template"));

    await waitFor(() => {
      expect(mockUploadDocxTemplate).toHaveBeenCalledWith("test-org", expect.any(FormData));
    });

    // Should redirect to edit page
    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith("/org/test-org/settings/templates/tmpl-123/edit");
    });
  });

  it("shows error message when action returns error", async () => {
    mockUploadDocxTemplate.mockResolvedValue({
      success: false,
      error: "A template with this slug already exists.",
    });

    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText("Open Upload DOCX 325A"));
    await waitFor(() => {
      expect(screen.getByText("Upload Word Template")).toBeInTheDocument();
    });

    const docxFile = new File(["fake docx content"], "template.docx", {
      type: DOCX_MIME,
    });

    const input = screen.getByTestId("docx-file-input");
    fireEvent.change(input, { target: { files: [docxFile] } });

    await waitFor(() => {
      expect(screen.getByLabelText("Name")).toHaveValue("template");
    });

    await user.click(screen.getByText("Upload Template"));

    await waitFor(() => {
      expect(screen.getByText("A template with this slug already exists.")).toBeInTheDocument();
    });
  });
});
