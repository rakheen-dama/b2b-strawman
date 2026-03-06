import { redirect } from "next/navigation";
import { AUTH_MODE, getAuthContext } from "@/lib/auth/server";

export default async function DashboardRedirectPage() {
  if (AUTH_MODE === "keycloak") {
    let orgSlug: string | undefined;
    try {
      const ctx = await getAuthContext();
      orgSlug = ctx.orgSlug;
    } catch {
      // no org in token
    }
    redirect(orgSlug ? `/org/${orgSlug}/dashboard` : "/create-org");
  }

  redirect("/create-org");
}
