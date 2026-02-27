import { AcceptancePage } from "./acceptance-page";

export default async function AcceptTokenPage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = await params;
  return <AcceptancePage token={token} />;
}
