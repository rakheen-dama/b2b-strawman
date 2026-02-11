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
        lead: "bg-indigo-100 text-indigo-700",
        member: "bg-olive-100 text-olive-700",
        owner: "bg-amber-100 text-amber-700",
        admin: "bg-slate-100 text-slate-700",
        // Plan variants
        starter: "bg-olive-100 text-olive-700",
        pro: "bg-indigo-100 text-indigo-700",
        // Status variants
        success: "bg-green-100 text-green-700",
        warning: "bg-amber-100 text-amber-700",
        destructive: "bg-red-100 text-red-700",
        // Generic
        neutral: "bg-olive-100 text-olive-600",
        outline: "border-olive-200 text-olive-700 bg-transparent",
        // Backward-compatible aliases
        default: "bg-olive-100 text-olive-600",
        secondary: "bg-olive-100 text-olive-600",
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
