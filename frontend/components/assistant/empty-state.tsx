import { Sparkles } from "lucide-react";
import Link from "next/link";

interface EmptyStateProps {
  orgRole: string;
  slug: string;
}

const ADMIN_ROLES = ["owner", "admin"];

export function EmptyState({ orgRole, slug }: EmptyStateProps) {
  const isAdmin = ADMIN_ROLES.includes(orgRole.toLowerCase());

  if (isAdmin) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 px-4 text-center">
        <Sparkles className="size-8 text-slate-300" />
        <div>
          <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
            AI not configured
          </p>
          <p className="mt-1 text-xs text-slate-400">
            Set up your Anthropic API key in{" "}
            <Link
              href={`/org/${slug}/settings/integrations`}
              className="text-teal-600 underline-offset-2 hover:underline"
            >
              Settings &rarr; Integrations
            </Link>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 px-4 text-center">
      <Sparkles className="size-8 text-slate-300" />
      <p className="text-sm text-slate-500 dark:text-slate-400">
        AI assistant is not available. Ask your admin to enable it.
      </p>
    </div>
  );
}
