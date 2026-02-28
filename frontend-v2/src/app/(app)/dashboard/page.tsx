import { redirect } from "next/navigation";

/**
 * Pre-org dashboard page.
 * If a user lands here without an active org, they are redirected to create one.
 * With org sync enabled via proxy.ts, this page typically redirects immediately.
 */
export default function DashboardRedirectPage() {
  redirect("/create-org");
}
