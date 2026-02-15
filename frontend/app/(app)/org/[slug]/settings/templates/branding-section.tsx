"use client";

import { useState, useRef } from "react";
import { Upload, X, Palette } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { OrgSettings } from "@/lib/types";

interface BrandingSectionProps {
  slug: string;
  settings: OrgSettings;
  onUploadLogo: (file: File) => Promise<{ success: boolean; error?: string }>;
  onDeleteLogo: () => Promise<{ success: boolean; error?: string }>;
  onSaveBranding: (
    brandColor: string,
    footerText: string,
  ) => Promise<{ success: boolean; error?: string }>;
}

const HEX_COLOR_REGEX = /^#[0-9a-fA-F]{6}$/;

export function BrandingSection({
  settings,
  onUploadLogo,
  onDeleteLogo,
  onSaveBranding,
}: BrandingSectionProps) {
  const [brandColor, setBrandColor] = useState(settings.brandColor ?? "#000000");
  const [footerText, setFooterText] = useState(
    settings.documentFooterText ?? "",
  );
  const [logoUrl, setLogoUrl] = useState(settings.logoUrl ?? null);
  const [isUploading, setIsUploading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const colorValid = HEX_COLOR_REGEX.test(brandColor);

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 2 * 1024 * 1024) {
      setError("Logo file must be under 2 MB.");
      return;
    }

    setIsUploading(true);
    setError(null);

    try {
      const result = await onUploadLogo(file);
      if (result.success) {
        // Force a visual update — the URL will be refreshed on next page load
        setLogoUrl(URL.createObjectURL(file));
        setSuccess("Logo uploaded successfully.");
        setTimeout(() => setSuccess(null), 3000);
      } else {
        setError(result.error ?? "Failed to upload logo.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsUploading(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function handleDeleteLogo() {
    setIsUploading(true);
    setError(null);

    try {
      const result = await onDeleteLogo();
      if (result.success) {
        setLogoUrl(null);
        setSuccess("Logo removed.");
        setTimeout(() => setSuccess(null), 3000);
      } else {
        setError(result.error ?? "Failed to remove logo.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsUploading(false);
    }
  }

  async function handleSave() {
    if (!colorValid) {
      setError("Brand color must be a valid hex color (e.g., #1a2b3c).");
      return;
    }

    setIsSaving(true);
    setError(null);

    try {
      const result = await onSaveBranding(brandColor, footerText);
      if (result.success) {
        setSuccess("Branding settings saved.");
        setTimeout(() => setSuccess(null), 3000);
      } else {
        setError(result.error ?? "Failed to save branding settings.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-6 rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <div className="flex items-center gap-2">
        <Palette className="size-5 text-slate-600 dark:text-slate-400" />
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Branding
        </h2>
      </div>

      <p className="text-sm text-slate-600 dark:text-slate-400">
        Customize your organization&apos;s branding for generated documents.
      </p>

      {/* Logo */}
      <div className="space-y-3">
        <Label>Logo</Label>
        <div className="flex items-center gap-4">
          {logoUrl ? (
            <div className="relative">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={logoUrl}
                alt="Organization logo"
                className="size-16 rounded-lg border border-slate-200 object-contain dark:border-slate-800"
              />
              <button
                type="button"
                onClick={handleDeleteLogo}
                disabled={isUploading}
                className="absolute -right-2 -top-2 rounded-full bg-slate-950 p-0.5 text-white hover:bg-slate-700 dark:bg-slate-50 dark:text-slate-950 dark:hover:bg-slate-300"
              >
                <X className="size-3" />
              </button>
            </div>
          ) : (
            <div className="flex size-16 items-center justify-center rounded-lg border-2 border-dashed border-slate-300 dark:border-slate-700">
              <Upload className="size-6 text-slate-400" />
            </div>
          )}
          <div>
            <input
              ref={fileRef}
              type="file"
              accept="image/png,image/jpeg,image/svg+xml"
              onChange={handleFileChange}
              className="hidden"
              id="logo-upload"
            />
            <Button
              variant="soft"
              size="sm"
              onClick={() => fileRef.current?.click()}
              disabled={isUploading}
            >
              {isUploading ? "Uploading..." : "Upload Logo"}
            </Button>
            <p className="mt-1 text-xs text-slate-400">
              PNG, JPG, or SVG. Max 2 MB.
            </p>
          </div>
        </div>
      </div>

      {/* Brand Color */}
      <div className="space-y-2">
        <Label htmlFor="brand-color">Brand Color</Label>
        <div className="flex items-center gap-3">
          <input
            type="color"
            value={brandColor}
            onChange={(e) => setBrandColor(e.target.value)}
            className="size-9 cursor-pointer rounded border border-slate-200 dark:border-slate-800"
          />
          <Input
            id="brand-color"
            value={brandColor}
            onChange={(e) => setBrandColor(e.target.value)}
            placeholder="#000000"
            className="max-w-32 font-mono"
          />
          {!colorValid && brandColor && (
            <span className="text-xs text-destructive">
              Must be a valid hex color
            </span>
          )}
        </div>
      </div>

      {/* Footer Text */}
      <div className="space-y-2">
        <Label htmlFor="footer-text">Document Footer Text</Label>
        <Textarea
          id="footer-text"
          value={footerText}
          onChange={(e) => setFooterText(e.target.value)}
          placeholder="Text that appears at the bottom of generated documents..."
          rows={3}
          maxLength={500}
        />
        <p className="text-xs text-slate-400">
          {footerText.length}/500 characters
        </p>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
      {success && <p className="text-sm text-teal-600">{success}</p>}

      <div className="flex justify-end">
        <Button onClick={handleSave} disabled={isSaving || !colorValid}>
          {isSaving ? "Saving..." : "Save Branding"}
        </Button>
      </div>
    </div>
  );
}
