"use server";

import { BLOCKED_EMAIL_DOMAINS } from "@/lib/access-request-data";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
const GATEWAY_URL = process.env.GATEWAY_URL || "http://localhost:8443";
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
const API_BASE = AUTH_MODE === "keycloak" ? GATEWAY_URL : BACKEND_URL;

interface AccessRequestData {
  email: string;
  fullName: string;
  organizationName: string;
  country: string;
  industry: string;
}

interface AccessRequestResult {
  success: boolean;
  message?: string;
  expiresInMinutes?: number;
  error?: string;
}

export async function submitAccessRequest(data: AccessRequestData): Promise<AccessRequestResult> {
  const { email, fullName, organizationName, country, industry } = data;

  if (!email?.trim()) {
    return { success: false, error: "Email is required." };
  }
  if (!fullName?.trim()) {
    return { success: false, error: "Full name is required." };
  }
  if (!organizationName?.trim()) {
    return { success: false, error: "Organisation name is required." };
  }
  if (!country?.trim()) {
    return { success: false, error: "Country is required." };
  }
  if (!industry?.trim()) {
    return { success: false, error: "Industry is required." };
  }

  const emailDomain = email.split("@")[1]?.toLowerCase();
  if (emailDomain && BLOCKED_EMAIL_DOMAINS.includes(emailDomain)) {
    return { success: false, error: "Please use a company email address." };
  }

  try {
    const response = await fetch(`${API_BASE}/api/access-requests`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: email.trim(),
        fullName: fullName.trim(),
        organizationName: organizationName.trim(),
        country: country.trim(),
        industry: industry.trim(),
      }),
    });

    if (response.ok) {
      const body = await response.json();
      return {
        success: true,
        message: body.message,
        expiresInMinutes: body.expiresInMinutes,
      };
    }

    const errorBody = await response.json().catch(() => null);
    const errorMessage = errorBody?.error || errorBody?.message || "Something went wrong.";
    return { success: false, error: errorMessage };
  } catch {
    return {
      success: false,
      error: "Unable to reach the server. Please try again later.",
    };
  }
}

interface VerifyOtpResult {
  success: boolean;
  message?: string;
  error?: string;
}

export async function verifyAccessRequestOtp(email: string, otp: string): Promise<VerifyOtpResult> {
  if (!email?.trim()) {
    return { success: false, error: "Email is required." };
  }
  if (!otp?.trim()) {
    return { success: false, error: "Verification code is required." };
  }

  try {
    const response = await fetch(`${API_BASE}/api/access-requests/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: email.trim(),
        otp: otp.trim(),
      }),
    });

    if (response.ok) {
      const body = await response.json();
      return {
        success: true,
        message: body.message,
      };
    }

    const errorBody = await response.json().catch(() => null);
    const errorMessage =
      errorBody?.detail || errorBody?.error || errorBody?.message || "Something went wrong.";
    return { success: false, error: errorMessage };
  } catch {
    return {
      success: false,
      error: "Unable to reach the server. Please try again later.",
    };
  }
}
