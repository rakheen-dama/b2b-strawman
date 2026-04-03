import { Footer, Layout, Navbar } from "nextra-theme-docs";
import { Head } from "nextra/components";
import { getPageMap } from "nextra/page-map";
import { Sora, IBM_Plex_Sans, JetBrains_Mono } from "next/font/google";
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
                <span className="font-display font-bold">HeyKazi Docs</span>
              }
              projectLink="https://app.heykazi.com"
            />
          }
          pageMap={await getPageMap()}
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
