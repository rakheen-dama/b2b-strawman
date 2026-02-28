import { SignUp } from "@clerk/nextjs";
import { AuthPage } from "@/components/auth-page";

export default function SignUpPage() {
  return (
    <AuthPage
      heading="Create your workspace"
      subtitle="Get started with DocTeams in seconds"
    >
      <SignUp
        routing="path"
        path="/sign-up"
        signInUrl="/sign-in"
        forceRedirectUrl="/dashboard"
      />
    </AuthPage>
  );
}
