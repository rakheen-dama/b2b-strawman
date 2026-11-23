import { describe, it, expect, vi, afterEach } from "vitest";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import type { ReactNode } from "react";

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/settings/matters",
  useSearchParams: () => new URLSearchParams(),
}));

const actions = vi.hoisted(() => ({
  uploadTakeOnFile: vi.fn(),
  addTakeOnSource: vi.fn(),
  removeTakeOnSource: vi.fn(),
  setSheet: vi.fn(),
  saveMapping: vi.fn(),
  savePosition: vi.fn(),
  validateBatch: vi.fn(),
  cancelBatch: vi.fn(),
  attest: vi.fn(),
  revokeSignOff: vi.fn(),
  commitBatch: vi.fn(),
  createPortalContacts: vi.fn(),
  downloadTakeOnResult: vi.fn(),
  fetchTakeOnBatch: vi.fn(),
  fetchTakeOnPreview: vi.fn(),
  fetchTakeOnRows: vi.fn(),
  fetchTakeOnCustomFields: vi.fn(),
  fetchTakeOnBankingAccounts: vi.fn(),
  downloadTakeOnTemplate: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import { ResultsStep, COMMIT_POLL_MS } from "@/components/take-on/results-step";
import { TakeOnWizard } from "@/components/take-on/take-on-wizard";
import type { TakeOnBatch } from "@/lib/types";
import { blobText, stubDownload } from "../helpers/csv-download";
import { ATTESTATION, CLIENTS_CATALOGUE, clientsBatch } from "./fixtures";

const withSWR = (ui: ReactNode) => (
  <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>{ui}</SWRConfig>
);

function committing(done: { created: number; skipped: number; failed: number }): TakeOnBatch {
  const base = clientsBatch();
  return clientsBatch({
    status: "COMMITTING",
    signOffs: [ATTESTATION],
    attestationStatement: null,
    allowedActions: [],
    commitStartedAt: "2026-11-23T10:05:00Z",
    counts: { ...base.counts, ...done },
  });
}

const COMPLETED = clientsBatch({
  status: "COMPLETED",
  signOffs: [ATTESTATION],
  attestationStatement: null,
  allowedActions: ["DOWNLOAD_RESULT", "CREATE_PORTAL_ACCESS"],
  commitStartedAt: "2026-11-23T10:05:00Z",
  completedAt: "2026-11-23T10:06:00Z",
  counts: { ...clientsBatch().counts, created: 7, skipped: 2, failed: 1 },
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  vi.clearAllMocks();
});

describe("TakeOnWizard — commit progress", () => {
  it("polls the batch while committing, shows Committing… n of m, then the results", async () => {
    vi.useFakeTimers();
    actions.fetchTakeOnBatch
      .mockResolvedValueOnce({
        success: true,
        data: committing({ created: 3, skipped: 0, failed: 0 }),
      })
      .mockResolvedValueOnce({
        success: true,
        data: committing({ created: 5, skipped: 1, failed: 1 }),
      })
      .mockResolvedValue({ success: true, data: COMPLETED });
    actions.fetchTakeOnRows.mockResolvedValue({
      success: true,
      data: { content: [], page: { totalElements: 0, totalPages: 0, size: 50, number: 0 } },
    });

    render(
      withSWR(
        <TakeOnWizard
          batch={committing({ created: 0, skipped: 0, failed: 0 })}
          dataset={CLIENTS_CATALOGUE}
        />
      )
    );
    expect(screen.getByTestId("take-on-step-results")).toHaveAttribute("aria-current", "step");
    expect(screen.getByTestId("take-on-commit-progress").textContent).toBe("Committing… 0 of 10");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByTestId("take-on-commit-progress").textContent).toBe("Committing… 3 of 10");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(COMMIT_POLL_MS);
    });
    expect(screen.getByTestId("take-on-commit-progress").textContent).toBe("Committing… 7 of 10");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(COMMIT_POLL_MS);
    });
    expect(screen.queryByTestId("take-on-commit-progress")).not.toBeInTheDocument();
    expect(screen.getByTestId("take-on-result-created")).toHaveTextContent("7");
    expect(screen.getByTestId("take-on-batch-status")).toHaveTextContent("Completed");

    const calls = actions.fetchTakeOnBatch.mock.calls.length;
    expect(calls).toBe(3);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(COMMIT_POLL_MS * 3);
    });
    expect(actions.fetchTakeOnBatch).toHaveBeenCalledTimes(calls); // polling stopped
    expect(actions.fetchTakeOnBatch).toHaveBeenCalledWith("batch-655");
  });
});

describe("ResultsStep — completed", () => {
  it("shows the counts with X3 labels and the result report download", () => {
    render(withSWR(<ResultsStep batch={COMPLETED} onBatchChange={vi.fn()} />));
    expect(screen.getByTestId("take-on-result-created")).toHaveTextContent("Created7");
    expect(screen.getByTestId("take-on-result-skipped")).toHaveTextContent("Duplicate — skipped2");
    expect(screen.getByTestId("take-on-result-failed")).toHaveTextContent("Failed1");
    expect(screen.getByTestId("take-on-result-failed-note")).toHaveTextContent(
      "1 row was not committed."
    );
    expect(screen.getByTestId("take-on-result-download")).toHaveTextContent(
      "Download the result report"
    );
  });

  it("offers neither the download nor portal access without their actions", () => {
    render(
      withSWR(<ResultsStep batch={{ ...COMPLETED, allowedActions: [] }} onBatchChange={vi.fn()} />)
    );
    expect(screen.queryByTestId("take-on-result-download")).not.toBeInTheDocument();
    expect(screen.queryByTestId("take-on-portal-access")).not.toBeInTheDocument();
  });

  it("downloads the result CSV through the action, with its file name and the BOM", async () => {
    const user = userEvent.setup();
    const download = stubDownload();
    actions.downloadTakeOnResult.mockResolvedValue({
      success: true,
      data: { fileName: "take-on-result-batch-655.csv", content: "Row,Status\r\n2,Created\r\n" },
    });
    render(withSWR(<ResultsStep batch={COMPLETED} onBatchChange={vi.fn()} />));
    await user.click(screen.getByTestId("take-on-result-download"));
    await waitFor(() =>
      expect(download.captured.downloads).toEqual(["take-on-result-batch-655.csv"])
    );
    expect(actions.downloadTakeOnResult).toHaveBeenCalledWith("batch-655");
    expect(await blobText(download.captured.blobs[0])).toBe("﻿Row,Status\r\n2,Created\r\n");
    await download.restore();
  });

  it("creates portal access, says no email is sent, and shows the counts", async () => {
    const user = userEvent.setup();
    const onBatchChange = vi.fn();
    actions.createPortalContacts.mockResolvedValue({
      success: true,
      data: { batch: COMPLETED, createdCount: 6, skippedCount: 1 },
    });
    render(withSWR(<ResultsStep batch={COMPLETED} onBatchChange={onBatchChange} />));
    expect(screen.getByTestId("take-on-portal")).toHaveTextContent("No email is sent");
    await user.click(screen.getByTestId("take-on-portal-access"));
    expect(actions.createPortalContacts).toHaveBeenCalledWith("batch-655");
    expect((await screen.findByTestId("take-on-portal-result")).textContent).toBe(
      "6 portal contacts created · 1 already had access"
    );
    expect(onBatchChange).toHaveBeenCalledWith(COMPLETED);
  });

  it("shows a portal-access refusal verbatim", async () => {
    const user = userEvent.setup();
    const detail = "Portal access can be created once the take-on has been committed.";
    actions.createPortalContacts.mockResolvedValue({ success: false, error: detail });
    render(withSWR(<ResultsStep batch={COMPLETED} onBatchChange={vi.fn()} />));
    await user.click(screen.getByTestId("take-on-portal-access"));
    expect((await screen.findByTestId("take-on-portal-error")).textContent).toBe(detail);
  });
});
