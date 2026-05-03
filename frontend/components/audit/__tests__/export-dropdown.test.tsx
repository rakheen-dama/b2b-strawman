import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/org/[slug]/settings/audit-log/actions", () => ({
  exportAuditCsvAction: vi.fn(),
  exportAuditPdfAction: vi.fn(),
  countAuditEventsAction: vi.fn(),
}));

import {
  countAuditEventsAction,
  exportAuditCsvAction,
  exportAuditPdfAction,
} from "@/app/(app)/org/[slug]/settings/audit-log/actions";
import { ExportDropdown } from "../export-dropdown";

const mockCsv = exportAuditCsvAction as unknown as ReturnType<typeof vi.fn>;
const mockPdf = exportAuditPdfAction as unknown as ReturnType<typeof vi.fn>;
const mockCount = countAuditEventsAction as unknown as ReturnType<typeof vi.fn>;

// Capture originals so they can be restored after each test — preventing
// cross-test leakage of these prototype/global stubs.
let originalCreateObjectURL: typeof URL.createObjectURL | undefined;
let originalRevokeObjectURL: typeof URL.revokeObjectURL | undefined;
let originalAnchorClick: HTMLAnchorElement["click"] | undefined;

beforeEach(() => {
  originalCreateObjectURL = global.URL.createObjectURL;
  originalRevokeObjectURL = global.URL.revokeObjectURL;
  originalAnchorClick = HTMLAnchorElement.prototype.click;

  // happy-dom doesn't ship URL.createObjectURL — stub.
  global.URL.createObjectURL = vi.fn(() => "blob:mock");
  global.URL.revokeObjectURL = vi.fn();

  // Stub anchor.click() so the test doesn't trigger navigation.
  const anchorProto = HTMLAnchorElement.prototype as unknown as { click: () => void };
  anchorProto.click = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  if (originalCreateObjectURL) {
    global.URL.createObjectURL = originalCreateObjectURL;
  }
  if (originalRevokeObjectURL) {
    global.URL.revokeObjectURL = originalRevokeObjectURL;
  }
  if (originalAnchorClick) {
    HTMLAnchorElement.prototype.click = originalAnchorClick;
  }
});

async function openMenu(user: ReturnType<typeof userEvent.setup>) {
  const trigger = screen.getByTestId("export-dropdown-trigger");
  await user.click(trigger);
}

describe("<ExportDropdown>", () => {
  it("CSV item triggers exportAuditCsvAction and downloads a Blob", async () => {
    mockCount.mockResolvedValue({ data: 5 });
    mockCsv.mockResolvedValue({ data: "id,event\n1,login\n" });

    const user = userEvent.setup();
    render(<ExportDropdown filter={{}} />);

    await openMenu(user);
    const csvItem = await screen.findByTestId("export-dropdown-csv");
    await user.click(csvItem);

    await waitFor(() => expect(mockCsv).toHaveBeenCalled());
    expect(global.URL.createObjectURL).toHaveBeenCalled();
  });

  it("PDF item is disabled when count exceeds 10000", async () => {
    mockCount.mockResolvedValue({ data: 10_001 });

    const user = userEvent.setup();
    render(<ExportDropdown filter={{}} />);

    // Wait for debounced count fetch + state update
    await waitFor(() => expect(mockCount).toHaveBeenCalled());

    await openMenu(user);
    const item = await screen.findByTestId("export-dropdown-pdf");
    // Radix sets data-disabled and/or aria-disabled on disabled MenuItem.
    const disabledAttr = item.getAttribute("data-disabled") ?? item.getAttribute("aria-disabled");
    expect(disabledAttr).not.toBeNull();
  });

  it("PDF item triggers exportAuditPdfAction when count is under cap", async () => {
    mockCount.mockResolvedValue({ data: 42 });
    // Base64 of "PDF" → "UERG"
    mockPdf.mockResolvedValue({ data: "UERG" });

    const user = userEvent.setup();
    render(<ExportDropdown filter={{}} />);
    await waitFor(() => expect(mockCount).toHaveBeenCalled());

    await openMenu(user);
    const pdfItem = await screen.findByTestId("export-dropdown-pdf");
    await user.click(pdfItem);

    await waitFor(() => expect(mockPdf).toHaveBeenCalled());
  });

  it("surfaces 413 detail in the error message", async () => {
    mockCount.mockResolvedValue({ data: 5 });
    mockPdf.mockResolvedValue({
      data: null,
      error: "PDF export limited to 10,000 events.",
      detail: { rowCount: 47238, cap: 10000 },
    });

    const user = userEvent.setup();
    render(<ExportDropdown filter={{}} />);
    await waitFor(() => expect(mockCount).toHaveBeenCalled());

    await openMenu(user);
    const pdfItem = await screen.findByTestId("export-dropdown-pdf");
    await user.click(pdfItem);

    await waitFor(() => {
      // Note: the dropdown menu is still open at this point and Radix sets
      // aria-hidden on the outside content, which excludes the alert from
      // ARIA role queries. Match by text instead.
      expect(screen.getByText(/47,238/)).toBeTruthy();
      expect(screen.getByText(/47,238/).textContent).toMatch(/10,000/);
    });
  });
});
