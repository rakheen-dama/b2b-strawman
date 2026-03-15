import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { redirect } from "next/navigation";
import { CreateRuleClient } from "./create-rule-client";

export default async function NewAutomationPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    redirect(`/org/${slug}/settings/automations`);
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/automations`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Automations
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          New Automation Rule
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure the trigger, conditions, and actions for your new rule.
        </p>
      </div>

      <CreateRuleClient slug={slug} />
    </div>
  );
}
