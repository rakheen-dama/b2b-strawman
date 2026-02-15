import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { CreateTemplateForm } from "@/components/templates/CreateTemplateForm";

export default async function NewTemplatePage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/templates`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Templates
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          New Template
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Create a custom document template.
        </p>
      </div>

      <CreateTemplateForm slug={slug} />
    </div>
  );
}
