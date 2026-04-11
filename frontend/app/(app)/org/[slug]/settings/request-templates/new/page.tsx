import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { CreateRequestTemplateForm } from "@/components/information-requests/create-request-template-form";

export default async function NewRequestTemplatePage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings/request-templates`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Request Templates
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          New Request Template
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to create request templates. Only admins and owners can access
          this page.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/request-templates`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Request Templates
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          New Request Template
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Create a template for information requests.
        </p>
      </div>

      <CreateRequestTemplateForm slug={slug} />
    </div>
  );
}
