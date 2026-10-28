import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { OrgProfileProvider } from "@/lib/org-profile";
import { SUGGESTED_WORK_TYPES } from "@/lib/constants/work-types";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const mockCreateProject = vi.fn();
const mockFetchActiveCustomers = vi.fn();

vi.mock("@/app/(app)/org/[slug]/matters/actions", () => ({
  createProject: (...args: unknown[]) => mockCreateProject(...args),
  fetchActiveCustomers: (...args: unknown[]) => mockFetchActiveCustomers(...args),
}));

function renderDialog() {
  return render(
    <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
      <CreateProjectDialog slug="acme" />
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

    await user.click(screen.getByText(/new matter/i));

    const input = await screen.findByLabelText(/reference number/i);
    expect(input).toBeInTheDocument();
    expect(input.getAttribute("maxLength")).toBe("100");
  });

  it("renders priority select with LOW, MEDIUM, HIGH options", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText(/new matter/i));

    const select = (await screen.findByLabelText(/priority/i)) as HTMLSelectElement;
    expect(select).toBeInTheDocument();
    expect(select.querySelector('option[value="LOW"]')).not.toBeNull();
    expect(select.querySelector('option[value="MEDIUM"]')).not.toBeNull();
    expect(select.querySelector('option[value="HIGH"]')).not.toBeNull();
  });

  it("renders work type input", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText(/new matter/i));

    const input = await screen.findByLabelText(/work type/i);
    expect(input).toBeInTheDocument();
  });

  it("offers the canonical legal matter types as work type suggestions", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText(/new matter/i));

    const input = await screen.findByLabelText(/work type/i);
    expect(input).toHaveAttribute("placeholder", "e.g. LITIGATION, CONVEYANCING");
    const listId = input.getAttribute("list");
    expect(listId).toBeTruthy();
    const options = document.getElementById(listId!)?.querySelectorAll("option") ?? [];
    expect(Array.from(options, (o) => o.value)).toEqual([...SUGGESTED_WORK_TYPES]);
  });

  it("submits form with promoted fields in the FormData payload", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText(/new matter/i));

    await user.type(screen.getByLabelText(/^name$/i), "My Project");
    await user.type(screen.getByLabelText(/reference number/i), "REF-001");
    await user.selectOptions(screen.getByLabelText(/priority/i), "HIGH");
    await user.type(screen.getByLabelText(/work type/i), "Consulting");

    await user.click(screen.getByRole("button", { name: /create matter/i }));

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

// Phase 88 G-A6: the stale "Coming Soon" card becomes a link-out to the conflict-check page,
// prefilled with the selected client. R-17: only checkType + customerId travel in the URL (no
// name, no ID number — POPIA); the conflict-check page resolves the client server-side.
describe("CreateProjectDialog conflict-check link (G-A6)", () => {
  const CLIENT_ID = "11111111-1111-4111-8111-111111111111";

  function renderDialogWithConflictCheck() {
    return render(
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["conflict_check"]}
        terminologyNamespace={null}
      >
        <CreateProjectDialog slug="acme" />
      </OrgProfileProvider>
    );
  }

  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchActiveCustomers.mockResolvedValue([
      {
        id: CLIENT_ID,
        name: "Sipho Ndlovu",
        idNumber: " 8501015800087 ",
        email: "sipho@example.co.za",
        phone: null,
        status: "ACTIVE",
      },
      {
        id: "22222222-2222-4222-8222-222222222222",
        name: "Acme Holdings",
        idNumber: "   ",
        email: null,
        phone: null,
        status: "ACTIVE",
      },
    ]);
  });

  afterEach(() => {
    cleanup();
  });

  it("replaces the Coming Soon stub with an enabled link-out", async () => {
    const user = userEvent.setup();
    renderDialogWithConflictCheck();

    await user.click(screen.getByText(/new matter/i));

    const link = await screen.findByRole("link", { name: /run a conflict check first/i });
    expect(link).toHaveAttribute("href", "/org/acme/conflict-check?checkType=NEW_MATTER");
    expect(screen.queryByText("Coming Soon")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /run conflict check/i })).not.toBeInTheDocument();
  });

  it("passes only the selected client's id in the link, never its name or ID number", async () => {
    const user = userEvent.setup();
    renderDialogWithConflictCheck();

    await user.click(screen.getByText(/new matter/i));
    const clientSelect = await screen.findByLabelText(/^client/i);
    await screen.findByRole("option", { name: "Sipho Ndlovu" });
    await user.selectOptions(clientSelect, CLIENT_ID);

    const href = screen
      .getByRole("link", { name: /run a conflict check first/i })
      .getAttribute("href")!;
    const url = new URL(href, "http://localhost");
    expect(url.pathname).toBe("/org/acme/conflict-check");
    expect(url.searchParams.get("checkType")).toBe("NEW_MATTER");
    expect(url.searchParams.get("customerId")).toBe(CLIENT_ID);
    expect([...url.searchParams.keys()].sort()).toEqual(["checkType", "customerId"]);
    expect(href).not.toContain("checkedIdNumber");
    expect(href).not.toContain("checkedName");
    expect(href).not.toContain("8501015800087");
  });

  it("drops the client id again when the client is cleared", async () => {
    const user = userEvent.setup();
    renderDialogWithConflictCheck();

    await user.click(screen.getByText(/new matter/i));
    const clientSelect = await screen.findByLabelText(/^client/i);
    await screen.findByRole("option", { name: "Acme Holdings" });
    await user.selectOptions(clientSelect, "22222222-2222-4222-8222-222222222222");
    await user.selectOptions(clientSelect, "");

    expect(screen.getByRole("link", { name: /run a conflict check first/i })).toHaveAttribute(
      "href",
      "/org/acme/conflict-check?checkType=NEW_MATTER"
    );
  });

  it("hides the conflict-check card when the conflict_check module is off", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByText(/new matter/i));
    await screen.findByLabelText(/reference number/i);

    expect(
      screen.queryByRole("link", { name: /run a conflict check first/i })
    ).not.toBeInTheDocument();
  });
});
