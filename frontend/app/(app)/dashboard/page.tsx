import { redirect } from "next/navigation";

// Primary redirect logic lives in middleware.ts (instant, no page render).
// This page only renders if middleware doesn't redirect (shouldn't happen).
export default function DashboardRedirectPage() {
  redirect("/create-org");
}
