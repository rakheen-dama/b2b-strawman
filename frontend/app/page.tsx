import { NavBar } from "@/components/marketing/nav-bar";
import { HeroSection } from "@/components/marketing/hero-section";
import { BuiltForAfrica } from "@/components/marketing/built-for-africa";
import { FeaturesSection } from "@/components/marketing/features-section";
import { PricingPreview } from "@/components/marketing/pricing-preview";
import { CtaSection } from "@/components/marketing/cta-section";
import { Footer } from "@/components/marketing/footer";

export default function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <NavBar />
      <main className="flex-1">
        <HeroSection />
        <BuiltForAfrica />
        <FeaturesSection />
        <PricingPreview />
        <CtaSection />
      </main>
      <Footer />
    </div>
  );
}
