import { Badge } from "@/components/ui/badge";
import type { FieldDefinitionResponse } from "@/lib/types";
import { formatDate, formatCurrency } from "@/lib/format";

interface CustomFieldBadgesProps {
  customFields: Record<string, unknown>;
  fieldDefinitions: FieldDefinitionResponse[];
  maxFields?: number;
}

function formatBadgeValue(
  field: FieldDefinitionResponse,
  value: unknown,
): string | null {
  if (value == null || value === "") return null;

  switch (field.fieldType) {
    case "BOOLEAN":
      return value === true ? "Yes" : "No";
    case "DATE":
      return formatDate(String(value));
    case "DROPDOWN": {
      const opt = field.options?.find((o) => o.value === value);
      return opt?.label ?? String(value);
    }
    case "CURRENCY": {
      const obj = value as { amount?: number; currency?: string };
      if (obj?.amount != null && obj?.currency) {
        return formatCurrency(obj.amount, obj.currency);
      }
      return null;
    }
    case "NUMBER":
      return new Intl.NumberFormat("en-US").format(Number(value));
    case "TEXT":
    case "URL":
    case "EMAIL":
    case "PHONE": {
      const str = String(value);
      return str.length > 30 ? str.slice(0, 27) + "..." : str;
    }
    default:
      return String(value);
  }
}

export function CustomFieldBadges({
  customFields,
  fieldDefinitions,
  maxFields = 3,
}: CustomFieldBadgesProps) {
  const fieldMap = new Map(fieldDefinitions.map((f) => [f.slug, f]));

  const badges: { name: string; value: string }[] = [];
  for (const [slug, value] of Object.entries(customFields)) {
    if (badges.length >= maxFields) break;
    const field = fieldMap.get(slug);
    if (!field || !field.active) continue;
    const formatted = formatBadgeValue(field, value);
    if (formatted) {
      badges.push({ name: field.name, value: formatted });
    }
  }

  if (badges.length === 0) return null;

  return (
    <div className="mt-3 flex flex-wrap gap-1.5" data-testid="custom-field-badges">
      {badges.map((badge) => (
        <Badge
          key={badge.name}
          variant="secondary"
          className="text-xs font-normal"
        >
          <span className="font-medium">{badge.name}:</span>{" "}
          {badge.value}
        </Badge>
      ))}
    </div>
  );
}
