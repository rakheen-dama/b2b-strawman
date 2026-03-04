import { lazy, Suspense } from "react";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "keycloakify/login/Template";
import UserProfileFormFields from "keycloakify/login/UserProfileFormFields";

const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const LoginResetPassword = lazy(() => import("./pages/LoginResetPassword"));
const LoginVerifyEmail = lazy(() => import("./pages/LoginVerifyEmail"));
const ErrorPage = lazy(() => import("./pages/Error"));
const Info = lazy(() => import("./pages/Info"));

export function KcPage(props: { kcContext: KcContext }) {
  const { kcContext } = props;
  const { i18n } = useI18n({ kcContext });

  return (
    <Suspense>
      {(() => {
        switch (kcContext.pageId) {
          case "login.ftl":
            return (
              <Login
                kcContext={kcContext}
                i18n={i18n}
                Template={Template}
                classes={{}}
                doUseDefaultCss={false}
              />
            );
          case "register.ftl":
            return (
              <Register
                kcContext={kcContext}
                i18n={i18n}
                Template={Template}
                classes={{}}
                doUseDefaultCss={false}
              />
            );
          case "login-reset-password.ftl":
            return (
              <LoginResetPassword
                kcContext={kcContext}
                i18n={i18n}
                Template={Template}
                classes={{}}
                doUseDefaultCss={false}
              />
            );
          case "login-verify-email.ftl":
            return (
              <LoginVerifyEmail
                kcContext={kcContext}
                i18n={i18n}
                Template={Template}
                classes={{}}
                doUseDefaultCss={false}
              />
            );
          case "error.ftl":
            return (
              <ErrorPage
                kcContext={kcContext}
                i18n={i18n}
                Template={Template}
                classes={{}}
                doUseDefaultCss={false}
              />
            );
          case "info.ftl":
            return (
              <Info
                kcContext={kcContext}
                i18n={i18n}
                Template={Template}
                classes={{}}
                doUseDefaultCss={false}
              />
            );
          default:
            return (
              <DefaultPage
                kcContext={kcContext}
                i18n={i18n}
                classes={{}}
                Template={Template}
                doUseDefaultCss={true}
                UserProfileFormFields={UserProfileFormFields}
                doMakeUserConfirmPassword={true}
              />
            );
        }
      })()}
    </Suspense>
  );
}
