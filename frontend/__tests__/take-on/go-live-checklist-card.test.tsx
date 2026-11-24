import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("server-only", () => ({}));

const actions = vi.hoisted(() => ({
  acknowledgeGoLiveItem: vi.fn(),
  withdrawGoLiveItem: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import { GoLiveChecklistCard } from "@/components/take-on/go-live-checklist-card";
import { GO_LIVE_TEXT, goLiveItems } from "./fixtures";

// 2026-11-23T22:30Z is 24 Nov in South Africa: the acknowledged line shows the SA calendar day.
const ALICE = {
  name: "Alice Owner",
  at: "2026-11-23T22:30:00Z",
  note: "Archive kept by Ndlovu IT",
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("GoLiveChecklistCard (656A)", () => {
  it("shows the four X3 statements verbatim, in order, with who acknowledged what", () => {
    render(<GoLiveChecklistCard items={goLiveItems({ AUDITOR_INFORMED: ALICE })} />);
    const card = screen.getByTestId("take-on-go-live-card");
    expect(within(card).getByText("Go-live checklist")).toBeInTheDocument();
    expect(
      within(card)
        .getAllByTestId(/^take-on-go-live-text-/)
        .map((p) => p.textContent)
    ).toEqual([
      GO_LIVE_TEXT.TRUST_RECORDS_RETAINED,
      GO_LIVE_TEXT.FICA_RECORDS_RETAINED,
      GO_LIVE_TEXT.ARCHIVE_HOSTING_CONSIDERED,
      GO_LIVE_TEXT.AUDITOR_INFORMED,
    ]);
    expect(screen.getByTestId("take-on-go-live-acknowledged-auditor-informed")).toHaveTextContent(
      "Acknowledged by Alice Owner on 24 Nov 2026Archive kept by Ndlovu IT"
    );
    expect(screen.getByTestId("take-on-go-live-withdraw-auditor-informed")).toBeInTheDocument();
    expect(
      screen.queryByTestId("take-on-go-live-acknowledge-auditor-informed")
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("take-on-go-live-acknowledge-trust-records-retained")).toBeEnabled();
    // A note field for each item not yet acknowledged.
    expect(screen.getAllByLabelText("Note (optional)")).toHaveLength(3);
  });

  it("acknowledges an item with the trimmed note and shows the checklist the backend returns", async () => {
    const user = userEvent.setup();
    actions.acknowledgeGoLiveItem.mockResolvedValue({
      success: true,
      data: goLiveItems({
        FICA_RECORDS_RETAINED: { name: "Bob Admin", at: "2026-11-24T08:00:00Z", note: "Box 7" },
      }),
    });
    render(<GoLiveChecklistCard items={goLiveItems()} />);
    await user.type(screen.getByTestId("take-on-go-live-note-fica-records-retained"), "  Box 7  ");
    await user.click(screen.getByTestId("take-on-go-live-acknowledge-fica-records-retained"));
    expect(actions.acknowledgeGoLiveItem).toHaveBeenCalledWith("FICA_RECORDS_RETAINED", "Box 7");
    expect(
      await screen.findByTestId("take-on-go-live-acknowledged-fica-records-retained")
    ).toHaveTextContent("Acknowledged by Bob Admin on 24 Nov 2026");
    expect(
      screen.getByTestId("take-on-go-live-withdraw-fica-records-retained")
    ).toBeInTheDocument();
  });

  it("acknowledges without a note as null", async () => {
    const user = userEvent.setup();
    actions.acknowledgeGoLiveItem.mockResolvedValue({ success: true, data: goLiveItems() });
    render(<GoLiveChecklistCard items={goLiveItems()} />);
    await user.click(screen.getByTestId("take-on-go-live-acknowledge-trust-records-retained"));
    expect(actions.acknowledgeGoLiveItem).toHaveBeenCalledWith("TRUST_RECORDS_RETAINED", null);
  });

  it("withdraws an acknowledgement, and shows a refusal verbatim", async () => {
    const user = userEvent.setup();
    actions.withdrawGoLiveItem.mockResolvedValueOnce({ success: true, data: goLiveItems() });
    render(<GoLiveChecklistCard items={goLiveItems({ AUDITOR_INFORMED: ALICE })} />);
    await user.click(screen.getByTestId("take-on-go-live-withdraw-auditor-informed"));
    expect(actions.withdrawGoLiveItem).toHaveBeenCalledWith("AUDITOR_INFORMED");
    expect(
      await screen.findByTestId("take-on-go-live-acknowledge-auditor-informed")
    ).toBeInTheDocument();
    cleanup();

    const refusal = "This item has not been acknowledged, so there is nothing to withdraw.";
    actions.withdrawGoLiveItem.mockResolvedValueOnce({ success: false, error: refusal });
    render(<GoLiveChecklistCard items={goLiveItems({ AUDITOR_INFORMED: ALICE })} />);
    await user.click(screen.getByTestId("take-on-go-live-withdraw-auditor-informed"));
    const error = await screen.findByTestId("take-on-go-live-error");
    expect(error).toHaveTextContent(refusal);
    expect(
      within(screen.getByTestId("take-on-go-live-item-auditor-informed")).getByTestId(
        "take-on-go-live-error"
      )
    ).toBe(error);
    // The item is still acknowledged: nothing changed.
    expect(screen.getByTestId("take-on-go-live-acknowledged-auditor-informed")).toBeInTheDocument();
  });
});
