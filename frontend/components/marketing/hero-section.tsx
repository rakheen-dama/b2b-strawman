import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";
const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";
const signUpHref = AUTH_MODE === "keycloak" ? "/request-access" : "/sign-up";
const signInHref = AUTH_MODE === "keycloak" ? `${GATEWAY_URL}/oauth2/authorization/keycloak` : "/sign-in";

function AnnouncementBadge() {
  return (
    <Link
      href={signUpHref}
      className="inline-flex items-center gap-2 rounded-full bg-teal-50 px-4 py-1.5 text-sm font-medium text-teal-700 transition-colors hover:bg-teal-100 dark:bg-teal-950 dark:text-teal-300 dark:hover:bg-teal-900"
    >
      New: Pro plan with dedicated infrastructure
      <ArrowRight className="size-3.5" />
    </Link>
  );
}

export function HeroSection() {
  return (
    <section className="bg-slate-50 px-6 py-20 sm:py-28 lg:py-32 dark:bg-slate-950">
      <div className="mx-auto grid max-w-7xl items-center gap-12 lg:grid-cols-5 lg:gap-16">
        {/* Text content — 60% */}
        <div className="lg:col-span-3">
          <AnnouncementBadge />

          <h1 className="mt-6 font-display text-5xl leading-tight text-slate-950 text-balance sm:text-[5rem] sm:leading-[1.1] dark:text-slate-50">
            Document collaboration for modern teams
          </h1>

          <p className="mt-6 max-w-xl text-lg leading-8 text-slate-700 dark:text-slate-300">
            Organize documents, manage projects, and collaborate across your
            organization — all in one workspace. Built for teams that value
            security and simplicity.
          </p>

          <div className="mt-8 flex flex-wrap gap-4">
            <Button size="lg" asChild>
              <Link href={signUpHref}>Get Started</Link>
            </Button>
            <Button variant="soft" size="lg" asChild>
              {AUTH_MODE === "keycloak" ? (
                <a href={signInHref}>Sign In</a>
              ) : (
                <Link href={signInHref}>Sign In</Link>
              )}
            </Button>
          </div>
        </div>

        {/* Screenshot placeholder — 40% */}
        <div className="lg:col-span-2">
          <div className="aspect-video rounded-lg bg-slate-200 dark:bg-slate-800" />
        </div>
      </div>
    </section>
  );
}
