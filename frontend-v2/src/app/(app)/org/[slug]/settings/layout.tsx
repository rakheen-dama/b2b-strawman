import type { ReactNode } from "react";

export default async function SettingsRootLayout({
  children,
}: {
  children: ReactNode;
}) {
  return <>{children}</>;
}
