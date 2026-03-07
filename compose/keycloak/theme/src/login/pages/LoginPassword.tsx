import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { Layout } from "./shared/Layout";

export default function LoginPassword(
  props: PageProps<Extract<KcContext, { pageId: "login-password.ftl" }>, I18n>
) {
  const { kcContext, i18n } = props;
  const { url, realm, message } = kcContext;
  const { msg, msgStr } = i18n;

  return (
    <Layout title="Enter your password">
      {/* Error/info message from Keycloak */}
      {message && message.type !== "success" && (
        <div
          className={`mb-4 rounded-md px-4 py-3 text-sm ${
            message.type === "error"
              ? "bg-red-50 text-red-700 border border-red-200"
              : "bg-blue-50 text-blue-700 border border-blue-200"
          }`}
        >
          {/* kcSanitize is Keycloak's built-in HTML sanitizer — safe for rendering server messages */}
          <span dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }} />
        </div>
      )}

      {/* Password form */}
      <form action={url.loginAction} method="post" className="space-y-4">
        <div>
          <label htmlFor="password" className="mb-1.5 block text-sm font-medium text-slate-700">
            {msgStr("password")}
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            autoFocus
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Forgot password link */}
        {realm.resetPasswordAllowed && (
          <div className="flex justify-end">
            <a
              href={url.loginResetCredentialsUrl}
              className="text-sm text-teal-600 hover:text-teal-700"
            >
              {msg("doForgotPassword")}
            </a>
          </div>
        )}

        {/* Submit */}
        <button
          type="submit"
          className="inline-flex h-9 w-full items-center justify-center rounded-full bg-slate-950 px-4 text-sm font-medium text-white transition-all hover:bg-slate-950/90 active:scale-[0.97]"
        >
          {msg("doLogIn")}
        </button>
      </form>
    </Layout>
  );
}
