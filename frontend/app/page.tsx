import { HeroSection } from "@/components/marketing/hero-section";
import { LogoBar } from "@/components/marketing/logo-bar";
import { CtaSection } from "@/components/marketing/cta-section";
import { Footer } from "@/components/marketing/footer";

export default function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <main className="flex-1">
        <HeroSection />
        <LogoBar />
        {/* Features, Stats, Pricing, Testimonials sections added in Epic 32B */}
        <CtaSection />
      </main>
      <Footer />
    </div>
  );
}
