import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import type { ReactNode } from "react";

vi.mock("server-only", () => ({}));

const actions = vi.hoisted(() => ({
  getTakeOnReport: vi.fn(),
  downloadTakeOnReport: vi.fn(),
  retryReportRender: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import {
  REPORT_FILES_FAILED,
  REPORT_FILES_PREPARING,
  ReportDownloads,
} from "@/components/take-on/report-downloads";
import type { TakeOnReportFiles, TakeOnReportFileStatus } from "@/lib/types";
import { blobText, stubDownload } from "../helpers/csv-download";
import { TAKE_ON_REPORT_17313 } from "./fixtures";

const withSWR = (ui: ReactNode) => (
  <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>{ui}</SWRConfig>
);

function files(pdf: TakeOnReportFileStatus, csv: TakeOnReportFileStatus = pdf): TakeOnReportFiles {
  const state = (status: TakeOnReportFileStatus) =>
    status === "READY"
      ? { status, sha256: "b".repeat(64), fileSize: 10, renderedAt: "2026-11-24T10:00:05Z" }
      : { status, sha256: null, fileSize: null, renderedAt: null };
  return { pdf: state(pdf), csv: state(csv) };
}

function reportWith(f: TakeOnReportFiles) {
  return { success: true, data: { ...TAKE_ON_REPORT_17313, files: f } };
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ReportDownloads (657D)", () => {
  it("offers both files when they are ready, and saves the PDF bytes unchanged", async () => {
    actions.getTakeOnReport.mockResolvedValue(reportWith(files("READY")));
    actions.downloadTakeOnReport.mockResolvedValue({
      success: true,
      data: {
        status: "READY",
        fileName: "take-on-report-trust-account-g-2026-10-31.pdf",
        contentType: "application/pdf",
        base64: btoa("%PDF-1.4 bytes"),
      },
    });
    const download = stubDownload();
    try {
      render(withSWR(<ReportDownloads reportId="report-1" files={files("READY")} />));
      await userEvent.click(screen.getByTestId("take-on-report-download-pdf"));
      await waitFor(() => expect(download.captured.downloads).toHaveLength(1));
      expect(actions.downloadTakeOnReport).toHaveBeenCalledWith("report-1", "pdf");
      expect(download.captured.downloads[0]).toBe("take-on-report-trust-account-g-2026-10-31.pdf");
      expect(download.captured.blobs[0].type).toBe("application/pdf");
      expect(await blobText(download.captured.blobs[0])).toBe("%PDF-1.4 bytes");
      expect(screen.getByTestId("take-on-report-download-csv")).toHaveTextContent("Download CSV");
      expect(screen.queryByTestId("take-on-report-files-preparing")).not.toBeInTheDocument();
      expect(screen.queryByTestId("take-on-report-files-failed")).not.toBeInTheDocument();
      // both files given as ready: the report is never read
      expect(actions.getTakeOnReport).not.toHaveBeenCalled();
    } finally {
      await download.restore();
    }
  });

  it("given both files ready, reads the report only once a download finds one rendering", async () => {
    actions.getTakeOnReport.mockResolvedValue(reportWith(files("READY", "RENDERING")));
    actions.downloadTakeOnReport.mockResolvedValue({
      success: true,
      data: { status: "RENDERING", retryAfterSeconds: 5 },
    });
    render(withSWR(<ReportDownloads reportId="report-1" files={files("READY")} />));
    expect(actions.getTakeOnReport).not.toHaveBeenCalled();
    await userEvent.click(screen.getByTestId("take-on-report-download-csv"));
    expect(await screen.findByTestId("take-on-report-files-preparing")).toBeInTheDocument();
    expect(actions.getTakeOnReport).toHaveBeenCalledWith("report-1");
    expect(screen.queryByTestId("take-on-report-download-error")).not.toBeInTheDocument();
  });

  it("reads the report under one key: two readers of the same report fetch it once", async () => {
    actions.getTakeOnReport.mockResolvedValue(reportWith(files("READY")));
    render(
      withSWR(
        <>
          <ReportDownloads reportId="report-1" />
          <ReportDownloads reportId="report-1" />
        </>
      )
    );
    await waitFor(() =>
      expect(screen.getAllByTestId("take-on-report-download-pdf")).toHaveLength(2)
    );
    expect(actions.getTakeOnReport).toHaveBeenCalledTimes(1);
  });

  it("says the files are being prepared while they render, then offers them", async () => {
    actions.getTakeOnReport
      .mockResolvedValueOnce(reportWith(files("RENDERING")))
      .mockResolvedValue(reportWith(files("READY")));
    render(withSWR(<ReportDownloads reportId="report-1" />));

    const preparing = await screen.findByTestId("take-on-report-files-preparing");
    expect(preparing).toHaveTextContent(REPORT_FILES_PREPARING);
    expect(preparing).toHaveAttribute("role", "status");
    expect(screen.queryByTestId("take-on-report-download-pdf")).not.toBeInTheDocument();
    // re-read every three seconds while rendering
    expect(
      await screen.findByTestId("take-on-report-download-pdf", {}, { timeout: 5000 })
    ).toBeInTheDocument();
    expect(screen.queryByTestId("take-on-report-files-preparing")).not.toBeInTheDocument();
  }, 10000);

  it("a 202 on download (still rendering) shows the preparing state, never an error", async () => {
    actions.getTakeOnReport
      .mockResolvedValueOnce(reportWith(files("READY")))
      .mockResolvedValue(reportWith(files("READY", "RENDERING")));
    actions.downloadTakeOnReport.mockResolvedValue({
      success: true,
      data: { status: "RENDERING", retryAfterSeconds: 5 },
    });
    render(withSWR(<ReportDownloads reportId="report-1" />));
    await userEvent.click(await screen.findByTestId("take-on-report-download-csv"));
    expect(await screen.findByTestId("take-on-report-files-preparing")).toBeInTheDocument();
    expect(screen.queryByTestId("take-on-report-download-error")).not.toBeInTheDocument();
    expect(screen.getByTestId("take-on-report-download-pdf")).toBeInTheDocument();
  });

  it("shows the X3 failure text and 'Try again' once the render gave up, and retries", async () => {
    actions.getTakeOnReport.mockResolvedValue(reportWith(files("FAILED")));
    actions.retryReportRender.mockResolvedValue({
      success: true,
      data: { ...TAKE_ON_REPORT_17313, files: files("RENDERING") },
    });
    render(withSWR(<ReportDownloads reportId="report-1" files={files("FAILED")} />));

    expect(screen.getByTestId("take-on-report-files-failed")).toHaveTextContent(
      REPORT_FILES_FAILED
    );
    expect(screen.queryByTestId("take-on-report-download-pdf")).not.toBeInTheDocument();
    await userEvent.click(screen.getByTestId("take-on-report-files-retry"));
    expect(actions.retryReportRender).toHaveBeenCalledWith("report-1");
    expect(await screen.findByTestId("take-on-report-files-preparing")).toBeInTheDocument();
    expect(screen.queryByTestId("take-on-report-files-failed")).not.toBeInTheDocument();
  });

  it("shows a refused download's detail (e.g. 409 REPORT_RENDER_FAILED) verbatim", async () => {
    actions.getTakeOnReport.mockResolvedValue(reportWith(files("READY")));
    actions.downloadTakeOnReport.mockResolvedValue({
      success: false,
      error: REPORT_FILES_FAILED,
      code: "REPORT_RENDER_FAILED",
    });
    render(withSWR(<ReportDownloads reportId="report-1" files={files("READY")} />));
    await userEvent.click(screen.getByTestId("take-on-report-download-pdf"));
    expect((await screen.findByTestId("take-on-report-download-error")).textContent).toBe(
      REPORT_FILES_FAILED
    );
  });

  it("never shows a raw status code", async () => {
    actions.getTakeOnReport.mockResolvedValue(reportWith(files("FAILED", "RENDERING")));
    render(withSWR(<ReportDownloads reportId="report-1" files={files("FAILED", "RENDERING")} />));
    const root = screen.getByTestId("take-on-report-downloads");
    expect(root.textContent).not.toMatch(/READY|RENDERING|FAILED/);
  });
});
