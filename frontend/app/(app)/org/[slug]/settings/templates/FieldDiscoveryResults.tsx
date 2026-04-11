import { Badge } from "@/components/ui/badge";
import type { DiscoveredField } from "@/lib/types";

interface FieldDiscoveryResultsProps {
  fields: DiscoveredField[];
}

export function FieldDiscoveryResults({ fields }: FieldDiscoveryResultsProps) {
  if (fields.length === 0) {
    return (
      <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
        No merge fields discovered in this template.
      </p>
    );
  }

  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium text-slate-950 dark:text-slate-50">
        Discovered Fields ({fields.length})
      </h3>
      <ul className="divide-y divide-slate-200 rounded-md border border-slate-200 dark:divide-slate-700 dark:border-slate-700">
        {fields.map((field) => (
          <li key={field.path} className="flex items-center justify-between px-3 py-2">
            <div className="min-w-0 flex-1">
              <p className="truncate font-mono text-sm text-slate-950 dark:text-slate-50">
                {field.path}
              </p>
              {field.label && (
                <p className="truncate text-xs text-slate-500 dark:text-slate-400">{field.label}</p>
              )}
            </div>
            <div className="ml-3 shrink-0">
              {field.status === "VALID" ? (
                <Badge variant="success">Valid</Badge>
              ) : (
                <Badge variant="warning">Unknown</Badge>
              )}
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
