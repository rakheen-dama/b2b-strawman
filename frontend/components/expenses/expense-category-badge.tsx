import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { ExpenseCategory } from "@/lib/types";

const CATEGORY_STYLES: Record<ExpenseCategory, string> = {
  FILING_FEE: "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300",
  TRAVEL: "bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300",
  COURIER: "bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300",
  SOFTWARE: "bg-teal-100 text-teal-800 dark:bg-teal-900/30 dark:text-teal-300",
  SUBCONTRACTOR: "bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300",
  PRINTING: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
  COMMUNICATION: "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300",
  OTHER: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
};

const CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  FILING_FEE: "Filing Fee",
  TRAVEL: "Travel",
  COURIER: "Courier",
  SOFTWARE: "Software",
  SUBCONTRACTOR: "Subcontractor",
  PRINTING: "Printing",
  COMMUNICATION: "Communication",
  OTHER: "Other",
};

interface ExpenseCategoryBadgeProps {
  category: ExpenseCategory;
}

export function ExpenseCategoryBadge({ category }: ExpenseCategoryBadgeProps) {
  return (
    <Badge variant="neutral" className={cn(CATEGORY_STYLES[category])}>
      {CATEGORY_LABELS[category]}
    </Badge>
  );
}
