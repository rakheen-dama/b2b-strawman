import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// FU-6b (rulings F-646C-1, F-652C-1): a second statement ending on the same date is refused unless
// it replaces the first — the upload offers "Replace existing statement" and re-submits with the
// replace parameter; the wizard's re-upload replaces its own statement; an imported statement can
// be discarded with a reason. Refusals show the server's detail.

vi.mock("server-only", () => ({}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const { mockPost, mockGetAuthFetchOptions, mockRefresh } = vi.hoisted(() => ({
  mockPost: vi.fn(),
  mockGetAuthFetchOptions: vi.fn(),
  mockRefresh: vi.fn(),
}));

vi.mock("@/lib/api", () => ({
  api: { get: vi.fn(), post: (...args: unknown[]) => mockPost(...args), put: vi.fn() },
  API_BASE: "http://backend",
  ApiError: class extends Error {},
  getAuthFetchOptions: (...args: unknown[]) => mockGetAuthFetchOptions(...args),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: mockRefresh, push: vi.fn() }),
}));

import {
  discardBankStatement,
  uploadBankStatement,
} from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import { BankStatementUpload } from "@/components/trust/BankStatementUpload";
import { DiscardStatementDialog } from "@/components/trust/DiscardStatementDialog";
import type { BankStatementResponse } from "@/lib/types";

function statement(overrides: Partial<BankStatementResponse> = {}): BankStatementResponse {
  return {
    id: "stmt-new",
    trustAccountId: "acc-1",
    periodStart: "2026-03-01",
    periodEnd: "2026-03-31",
    openingBalance: 0,
    closingBalance: 990,
    fileName: "march-full.csv",
    format: "CSV",
    lineCount: 2,
    matchedCount: 0,
    status: "IMPORTED",
    importedBy: "member-1",
    createdAt: "2026-04-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
    retiredAt: null,
    retiredBy: null,
    retiredReason: null,
    replacedByStatementId: null,
    lines: [],
    ...overrides,
  };
}

const csv = () => new File(["csv"], "march-full.csv", { type: "text/csv" });

afterEach(() => cleanup());

describe("uploadBankStatement (FU-6b)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockGetAuthFetchOptions.mockResolvedValue({ headers: {}, credentials: "include" });
  });

  it("sends replacesStatementId with the file when replacing", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(JSON.stringify(statement()), { status: 201 }));

    const result = await uploadBankStatement("acc-1", csv(), "stmt-old", "idem-key");

    expect(result.success).toBe(true);
    const body = fetchSpy.mock.calls[0][1]?.body as FormData;
    expect(body.get("replacesStatementId")).toBe("stmt-old");
    expect(body.get("file")).toBeInstanceOf(File);
  });

  it("sends no replace parameter on a plain upload", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(JSON.stringify(statement()), { status: 201 }));

    await uploadBankStatement("acc-1", csv(), undefined, "idem-key");

    const body = fetchSpy.mock.calls[0][1]?.body as FormData;
    expect(body.has("replacesStatementId")).toBe(false);
  });

  it("returns the duplicate statement from a 409 duplicate_end_date refusal", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          title: "A statement ending on this date is already imported",
          detail: "Statement march-intraday.csv of this trust account also ends on 31 March 2026.",
          code: "bank_statement.duplicate_end_date",
          existingStatementId: "stmt-old",
          existingFileName: "march-intraday.csv",
        }),
        { status: 409 }
      )
    );

    const result = await uploadBankStatement("acc-1", csv(), undefined, "idem-key");

    expect(result).toEqual({
      success: false,
      error: "Statement march-intraday.csv of this trust account also ends on 31 March 2026.",
      duplicate: { existingStatementId: "stmt-old", existingFileName: "march-intraday.csv" },
    });
  });

  it("maps the while-replacing refusal to a plain error with no duplicate (FU-6b D-2)", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          title: "A statement ending on this date is already imported",
          detail:
            "Statement march-c.csv also ends on 31 March 2026. Discard it from the Reconciliation page first, then upload again.",
          code: "bank_statement.duplicate_end_date_while_replacing",
        }),
        { status: 409 }
      )
    );

    const result = await uploadBankStatement("acc-1", csv(), "stmt-a", "idem-key");

    expect(result).toEqual({
      success: false,
      error:
        "Statement march-c.csv also ends on 31 March 2026. Discard it from the Reconciliation page first, then upload again.",
    });
  });

  it("returns a replace refusal's detail with no duplicate", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          title: "Cannot replace statement",
          detail: "1 line of this statement was recorded from the statement.",
        }),
        { status: 400 }
      )
    );

    const result = await uploadBankStatement("acc-1", csv(), "stmt-old", "idem-key");

    expect(result).toEqual({
      success: false,
      error: "1 line of this statement was recorded from the statement.",
    });
  });
});

describe("discardBankStatement (FU-6b)", () => {
  // Braces: a beforeEach that returns a function runs it as teardown (it would call mockPost).
  beforeEach(() => {
    mockPost.mockReset();
  });

  it("posts the reason to the discard endpoint", async () => {
    mockPost.mockResolvedValue(statement({ status: "RETIRED" }));

    const result = await discardBankStatement("stmt-1", "Uploaded in error", "idem-key");

    expect(mockPost).toHaveBeenCalledWith(
      "/api/bank-statements/stmt-1/discard",
      {
        reason: "Uploaded in error",
      },
      { headers: { "Idempotency-Key": expect.any(String) } }
    );
    expect(result.success).toBe(true);
  });

  it("returns the server's refusal", async () => {
    mockPost.mockRejectedValue(
      Object.assign(new Error("The draft reconciliation to 31 March 2026 uses it."), {
        status: 400,
      })
    );

    expect(await discardBankStatement("stmt-1", "x", "idem-key")).toEqual({
      success: false,
      error: "The draft reconciliation to 31 March 2026 uses it.",
    });
  });
});

describe("BankStatementUpload replace flow (FU-6b)", () => {
  it("offers to replace a same-end-date statement and re-submits with its id", async () => {
    const upload = vi
      .fn()
      .mockResolvedValueOnce({
        success: false,
        error: "Statement march-intraday.csv also ends on 31 March 2026.",
        duplicate: { existingStatementId: "stmt-old", existingFileName: "march-intraday.csv" },
      })
      .mockResolvedValueOnce({ success: true, data: statement() });
    const onComplete = vi.fn();
    render(
      <BankStatementUpload accountId="acc-1" onUploadComplete={onComplete} uploadAction={upload} />
    );

    fireEvent.change(screen.getByTestId("file-input"), { target: { files: [csv()] } });

    const dialog = await screen.findByTestId("replace-statement-dialog");
    expect(dialog).toHaveTextContent("Replace existing statement?");
    expect(dialog).toHaveTextContent("march-intraday.csv");
    expect(upload).toHaveBeenNthCalledWith(1, "acc-1", expect.any(File), undefined);
    expect(onComplete).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("replace-statement-confirm"));

    await waitFor(() => expect(screen.getByTestId("upload-result")).toBeInTheDocument());
    expect(upload).toHaveBeenNthCalledWith(2, "acc-1", expect.any(File), "stmt-old");
    expect(onComplete).toHaveBeenCalledWith(expect.objectContaining({ id: "stmt-new" }));
  });

  it("cancelling the replace stores nothing and calls no second upload", async () => {
    const upload = vi.fn().mockResolvedValue({
      success: false,
      error: "duplicate",
      duplicate: { existingStatementId: "stmt-old", existingFileName: "march-intraday.csv" },
    });
    render(
      <BankStatementUpload accountId="acc-1" onUploadComplete={vi.fn()} uploadAction={upload} />
    );

    fireEvent.change(screen.getByTestId("file-input"), { target: { files: [csv()] } });
    await screen.findByTestId("replace-statement-dialog");
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    await waitFor(() =>
      expect(screen.queryByTestId("replace-statement-dialog")).not.toBeInTheDocument()
    );
    expect(upload).toHaveBeenCalledTimes(1);
  });

  it("a wizard re-upload replaces the wizard's statement", async () => {
    const upload = vi.fn().mockResolvedValue({ success: true, data: statement() });
    render(
      <BankStatementUpload
        accountId="acc-1"
        onUploadComplete={vi.fn()}
        replacesStatementId="stmt-wizard"
        uploadAction={upload}
      />
    );

    fireEvent.change(screen.getByTestId("file-input"), { target: { files: [csv()] } });

    await waitFor(() =>
      expect(upload).toHaveBeenCalledWith("acc-1", expect.any(File), "stmt-wizard")
    );
  });

  // FU-6b D-2: the wizard holds A and uploads B replacing A, but B clashes with another statement C.
  // One upload replaces at most one statement: the refusal is shown plainly, never a second replace.
  it("never offers a second replace when an upload that replaces a statement clashes with another", async () => {
    const upload = vi
      .fn()
      .mockResolvedValueOnce({
        // the backend's own refusal carries no duplicate
        success: false,
        error:
          "Statement march-c.csv also ends on 31 March 2026. Discard it from the Reconciliation page first, then upload again.",
      })
      .mockResolvedValueOnce({
        // even a duplicate (defensive) is not offered while replacing
        success: false,
        error: "Statement march-c.csv also ends on 31 March 2026.",
        duplicate: { existingStatementId: "stmt-c", existingFileName: "march-c.csv" },
      });
    render(
      <BankStatementUpload
        accountId="acc-1"
        onUploadComplete={vi.fn()}
        replacesStatementId="stmt-a"
        uploadAction={upload}
      />
    );

    fireEvent.change(screen.getByTestId("file-input"), { target: { files: [csv()] } });
    expect(await screen.findByTestId("upload-error")).toHaveTextContent(
      "Discard it from the Reconciliation page first, then upload again."
    );
    expect(screen.queryByTestId("replace-statement-dialog")).not.toBeInTheDocument();

    fireEvent.change(screen.getByTestId("file-input"), { target: { files: [csv()] } });
    await waitFor(() => expect(upload).toHaveBeenCalledTimes(2));
    expect(await screen.findByTestId("upload-error")).toHaveTextContent(
      "Statement march-c.csv also ends on 31 March 2026."
    );
    expect(screen.queryByTestId("replace-statement-dialog")).not.toBeInTheDocument();
    expect(upload).toHaveBeenNthCalledWith(2, "acc-1", expect.any(File), "stmt-a");
  });

  it("shows a replace refusal's detail", async () => {
    const upload = vi.fn().mockResolvedValue({
      success: false,
      error: "This statement's reconciliation is complete, so it cannot be replaced.",
    });
    render(
      <BankStatementUpload
        accountId="acc-1"
        onUploadComplete={vi.fn()}
        replacesStatementId="stmt-wizard"
        uploadAction={upload}
      />
    );

    fireEvent.change(screen.getByTestId("file-input"), { target: { files: [csv()] } });

    expect(await screen.findByTestId("upload-error")).toHaveTextContent(
      "This statement's reconciliation is complete, so it cannot be replaced."
    );
    expect(screen.queryByTestId("replace-statement-dialog")).not.toBeInTheDocument();
  });
});

describe("DiscardStatementDialog (FU-6b)", () => {
  beforeEach(() => {
    mockPost.mockReset();
    mockRefresh.mockReset();
  });

  it("requires a reason, then discards and refreshes", async () => {
    mockPost.mockResolvedValue(statement({ status: "RETIRED" }));
    const user = userEvent.setup();
    render(<DiscardStatementDialog statementId="stmt-left" fileName="leftover.csv" />);

    await user.click(screen.getByTestId("discard-statement-stmt-left"));
    expect(await screen.findByTestId("discard-statement-dialog")).toHaveTextContent("leftover.csv");
    await user.click(screen.getByTestId("discard-statement-confirm"));
    expect(await screen.findByText("Reason is required")).toBeInTheDocument();
    expect(mockPost).not.toHaveBeenCalled();

    await user.type(screen.getByTestId("discard-reason-input"), "Uploaded in error");
    await user.click(screen.getByTestId("discard-statement-confirm"));

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(
        "/api/bank-statements/stmt-left/discard",
        {
          reason: "Uploaded in error",
        },
        { headers: { "Idempotency-Key": expect.any(String) } }
      )
    );
    await waitFor(() => expect(mockRefresh).toHaveBeenCalled());
    await waitFor(() =>
      expect(screen.queryByTestId("discard-statement-dialog")).not.toBeInTheDocument()
    );
  });

  it("shows the server's refusal and stays open", async () => {
    mockPost.mockRejectedValue(
      new Error(
        "The draft reconciliation to 31 March 2026 uses this statement. Continue that reconciliation with another statement, or import the corrected statement as its replacement."
      )
    );
    const user = userEvent.setup();
    render(<DiscardStatementDialog statementId="stmt-draft" fileName="march.csv" />);

    await user.click(screen.getByTestId("discard-statement-stmt-draft"));
    await user.type(await screen.findByTestId("discard-reason-input"), "Wrong file");
    await user.click(screen.getByTestId("discard-statement-confirm"));

    expect(await screen.findByTestId("discard-error")).toHaveTextContent(
      "The draft reconciliation to 31 March 2026 uses this statement."
    );
    expect(screen.getByTestId("discard-statement-dialog")).toBeInTheDocument();
    expect(mockRefresh).not.toHaveBeenCalled();
  });
});
