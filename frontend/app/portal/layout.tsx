import type { Metadata } from "next";

export const metadata: Metadata = {
  title: {
    default: "Portal | Kazi",
    template: "%s | Portal | Kazi",
  },
  description: "Customer portal for viewing shared documents and projects",
};

export default function PortalRootLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
