"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight, Loader2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/format";
import { getCompliancePackDetail } from "@/app/(app)/org/[slug]/settings/compliance/actions";
import type { CompliancePackEntry, CompliancePackDetail } from "@/lib/types";

interface CompliancePackListProps {
  packs: CompliancePackEntry[];
}

export function CompliancePackList({ packs }: CompliancePackListProps) {
  const [expandedPackId, setExpandedPackId] = useState<string | null>(null);
  const [packDetails, setPackDetails] = useState<
    Record<string, CompliancePackDetail>
  >({});
  const [loadingPackId, setLoadingPackId] = useState<string | null>(null);

  async function handleToggle(packId: string) {
    if (expandedPackId === packId) {
      setExpandedPackId(null);
      return;
    }

    setExpandedPackId(packId);

    if (!packDetails[packId]) {
      setLoadingPackId(packId);
      const detail = await getCompliancePackDetail(packId);
      if (detail) {
        setPackDetails((prev) => ({ ...prev, [packId]: detail }));
      }
      setLoadingPackId(null);
    }
  }

  if (packs.length === 0) {
    return (
      <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">
        No compliance packs applied.
      </p>
    );
  }

  return (
    <div className="mt-4 space-y-2">
      {packs.map((pack) => {
        const isExpanded = expandedPackId === pack.packId;
        const detail = packDetails[pack.packId];
        const isLoading = loadingPackId === pack.packId;

        return (
          <div
            key={pack.packId}
            className="rounded-md border border-slate-100 dark:border-slate-800"
          >
            <button
              type="button"
              onClick={() => handleToggle(pack.packId)}
              className="flex w-full items-center justify-between px-4 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900 rounded-md transition-colors"
            >
              <div className="flex items-center gap-2">
                {isExpanded ? (
                  <ChevronDown className="size-4 shrink-0 text-slate-400" />
                ) : (
                  <ChevronRight className="size-4 shrink-0 text-slate-400" />
                )}
                <div>
                  <p className="font-medium text-slate-900 dark:text-slate-100">
                    {detail?.name ?? pack.packId}
                  </p>
                  {detail?.name && (
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {pack.packId}
                    </p>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-3">
                <Badge variant="neutral">v{pack.version}</Badge>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Applied {formatDate(pack.appliedAt)}
                </p>
              </div>
            </button>

            {isExpanded && (
              <div className="border-t border-slate-100 px-4 py-4 dark:border-slate-800">
                {isLoading ? (
                  <div className="flex items-center gap-2 text-sm text-slate-500">
                    <Loader2 className="size-4 animate-spin" />
                    Loading pack details...
                  </div>
                ) : detail ? (
                  <PackDetailView detail={detail} />
                ) : (
                  <p className="text-sm text-slate-500 dark:text-slate-400">
                    Could not load pack details.
                  </p>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

function PackDetailView({ detail }: { detail: CompliancePackDetail }) {
  return (
    <div className="space-y-5">
      {/* Description and metadata */}
      <div>
        <p className="text-sm text-slate-700 dark:text-slate-300">
          {detail.description}
        </p>
        <div className="mt-2 flex items-center gap-2">
          {detail.jurisdiction && (
            <Badge variant="outline">{detail.jurisdiction}</Badge>
          )}
          <Badge variant="outline">{detail.customerType}</Badge>
        </div>
      </div>

      {/* Checklist Items */}
      {detail.checklistTemplate &&
        detail.checklistTemplate.items.length > 0 && (
          <div>
            <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              Checklist Items
            </h4>
            <div className="mt-2 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-700">
                    <th className="pb-2 pr-4 text-left font-medium text-slate-600 dark:text-slate-400">
                      Name
                    </th>
                    <th className="pb-2 pr-4 text-left font-medium text-slate-600 dark:text-slate-400">
                      Description
                    </th>
                    <th className="pb-2 pr-4 text-left font-medium text-slate-600 dark:text-slate-400">
                      Required
                    </th>
                    <th className="pb-2 text-left font-medium text-slate-600 dark:text-slate-400">
                      Document Required
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {detail.checklistTemplate.items
                    .sort((a, b) => a.sortOrder - b.sortOrder)
                    .map((item, idx) => (
                      <tr
                        key={idx}
                        className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                      >
                        <td className="py-2 pr-4 text-slate-900 dark:text-slate-100">
                          {item.name}
                        </td>
                        <td className="py-2 pr-4 text-slate-600 dark:text-slate-400">
                          {item.description}
                        </td>
                        <td className="py-2 pr-4">
                          <Badge
                            variant={item.required ? "success" : "neutral"}
                          >
                            {item.required ? "Yes" : "No"}
                          </Badge>
                        </td>
                        <td className="py-2">
                          {item.requiresDocument ? (
                            <Badge variant="warning">
                              {item.requiredDocumentLabel ?? "Yes"}
                            </Badge>
                          ) : (
                            <Badge variant="neutral">No</Badge>
                          )}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

      {/* Field Definitions */}
      {detail.fieldDefinitions && detail.fieldDefinitions.length > 0 && (
        <div>
          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            Field Definitions
          </h4>
          <ul className="mt-2 space-y-1">
            {detail.fieldDefinitions.map((field, idx) => (
              <li
                key={idx}
                className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300"
              >
                <span className="font-medium">{field.label}</span>
                <Badge variant="outline">{field.fieldType}</Badge>
                {field.required && <Badge variant="success">Required</Badge>}
                {field.groupName && (
                  <span className="text-xs text-slate-500">
                    ({field.groupName})
                  </span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Retention Overrides */}
      {detail.retentionOverrides && detail.retentionOverrides.length > 0 && (
        <div>
          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            Retention Overrides
          </h4>
          <div className="mt-2 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700">
                  <th className="pb-2 pr-4 text-left font-medium text-slate-600 dark:text-slate-400">
                    Record Type
                  </th>
                  <th className="pb-2 pr-4 text-left font-medium text-slate-600 dark:text-slate-400">
                    Trigger
                  </th>
                  <th className="pb-2 pr-4 text-left font-medium text-slate-600 dark:text-slate-400">
                    Days
                  </th>
                  <th className="pb-2 text-left font-medium text-slate-600 dark:text-slate-400">
                    Action
                  </th>
                </tr>
              </thead>
              <tbody>
                {detail.retentionOverrides.map((override, idx) => (
                  <tr
                    key={idx}
                    className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                  >
                    <td className="py-2 pr-4 text-slate-900 dark:text-slate-100">
                      {override.recordType}
                    </td>
                    <td className="py-2 pr-4 text-slate-600 dark:text-slate-400">
                      {override.triggerEvent}
                    </td>
                    <td className="py-2 pr-4 text-slate-900 dark:text-slate-100">
                      {override.retentionDays}
                    </td>
                    <td className="py-2">
                      <Badge variant="outline">{override.action}</Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
