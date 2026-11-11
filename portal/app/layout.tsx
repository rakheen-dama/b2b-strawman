import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";

// G89-F46: self-hosted with next/font/local (no build-time Google Fonts download). Latin subset
// woff2 from @fontsource 5.3.0 (Sora and JetBrains Mono variable, wght 100–800; IBM Plex Sans
// 400–700 static); SIL OFL 1.1, licence beside each file in ./fonts/<family>/OFL.txt.
const sora = localFont({
  src: [
    {
      path: "./fonts/sora/sora-latin-wght-normal.woff2",
      weight: "100 800",
      style: "normal",
    },
  ],
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
  fallback: [
    "ui-monospace",
    "SFMono-Regular",
    "Menlo",
    "Consolas",
    "monospace",
  ],
});

export const metadata: Metadata = {
  title: {
    default: "Client Portal",
    template: "%s | Client Portal",
  },
  description: "Client portal for document management and collaboration",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body
        className={`${sora.variable} ${plexSans.variable} ${jetbrainsMono.variable} antialiased`}
      >
        {children}
      </body>
    </html>
  );
}
