import type { PageProps } from "keycloakify/login/pages/PageProps";
import type { KcContext } from "../KcContext";
import type { I18n } from "../i18n";
import { Layout } from "./shared/Layout";

export default function LoginPageExpired(
  props: PageProps<Extract<KcContext, { pageId: "login-page-expired.ftl" }>, I18n>
) {
  const { kcContext } = props;
  const { url } = kcContext;

  return (
    <Layout title="This page has expired">
      <p className="mb-6 text-sm text-slate-600">
        For your security, this page has expired.
      </p>
      <div className="space-y-3">
        <a
          href={url.loginRestartFlowUrl}
          className="inline-flex h-9 w-full items-center justify-center rounded-full bg-slate-950 px-4 text-sm font-medium text-white transition-all hover:bg-slate-950/90 active:scale-[0.97]"
        >
          Restart sign-in
        </a>
        <a
          href={url.loginAction}
          className="block text-center text-sm font-medium text-teal-600 hover:text-teal-700"
        >
          Continue where you left off
        </a>
      </div>
    </Layout>
  );
}
