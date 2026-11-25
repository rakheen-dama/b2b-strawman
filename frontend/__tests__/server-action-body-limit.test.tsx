import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// FR-3 (founder ruling 2026-11-24, 653A F-2): the shared server-action body limit is 3 MB. Every
// file that goes through a server action is refused in the browser above 3 MB (3 000 000 bytes),
// so a body over the limit never reaches Next (whose own refusal is not readable).

import nextConfig from "@/next.config";
import { BankStatementUpload } from "@/components/trust/BankStatementUpload";

function fileOf(name: string, size: number, type: string) {
  const file = new File(["x"], name, { type });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

afterEach(() => cleanup());

describe("server-action body limit (FR-3)", () => {
  it("is 3 MB", () => {
    expect(nextConfig.experimental?.serverActions?.bodySizeLimit).toBe("3mb");
  });
});

describe("bank statement upload size (FR-3)", () => {
  it("refuses a statement over 3 MB in the browser, without calling the action", async () => {
    const uploadAction = vi.fn();
    const user = userEvent.setup({ applyAccept: false });
    render(
      <BankStatementUpload
        accountId="acc-1"
        onUploadComplete={vi.fn()}
        uploadAction={uploadAction}
      />
    );
    await user.upload(screen.getByTestId("file-input"), fileOf("big.csv", 3_000_001, "text/csv"));
    expect(screen.getByTestId("upload-error")).toHaveTextContent(
      "File exceeds the 3 MB limit (3.1 MB)"
    );
    expect(uploadAction).not.toHaveBeenCalled();
  });

  it("states the 3 MB limit and sends a statement of exactly 3 MB", async () => {
    const uploadAction = vi.fn().mockResolvedValue({ success: false, error: "refused" });
    const user = userEvent.setup({ applyAccept: false });
    render(
      <BankStatementUpload
        accountId="acc-1"
        onUploadComplete={vi.fn()}
        uploadAction={uploadAction}
      />
    );
    expect(screen.getByText(/up to 3 MB/)).toBeInTheDocument();
    await user.upload(screen.getByTestId("file-input"), fileOf("ok.csv", 3_000_000, "text/csv"));
    expect(uploadAction).toHaveBeenCalledTimes(1);
  });
});
