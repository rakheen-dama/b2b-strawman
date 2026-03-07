"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Loader2 } from "lucide-react";
import { submitAccessRequest } from "@/app/request-access/actions";
import {
  BLOCKED_EMAIL_DOMAINS,
  COUNTRIES,
  INDUSTRIES,
} from "@/lib/access-request-data";

interface FormFields {
  email: string;
  fullName: string;
  organizationName: string;
  country: string;
  industry: string;
}

function isBlockedDomain(email: string): boolean {
  const domain = email.split("@")[1]?.toLowerCase();
  return domain ? BLOCKED_EMAIL_DOMAINS.includes(domain) : false;
}

export function RequestAccessForm() {
  const [step, setStep] = useState(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [expiresInMinutes, setExpiresInMinutes] = useState<number | null>(null);
  const [fields, setFields] = useState<FormFields>({
    email: "",
    fullName: "",
    organizationName: "",
    country: "",
    industry: "",
  });

  function updateField(key: keyof FormFields, value: string) {
    setFields((prev) => ({ ...prev, [key]: value }));

    if (key === "email") {
      setEmailError(null);
      if (value && isBlockedDomain(value)) {
        setEmailError("Please use a company email address.");
      }
    }
  }

  function isFormValid(): boolean {
    return (
      fields.email.trim() !== "" &&
      fields.fullName.trim() !== "" &&
      fields.organizationName.trim() !== "" &&
      fields.country !== "" &&
      fields.industry !== "" &&
      !isBlockedDomain(fields.email)
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (isBlockedDomain(fields.email)) {
      setEmailError("Please use a company email address.");
      return;
    }

    setIsSubmitting(true);

    try {
      const result = await submitAccessRequest({
        email: fields.email.trim(),
        fullName: fields.fullName.trim(),
        organizationName: fields.organizationName.trim(),
        country: fields.country,
        industry: fields.industry,
      });

      if (result.success) {
        setSuccessMessage(result.message ?? "Verification code sent.");
        setExpiresInMinutes(result.expiresInMinutes ?? null);
        setStep(2);
      } else {
        setError(result.error ?? "Failed to submit request.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  if (step === 2) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="font-display text-lg">Check Your Email</CardTitle>
          <CardDescription>
            {successMessage}
            {expiresInMinutes != null && (
              <span className="mt-1 block text-xs text-slate-500">
                The code expires in {expiresInMinutes} minutes.
              </span>
            )}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Enter the verification code sent to{" "}
            <span className="font-medium text-slate-900 dark:text-slate-100">
              {fields.email}
            </span>{" "}
            to continue.
          </p>
          {/* OTP input will be added in Epic 300B */}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-display text-lg">Request Access</CardTitle>
        <CardDescription>
          Fill in your details to request access to DocTeams.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="email">Work Email</Label>
            <Input
              id="email"
              type="email"
              placeholder="you@company.com"
              value={fields.email}
              onChange={(e) => updateField("email", e.target.value)}
              aria-invalid={emailError ? true : undefined}
              required
            />
            {emailError && (
              <p className="text-sm text-red-600">{emailError}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="fullName">Full Name</Label>
            <Input
              id="fullName"
              type="text"
              placeholder="Jane Smith"
              value={fields.fullName}
              onChange={(e) => updateField("fullName", e.target.value)}
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="organizationName">Organisation Name</Label>
            <Input
              id="organizationName"
              type="text"
              placeholder="Acme Corp"
              value={fields.organizationName}
              onChange={(e) => updateField("organizationName", e.target.value)}
              required
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="country">Country</Label>
            <Select
              value={fields.country}
              onValueChange={(value) => updateField("country", value)}
            >
              <SelectTrigger id="country" className="w-full">
                <SelectValue placeholder="Select a country" />
              </SelectTrigger>
              <SelectContent>
                {COUNTRIES.map((country) => (
                  <SelectItem key={country} value={country}>
                    {country}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="industry">Industry</Label>
            <Select
              value={fields.industry}
              onValueChange={(value) => updateField("industry", value)}
            >
              <SelectTrigger id="industry" className="w-full">
                <SelectValue placeholder="Select an industry" />
              </SelectTrigger>
              <SelectContent>
                {INDUSTRIES.map((industry) => (
                  <SelectItem key={industry} value={industry}>
                    {industry}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {error && (
            <div className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400">
              {error}
            </div>
          )}

          <Button
            type="submit"
            variant="accent"
            size="lg"
            className="w-full"
            disabled={!isFormValid() || isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="animate-spin" />
                Submitting...
              </>
            ) : (
              "Request Access"
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
