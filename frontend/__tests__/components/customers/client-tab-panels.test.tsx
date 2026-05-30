import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// ---------------------------------------------------------------------------
// Mocks — hoisted above component imports
// ---------------------------------------------------------------------------

vi.mock("@/components/customers/customer-address-block", () => ({
  CustomerAddressBlock: ({ customer }: { customer: { id: string } }) => (
    <div data-testid="customer-address-block">Address: {customer.id}</div>
  ),
}));

vi.mock("@/components/customers/customer-contact-card", () => ({
  CustomerContactCard: ({ customer }: { customer: { id: string } }) => (
    <div data-testid="customer-contact-card">Contact: {customer.id}</div>
  ),
}));

vi.mock("@/components/field-definitions/FieldGroupSelector", () => ({
  FieldGroupSelector: () => <div data-testid="field-group-selector">FieldGroupSelector</div>,
}));

vi.mock("@/components/field-definitions/CustomFieldSection", () => ({
  CustomFieldSection: () => <div data-testid="custom-field-section">CustomFieldSection</div>,
}));

vi.mock("@/components/tags/TagInput", () => ({
  TagInput: () => <div data-testid="tag-input">TagInput</div>,
}));

// ---------------------------------------------------------------------------
// Import components AFTER mocks
// ---------------------------------------------------------------------------

import { ClientDetailsTab } from "@/components/customers/client-details-tab";
import { ClientFieldsTab } from "@/components/customers/client-fields-tab";
import { ClientTagsTab } from "@/components/customers/client-tags-tab";

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

const mockCustomer = {
  id: "cust-1",
  name: "Acme Corporation",
  email: "info@acme.com",
  phone: "+27 11 123 4567",
  idNumber: null,
  status: "ACTIVE" as const,
  notes: null,
  createdBy: "user-1",
  createdByName: "Alice",
  createdAt: "2026-01-10T10:00:00Z",
  updatedAt: "2026-01-15T10:00:00Z",
  registrationNumber: "2026/123456/07",
  taxNumber: "9876543210",
  entityType: "PTY_LTD",
  financialYearEnd: "2026-02-28",
};

const mockCustomerNoBusinessDetails = {
  ...mockCustomer,
  registrationNumber: null,
  taxNumber: null,
  entityType: null,
  financialYearEnd: null,
};

// ---------------------------------------------------------------------------
// Cleanup
// ---------------------------------------------------------------------------

afterEach(() => {
  cleanup();
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ClientDetailsTab", () => {
  it("renders address and contact cards", () => {
    render(<ClientDetailsTab customer={mockCustomer} />);

    expect(screen.getByTestId("client-details-tab")).toBeInTheDocument();
    expect(screen.getByTestId("customer-address-block")).toBeInTheDocument();
    expect(screen.getByTestId("customer-contact-card")).toBeInTheDocument();
  });

  it("renders business details card with key-value rows", () => {
    render(<ClientDetailsTab customer={mockCustomer} />);

    expect(screen.getByTestId("customer-business-details")).toBeInTheDocument();
    expect(screen.getByText("Registration Number")).toBeInTheDocument();
    expect(screen.getByText("2026/123456/07")).toBeInTheDocument();
    expect(screen.getByText("Tax Number")).toBeInTheDocument();
    expect(screen.getByText("9876543210")).toBeInTheDocument();
    expect(screen.getByText("Entity Type")).toBeInTheDocument();
    expect(screen.getByText("Pty Ltd (Private Company)")).toBeInTheDocument();
    expect(screen.getByText("Financial Year End")).toBeInTheDocument();
  });

  it("hides business details card when all fields are null", () => {
    render(<ClientDetailsTab customer={mockCustomerNoBusinessDetails} />);

    expect(screen.getByTestId("client-details-tab")).toBeInTheDocument();
    expect(screen.queryByTestId("customer-business-details")).not.toBeInTheDocument();
  });
});

describe("ClientFieldsTab", () => {
  it("renders FieldGroupSelector and CustomFieldSection", () => {
    render(
      <ClientFieldsTab
        entityId="cust-1"
        appliedFieldGroups={[]}
        slug="test-org"
        canManage={true}
        allGroups={[]}
        customFields={{}}
        editable={true}
        fieldDefinitions={[]}
        fieldGroups={[]}
        groupMembers={{}}
        promotedFieldValues={{}}
      />
    );

    expect(screen.getByTestId("client-fields-tab")).toBeInTheDocument();
    expect(screen.getByTestId("field-group-selector")).toBeInTheDocument();
    expect(screen.getByTestId("custom-field-section")).toBeInTheDocument();
  });
});

describe("ClientTagsTab", () => {
  it("renders TagInput", () => {
    render(
      <ClientTagsTab
        entityId="cust-1"
        tags={[]}
        allTags={[]}
        editable={true}
        canInlineCreate={true}
        slug="test-org"
      />
    );

    expect(screen.getByTestId("client-tags-tab")).toBeInTheDocument();
    expect(screen.getByTestId("tag-input")).toBeInTheDocument();
  });
});
