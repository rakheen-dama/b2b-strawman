import { redirect } from "next/navigation";
import { SignIn } from "@clerk/nextjs";
import { AuthPage } from "@/components/auth-page";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

export default function SignInPage() {
  if (AUTH_MODE === "mock") {
    redirect("/mock-login");
  }

  return (
    <AuthPage heading="Welcome back" subtitle="Sign in to your workspace">
      <SignIn
        routing="path"
        path="/sign-in"
        signUpUrl="/sign-up"
        forceRedirectUrl="/dashboard"
      />
    </AuthPage>
  );
}
