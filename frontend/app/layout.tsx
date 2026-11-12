import type { Metadata } from "next";
import { AuthProvider } from "@/lib/auth/client/auth-provider";
import localFont from "next/font/local";
import { KaziThemeProvider } from "@/components/theme/theme-provider";
import { ThemedToaster } from "@/components/theme/themed-toaster";
import "./globals.css";

// G89-F46: self-hosted with next/font/local (no build-time Google Fonts download). Latin subset
// woff2 from @fontsource 5.3.0 (Sora and JetBrains Mono variable, wght 100–800; IBM Plex Sans
// 400–700 static); SIL OFL 1.1, licence beside each file in ./fonts/<family>/OFL.txt.
const sora = localFont({
  src: [{ path: "./fonts/sora/sora-latin-wght-normal.woff2", weight: "100 800", style: "normal" }],
  variable: "--font-sora",
});

const plexSans = localFont({
  src: [
    {
      path: "./fonts/ibm-plex-sans/ibm-plex-sans-latin-400-normal.woff2",
      weight: "400",
      style: "normal",
    },
    {
      path: "./fonts/ibm-plex-sans/ibm-plex-sans-latin-500-normal.woff2",
      weight: "500",
      style: "normal",
    },
    {
      path: "./fonts/ibm-plex-sans/ibm-plex-sans-latin-600-normal.woff2",
      weight: "600",
      style: "normal",
    },
    {
      path: "./fonts/ibm-plex-sans/ibm-plex-sans-latin-700-normal.woff2",
      weight: "700",
      style: "normal",
    },
  ],
  variable: "--font-plex",
});

const jetbrainsMono = localFont({
  src: [
    {
      path: "./fonts/jetbrains-mono/jetbrains-mono-latin-wght-normal.woff2",
      weight: "100 800",
      style: "normal",
    },
  ],
  variable: "--font-jetbrains",
  // A metric-matched Arial fallback suits a proportional face, not a monospace one.
  adjustFontFallback: false,
  fallback: ["ui-monospace", "SFMono-Regular", "Menlo", "Consolas", "monospace"],
});

export const metadata: Metadata = {
  title: {
    default: "Kazi — Practice management, built for Africa",
    template: "%s | Kazi",
  },
  description:
    "Time tracking, invoicing, compliance, and profitability for South African professional services firms.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <AuthProvider>
      <html lang="en" suppressHydrationWarning>
        <body
          className={`${sora.variable} ${plexSans.variable} ${jetbrainsMono.variable} antialiased`}
        >
          <KaziThemeProvider>
            <a
              href="#main-content"
              className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-[100] focus:rounded-md focus:bg-slate-950 focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-white focus:shadow-lg"
            >
              Skip to content
            </a>
            {children}
            <ThemedToaster />
          </KaziThemeProvider>
        </body>
      </html>
    </AuthProvider>
  );
}
