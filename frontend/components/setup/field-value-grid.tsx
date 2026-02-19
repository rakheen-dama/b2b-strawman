import Link from "next/link";
import { cn } from "@/lib/utils";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import type { FieldValueGridProps, FieldValueProps } from "./types";

function FieldItem({ field }: { field: FieldValueProps }) {
  const unfilled = field.required && (!field.value || field.value === "");

  return (
    <div
      className={cn(
        "space-y-1 rounded-md p-2",
        unfilled && "border-l-2 border-amber-400",
      )}
    >
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {field.name}
      </p>
      {field.value ? (
        <p className="text-sm text-slate-900 dark:text-slate-100">
          {field.value}
        </p>
      ) : (
        <p className="text-sm italic text-muted-foreground">Not set</p>
      )}
    </div>
  );
}

export function FieldValueGrid({
  fields,
  groups,
  editHref,
}: FieldValueGridProps) {
  // Group fields if groups are provided
  const groupedContent = groups ? renderGrouped(fields, groups) : null;

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">Custom Fields</CardTitle>
          {editHref && (
            <Link
              href={editHref}
              className="text-xs text-teal-600 hover:underline dark:text-teal-400"
            >
              Edit Fields
            </Link>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {groupedContent ?? (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {fields.map((field) => (
              <FieldItem key={field.slug} field={field} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function renderGrouped(
  fields: FieldValueProps[],
  groups: { id: string; name: string }[],
) {
  const groupMap = new Map(groups.map((g) => [g.id, g.name]));
  const grouped = new Map<string, FieldValueProps[]>();

  for (const field of fields) {
    const key = field.groupId && groupMap.has(field.groupId)
      ? field.groupId
      : "__other__";
    const list = grouped.get(key) ?? [];
    list.push(field);
    grouped.set(key, list);
  }

  const orderedKeys = [
    ...groups.map((g) => g.id).filter((id) => grouped.has(id)),
    ...(grouped.has("__other__") ? ["__other__"] : []),
  ];

  return (
    <div className="space-y-4">
      {orderedKeys.map((key) => (
        <div key={key}>
          <h4 className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
            {key === "__other__" ? "Other" : groupMap.get(key)}
          </h4>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {grouped.get(key)!.map((field) => (
              <FieldItem key={field.slug} field={field} />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
