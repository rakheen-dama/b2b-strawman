import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Layout } from "./shared/Layout";

export default function LoginVerifyEmail(
  props: PageProps<Extract<KcContext, { pageId: "login-verify-email.ftl" }>, I18n>
) {
  const { kcContext, i18n } = props;
  const { url, user } = kcContext;
  const { msg } = i18n;

  return (
    <Layout title="Check your email">
      {/* Email icon */}
      <div className="mb-6 flex justify-center">
        <div className="flex size-14 items-center justify-center rounded-full bg-teal-600/10">
          <svg
            className="size-7 text-teal-600"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1.5}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M21.75 6.75v10.5a2.25 2.25 0 0 1-2.25 2.25h-15a2.25 2.25 0 0 1-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0 0 19.5 4.5h-15a2.25 2.25 0 0 0-2.25 2.25m19.5 0v.243a2.25 2.25 0 0 1-1.07 1.916l-7.5 4.615a2.25 2.25 0 0 1-2.36 0L3.32 8.91a2.25 2.25 0 0 1-1.07-1.916V6.75"
            />
          </svg>
        </div>
      </div>

      <div className="mb-6 text-center">
        <p className="text-sm text-slate-600">
          {msg("emailVerifyInstruction1")}
          {user?.email && (
            <>
              {" "}
              <span className="font-medium text-slate-900">{user.email}</span>
            </>
          )}
        </p>
        <p className="mt-3 text-sm text-slate-600">
          {msg("emailVerifyInstruction2")}{" "}
          <a
            href={url.loginAction}
            className="font-medium text-teal-600 hover:text-teal-700"
          >
            {msg("doClickHere")}
          </a>{" "}
          {msg("emailVerifyInstruction3")}
        </p>
      </div>
    </Layout>
  );
}
