import { HeroSection } from "@/components/marketing/hero-section";
import { LogoBar } from "@/components/marketing/logo-bar";
import { FeaturesSection } from "@/components/marketing/features-section";
import { StatsSection } from "@/components/marketing/stats-section";
import { PricingPreview } from "@/components/marketing/pricing-preview";
import { TestimonialsSection } from "@/components/marketing/testimonials-section";
import { CtaSection } from "@/components/marketing/cta-section";
import { Footer } from "@/components/marketing/footer";

export default function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <main className="flex-1">
        <HeroSection />
        <LogoBar />
        <FeaturesSection />
        <StatsSection />
        <PricingPreview />
        <TestimonialsSection />
        <CtaSection />
      </main>
      <Footer />
    </div>
  );
}
