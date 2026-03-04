import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { Layout } from "./shared/Layout";

export default function ErrorPage(
  props: PageProps<Extract<KcContext, { pageId: "error.ftl" }>, I18n>
) {
  const { kcContext, i18n } = props;
  const { message, client, skipLink } = kcContext;
  const { msg } = i18n;

  return (
    <Layout title="Something went wrong">
      {/* Error icon */}
      <div className="mb-6 flex justify-center">
        <div className="flex size-14 items-center justify-center rounded-full bg-red-50">
          <svg
            className="size-7 text-red-600"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1.5}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z"
            />
          </svg>
        </div>
      </div>

      {/* Error message (sanitized via kcSanitize — Keycloak's built-in HTML sanitizer) */}
      <div className="mb-6 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
        <span
          // kcSanitize is Keycloak's built-in HTML sanitizer for server messages
          dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }}
        />
      </div>

      {/* Back to application link */}
      {!skipLink && client?.baseUrl && (
        <div className="text-center">
          <a
            href={client.baseUrl}
            className="inline-flex h-9 w-full items-center justify-center rounded-full bg-slate-950 px-4 text-sm font-medium text-white transition-all hover:bg-slate-950/90 active:scale-[0.97]"
          >
            {msg("backToApplication")}
          </a>
        </div>
      )}
    </Layout>
  );
}
