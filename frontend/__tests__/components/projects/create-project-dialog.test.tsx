import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { TerminologyProvider } from "@/lib/terminology";
import { OrgProfileProvider } from "@/lib/org-profile";

const mockCreateProject = vi.fn();
const mockFetchActiveCustomers = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/actions", () => ({
  createProject: (...args: unknown[]) => mockCreateProject(...args),
  fetchActiveCustomers: (...args: unknown[]) => mockFetchActiveCustomers(...args),
}));

function renderDialog() {
  return render(
    <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
      <TerminologyProvider verticalProfile={null}>
        <CreateProjectDialog slug="acme" />
      </TerminologyProvider>
    </OrgProfileProvider>
  );
}

describe("CreateProjectDialog promoted fields (Epic 464)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreateProject.mockResolvedValue({ success: true });
    mockFetchActiveCustomers.mockResolvedValue([]);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders reference number input with maxLength", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText(/new project/i));

    const input = await screen.findByLabelText(/reference number/i);
    expect(input).toBeInTheDocument();
    expect(input.getAttribute("maxLength")).toBe("100");
  });

  it("renders priority select with LOW, MEDIUM, HIGH options", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText(/new project/i));

    const select = (await screen.findByLabelText(/priority/i)) as HTMLSelectElement;
    expect(select).toBeInTheDocument();
    expect(select.querySelector('option[value="LOW"]')).not.toBeNull();
    expect(select.querySelector('option[value="MEDIUM"]')).not.toBeNull();
    expect(select.querySelector('option[value="HIGH"]')).not.toBeNull();
  });

  it("renders work type input", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText(/new project/i));

    const input = await screen.findByLabelText(/work type/i);
    expect(input).toBeInTheDocument();
  });

  it("submits form with promoted fields in the FormData payload", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText(/new project/i));

    await user.type(screen.getByLabelText(/^name$/i), "My Project");
    await user.type(screen.getByLabelText(/reference number/i), "REF-001");
    await user.selectOptions(screen.getByLabelText(/priority/i), "HIGH");
    await user.type(screen.getByLabelText(/work type/i), "Consulting");

    await user.click(screen.getByRole("button", { name: /create project/i }));

    await waitFor(() => {
      expect(mockCreateProject).toHaveBeenCalled();
    });
    const lastCall = mockCreateProject.mock.calls[mockCreateProject.mock.calls.length - 1];
    const formData = lastCall[1] as FormData;
    expect(formData.get("referenceNumber")).toBe("REF-001");
    expect(formData.get("priority")).toBe("HIGH");
    expect(formData.get("workType")).toBe("Consulting");
  });
});
