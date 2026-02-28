import type { Metadata } from "next";
import { NuqsAdapter } from "nuqs/adapters/next/app";
import { AuthProvider } from "@/lib/auth/client/auth-provider";
import { Toaster } from "sonner";
import { Sora, IBM_Plex_Sans, JetBrains_Mono } from "next/font/google";
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

export const metadata: Metadata = {
  title: {
    default: "DocTeams",
    template: "%s | DocTeams",
  },
  description: "Multi-tenant document management for teams",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <AuthProvider>
      <html lang="en">
        <body
          className={`${sora.variable} ${plexSans.variable} ${jetbrainsMono.variable} antialiased`}
        >
          <NuqsAdapter>{children}</NuqsAdapter>
          <Toaster position="bottom-right" />
        </body>
      </html>
    </AuthProvider>
  );
}
