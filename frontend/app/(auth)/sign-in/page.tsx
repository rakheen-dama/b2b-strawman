import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { safeReturnTo } from "@/lib/auth/return-to";
import { SignInCta } from "./sign-in-cta";

/**
 * Branded sign-in route — the real target of the graceful expiry funnel
 * (previously a dangling 404). Public per the middleware allowlist
 * (`/sign-in(.*)`). NOT a credential form — Kazi never collects credentials;
 * the CTA hands off to the gateway's Keycloak OAuth2 flow.
 *
 * Next.js 16: `searchParams` is a Promise — always `await` it.
 */
export default async function SignInPage({
  searchParams,
}: {
  searchParams: Promise<{ reason?: string; returnTo?: string }>;
}) {
  const { reason, returnTo } = await searchParams;
  const expired = reason === "expired";
  const safeReturn = safeReturnTo(returnTo);

  const title = expired ? "Your session expired" : "Sign in to Kazi";
  const description = expired
    ? "Your session expired for security. Sign in to continue."
    : "Sign in to continue to your workspace.";

  return (
    <Card className="w-full max-w-md">
      <CardHeader className="text-center">
        <CardTitle className="font-display text-2xl text-slate-900 dark:text-slate-100">
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        <p className="text-center text-sm text-slate-600 dark:text-slate-400">{description}</p>
        <SignInCta returnTo={safeReturn} />
      </CardContent>
    </Card>
  );
}
