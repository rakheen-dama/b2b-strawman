import Link from "next/link";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";

/**
 * Branded "You've been signed out" confirmation page — the post-logout landing
 * target (Epic 570). Replaces the unstyled frontend root as the terminus of the
 * gateway's RP-initiated logout, killing the whitelabel leak. Public per the
 * middleware allowlist (`/signed-out`); the user reaching it has no SESSION.
 *
 * Server Component — a static confirmation with no authenticated data fetch and
 * no client interactivity. The "Sign in again" CTA is a plain `<Link>` to the
 * branded `/sign-in` route (which hands off to the Keycloak OAuth2 flow).
 */
export default function SignedOutPage() {
  return (
    <Card className="w-full max-w-md">
      <CardHeader className="text-center">
        <CardTitle className="font-display text-2xl text-slate-900 dark:text-slate-100">
          You&apos;ve been signed out
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        <p className="text-center text-sm text-slate-600 dark:text-slate-400">
          Your session has ended. You can safely close this tab, or sign in again to return to your
          workspace.
        </p>
        <Link
          href="/sign-in"
          className="inline-flex w-full items-center justify-center rounded-full bg-teal-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-teal-500"
        >
          Sign in again
        </Link>
      </CardContent>
    </Card>
  );
}
