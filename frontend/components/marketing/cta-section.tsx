import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
const signUpHref = AUTH_MODE === "mock" ? "/sign-up" : "/request-access";

export function CtaSection() {
  return (
    <section className="relative overflow-hidden bg-slate-950 px-6 py-24 lg:py-32">
      {/* Background glow */}
      <div className="pointer-events-none absolute left-1/2 top-0 h-[400px] w-[600px] -translate-x-1/2 rounded-full bg-teal-500/8 blur-[120px]" />

      <div className="relative mx-auto max-w-2xl text-center">
        <h2 className="font-display text-3xl tracking-tight text-white sm:text-4xl">
          Ready to see what Kazi can do for your practice?
        </h2>
        <p className="mt-4 text-lg text-slate-400">
          Book a walkthrough and we&apos;ll show you how Kazi fits your
          workflows — no pressure, no sales pitch.
        </p>
        <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
          <Button size="lg" variant="accent" asChild>
            <a href="mailto:hello@kazi.africa">
              Book a Demo
              <ArrowRight className="size-4" />
            </a>
          </Button>
          <Button
            size="lg"
            variant="soft"
            asChild
            className="bg-white/8 text-white hover:bg-white/12"
          >
            <Link href={signUpHref}>Get Started</Link>
          </Button>
        </div>
      </div>
    </section>
  );
}
