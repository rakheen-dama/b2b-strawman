import Link from "next/link";
import { Button } from "@/components/ui/button";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";
const signUpHref = AUTH_MODE === "mock" ? "/sign-up" : "/request-access";
const signInHref =
  AUTH_MODE === "mock" ? "/sign-in" : `${GATEWAY_URL}/oauth2/authorization/keycloak`;

export function NavBar() {
  return (
    <nav className="fixed top-0 z-50 w-full border-b border-white/10 bg-slate-950/80 backdrop-blur-xl">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-6">
        <Link href="/" className="font-display text-xl tracking-tight text-white">
          kazi
        </Link>

        <div className="hidden items-center gap-8 sm:flex">
          <a href="#features" className="text-sm text-white/60 transition-colors hover:text-white">
            Features
          </a>
          <a href="#pricing" className="text-sm text-white/60 transition-colors hover:text-white">
            Pricing
          </a>
        </div>

        <div className="flex items-center gap-3">
          {AUTH_MODE === "keycloak" ? (
            <a
              href={signInHref}
              className="text-sm text-white/60 transition-colors hover:text-white"
            >
              Sign In
            </a>
          ) : (
            <Link
              href={signInHref}
              className="text-sm text-white/60 transition-colors hover:text-white"
            >
              Sign In
            </Link>
          )}
          <Button
            variant="soft"
            size="sm"
            asChild
            className="bg-white/8 text-white hover:bg-white/12 dark:bg-white/8 dark:text-white dark:hover:bg-white/12"
          >
            <Link href={signUpHref}>Get Started</Link>
          </Button>
          <Button variant="accent" size="sm" asChild>
            <a href="mailto:hello@kazi.africa">Book a Demo</a>
          </Button>
        </div>
      </div>
    </nav>
  );
}
