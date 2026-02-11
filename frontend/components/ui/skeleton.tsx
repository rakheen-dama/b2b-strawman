import { cn } from "@/lib/utils";

function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      className={cn("animate-pulse rounded-md bg-olive-200/50 dark:bg-olive-800/50", className)}
      {...props}
    />
  );
}

export { Skeleton };
