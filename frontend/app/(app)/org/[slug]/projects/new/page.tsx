import { redirect } from "next/navigation";

/**
 * `/org/[slug]/projects/new` route.
 *
 * This page exists purely to disambiguate the literal `new` segment from the
 * sibling `[id]` dynamic segment. Without it, Next.js falls through to
 * `/projects/[id]/page.tsx` with `id = "new"`, which then tries to fetch
 * `/api/projects/new` and crashes with `Parameter 'id' should be of type UUID`.
 *
 * Instead, we redirect to the projects list with `?new=1` so the existing
 * "New from Template" dialog auto-opens, optionally pre-selecting the customer
 * when `customerId` is supplied (used by deep-links such as "New Matter for
 * this client" on customer detail pages).
 *
 * Spec: qa_cycle/fix-specs/GAP-S3-05.md
 */
export default async function NewProjectRedirectPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;

  const query = new URLSearchParams();
  query.set("new", "1");

  const customerId =
    typeof resolvedSearchParams.customerId === "string" ? resolvedSearchParams.customerId : null;
  if (customerId) {
    query.set("customerId", customerId);
  }

  const templateId =
    typeof resolvedSearchParams.templateId === "string" ? resolvedSearchParams.templateId : null;
  if (templateId) {
    query.set("templateId", templateId);
  }

  redirect(`/org/${slug}/projects?${query.toString()}`);
}
