import { expect, vi } from "vitest";
import { CSV_BOM } from "@/components/data-table/data-table-csv";

/**
 * CSV-export test helpers shared by the DataTable consumer tests (`clients-table.test.tsx`,
 * `matters-table.test.tsx`; `data-table.test.tsx` uses `blobText`). Phase 90 642A review
 * follow-up, extracted in 642B. Not a test file (no `.test.` suffix).
 */

/** Captures the CSV Blob and the download name of each "Export CSV" click (639C pattern). */
export function stubDownload() {
  const captured: { blobs: Blob[]; downloads: string[] } = { blobs: [], downloads: [] };
  const originalCreate = URL.createObjectURL;
  const originalRevoke = URL.revokeObjectURL;
  URL.createObjectURL = vi.fn((blob: Blob) => {
    captured.blobs.push(blob);
    return "blob:mock";
  }) as typeof URL.createObjectURL;
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
    this: HTMLAnchorElement
  ) {
    captured.downloads.push(this.download);
  });
  return {
    captured,
    /** Waits one task first so the deferred `revokeObjectURL` still hits the mock. */
    async restore() {
      await new Promise((resolve) => setTimeout(resolve, 0));
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
    },
  };
}

/** Decodes a captured CSV Blob, keeping a leading BOM so a test can assert it. */
export async function blobText(blob: Blob | null | undefined): Promise<string> {
  if (!blob) throw new Error("no CSV blob was created");
  // ignoreBOM keeps a leading BOM in the decoded text so the test can assert it.
  return new TextDecoder("utf-8", { ignoreBOM: true }).decode(await blob.arrayBuffer());
}

/** CSV records after the BOM (the trailing CRLF leaves an empty last element). */
export async function csvRecords(blob: Blob | null | undefined): Promise<string[]> {
  const text = await blobText(blob);
  expect(text.startsWith(CSV_BOM)).toBe(true);
  return text.slice(CSV_BOM.length).split("\r\n");
}
