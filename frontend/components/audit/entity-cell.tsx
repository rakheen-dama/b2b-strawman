import Link from "next/link";

import { entityDisplayName, entityTypeLabel } from "@/components/audit/entity-type-labels";

const ENTITY_ROUTES: Record<string, (id: string) => string> = {
  customer: (id) => `/clients/${id}`,
  project: (id) => `/matters/${id}`,
  invoice: (id) => `/invoices/${id}`,
  information_request: (id) => `/information-requests/${id}`,
  document: (id) => `/documents/${id}`,
  trust_transaction: (id) => `/trust-accounting/transactions/${id}`,
  matter_closure: (id) => `/matters/${id}/closure`,
  data_subject_request: (id) => `/settings/data-protection/requests/${id}`,
  org_role: (id) => `/settings/roles/${id}`,
  member: (id) => `/team#member-${id}`,
};

// entityTypes that should never link (synthetic / not navigable)
const NO_LINK_TYPES = new Set(["audit_export", "task"]);

function shortId(id: string): string {
  return id.length > 8 ? id.slice(0, 8) : id;
}

export interface EntityCellProps {
  /** Audit storage literal (ADR-342), e.g. `project`; rendered through the legal label map. */
  entityType: string;
  entityId: string | null;
  slug: string;
  /** The row's audit details; a fee note's `invoice_number` names the entity ("Fee note INV-0003"). */
  details?: Record<string, unknown> | null;
}

/**
 * Entity column of the audit trail: the legal label plus the entity's name when the details carry
 * one, otherwise its short id ("Matter 3f2a1b9c"). Links to the entity page where one exists.
 */
export function EntityCell({ entityType, entityId, slug, details }: EntityCellProps) {
  const label = entityTypeLabel(entityType);

  if (!entityId) {
    return (
      <span
        className="text-xs whitespace-nowrap text-slate-400"
        data-testid="entity-cell-empty"
        data-entity-type={entityType}
      >
        {label || "—"}
      </span>
    );
  }

  const name = entityDisplayName(entityType, details);
  const idText = (
    <span className={name ? "text-slate-500" : "font-mono text-slate-500"}>
      {name ?? shortId(entityId)}
    </span>
  );

  const route = NO_LINK_TYPES.has(entityType) ? undefined : ENTITY_ROUTES[entityType];
  if (!route) {
    return (
      <span
        className="text-xs whitespace-nowrap text-slate-600 dark:text-slate-400"
        data-testid="entity-cell-literal"
        data-entity-type={entityType}
      >
        {label} {idText}
      </span>
    );
  }

  const href = `/org/${slug}${route(entityId)}`;
  return (
    <Link
      href={href}
      data-testid="entity-cell-link"
      data-entity-type={entityType}
      className="text-xs whitespace-nowrap text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400"
    >
      {label} {idText}
    </Link>
  );
}
