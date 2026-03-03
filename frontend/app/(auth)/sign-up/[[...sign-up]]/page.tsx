import { redirect } from "next/navigation";
import { SignUp } from "@clerk/nextjs";
import { AuthPage } from "@/components/auth-page";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

export default function SignUpPage() {
  if (AUTH_MODE === "mock") {
    redirect("/mock-login");
  }

  if (AUTH_MODE === "keycloak") {
    redirect("/create-org");
  }

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
