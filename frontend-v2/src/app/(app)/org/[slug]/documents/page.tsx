import { api, handleApiError } from "@/lib/api";
import type { Document } from "@/lib/types";
import { PageHeader } from "@/components/layout/page-header";
import { DocumentList } from "@/components/documents/document-list";

export default async function DocumentsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let documents: Document[] = [];
  try {
    documents = await api.get<Document[]>("/api/documents");
  } catch (error) {
    handleApiError(error);
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Documents"
        description="Organization documents and files"
        count={documents.length}
      />
      <DocumentList documents={documents} slug={slug} />
    </div>
  );
}
