import Link from "next/link";
import { Lock } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

interface ModuleDisabledFallbackProps {
  moduleName: string;
  slug: string;
}

/**
 * Server component fallback shown when a module is disabled.
 * Renders a centered card with a link to Settings → Features.
 */
export function ModuleDisabledFallback({ moduleName, slug }: ModuleDisabledFallbackProps) {
  return (
    <div className="flex min-h-[40vh] items-center justify-center px-4">
      <Card className="max-w-md">
        <CardContent className="flex flex-col items-center gap-4 p-8 text-center">
          <div className="rounded-full bg-slate-100 p-3 dark:bg-slate-800">
            <Lock className="size-6 text-slate-500 dark:text-slate-400" aria-hidden="true" />
          </div>
          <div className="space-y-2">
            <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">
              {moduleName} is not enabled
            </h2>
            <p className="text-sm text-slate-600 dark:text-slate-400">
              This feature is not enabled for your organization. An admin can enable it in Settings
              → Features.
            </p>
          </div>
          <Link
            href={`/org/${slug}/settings/features`}
            className="inline-flex items-center text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
          >
            Go to Features
          </Link>
        </CardContent>
      </Card>
    </div>
  );
}
