import Link from "next/link";

const ENTITY_ROUTES: Record<string, (id: string) => string> = {
  customer: (id) => `/customers/${id}`,
  project: (id) => `/projects/${id}`,
  invoice: (id) => `/invoices/${id}`,
  proposal: (id) => `/proposals/${id}`,
  information_request: (id) => `/information-requests/${id}`,
  document: (id) => `/documents/${id}`,
  trust_transaction: (id) => `/trust-accounting/transactions/${id}`,
  matter_closure: (id) => `/projects/${id}/closure`,
  data_subject_request: (id) => `/settings/data-protection/requests/${id}`,
  org_role: (id) => `/settings/roles/${id}`,
  member: (id) => `/team#member-${id}`,
};

// entityTypes that should never link (synthetic / not navigable)
const NO_LINK_TYPES = new Set(["audit_export", "task"]);

function shortId(id: string): string {
  return id.length > 8 ? id.slice(0, 8) : id;
}

function literalLabel(entityType: string, entityId: string): string {
  return `${entityType}:${shortId(entityId)}`;
}

export interface EntityCellProps {
  entityType: string;
  entityId: string | null;
  slug: string;
}

export function EntityCell({ entityType, entityId, slug }: EntityCellProps) {
  if (!entityId) {
    return (
      <span className="text-xs text-slate-400" data-testid="entity-cell-empty">
        {entityType || "—"}
      </span>
    );
  }

  if (NO_LINK_TYPES.has(entityType)) {
    return (
      <span
        className="font-mono text-xs text-slate-600 dark:text-slate-400"
        data-testid="entity-cell-literal"
      >
        {literalLabel(entityType, entityId)}
      </span>
    );
  }

  const route = ENTITY_ROUTES[entityType];
  if (!route) {
    return (
      <span
        className="font-mono text-xs text-slate-600 dark:text-slate-400"
        data-testid="entity-cell-literal"
      >
        {literalLabel(entityType, entityId)}
      </span>
    );
  }

  const href = `/org/${slug}${route(entityId)}`;
  return (
    <Link
      href={href}
      data-testid="entity-cell-link"
      data-entity-type={entityType}
      className="text-xs text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400"
    >
      {entityType}{" "}
      <span className="font-mono text-slate-500">{shortId(entityId)}</span>
    </Link>
  );
}
