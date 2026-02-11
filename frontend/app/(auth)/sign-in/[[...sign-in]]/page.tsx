import { SignIn } from "@clerk/nextjs";
import { AuthPage } from "@/components/auth-page";

export default function SignInPage() {
  return (
    <AuthPage heading="Welcome back" subtitle="Sign in to your workspace">
      <SignIn routing="path" path="/sign-in" signUpUrl="/sign-up" forceRedirectUrl="/dashboard" />
    </AuthPage>
  );
}
