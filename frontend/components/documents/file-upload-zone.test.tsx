import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, fireEvent, cleanup, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FileUploadZone } from "./file-upload-zone";

vi.mock("lucide-react", () => ({
  Upload: () => <svg data-testid="upload-icon" />,
}));

afterEach(cleanup);

describe("FileUploadZone", () => {
  it("renders the drop zone with expected text", () => {
    render(<FileUploadZone onFilesSelected={vi.fn()} />);

    expect(
      screen.getByText("Drag and drop files here, or click to browse"),
    ).toBeInTheDocument();
    expect(screen.getByText("Max 100 MB per file")).toBeInTheDocument();
  });

  it("calls onFilesSelected when a file is chosen via input", async () => {
    const onFilesSelected = vi.fn();
    const { container } = render(
      <FileUploadZone onFilesSelected={onFilesSelected} />,
    );

    const input = container.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    const file = new File(["hello"], "test.pdf", {
      type: "application/pdf",
    });

    await userEvent.upload(input, file);

    expect(onFilesSelected).toHaveBeenCalledWith([file]);
  });

  it("does not trigger file picker when disabled", () => {
    const { container } = render(
      <FileUploadZone onFilesSelected={vi.fn()} disabled={true} />,
    );

    const button = within(container).getByRole("button");
    expect(button).toHaveAttribute("tabindex", "-1");

    const input = container.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    expect(input.disabled).toBe(true);
  });

  it("calls onFilesSelected when files are dropped", () => {
    const onFilesSelected = vi.fn();
    const { container } = render(
      <FileUploadZone onFilesSelected={onFilesSelected} />,
    );

    const dropZone = within(container).getByRole("button");
    const file = new File(["data"], "doc.pdf", {
      type: "application/pdf",
    });

    fireEvent.drop(dropZone, {
      dataTransfer: { files: [file] },
    });

    expect(onFilesSelected).toHaveBeenCalledWith([file]);
  });

  it("does not call onFilesSelected when dropped while disabled", () => {
    const onFilesSelected = vi.fn();
    const { container } = render(
      <FileUploadZone onFilesSelected={onFilesSelected} disabled={true} />,
    );

    const dropZone = within(container).getByRole("button");
    const file = new File(["data"], "doc.pdf", {
      type: "application/pdf",
    });

    fireEvent.drop(dropZone, {
      dataTransfer: { files: [file] },
    });

    expect(onFilesSelected).not.toHaveBeenCalled();
  });

  it("sets accept attribute with allowed file types", () => {
    const { container } = render(
      <FileUploadZone onFilesSelected={vi.fn()} />,
    );

    const input = container.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;
    expect(input.accept).toContain(".pdf");
    expect(input.accept).toContain(".docx");
    expect(input.accept).toContain(".png");
  });
});
