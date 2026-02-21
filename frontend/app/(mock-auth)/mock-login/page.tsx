import { MockLoginForm } from "@/components/auth/mock-login-form";

const MOCK_IDP_URL = process.env.MOCK_IDP_URL || "http://localhost:8090";

export default function MockLoginPage() {
  return (
    <div className="w-full max-w-sm">
      <h1 className="font-display mb-6 text-center text-xl text-slate-900 dark:text-slate-100">
        E2E Test Login
      </h1>
      <MockLoginForm mockIdpUrl={MOCK_IDP_URL} />
    </div>
  );
}
