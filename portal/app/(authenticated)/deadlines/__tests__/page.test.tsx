import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { inZone } from "@/__tests__/helpers/time-zone";

// The deadlines window is "today..today+60" on the South African calendar — the backend's default
// (PortalDeadlineService.listForContact, Clock.system(KaziTime.ZONE); no lower bound for the
// Overdue, Completed and Cancelled filters, D-127). Building `from`/`to` from
// the browser's local date started the window on SA-tomorrow east of SA late in the evening
// (Dubai from 22:00 SAST, Nairobi from 23:00), so today's deadline dropped off the list.

const mockListDeadlines = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

// One stable object: the page's fetch effect depends on the context identity.
const CTX = { enabledModules: ["deadlines"] };

vi.mock("@/hooks/use-portal-context", () => ({
  usePortalContext: () => CTX,
  usePortalContextIsSettled: () => true,
}));

vi.mock("@/lib/api/deadlines", () => ({
  listDeadlines: (...args: unknown[]) => mockListDeadlines(...args),
}));

import DeadlinesPage from "@/app/(authenticated)/deadlines/page";

// 22:30 SAST on Sat 28 Nov 2026 = 00:30 on Sun 29 Nov in Dubai.
const LATE_EVENING_SAST = new Date("2026-11-28T20:30:00Z");

describe("DeadlinesPage date window", () => {
  beforeEach(() => {
    mockListDeadlines.mockReset();
    mockListDeadlines.mockResolvedValue([
      {
        id: "h-1",
        sourceEntity: "COURT_DATE",
        deadlineType: "COURT_DATE",
        label: "Hearing today",
        dueDate: "2026-11-28",
        status: "DUE_SOON",
        descriptionSanitised: "",
        matterId: null,
      },
    ]);
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllEnvs();
  });

  it.each([
    "Asia/Dubai",
    "Africa/Nairobi",
    "Africa/Johannesburg",
    "America/New_York",
  ])(
    "under TZ=%s at 22:30 SAST, leaves the window to the backend's SA-calendar default",
    async (zone) => {
      inZone(zone);
      vi.useFakeTimers({ toFake: ["Date"] });
      vi.setSystemTime(LATE_EVENING_SAST);

      render(<DeadlinesPage />);

      await waitFor(() => expect(mockListDeadlines).toHaveBeenCalled());
      const params = mockListDeadlines.mock.calls[0][0] ?? {};
      expect(params.from).toBeUndefined();
      expect(params.to).toBeUndefined();
      // Dates print on the SA calendar too: the 28 Nov hearing reads 28 Nov.
      expect(
        await screen.findByTestId("deadline-row-COURT_DATE-h-1"),
      ).toHaveTextContent("28 Nov 2026");
    },
  );

  it("refetches with the default window on a filter change (no window frozen at mount)", async () => {
    inZone("Asia/Dubai");
    render(<DeadlinesPage />);
    await screen.findByTestId("deadline-row-COURT_DATE-h-1");

    await userEvent.selectOptions(screen.getByLabelText(/status/i), "DUE_SOON");

    await waitFor(() => expect(mockListDeadlines).toHaveBeenCalledTimes(2));
    expect(mockListDeadlines.mock.calls[1][0]).toEqual({ status: "DUE_SOON" });
  });
});
