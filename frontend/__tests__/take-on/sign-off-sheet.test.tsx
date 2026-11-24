import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SWRConfig } from "swr";
import type { ReactNode } from "react";

vi.mock("server-only", () => ({}));

const actions = vi.hoisted(() => ({
  getSignOffQueue: vi.fn(),
  getSignOffReview: vi.fn(),
  signOff: vi.fn(),
  withdrawMySignOff: vi.fn(),
  fetchTakeOnBatch: vi.fn(),
  commitBatch: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => actions);

import { TakeOnSignOffWidget } from "@/components/dashboard/take-on-sign-off-widget";
import { signOffSheetTitle } from "@/components/take-on/sign-off-sheet";
import { QUEUE_ITEM, signOffReview, signedReview } from "./fixtures";

const TITLE = "Sign off the trust take-on of Trust Account G at 31 October 2026";

const withSWR = (ui: ReactNode) => (
  <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0, shouldRetryOnError: false }}>
    {ui}
  </SWRConfig>
);

function renderWidget(items = [QUEUE_ITEM]) {
  render(withSWR(<TakeOnSignOffWidget data={items} />));
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Trust take-on sign-off — Home widget and sheet (657C)", () => {
  it("lists each take-on awaiting sign-off and reads nothing until one is opened", () => {
    renderWidget();
    expect(screen.getByText("Trust take-on awaiting your sign-off")).toBeInTheDocument();
    const item = screen.getByTestId("take-on-sign-off-item-batch-657b");
    expect(item).toHaveTextContent("Trust Account G at 31 October 2026");
    expect(item).toHaveTextContent("Prepared by Bob Admin");
    expect(actions.getSignOffReview).not.toHaveBeenCalled();
    expect(screen.queryByTestId("takeon-signoff-sheet")).not.toBeInTheDocument();
  });

  it("opens the sheet from the widget item, titled with the account and the cutover date", async () => {
    const user = userEvent.setup();
    actions.getSignOffReview.mockResolvedValue({ success: true, data: signOffReview() });
    renderWidget();

    await user.click(screen.getByTestId("take-on-sign-off-item-batch-657b"));
    expect(await screen.findByTestId("takeon-signoff-sheet-title")).toHaveTextContent(TITLE);
    expect(await screen.findByTestId("takeon-signoff-panel")).toBeInTheDocument();
    expect(actions.getSignOffReview).toHaveBeenCalledWith("batch-657b", 0, 50);
  });

  it("stays open after signing (the signer can still withdraw); closing re-reads the queue and the take-on leaves the widget", async () => {
    const user = userEvent.setup();
    actions.getSignOffReview.mockResolvedValue({ success: true, data: signOffReview() });
    actions.signOff.mockResolvedValue({ success: true, data: signedReview() });
    actions.getSignOffQueue.mockResolvedValue({ success: true, data: [] });
    renderWidget();

    await user.click(screen.getByTestId("take-on-sign-off-item-batch-657b"));
    await user.click(await screen.findByTestId("takeon-signoff-accept"));
    await user.click(screen.getByTestId("takeon-signoff-submit"));
    expect(await screen.findByTestId("takeon-signoff-withdraw")).toBeInTheDocument();
    expect(screen.getByTestId("takeon-signoff-sheet")).toBeInTheDocument();
    expect(actions.getSignOffQueue).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Close" }));
    await waitFor(() => expect(actions.getSignOffQueue).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(screen.queryByTestId("take-on-sign-off-item-batch-657b")).not.toBeInTheDocument()
    );
    expect(screen.getByTestId("take-on-sign-off-none")).toHaveTextContent(
      "Nothing else is awaiting your sign-off."
    );
  });

  it("closing without a sign-off does not re-read the queue", async () => {
    const user = userEvent.setup();
    actions.getSignOffReview.mockResolvedValue({ success: true, data: signOffReview() });
    renderWidget();

    await user.click(screen.getByTestId("take-on-sign-off-item-batch-657b"));
    await screen.findByTestId("takeon-signoff-panel");
    await user.click(screen.getByRole("button", { name: "Close" }));
    await waitFor(() =>
      expect(screen.queryByTestId("takeon-signoff-sheet")).not.toBeInTheDocument()
    );
    expect(actions.getSignOffQueue).not.toHaveBeenCalled();
    expect(screen.getByTestId("take-on-sign-off-item-batch-657b")).toBeInTheDocument();
  });

  it("words the title for a missing account name and shows a failed queue read quietly", () => {
    expect(signOffSheetTitle(null, "2026-02-28")).toBe(
      "Sign off the trust take-on of the trust account at 28 February 2026"
    );
    render(withSWR(<TakeOnSignOffWidget data={null} />));
    expect(screen.getByText(/could not be loaded/)).toBeInTheDocument();
  });
});
