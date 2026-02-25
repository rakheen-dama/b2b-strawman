import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CustomFieldSection } from "@/components/field-definitions/CustomFieldSection";
import { FieldGroupSelector } from "@/components/field-definitions/FieldGroupSelector";
import type {
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
} from "@/lib/types";

// Mock server actions (both components import from this module)
vi.mock(
  "@/app/(app)/org/[slug]/settings/custom-fields/actions",
  () => ({
    updateEntityCustomFieldsAction: vi.fn().mockResolvedValue({ success: true }),
    setEntityFieldGroupsAction: vi.fn().mockResolvedValue({ success: true }),
  }),
);

// --- Test data ---

const invoiceFieldDef: FieldDefinitionResponse = {
  id: "fd-invoice-1",
  entityType: "INVOICE",
  name: "PO Number",
  slug: "po_number",
  fieldType: "TEXT",
  description: "Purchase order reference",
  required: false,
  defaultValue: null,
  options: null,
  validation: null,
  sortOrder: 0,
  packId: null,
  packFieldKey: null,
  visibilityCondition: null,
  active: true,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const invoiceFieldGroup: FieldGroupResponse = {
  id: "grp-invoice-1",
  entityType: "INVOICE",
  name: "Invoice Details",
  slug: "invoice_details",
  description: "Additional invoice metadata",
  packId: null,
  sortOrder: 0,
  active: true,
  autoApply: false,
  dependsOn: null,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const invoiceGroupMember: FieldGroupMemberResponse = {
  id: "gm-invoice-1",
  fieldGroupId: "grp-invoice-1",
  fieldDefinitionId: "fd-invoice-1",
  sortOrder: 0,
};

describe("Invoice Custom Fields", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders_custom_field_section_on_invoice_detail", () => {
    render(
      <CustomFieldSection
        entityType="INVOICE"
        entityId="inv-1"
        customFields={{}}
        appliedFieldGroups={["grp-invoice-1"]}
        editable={true}
        slug="acme"
        fieldDefinitions={[invoiceFieldDef]}
        fieldGroups={[invoiceFieldGroup]}
        groupMembers={{ "grp-invoice-1": [invoiceGroupMember] }}
      />,
    );

    expect(screen.getByTestId("custom-field-section")).toBeInTheDocument();
    expect(screen.getByText("Invoice Details")).toBeInTheDocument();
    expect(screen.getByLabelText(/PO Number/)).toBeInTheDocument();
  });

  it("renders_field_group_selector_on_invoice_detail", () => {
    render(
      <FieldGroupSelector
        entityType="INVOICE"
        entityId="inv-1"
        appliedFieldGroups={["grp-invoice-1"]}
        slug="acme"
        canManage={true}
        allGroups={[invoiceFieldGroup]}
      />,
    );

    expect(screen.getByTestId("field-group-selector")).toBeInTheDocument();
    expect(screen.getByText("Invoice Details")).toBeInTheDocument();
  });
});
