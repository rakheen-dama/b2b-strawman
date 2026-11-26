import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import type { FieldGroupResponse } from "@/lib/types";

vi.mock("@/app/(app)/org/[slug]/settings/matters/_tabs/custom-fields-actions", () => ({
  updateEntityCustomFieldsAction: vi.fn(),
  setEntityFieldGroupsAction: vi.fn(),
}));

vi.mock("@/components/field-definitions/FieldGroupSelector", () => ({
  FieldGroupSelector: () => <div data-testid="field-group-selector-stub" />,
}));

import { MatterFieldsTab } from "@/components/projects/matter-fields-tab";

const emptyProps = {
  projectId: "proj-1",
  customFields: {},
  appliedFieldGroups: [] as string[],
  fieldDefinitions: [],
  fieldGroups: [] as FieldGroupResponse[],
  groupMembers: {},
  canEdit: true,
  canManage: true,
  slug: "acme",
};

const group: FieldGroupResponse = {
  id: "grp-1",
  entityType: "PROJECT",
  name: "Matter Info",
  slug: "matter-info",
  description: null,
  packId: null,
  sortOrder: 0,
  active: true,
  autoApply: false,
  dependsOn: null,
  fieldDefinitionIds: [],
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

describe("MatterFieldsTab (Phase 91 663A)", () => {
  afterEach(() => cleanup());

  it("renders the org-empty branch as an inline EmptyState inside the testid wrapper", () => {
    render(<MatterFieldsTab {...emptyProps} />);

    const tab = screen.getByTestId("matter-fields-tab");
    const empty = tab.querySelector('[data-slot="empty-state"]');
    expect(empty).toHaveAttribute("data-variant", "inline");
    expect(
      screen.getByText("No custom field groups have been configured for this organisation.")
    ).toBeInTheDocument();
    expect(screen.getByText("Admins can create field groups in Settings.")).toBeInTheDocument();
    expect(screen.queryByRole("heading")).toBeNull();
  });

  it("keeps the testid on the populated branch", () => {
    render(<MatterFieldsTab {...emptyProps} fieldGroups={[group]} />);

    const tab = screen.getByTestId("matter-fields-tab");
    expect(tab).toBeInTheDocument();
    expect(screen.getByText("Field Groups")).toBeInTheDocument();
    expect(
      screen.queryByText("No custom field groups have been configured for this organisation.")
    ).toBeNull();
  });
});
