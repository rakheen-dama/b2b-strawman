import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { Layout } from "./shared/Layout";

export default function Login(
  props: PageProps<Extract<KcContext, { pageId: "login.ftl" }>, I18n>
) {
  const { kcContext, i18n } = props;
  const { url, realm, social, login, message } = kcContext;
  const { msg, msgStr } = i18n;

  return (
    <Layout title="Sign in to DocTeams">
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

      {/* Social login */}
      {social?.providers && social.providers.length > 0 && (
        <div className="mb-6">
          {social.providers.map((provider) => (
            <a
              key={provider.alias}
              href={provider.loginUrl}
              className="flex w-full items-center justify-center gap-3 rounded-full border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
            >
              {provider.providerId === "google" && (
                <svg className="size-5" viewBox="0 0 24 24">
                  <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4" />
                  <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
                  <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
                  <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
                </svg>
              )}
              {msgStr("doLogIn")} with {provider.displayName}
            </a>
          ))}

          {/* Divider */}
          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-slate-200" />
            </div>
            <div className="relative flex justify-center text-sm">
              <span className="bg-white px-4 text-slate-400">or continue with email</span>
            </div>
          </div>
        </div>
      )}

      {/* Login form */}
      <form action={url.loginAction} method="post" className="space-y-4">
        {/* Email */}
        <div>
          <label htmlFor="username" className="mb-1.5 block text-sm font-medium text-slate-700">
            {msgStr("email")}
          </label>
          <input
            id="username"
            name="username"
            type="text"
            autoComplete="username"
            defaultValue={login.username ?? ""}
            autoFocus
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Password */}
        <div>
          <label htmlFor="password" className="mb-1.5 block text-sm font-medium text-slate-700">
            {msgStr("password")}
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Remember me + Forgot password */}
        <div className="flex items-center justify-between">
          {realm.rememberMe && (
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input
                name="rememberMe"
                type="checkbox"
                defaultChecked={!!login.rememberMe}
                className="size-4 rounded border-slate-300"
              />
              {msg("rememberMe")}
            </label>
          )}
          {realm.resetPasswordAllowed && (
            <a
              href={url.loginResetCredentialsUrl}
              className="text-sm text-teal-600 hover:text-teal-700"
            >
              {msg("doForgotPassword")}
            </a>
          )}
        </div>

        {/* Submit */}
        <button
          type="submit"
          name="login"
          className="inline-flex h-9 w-full items-center justify-center rounded-full bg-slate-950 px-4 text-sm font-medium text-white transition-all hover:bg-slate-950/90 active:scale-[0.97]"
        >
          {msg("doLogIn")}
        </button>
      </form>

      {/* Register link */}
      {realm.registrationAllowed && (
        <p className="mt-6 text-center text-sm text-slate-600">
          Don&apos;t have an account?{" "}
          <a href={url.registrationUrl} className="font-medium text-teal-600 hover:text-teal-700">
            {msg("doRegister")}
          </a>
        </p>
      )}
    </Layout>
  );
}
