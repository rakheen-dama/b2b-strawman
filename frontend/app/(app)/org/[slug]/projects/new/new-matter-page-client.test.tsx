import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import { SUGGESTED_WORK_TYPES } from "@/lib/constants/work-types";

const mockInstantiateTemplate = vi.fn();
const mockRouterPush = vi.fn();

vi.mock("@/app/(app)/org/[slug]/settings/project-templates/actions", () => ({
  instantiateTemplateAction: (...args: unknown[]) => mockInstantiateTemplate(...args),
}));

vi.mock("@/components/ai/matter-intake-panel", () => ({
  MatterIntakePanel: () => null,
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockRouterPush, refresh: vi.fn() }),
}));

import { NewMatterPageClient } from "./new-matter-page-client";

function template(id: string, name: string, workType: string | null): ProjectTemplateResponse {
  return {
    id,
    name,
    namePattern: "{customer}",
    description: null,
    billableDefault: true,
    source: "MANUAL",
    sourceMatterId: null,
    active: true,
    taskCount: 0,
    tagCount: 0,
    tasks: [],
    tags: [],
    workType,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  };
}

const TEMPLATES = [
  template("pt-conv", "Property Transfer (Conveyancing)", "CONVEYANCING"),
  template("pt-est", "Deceased Estate Administration", "ESTATES"),
  template("pt-none", "Blank Template", null),
];

function renderPage() {
  render(
    <NewMatterPageClient
      slug="acme"
      initialCustomerId=""
      customers={[]}
      templates={TEMPLATES}
      canExecuteAi={false}
      canReviewGates={false}
      isAiConfigured={false}
    />
  );
}

describe("NewMatterPageClient work type prefill", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("prefills Work Type from the selected template and submits it", async () => {
    mockInstantiateTemplate.mockResolvedValue({ success: true, projectId: "proj-1" });
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(screen.getByLabelText("Template"), "pt-conv");
    expect(screen.getByLabelText(/work type/i)).toHaveValue("CONVEYANCING");

    await user.type(screen.getByLabelText("Matter Name"), "Transfer of Erf 123");
    await user.click(screen.getByRole("button", { name: "Create Matter" }));
    await waitFor(() => {
      expect(mockInstantiateTemplate).toHaveBeenCalledWith(
        "acme",
        "pt-conv",
        expect.objectContaining({ workType: "CONVEYANCING" })
      );
    });
  });

  it("follows the template while the user has not edited Work Type", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(screen.getByLabelText("Template"), "pt-conv");
    await user.selectOptions(screen.getByLabelText("Template"), "pt-est");
    expect(screen.getByLabelText(/work type/i)).toHaveValue("ESTATES");

    await user.selectOptions(screen.getByLabelText("Template"), "pt-none");
    expect(screen.getByLabelText(/work type/i)).toHaveValue("");
  });

  it("does not overwrite a Work Type the user typed", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText(/work type/i), "LITIGATION");
    await user.selectOptions(screen.getByLabelText("Template"), "pt-conv");
    expect(screen.getByLabelText(/work type/i)).toHaveValue("LITIGATION");
  });

  it("keeps a user edit made after the prefill when the template changes", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(screen.getByLabelText("Template"), "pt-conv");
    const workTypeInput = screen.getByLabelText(/work type/i);
    await user.clear(workTypeInput);
    await user.type(workTypeInput, "COMMERCIAL");
    await user.selectOptions(screen.getByLabelText("Template"), "pt-est");
    expect(screen.getByLabelText(/work type/i)).toHaveValue("COMMERCIAL");
  });
  it("shows the inherited template work type when Work Type is left blank", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(screen.getByLabelText("Template"), "pt-conv");
    expect(screen.queryByText("Inherits CONVEYANCING from the template")).not.toBeInTheDocument();

    const workTypeInput = screen.getByLabelText(/work type/i);
    await user.clear(workTypeInput);
    expect(workTypeInput).toHaveAttribute("placeholder", "CONVEYANCING");
    expect(screen.getByText("Inherits CONVEYANCING from the template")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Template"), "pt-none");
    expect(screen.queryByText(/^Inherits /)).not.toBeInTheDocument();
    expect(workTypeInput).toHaveAttribute("placeholder", "e.g. LITIGATION, CONVEYANCING");
  });

  it("offers the canonical legal matter types as Work Type suggestions", () => {
    renderPage();

    const workTypeInput = screen.getByLabelText(/work type/i);
    const listId = workTypeInput.getAttribute("list");
    expect(listId).toBeTruthy();
    const options = document.getElementById(listId!)?.querySelectorAll("option") ?? [];
    expect(Array.from(options, (o) => o.value)).toEqual([...SUGGESTED_WORK_TYPES]);
  });
});
