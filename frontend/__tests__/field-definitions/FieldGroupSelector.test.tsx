import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FieldGroupSelector } from "@/components/field-definitions/FieldGroupSelector";
import type { FieldGroupResponse } from "@/lib/types";

const mockSetEntityFieldGroups = vi.fn();

vi.mock(
  "@/app/(app)/org/[slug]/settings/custom-fields/actions",
  () => ({
    setEntityFieldGroupsAction: (...args: unknown[]) =>
      mockSetEntityFieldGroups(...args),
  }),
);

const group1: FieldGroupResponse = {
  id: "grp-1",
  entityType: "PROJECT",
  name: "Litigation Fields",
  slug: "litigation_fields",
  description: "Fields for litigation matters",
  packId: null,
  sortOrder: 0,
  active: true,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const group2: FieldGroupResponse = {
  id: "grp-2",
  entityType: "PROJECT",
  name: "Tax Fields",
  slug: "tax_fields",
  description: "Fields for tax matters",
  packId: null,
  sortOrder: 1,
  active: true,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const group3: FieldGroupResponse = {
  id: "grp-3",
  entityType: "PROJECT",
  name: "Property Fields",
  slug: "property_fields",
  description: null,
  packId: null,
  sortOrder: 2,
  active: true,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const allGroups = [group1, group2, group3];

describe("FieldGroupSelector", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders applied groups as badges", () => {
    render(
      <FieldGroupSelector
        entityType="PROJECT"
        entityId="proj-1"
        appliedFieldGroups={["grp-1", "grp-2"]}
        slug="acme"
        canManage={false}
        allGroups={allGroups}
      />,
    );

    expect(screen.getByText("Litigation Fields")).toBeInTheDocument();
    expect(screen.getByText("Tax Fields")).toBeInTheDocument();
  });

  it("shows 'Add Group' button when canManage and unapplied groups exist", () => {
    render(
      <FieldGroupSelector
        entityType="PROJECT"
        entityId="proj-1"
        appliedFieldGroups={["grp-1"]}
        slug="acme"
        canManage={true}
        allGroups={allGroups}
      />,
    );

    expect(
      screen.getByRole("button", { name: /Add Group/i }),
    ).toBeInTheDocument();
  });

  it("hides 'Add Group' button when canManage is false", () => {
    render(
      <FieldGroupSelector
        entityType="PROJECT"
        entityId="proj-1"
        appliedFieldGroups={["grp-1"]}
        slug="acme"
        canManage={false}
        allGroups={allGroups}
      />,
    );

    expect(
      screen.queryByRole("button", { name: /Add Group/i }),
    ).not.toBeInTheDocument();
  });

  it("shows remove button on badges when canManage is true", () => {
    render(
      <FieldGroupSelector
        entityType="PROJECT"
        entityId="proj-1"
        appliedFieldGroups={["grp-1"]}
        slug="acme"
        canManage={true}
        allGroups={allGroups}
      />,
    );

    expect(
      screen.getByRole("button", { name: /Remove Litigation Fields/i }),
    ).toBeInTheDocument();
  });

  it("calls setEntityFieldGroupsAction when removing a group", async () => {
    const user = userEvent.setup();
    mockSetEntityFieldGroups.mockResolvedValue({ success: true });

    render(
      <FieldGroupSelector
        entityType="PROJECT"
        entityId="proj-1"
        appliedFieldGroups={["grp-1", "grp-2"]}
        slug="acme"
        canManage={true}
        allGroups={allGroups}
      />,
    );

    await user.click(
      screen.getByRole("button", { name: /Remove Litigation Fields/i }),
    );

    await waitFor(() => {
      expect(mockSetEntityFieldGroups).toHaveBeenCalledWith(
        "acme",
        "PROJECT",
        "proj-1",
        ["grp-2"],
      );
    });
  });

  it("renders nothing when no groups are available", () => {
    const { container } = render(
      <FieldGroupSelector
        entityType="PROJECT"
        entityId="proj-1"
        appliedFieldGroups={[]}
        slug="acme"
        canManage={true}
        allGroups={[]}
      />,
    );

    expect(container.innerHTML).toBe("");
  });

  it("shows 'No field groups applied' when none applied and not managing", () => {
    render(
      <FieldGroupSelector
        entityType="PROJECT"
        entityId="proj-1"
        appliedFieldGroups={[]}
        slug="acme"
        canManage={false}
        allGroups={allGroups}
      />,
    );

    expect(screen.getByText("No field groups applied")).toBeInTheDocument();
  });
});
