import { Footer, Layout, Navbar } from "nextra-theme-docs";
import { Head } from "nextra/components";
import { getPageMap } from "nextra/page-map";
import { Sora, IBM_Plex_Sans, JetBrains_Mono } from "next/font/google";
import Image from "next/image";
import type { ReactNode } from "react";
import "nextra-theme-docs/style.css";
import "./globals.css";

const sora = Sora({
  variable: "--font-sora",
  subsets: ["latin"],
});

const plexSans = IBM_Plex_Sans({
  weight: ["400", "500", "600", "700"],
  variable: "--font-plex",
  subsets: ["latin"],
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-jetbrains",
  subsets: ["latin"],
});

export const metadata = {
  title: "HeyKazi Docs",
  description:
    "Documentation for HeyKazi — professional services management platform",
};

export default async function RootLayout({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <html lang="en" dir="ltr" suppressHydrationWarning>
      <Head />
      <body
        className={`${sora.variable} ${plexSans.variable} ${jetbrainsMono.variable} antialiased`}
      >
        <Layout
          navbar={
            <Navbar
              logo={
                <span className="flex items-center gap-2 font-display font-bold">
                  <Image
                    src="/logo.svg"
                    alt="HeyKazi"
                    width={120}
                    height={32}
                    className="h-6 w-auto"
                  />
                  Docs
                </span>
              }
            />
          }
          pageMap={await getPageMap()}
          // Planned public repo URL — will resolve once heykazi/heykazi is published
          docsRepositoryBase="https://github.com/heykazi/heykazi"
          footer={
            <Footer>
              &copy; {new Date().getFullYear()} HeyKazi. All rights reserved.
            </Footer>
          }
        >
          {children}
        </Layout>
      </body>
    </html>
  );
}
