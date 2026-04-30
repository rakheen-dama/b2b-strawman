"use client";

import { useBranding } from "@/hooks/use-branding";
import { BRAND_NAME } from "@/lib/brand";

export function PortalFooter() {
  const { footerText } = useBranding();

  return (
    <footer className="border-t border-slate-200 bg-white py-4">
      <div className="mx-auto max-w-6xl px-4 text-center sm:px-6 lg:px-8">
        {footerText && (
          <p className="mb-1 text-sm text-slate-600">{footerText}</p>
        )}
        <p className="text-xs text-slate-400">Powered by {BRAND_NAME}</p>
      </div>
    </footer>
  );
}
