"use client";

import { useState } from "react";
import { Upload, Palette } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

interface BrandingSettingsFormProps {
  slug: string;
  logoUrl: string | null;
  brandColor: string;
  footerText: string;
  canEdit: boolean;
}

export function BrandingSettingsForm({
  slug,
  logoUrl: initialLogoUrl,
  brandColor: initialBrandColor,
  footerText: initialFooterText,
  canEdit,
}: BrandingSettingsFormProps) {
  const [logoUrl] = useState(initialLogoUrl);
  const [brandColor, setBrandColor] = useState(initialBrandColor);
  const [footerText, setFooterText] = useState(initialFooterText);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function handleSave() {
    setIsSaving(true);
    setMessage(null);
    try {
      const res = await fetch("/api/settings", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ brandColor, documentFooterText: footerText }),
      });
      if (res.ok) {
        setMessage("Branding saved successfully.");
      } else {
        setMessage("Failed to save branding.");
      }
    } catch {
      setMessage("Failed to save branding.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Logo Upload */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Logo</h2>
        <p className="mt-1 text-sm text-slate-500">
          Your organization logo appears on generated documents.
        </p>

        <div className="mt-4 flex items-center gap-6">
          <div className="flex size-20 items-center justify-center rounded-lg border-2 border-dashed border-slate-200 bg-slate-50">
            {logoUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={logoUrl}
                alt="Organization logo"
                className="size-16 object-contain"
              />
            ) : (
              <Upload className="size-6 text-slate-300" />
            )}
          </div>
          {canEdit && (
            <Button variant="outline" size="sm" disabled>
              Upload logo
            </Button>
          )}
        </div>
      </div>

      {/* Brand Color */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Brand Color</h2>
        <p className="mt-1 text-sm text-slate-500">
          Used as the accent color in generated documents and templates.
        </p>

        <div className="mt-4 flex items-center gap-4">
          <div className="flex items-center gap-3">
            <Palette className="size-4 text-slate-400" />
            <div
              className="size-8 rounded-md border border-slate-200"
              style={{ backgroundColor: brandColor }}
            />
            <Input
              type="text"
              value={brandColor}
              onChange={(e) => setBrandColor(e.target.value)}
              disabled={!canEdit}
              className="w-28"
              placeholder="#0d9488"
            />
          </div>
          <Input
            type="color"
            value={brandColor}
            onChange={(e) => setBrandColor(e.target.value)}
            disabled={!canEdit}
            className="h-9 w-12 cursor-pointer border-0 p-0"
          />
        </div>
      </div>

      {/* Footer Text */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">
          Document Footer
        </h2>
        <p className="mt-1 text-sm text-slate-500">
          Text displayed at the bottom of generated documents (e.g., company
          registration info).
        </p>

        <div className="mt-4 space-y-2">
          <Label htmlFor="footer-text">Footer text</Label>
          <Textarea
            id="footer-text"
            value={footerText}
            onChange={(e) => setFooterText(e.target.value)}
            disabled={!canEdit}
            rows={3}
            placeholder="Company Reg. No. 2024/123456/07 | VAT No. 4012345678"
          />
        </div>
      </div>

      {/* Save */}
      {canEdit && (
        <div className="flex items-center gap-4">
          <Button onClick={handleSave} disabled={isSaving} size="sm">
            {isSaving ? "Saving..." : "Save branding"}
          </Button>
          {message && (
            <p
              className={`text-sm ${
                message.includes("success")
                  ? "text-emerald-600"
                  : "text-red-600"
              }`}
            >
              {message}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
