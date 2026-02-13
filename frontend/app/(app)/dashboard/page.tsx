import { redirect } from "next/navigation";

// Primary redirect logic lives in proxy.ts (instant, no page render).
// This page only renders if proxy doesn't redirect (shouldn't happen).
export default function DashboardRedirectPage() {
  redirect("/create-org");
}
