"use client";

import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
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
import { CheckCircle2, Loader2 } from "lucide-react";
import {
  submitAccessRequest,
  verifyAccessRequestOtp,
} from "@/app/request-access/actions";
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

function formatTime(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export function RequestAccessForm() {
  const [step, setStep] = useState(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [otpError, setOtpError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [otp, setOtp] = useState("");
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [fields, setFields] = useState<FormFields>({
    email: "",
    fullName: "",
    organizationName: "",
    country: "",
    industry: "",
  });

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const interval = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [secondsLeft]);

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

  const handleResend = useCallback(async () => {
    setOtpError(null);
    setError(null);
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
        setSecondsLeft((result.expiresInMinutes ?? 10) * 60);
        setOtp("");
      } else {
        setError(result.error ?? "Failed to resend code.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }, [fields]);

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
        setSecondsLeft((result.expiresInMinutes ?? 10) * 60);
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

  async function handleVerify(e: React.FormEvent) {
    e.preventDefault();
    setOtpError(null);
    setIsVerifying(true);

    try {
      const result = await verifyAccessRequestOtp(fields.email.trim(), otp);

      if (result.success) {
        setStep(3);
      } else {
        setOtpError(result.error ?? "Verification failed.");
      }
    } catch {
      setOtpError("An unexpected error occurred.");
    } finally {
      setIsVerifying(false);
    }
  }

  if (step === 3) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="font-display text-lg">
            <span className="flex items-center gap-2">
              <CheckCircle2 className="size-5 text-green-600" />
              Request Submitted
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Your access request has been submitted for review. We&apos;ll notify
            you by email once it&apos;s been reviewed.
          </p>
          <Link
            href="/"
            className="inline-block text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
          >
            Back to home
          </Link>
        </CardContent>
      </Card>
    );
  }

  if (step === 2) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="font-display text-lg">
            Check Your Email
          </CardTitle>
          <CardDescription>
            {successMessage}
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
          <form onSubmit={handleVerify} className="mt-4 space-y-4">
            <div className="space-y-2">
              <Label htmlFor="otp">Verification Code</Label>
              <Input
                id="otp"
                type="text"
                inputMode="numeric"
                maxLength={6}
                pattern="[0-9]*"
                placeholder="000000"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
                className="text-center text-2xl tracking-[0.5em] font-mono"
                aria-invalid={otpError ? true : undefined}
                autoFocus
              />
              {otpError && (
                <p className="text-sm text-red-600" role="alert">{otpError}</p>
              )}
            </div>

            {secondsLeft > 0 ? (
              <p className="text-center text-xs text-slate-500">
                Code expires in {formatTime(secondsLeft)}
              </p>
            ) : (
              <p className="text-center text-xs text-red-600">
                Code expired.{" "}
                <button
                  type="button"
                  onClick={handleResend}
                  disabled={isSubmitting}
                  className="font-medium text-teal-600 hover:text-teal-700 underline disabled:opacity-50"
                >
                  Resend code
                </button>
              </p>
            )}

            {secondsLeft > 0 && (
              <p className="text-center text-xs text-slate-500">
                Didn&apos;t receive it?{" "}
                <button
                  type="button"
                  onClick={handleResend}
                  disabled={isSubmitting}
                  className="font-medium text-teal-600 hover:text-teal-700 underline disabled:opacity-50"
                >
                  Resend code
                </button>
              </p>
            )}

            {error && (
              <div
                role="alert"
                className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400"
              >
                {error}
              </div>
            )}

            <Button
              type="submit"
              variant="accent"
              size="lg"
              className="w-full"
              disabled={otp.length !== 6 || isVerifying || secondsLeft <= 0}
            >
              {isVerifying ? (
                <>
                  <Loader2 className="animate-spin" />
                  Verifying...
                </>
              ) : (
                "Verify"
              )}
            </Button>
          </form>
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
            <div
              role="alert"
              className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400"
            >
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
