import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Layout } from "./shared/Layout";

export default function LogoutConfirm(
  props: PageProps<Extract<KcContext, { pageId: "logout-confirm.ftl" }>, I18n>
) {
  const { kcContext, i18n } = props;
  const { url, client, logoutConfirm } = kcContext;
  const { msg, msgStr } = i18n;

  return (
    <Layout title="Sign out">
      <p className="mb-6 text-sm text-slate-600">{msg("logoutConfirmHeader")}</p>

      <form
        action={url.logoutConfirmAction}
        method="post"
        className="space-y-4"
      >
        <input type="hidden" name="session_code" value={logoutConfirm.code} />
        <button
          type="submit"
          name="confirmLogout"
          value="true"
          className="inline-flex h-9 w-full items-center justify-center rounded-full bg-slate-950 px-4 text-sm font-medium text-white transition-all hover:bg-slate-950/90 active:scale-[0.97]"
        >
          {msgStr("doLogout")}
        </button>
      </form>

      {!logoutConfirm.skipLink && client.baseUrl && (
        <p className="mt-6 text-center text-sm text-slate-600">
          <a
            href={client.baseUrl}
            className="font-medium text-teal-600 hover:text-teal-700"
          >
            {msg("backToApplication")}
          </a>
        </p>
      )}
    </Layout>
  );
}
