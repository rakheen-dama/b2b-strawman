import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Layout } from "./shared/Layout";

export default function LoginResetPassword(
  props: PageProps<Extract<KcContext, { pageId: "login-reset-password.ftl" }>, I18n>
) {
  const { kcContext } = props;
  const { url, message } = kcContext;

  return (
    <Layout title="Reset your password">
      <p className="mb-6 text-sm text-slate-600">
        Enter your email address and we'll send you a link to reset your password.
      </p>

      {message && (
        <div
          className={`mb-4 rounded-md px-4 py-3 text-sm ${
            message.type === "error"
              ? "bg-red-50 text-red-700 border border-red-200"
              : message.type === "success"
                ? "bg-green-50 text-green-700 border border-green-200"
                : "bg-blue-50 text-blue-700 border border-blue-200"
          }`}
        >
          {message.summary}
        </div>
      )}

      <form action={url.loginAction} method="post" className="space-y-4">
        <div>
          <label htmlFor="username" className="mb-1.5 block text-sm font-medium text-slate-700">
            Email
          </label>
          <input
            id="username"
            name="username"
            type="text"
            autoComplete="email"
            autoFocus
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        <button
          type="submit"
          className="inline-flex h-9 w-full items-center justify-center rounded-full bg-slate-950 px-4 text-sm font-medium text-white transition-all hover:bg-slate-950/90 active:scale-[0.97]"
        >
          Reset Password
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-slate-600">
        <a href={url.loginUrl} className="font-medium text-teal-600 hover:text-teal-700">
          Back to sign in
        </a>
      </p>
    </Layout>
  );
}
