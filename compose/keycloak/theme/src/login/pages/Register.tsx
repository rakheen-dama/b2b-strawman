import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Layout } from "./shared/Layout";

export default function Register(
  props: PageProps<Extract<KcContext, { pageId: "register.ftl" }>, I18n>
) {
  const { kcContext } = props;
  const { url, message } = kcContext;

  return (
    <Layout title="Create your account">
      {message && message.type === "error" && (
        <div className="mb-4 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {message.summary}
        </div>
      )}

      <form action={url.loginAction} method="post" className="space-y-4">
        {/* First name */}
        <div>
          <label htmlFor="firstName" className="mb-1.5 block text-sm font-medium text-slate-700">
            First name
          </label>
          <input
            id="firstName"
            name="firstName"
            type="text"
            autoComplete="given-name"
            autoFocus
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Last name */}
        <div>
          <label htmlFor="lastName" className="mb-1.5 block text-sm font-medium text-slate-700">
            Last name
          </label>
          <input
            id="lastName"
            name="lastName"
            type="text"
            autoComplete="family-name"
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Email */}
        <div>
          <label htmlFor="email" className="mb-1.5 block text-sm font-medium text-slate-700">
            Email
          </label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            className="h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 text-sm outline-none transition-[color,box-shadow] placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30"
          />
        </div>

        {/* Password */}
        <div>
          <label htmlFor="password" className="mb-1.5 block text-sm font-medium text-slate-700">
            Password
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
            Confirm password
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
          Create Account
        </button>
      </form>

      {/* Sign in link */}
      <p className="mt-6 text-center text-sm text-slate-600">
        Already have an account?{" "}
        <a href={url.loginUrl} className="font-medium text-teal-600 hover:text-teal-700">
          Sign in
        </a>
      </p>
    </Layout>
  );
}
