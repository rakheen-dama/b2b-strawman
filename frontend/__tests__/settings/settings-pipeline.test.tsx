import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";

vi.mock("server-only", () => ({}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(""),
}));

const mockReorderStagesAction = vi.fn();
const mockArchiveStageAction = vi.fn();
const mockDeleteStageAction = vi.fn();
const mockUpdateStageAction = vi.fn();
const mockCreateStageAction = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/pipeline/actions", () => ({
  reorderStagesAction: (...args: unknown[]) => mockReorderStagesAction(...args),
  archiveStageAction: (...args: unknown[]) => mockArchiveStageAction(...args),
  deleteStageAction: (...args: unknown[]) => mockDeleteStageAction(...args),
  updateStageAction: (...args: unknown[]) => mockUpdateStageAction(...args),
  createStageAction: (...args: unknown[]) => mockCreateStageAction(...args),
}));

import { StageConfigList } from "@/components/settings/StageConfigList";
import { StageEditDialog } from "@/components/settings/StageEditDialog";
import type { StageDto } from "@/lib/api/crm";

afterEach(() => {
  cleanup();
  mockReorderStagesAction.mockReset();
  mockArchiveStageAction.mockReset();
  mockDeleteStageAction.mockReset();
  mockUpdateStageAction.mockReset();
  mockCreateStageAction.mockReset();
});

const stages: StageDto[] = [
  {
    id: "s1",
    name: "Lead",
    position: 0,
    defaultProbabilityPct: 20,
    stageType: "OPEN",
    archived: false,
  },
  {
    id: "s2",
    name: "Qualified",
    position: 1,
    defaultProbabilityPct: 50,
    stageType: "OPEN",
    archived: false,
  },
];

describe("StageConfigList", () => {
  it("renders ordered stages with their type and probability", () => {
    render(<StageConfigList slug="acme" stages={stages} />);
    expect(screen.getByText("Lead")).toBeInTheDocument();
    expect(screen.getByText("Qualified")).toBeInTheDocument();
    expect(screen.getByText("20% default probability")).toBeInTheDocument();
  });

  it("surfaces the 409 DeleteGuard message when delete fails", async () => {
    mockDeleteStageAction.mockResolvedValue({
      success: false,
      status: 409,
      error: "Cannot delete a stage with attached deals.",
    });
    render(<StageConfigList slug="acme" stages={stages} />);

    // Open the delete confirm for the first stage.
    const deleteButtons = screen.getAllByRole("button", { name: /Delete/i });
    fireEvent.click(deleteButtons[0]);
    // Confirm in the alert dialog.
    const confirmButtons = await screen.findAllByRole("button", { name: /^Delete$/i });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]);

    await waitFor(() => {
      expect(mockDeleteStageAction).toHaveBeenCalledWith("acme", "s1");
    });
    await waitFor(() => {
      expect(screen.getByText("Cannot delete a stage with attached deals.")).toBeInTheDocument();
    });
  });

  it("surfaces the backend message when archive fails (last-of-type)", async () => {
    mockArchiveStageAction.mockResolvedValue({
      success: false,
      status: 400,
      error: "Cannot archive the last OPEN stage.",
    });
    render(<StageConfigList slug="acme" stages={stages} />);
    fireEvent.click(screen.getAllByRole("button", { name: /Archive/i })[0]);
    await waitFor(() => {
      expect(screen.getByText("Cannot archive the last OPEN stage.")).toBeInTheDocument();
    });
  });
});

describe("StageEditDialog", () => {
  it("persists an edit via updateStageAction", async () => {
    mockUpdateStageAction.mockResolvedValue({
      success: true,
      stage: { ...stages[0], name: "New Lead" },
    });
    render(<StageEditDialog slug="acme" stage={stages[0]} />);

    fireEvent.click(screen.getByRole("button", { name: /Edit/i }));
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "New Lead" } });
    fireEvent.click(screen.getByRole("button", { name: /^Save$/i }));

    await waitFor(() => {
      expect(mockUpdateStageAction).toHaveBeenCalledTimes(1);
    });
    const [, id, req] = mockUpdateStageAction.mock.calls[0];
    expect(id).toBe("s1");
    expect(req.name).toBe("New Lead");
    expect(req.defaultProbabilityPct).toBe(20);
    expect(req.stageType).toBe("OPEN");
  });
});
