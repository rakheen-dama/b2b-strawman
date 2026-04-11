"use client";

import { useState, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Loader2, Upload, X } from "lucide-react";
import {
  updateGeneralSettings,
  updateGeneralTaxSettings,
  uploadLogoAction,
  deleteLogoAction,
} from "@/app/(app)/org/[slug]/settings/general/actions";

const CURRENCIES = [
  { value: "ZAR", label: "ZAR — South African Rand" },
  { value: "USD", label: "USD — US Dollar" },
  { value: "EUR", label: "EUR — Euro" },
  { value: "GBP", label: "GBP — British Pound" },
] as const;

const HEX_COLOR_REGEX = /^#[0-9a-fA-F]{6}$/;

interface GeneralSettingsFormProps {
  slug: string;
  defaultCurrency: string;
  logoUrl: string | null;
  brandColor: string;
  documentFooterText: string;
  taxRegistrationNumber: string;
  taxLabel: string;
  taxInclusive: boolean;
}

export function GeneralSettingsForm({
  slug,
  defaultCurrency: initialCurrency,
  logoUrl: initialLogoUrl,
  brandColor: initialBrandColor,
  documentFooterText: initialFooterText,
  taxRegistrationNumber: initialTaxRegNumber,
  taxLabel: initialTaxLabel,
  taxInclusive: initialTaxInclusive,
}: GeneralSettingsFormProps) {
  // General settings state
  const [currency, setCurrency] = useState(initialCurrency);
  const [brandColor, setBrandColor] = useState(initialBrandColor);
  const [footerText, setFooterText] = useState(initialFooterText);

  // Tax settings state
  const [taxRegNumber, setTaxRegNumber] = useState(initialTaxRegNumber);
  const [taxLabel, setTaxLabel] = useState(initialTaxLabel);
  const [taxInclusive, setTaxInclusive] = useState(initialTaxInclusive);

  // Logo state
  const [logoUrl, setLogoUrl] = useState<string | null>(initialLogoUrl);
  const [isUploading, setIsUploading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  // Form state
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  const colorValid = !brandColor || HEX_COLOR_REGEX.test(brandColor);

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 2 * 1024 * 1024) {
      setMessage("Logo file must be under 2 MB.");
      setIsError(true);
      return;
    }

    setIsUploading(true);
    setMessage(null);

    const formData = new FormData();
    formData.append("file", file);

    try {
      const result = await uploadLogoAction(slug, formData);
      if (result.success) {
        setLogoUrl(URL.createObjectURL(file));
        setMessage("Logo uploaded successfully.");
        setIsError(false);
        setTimeout(() => setMessage(null), 3000);
      } else {
        setMessage(result.error ?? "Failed to upload logo.");
        setIsError(true);
      }
    } catch {
      setMessage("An unexpected error occurred.");
      setIsError(true);
    } finally {
      setIsUploading(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function handleDeleteLogo() {
    setIsUploading(true);
    setMessage(null);

    try {
      const result = await deleteLogoAction(slug);
      if (result.success) {
        setLogoUrl(null);
        setMessage("Logo removed.");
        setIsError(false);
        setTimeout(() => setMessage(null), 3000);
      } else {
        setMessage(result.error ?? "Failed to remove logo.");
        setIsError(true);
      }
    } catch {
      setMessage("An unexpected error occurred.");
      setIsError(true);
    } finally {
      setIsUploading(false);
    }
  }

  async function handleSave() {
    if (!colorValid) {
      setMessage("Brand color must be a valid hex color (e.g., #1a2b3c).");
      setIsError(true);
      return;
    }

    setSaving(true);
    setMessage(null);
    setIsError(false);

    const [settingsResult, taxResult] = await Promise.all([
      updateGeneralSettings(slug, {
        defaultCurrency: currency,
        brandColor: brandColor || undefined,
        documentFooterText: footerText || undefined,
      }),
      updateGeneralTaxSettings(slug, {
        taxRegistrationNumber: taxRegNumber || undefined,
        taxLabel: taxLabel || undefined,
        taxInclusive,
      }),
    ]);

    if (settingsResult.success && taxResult.success) {
      setMessage("Settings saved successfully.");
      setIsError(false);
      setTimeout(() => setMessage(null), 3000);
    } else {
      const errors = [
        !settingsResult.success && settingsResult.error,
        !taxResult.success && taxResult.error,
      ]
        .filter(Boolean)
        .join(" ");
      setMessage(errors || "Failed to save settings.");
      setIsError(true);
    }

    setSaving(false);
  }

  return (
    <div className="space-y-8">
      {/* Currency */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Currency</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Set the default currency for invoices, rates, and financial reports.
        </p>
        <div className="mt-4 max-w-xs">
          <Label htmlFor="default-currency">Default Currency</Label>
          <Select value={currency} onValueChange={setCurrency}>
            <SelectTrigger id="default-currency" className="mt-1" data-testid="default-currency">
              <SelectValue placeholder="Select currency" />
            </SelectTrigger>
            <SelectContent>
              {CURRENCIES.map((c) => (
                <SelectItem key={c.value} value={c.value}>
                  {c.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Tax Settings */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Tax Configuration
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure your organization&apos;s tax registration and display settings.
        </p>
        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <Label htmlFor="tax-reg-number">Tax Registration Number</Label>
            <Input
              id="tax-reg-number"
              type="text"
              maxLength={50}
              placeholder="e.g. VAT-123456789"
              value={taxRegNumber}
              onChange={(e) => setTaxRegNumber(e.target.value)}
              className="mt-1 w-full"
            />
          </div>
          <div>
            <Label htmlFor="tax-label">Tax Label</Label>
            <Input
              id="tax-label"
              type="text"
              maxLength={20}
              placeholder="e.g. VAT, GST, Tax"
              value={taxLabel}
              onChange={(e) => setTaxLabel(e.target.value)}
              className="mt-1 w-full"
            />
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Label shown on invoices and documents (e.g. &quot;VAT&quot;, &quot;GST&quot;).
            </p>
          </div>
          <div className="flex items-center gap-3 self-center">
            <Switch id="tax-inclusive" checked={taxInclusive} onCheckedChange={setTaxInclusive} />
            <Label htmlFor="tax-inclusive">Tax-inclusive pricing</Label>
          </div>
        </div>
      </div>

      {/* Branding */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Branding</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Customize your organization&apos;s branding for generated documents.
        </p>

        {/* Logo */}
        <div className="mt-4 space-y-3">
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
                  aria-label="Remove logo"
                  onClick={handleDeleteLogo}
                  disabled={isUploading}
                  className="absolute -top-2 -right-2 rounded-full bg-slate-950 p-0.5 text-white hover:bg-slate-700 dark:bg-slate-50 dark:text-slate-950 dark:hover:bg-slate-300"
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
              <p className="mt-1 text-xs text-slate-400">PNG, JPG, or SVG. Max 2 MB.</p>
            </div>
          </div>
        </div>

        {/* Brand Color */}
        <div className="mt-4 space-y-2">
          <Label htmlFor="brand-color">Brand Color</Label>
          <div className="flex items-center gap-3">
            <input
              type="color"
              value={brandColor || "#000000"}
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
              <span className="text-destructive text-xs">Must be a valid hex color</span>
            )}
          </div>
        </div>

        {/* Footer Text */}
        <div className="mt-4 space-y-2">
          <Label htmlFor="footer-text">Document Footer Text</Label>
          <Textarea
            id="footer-text"
            value={footerText}
            onChange={(e) => setFooterText(e.target.value)}
            placeholder="Text that appears at the bottom of generated documents..."
            rows={3}
            maxLength={500}
          />
          <p className="text-xs text-slate-400">{footerText.length}/500 characters</p>
        </div>
      </div>

      {/* Status Message + Save */}
      <div className="flex items-center gap-3">
        <Button disabled={saving || !colorValid} onClick={handleSave}>
          {saving && <Loader2 className="mr-1.5 size-4 animate-spin" />}
          Save Settings
        </Button>
        {message && (
          <p
            className={`text-sm ${isError ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"}`}
          >
            {message}
          </p>
        )}
      </div>
    </div>
  );
}
