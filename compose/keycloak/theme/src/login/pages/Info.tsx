import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { kcSanitize } from "keycloakify/lib/kcSanitize";
import { Layout } from "./shared/Layout";

export default function Info(
  props: PageProps<Extract<KcContext, { pageId: "info.ftl" }>, I18n>
) {
  const { kcContext, i18n } = props;
  const { message, messageHeader, requiredActions, skipLink, pageRedirectUri, actionUri, client } =
    kcContext;
  const { advancedMsgStr } = i18n;

  const title = messageHeader ?? "Information";

  // Determine navigation link: pageRedirectUri > actionUri > client.baseUrl
  const linkUrl = pageRedirectUri ?? actionUri ?? client?.baseUrl;

  return (
    <Layout title={title}>
      {/* Message from Keycloak (sanitized via kcSanitize — Keycloak's built-in HTML sanitizer) */}
      <div className="mb-6 rounded-md border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-700">
        <span
          // kcSanitize is Keycloak's built-in HTML sanitizer for server messages
          dangerouslySetInnerHTML={{ __html: kcSanitize(message.summary) }}
        />
      </div>

      {/* Required actions */}
      {requiredActions && requiredActions.length > 0 && (
        <div className="mb-6">
          <p className="mb-2 text-sm font-medium text-slate-700">Required actions:</p>
          <ul className="list-disc space-y-1 pl-5 text-sm text-slate-600">
            {requiredActions.map((action) => (
              <li key={action}>{advancedMsgStr(`requiredAction.${action}`)}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Navigation link */}
      {!skipLink && linkUrl && (
        <div className="text-center">
          <a
            href={linkUrl}
            className="inline-flex h-9 w-full items-center justify-center rounded-full bg-slate-950 px-4 text-sm font-medium text-white transition-all hover:bg-slate-950/90 active:scale-[0.97]"
          >
            {pageRedirectUri
              ? "Back to application"
              : actionUri
                ? "Click here to proceed"
                : "Back to application"}
          </a>
        </div>
      )}
    </Layout>
  );
}
