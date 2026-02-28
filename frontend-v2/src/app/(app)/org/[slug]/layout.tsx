import { redirect } from "next/navigation";
import { getAuthContext } from "@/lib/auth";
import { AppShell } from "@/components/shell/app-shell";

export default async function OrgLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let orgSlug: string;
  try {
    const ctx = await getAuthContext();
    orgSlug = ctx.orgSlug;
  } catch {
    redirect("/dashboard");
  }

  if (orgSlug !== slug) {
    redirect(`/org/${orgSlug}/dashboard`);
  }

  return <AppShell slug={slug}>{children}</AppShell>;
}
