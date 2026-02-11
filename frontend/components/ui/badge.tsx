import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { Slot } from "radix-ui";

import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center justify-center rounded-full border border-transparent px-2.5 py-0.5 text-xs font-medium w-fit whitespace-nowrap shrink-0 [&>svg]:size-3 gap-1 [&>svg]:pointer-events-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive transition-[color,box-shadow] overflow-hidden",
  {
    variants: {
      variant: {
        // Semantic role variants
        lead: "bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300",
        member: "bg-olive-100 text-olive-700 dark:bg-olive-800 dark:text-olive-300",
        owner: "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
        admin: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
        // Plan variants
        starter: "bg-olive-100 text-olive-700 dark:bg-olive-800 dark:text-olive-300",
        pro: "bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300",
        // Status variants
        success: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
        warning: "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
        destructive: "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300",
        // Generic
        neutral: "bg-olive-100 text-olive-600 dark:bg-olive-800 dark:text-olive-400",
        outline:
          "border-olive-200 text-olive-700 bg-transparent dark:border-olive-700 dark:text-olive-300",
        // Backward-compatible aliases
        default: "bg-olive-200 text-olive-800 dark:bg-olive-800 dark:text-olive-200",
        secondary: "bg-olive-100 text-olive-600 dark:bg-olive-800 dark:text-olive-400",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

function Badge({
  className,
  variant = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : "span";

  return (
    <Comp
      data-slot="badge"
      data-variant={variant}
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  );
}

export { Badge, badgeVariants };
