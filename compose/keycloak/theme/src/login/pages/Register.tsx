import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { Layout } from "./shared/Layout";

export default function Register(
  props: PageProps<Extract<KcContext, { pageId: "register.ftl" }>, I18n>
) {
  const { kcContext, i18n } = props;
  const { url, message, profile } = kcContext;
  const { msg, msgStr } = i18n;

  // Preserve form values on validation error via UserProfile attributes
  const attr = profile.attributesByName;

  return (
    <Layout title="Create your account">
      {message && message.type === "error" && (
        <div className="mb-4 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {/* kcSanitize is Keycloak's built-in HTML sanitizer — safe for rendering server messages */}
          <span dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }} />
        </div>
      )}

      <form action={url.registrationAction} method="post" className="space-y-4">
        {/* First name */}
        <div>
          <label htmlFor="firstName" className="mb-1.5 block text-sm font-medium text-slate-700">
            {msgStr("firstName")}
          </label>
          <input
            id="firstName"
            name="firstName"
            type="text"
            autoComplete="given-name"
            defaultValue={attr.firstName?.value ?? ""}
            autoFocus
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Last name */}
        <div>
          <label htmlFor="lastName" className="mb-1.5 block text-sm font-medium text-slate-700">
            {msgStr("lastName")}
          </label>
          <input
            id="lastName"
            name="lastName"
            type="text"
            autoComplete="family-name"
            defaultValue={attr.lastName?.value ?? ""}
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Email */}
        <div>
          <label htmlFor="email" className="mb-1.5 block text-sm font-medium text-slate-700">
            {msgStr("email")}
          </label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            defaultValue={attr.email?.value ?? ""}
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
            autoComplete="new-password"
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Confirm password */}
        <div>
          <label htmlFor="password-confirm" className="mb-1.5 block text-sm font-medium text-slate-700">
            {msgStr("passwordConfirm")}
          </label>
          <input
            id="password-confirm"
            name="password-confirm"
            type="password"
            autoComplete="new-password"
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Submit */}
        <button
          type="submit"
          className="inline-flex h-9 w-full items-center justify-center rounded-full bg-slate-950 px-4 text-sm font-medium text-white transition-all hover:bg-slate-950/90 active:scale-[0.97]"
        >
          {msg("doRegister")}
        </button>
      </form>

      {/* Sign in link */}
      <p className="mt-6 text-center text-sm text-slate-600">
        Already have an account?{" "}
        <a href={url.loginUrl} className="font-medium text-teal-600 hover:text-teal-700">
          {msg("doLogIn")}
        </a>
      </p>
    </Layout>
  );
}
