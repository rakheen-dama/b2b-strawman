import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { Layout } from "./shared/Layout";

export default function LoginUpdatePassword(
  props: PageProps<Extract<KcContext, { pageId: "login-update-password.ftl" }>, I18n>
) {
  const { kcContext, i18n } = props;
  const { url, messagesPerField, isAppInitiatedAction, message } = kcContext;
  const { msgStr } = i18n;

  return (
    <Layout title={msgStr("updatePasswordTitle")}>
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

      <form action={url.loginAction} method="post" className="space-y-4">
        <div>
          <label
            htmlFor="password-new"
            className="mb-1.5 block text-sm font-medium text-slate-700"
          >
            {msgStr("passwordNew")}
          </label>
          <input
            id="password-new"
            name="password-new"
            type="password"
            autoComplete="new-password"
            autoFocus
            aria-invalid={messagesPerField.existsError("password", "password-confirm")}
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
          {messagesPerField.existsError("password") && (
            <span
              className="mt-1 block text-sm text-red-600"
              aria-live="polite"
              dangerouslySetInnerHTML={{
                __html: kcSanitize(messagesPerField.get("password")),
              }}
            />
          )}
        </div>

        <div>
          <label
            htmlFor="password-confirm"
            className="mb-1.5 block text-sm font-medium text-slate-700"
          >
            {msgStr("passwordConfirm")}
          </label>
          <input
            id="password-confirm"
            name="password-confirm"
            type="password"
            autoComplete="new-password"
            aria-invalid={messagesPerField.existsError("password", "password-confirm")}
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
          {messagesPerField.existsError("password-confirm") && (
            <span
              className="mt-1 block text-sm text-red-600"
              aria-live="polite"
              dangerouslySetInnerHTML={{
                __html: kcSanitize(messagesPerField.get("password-confirm")),
              }}
            />
          )}
        </div>

        {isAppInitiatedAction && (
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <input
              type="checkbox"
              id="logout-sessions"
              name="logout-sessions"
              value="on"
              defaultChecked
              className="size-4 rounded border-slate-300"
            />
            {msgStr("logoutOtherSessions")}
          </label>
        )}

        <button
          type="submit"
          className="inline-flex h-9 w-full items-center justify-center rounded-full bg-slate-950 px-4 text-sm font-medium text-white transition-all hover:bg-slate-950/90 active:scale-[0.97]"
        >
          {msgStr("doSubmit")}
        </button>

        {isAppInitiatedAction && (
          <button
            type="submit"
            name="cancel-aia"
            value="true"
            className="inline-flex h-9 w-full items-center justify-center rounded-full border border-slate-200 px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            {msgStr("doCancel")}
          </button>
        )}
      </form>
    </Layout>
  );
}
