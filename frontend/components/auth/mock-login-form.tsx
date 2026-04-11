"use client";

import { useState } from "react";

interface MockLoginFormProps {
  mockIdpUrl: string;
}

interface SeedUser {
  userId: string;
  label: string;
}

const SEED_USERS: SeedUser[] = [
  { userId: "user_e2e_alice", label: "Alice (Owner)" },
  { userId: "user_e2e_bob", label: "Bob (Admin)" },
  { userId: "user_e2e_carol", label: "Carol (Member)" },
];

const DEFAULT_ORG_SLUG = "e2e-test-org";

export function MockLoginForm({ mockIdpUrl }: MockLoginFormProps) {
  const [selectedUser, setSelectedUser] = useState(SEED_USERS[0]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`${mockIdpUrl}/token`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          userId: selectedUser.userId,
          orgSlug: DEFAULT_ORG_SLUG,
        }),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || `Token request failed (${response.status})`);
      }

      const { access_token } = await response.json();
      document.cookie = `mock-auth-token=${access_token}; path=/; SameSite=Lax`;
      // Full page reload so MockAuthContextProvider remounts and reads the new cookie
      window.location.href = `/org/${DEFAULT_ORG_SLUG}/dashboard`;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign-in failed");
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label
          htmlFor="user-select"
          className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300"
        >
          Select User
        </label>
        <select
          id="user-select"
          value={selectedUser.userId}
          onChange={(e) => {
            const user = SEED_USERS.find((u) => u.userId === e.target.value);
            if (user) setSelectedUser(user);
          }}
          className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:border-teal-500 focus:ring-1 focus:ring-teal-500 focus:outline-none dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100"
        >
          {SEED_USERS.map((user) => (
            <option key={user.userId} value={user.userId}>
              {user.label}
            </option>
          ))}
        </select>
      </div>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

      <button
        type="submit"
        disabled={loading}
        className="w-full rounded-md bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 focus:ring-2 focus:ring-teal-500 focus:ring-offset-2 focus:outline-none disabled:opacity-50"
      >
        {loading ? "Signing in..." : "Sign In"}
      </button>
    </form>
  );
}
