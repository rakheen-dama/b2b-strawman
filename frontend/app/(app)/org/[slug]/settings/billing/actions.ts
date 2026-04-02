"use server";

import { api } from "@/lib/api";
import type {
  BillingResponse,
  SubscribeResponse,
  PaymentResponse,
} from "@/lib/internal-api";

interface PaginatedResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

export async function getSubscription(): Promise<BillingResponse> {
  return api.get<BillingResponse>("/api/billing/subscription");
}

export async function subscribe(): Promise<SubscribeResponse> {
  return api.post<SubscribeResponse>("/api/billing/subscribe");
}

export async function cancelSubscription(): Promise<BillingResponse> {
  return api.post<BillingResponse>("/api/billing/cancel");
}

export async function getPayments(): Promise<
  PaginatedResponse<PaymentResponse>
> {
  return api.get<PaginatedResponse<PaymentResponse>>("/api/billing/payments");
}
