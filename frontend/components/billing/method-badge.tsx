import { Badge } from "@/components/ui/badge";

const methodVariantMap: Record<string, "success" | "neutral" | "pro"> = {
  PAYFAST: "success",
  COMPLIMENTARY: "pro",
  MANUAL: "neutral",
};

const methodLabelMap: Record<string, string> = {
  PAYFAST: "PayFast",
  PILOT: "Pilot",
  COMPLIMENTARY: "Complimentary",
  DEBIT_ORDER: "Debit Order",
  MANUAL: "Manual",
};

interface MethodBadgeProps {
  method: string;
}

export function MethodBadge({ method }: MethodBadgeProps) {
  if (method === "PILOT") {
    return (
      <Badge
        variant="neutral"
        className="bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300"
      >
        {methodLabelMap[method] ?? method}
      </Badge>
    );
  }

  if (method === "DEBIT_ORDER") {
    return (
      <Badge
        variant="neutral"
        className="bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300"
      >
        {methodLabelMap[method] ?? method}
      </Badge>
    );
  }

  const variant = methodVariantMap[method] ?? "neutral";
  return (
    <Badge variant={variant}>{methodLabelMap[method] ?? method}</Badge>
  );
}
