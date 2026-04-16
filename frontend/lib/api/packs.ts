import "server-only";

import { api } from "./client";

// === Types ===

export type PackType = "DOCUMENT_TEMPLATE" | "AUTOMATION_TEMPLATE";

export interface PackCatalogEntry {
  packId: string;
  name: string;
  description: string;
  version: string;
  type: PackType;
  verticalProfile: string | null;
  itemCount: number;
  installed: boolean;
  installedAt: string | null;
}

export interface UninstallCheck {
  canUninstall: boolean;
  blockingReason: string | null;
}

export interface PackInstallResponse {
  id: string;
  packId: string;
  packType: string;
  packVersion: string;
  packName: string;
  installedAt: string;
  installedByMemberId: string | null;
  itemCount: number;
}

// === API Functions ===

export async function listPackCatalog(opts?: {
  all?: boolean;
}): Promise<PackCatalogEntry[]> {
  const searchParams = new URLSearchParams();
  if (opts?.all) searchParams.set("all", "true");
  const qs = searchParams.toString();
  return api.get<PackCatalogEntry[]>(`/api/packs/catalog${qs ? `?${qs}` : ""}`);
}

export async function listInstalledPacks(): Promise<PackCatalogEntry[]> {
  return api.get<PackCatalogEntry[]>("/api/packs/installed");
}

export async function checkPackUninstallable(
  packId: string
): Promise<UninstallCheck> {
  return api.get<UninstallCheck>(`/api/packs/${packId}/uninstall-check`);
}

export async function installPack(
  packId: string
): Promise<PackInstallResponse> {
  return api.post<PackInstallResponse>(`/api/packs/${packId}/install`);
}

export async function uninstallPack(packId: string): Promise<void> {
  return api.delete<void>(`/api/packs/${packId}`);
}
