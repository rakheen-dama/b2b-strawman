import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { EditProjectDialog } from "@/components/projects/edit-project-dialog";
import { TerminologyProvider } from "@/lib/terminology";
import { SUGGESTED_WORK_TYPES } from "@/lib/constants/work-types";
import type { Project } from "@/lib/types";

const mockUpdateProject = vi.fn();
const mockFetchActiveCustomers = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/actions", () => ({
  updateProject: (...args: unknown[]) => mockUpdateProject(...args),
  fetchActiveCustomers: (...args: unknown[]) => mockFetchActiveCustomers(...args),
}));

const PROJECT = {
  id: "proj-1",
  name: "Transfer of Erf 1",
  description: null,
  dueDate: null,
  customerId: null,
  referenceNumber: null,
  priority: null,
  workType: "CONVEYANCING",
} as unknown as Project;

function renderDialog() {
  return render(
    <TerminologyProvider verticalProfile={null}>
      <EditProjectDialog project={PROJECT} slug="acme" open onOpenChange={() => {}} />
    </TerminologyProvider>
  );
}

describe("EditProjectDialog work type", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchActiveCustomers.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
  });

  it("keeps a free-text work type input and offers the canonical legal matter types", async () => {
    renderDialog();

    const input = await screen.findByLabelText(/work type/i);
    expect(input).toHaveValue("CONVEYANCING");
    expect(input).toHaveAttribute("placeholder", "e.g. LITIGATION, CONVEYANCING");
    const listId = input.getAttribute("list");
    expect(listId).toBeTruthy();
    const options = document.getElementById(listId!)?.querySelectorAll("option") ?? [];
    expect(Array.from(options, (o) => o.value)).toEqual([...SUGGESTED_WORK_TYPES]);
  });
});
