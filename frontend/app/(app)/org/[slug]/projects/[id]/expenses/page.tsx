import { getAuthContext, getCurrentUserEmail } from "@/lib/auth";
import { api } from "@/lib/api";
import { ExpenseList } from "@/components/expenses/expense-list";
import { LogExpenseDialog } from "@/components/expenses/log-expense-dialog";
import { Button } from "@/components/ui/button";
import { Receipt } from "lucide-react";
import type {
  PaginatedExpenseResponse,
  ProjectMember,
  Task,
  OrgMember,
} from "@/lib/types";

export default async function ProjectExpensesPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await getAuthContext();

  let expenses: PaginatedExpenseResponse = {
    content: [],
    page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
  };
  try {
    expenses = await api.get<PaginatedExpenseResponse>(
      `/api/projects/${id}/expenses?sort=date,desc`,
    );
  } catch {
    // Non-fatal: show empty list
  }

  let members: ProjectMember[] = [];
  try {
    members = await api.get<ProjectMember[]>(`/api/projects/${id}/members`);
  } catch {
    // Non-fatal
  }

  let tasks: Task[] = [];
  try {
    tasks = await api.get<Task[]>(`/api/projects/${id}/tasks`);
  } catch {
    // Non-fatal
  }

  // Resolve current user's backend member ID
  let currentMemberId: string | null = null;
  try {
    const [email, orgMembers] = await Promise.all([
      getCurrentUserEmail(),
      api.get<OrgMember[]>("/api/members"),
    ]);
    if (email) {
      const match = orgMembers.find((m) => m.email === email);
      if (match) currentMemberId = match.id;
    }
  } catch {
    // Non-fatal
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">
          Expenses
        </h2>
        <LogExpenseDialog
          slug={slug}
          projectId={id}
          tasks={tasks.map((t) => ({ id: t.id, title: t.title }))}
        >
          <Button size="sm">
            <Receipt className="mr-1.5 size-4" />
            Log Expense
          </Button>
        </LogExpenseDialog>
      </div>

      <ExpenseList
        expenses={expenses.content}
        slug={slug}
        projectId={id}
        tasks={tasks.map((t) => ({ id: t.id, title: t.title }))}
        members={members.map((m) => ({ id: m.memberId, name: m.name }))}
        currentMemberId={currentMemberId}
        orgRole={orgRole}
      />
    </div>
  );
}
